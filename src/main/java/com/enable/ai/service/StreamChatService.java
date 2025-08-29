package com.enable.ai.service;

import com.enable.ai.rag.vo.SortType;
import com.enable.ai.util.Constants;
import com.enable.ai.util.PromptConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class StreamChatService extends AbstractChatService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private McpService mcpService;

    @Autowired
    private PromptRagService promptRagService;

    @Autowired
    private PromptHistoryService promptHistoryService;

    @Autowired
    private ObjectMapper objectMapper;

    // 用于跟踪每次聊天中已发送的推理步骤
    private final ThreadLocal<Set<String>> sentReasoningSteps = ThreadLocal.withInitial(HashSet::new);

    public void chatWithReactModeStream(long userId, String userPrompt, SseEmitter emitter) {
        // 清空之前的推理步骤记录
        sentReasoningSteps.get().clear();
        try {
            String finalAnswer = chatWithReactModeInternalStream(userId, userPrompt, userPrompt, 1, "task", emitter);

            // 保存到RAG
            promptRagService.addUserPromptToCollection(Constants.USER_PROMPTS_COLLECTION_NAME, userId,
                    "Question: " + userPrompt + "\nAnswer: " + finalAnswer);

            // 发送完成事件
            sendEvent(emitter, "done", Map.of("final_content", finalAnswer));
            emitter.complete();
        } catch (Exception e) {
            log.error("Error in stream chat", e);
            try {
                sendEvent(emitter, "error", Map.of("message", "处理聊天时出错: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                log.error("Error sending error event", ioException);
            }
        } finally {
            // 清理ThreadLocal
            sentReasoningSteps.remove();
        }
    }

    private String chatWithReactModeInternalStream(long userId, String userPrompt,
                                                   int depth, String promptXmlTag, SseEmitter emitter) throws IOException {
        if (depth > 20) {
            sendEvent(emitter, "error", Map.of("message", "超过最大推理深度"));
            return "Error: Exceeded maximum reasoning depth.";
        }

        log.info("\n### [STREAM CHAT BEGIN {}] #########################################################################", depth);

        String answer = chatWithUserHistoryStream(userId, PromptConstants.SYSTEM_PROMPT_REACT_MODE, addXmlTagToUserPrompt(userPrompt, promptXmlTag), emitter);

        log.info("\n### [STREAM CHAT END {}] ###########################################################################", depth);

        if (isFinalAnswerPresent(answer)) {
            return convertToFinalAnswer(answer);
        } else {
            return chatWithReactModeInternalStream(userId, answer, depth + 1, null, emitter);
        }
    }

    private String chatWithUserHistoryStream(long userId, String systemPrompt, String userPrompt, SseEmitter emitter) throws IOException {
        List<Message> currentMessages = Lists.newArrayList();

        currentMessages.add(SystemMessage.builder().text(systemPrompt).build());

        StringBuilder userPromptBuilder = new StringBuilder();

        if (userId > 0) {
            List<String> userPromptHistories = promptRagService.findRelatedUserPrompts(
                    Constants.USER_PROMPTS_COLLECTION_NAME, userId, userPrompt, 20, SortType.TIMESTAMP);
            userPromptHistories = promptHistoryService.compressUserPromptHistories(userPromptHistories, userId);
            if (CollectionUtils.isNotEmpty(userPromptHistories)) {
                userPromptBuilder.append("\n").append("Here are some of your previous related conversations:");
                for (String historyPrompt : userPromptHistories) {
                    userPromptBuilder.append("\n").append(historyPrompt);
                }
            }
        }
        userPromptBuilder.append("\n").append("Now, please help to complete the following task or conversation:");
        userPromptBuilder.append("\n").append(userPrompt);
        currentMessages.add(UserMessage.builder().text(userPromptBuilder.toString()).build());

        log.info("\n>>> System prompt: \n{}", systemPrompt);
        log.info("\n>>> User prompt: \n{}", userPromptBuilder);

        Prompt promptObj = new Prompt(currentMessages);
        ToolCallback[] toolCallbacks = mcpService.findRelatedToolCallbacks(userPrompt, 5);

        log.info("\n>>> [{} tools] registered.", toolCallbacks.length);

        // 发送可用工具列表事件
        if (toolCallbacks.length > 0) {
            Map<String, String> tools = new HashMap<>();
            for (ToolCallback callback : toolCallbacks) {
                log.info("\n>>> Tool: {}", callback.getToolDefinition());
                tools.put(callback.getToolDefinition().name(),
                        callback.getToolDefinition().description());
            }
            sendEvent(emitter, "available_tools", Map.of("tools", tools));
        }

        // 使用流式响应
        StringBuilder responseBuilder = new StringBuilder();

        chatClient
                .prompt(promptObj)
                .toolCallbacks(toolCallbacks)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    try {
                        responseBuilder.append(chunk);
                        String accumulated = responseBuilder.toString();

                        // 解析并发送结构化推理步骤
                        parseAndSendReasoningSteps(accumulated, emitter);

                        sendEvent(emitter, "content", Map.of(
                                "delta", chunk,
                                "accumulated", accumulated
                        ));
                    } catch (IOException e) {
                        log.error("Error sending content chunk", e);
                    }
                })
                .doOnError(error -> {
                    log.error("Error in stream", error);
                    try {
                        sendEvent(emitter, "error", Map.of("message", "流式响应错误: " + error.getMessage()));
                    } catch (IOException e) {
                        log.error("Error sending error event", e);
                    }
                })
                .blockLast(); // 等待流完成

        String response = responseBuilder.toString();
        log.info("\n>>> [AI response]: \n{}", response);

        return response;
    }

    private void sendEvent(SseEmitter emitter, String eventType, Map<String, Object> data) throws IOException {
        Map<String, Object> event = new HashMap<>(data);
        event.put("type", eventType);
        event.put("timestamp", System.currentTimeMillis());

        String jsonData = objectMapper.writeValueAsString(event);
        emitter.send(SseEmitter.event()
                .name(eventType)
                .data(jsonData));
    }

    private static String addXmlTagToUserPrompt(String userPrompt, String promptXmlTag) {
        return promptXmlTag != null ? ("<" + promptXmlTag + ">" + userPrompt + "</" + promptXmlTag + ">") : userPrompt;
    }

    private String convertToFinalAnswer(String answer) {
        if (answer.contains("<final_answer>") && answer.contains("</final_answer>")) {
            return answer.substring(answer.indexOf("<final_answer>") + 14, answer.indexOf("</final_answer>")).trim();
        } else if (answer.contains("<final_answer>")) {
            return answer.substring(answer.indexOf("<final_answer>") + 14);
        } else {
            return answer;
        }
    }

    private void parseAndSendReasoningSteps(String content, SseEmitter emitter) throws IOException {
        Set<String> sent = sentReasoningSteps.get();

        log.debug("Parsing content for reasoning steps. Content length: {}", content.length());

        // 定义各种推理步骤的正则表达式 - 只匹配完整的标签对，避免流式重复
        String[] stepTypes = {"task", "thought", "action", "observation", "final_answer"};

        for (String stepType : stepTypes) {
            // 只匹配完整的标签对
            Pattern completePattern = Pattern.compile("<" + stepType + ">(.*?)</" + stepType + ">", Pattern.DOTALL);
            Matcher completeMatcher = completePattern.matcher(content);
            int stepCount = 0;

            while (completeMatcher.find()) {
                stepCount++;
                String stepKey = stepType + "_" + stepCount;

                if (!sent.contains(stepKey)) {
                    String stepContent = completeMatcher.group(1).trim();
                    if (!stepContent.isEmpty()) {
                        log.info("Found complete reasoning step: {} - {}", stepType, stepContent.substring(0, Math.min(50, stepContent.length())));
                        sendEvent(emitter, "reasoning_step", Map.of(
                                "type", stepType,
                                "content", stepContent,
                                "step_number", stepCount
                        ));
                        sent.add(stepKey);
                    }
                }
            }
        }
    }
}
