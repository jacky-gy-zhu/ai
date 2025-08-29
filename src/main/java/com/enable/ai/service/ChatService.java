package com.enable.ai.service;

import com.enable.ai.rag.vo.SortType;
import com.enable.ai.util.Constants;
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

@Slf4j
@Service
public class ChatService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private McpService mcpService;

    @Autowired
    private PromptRagService promptRagService;

    @Autowired
    private PromptHistoryService promptHistoryService;

    @Autowired
    private SseService sseService;

    public String chat(Long userId, String systemPrompt, String userPrompt) {
        return chat(userId, systemPrompt, userPrompt, true);
    }

    public String chat(Long userId, String systemPrompt, String userPrompt, boolean includesTools) {
        List<Message> currentMessages = Lists.newArrayList();

        currentMessages.add(SystemMessage.builder().text(systemPrompt).build());

        StringBuilder userPromptBuilder = new StringBuilder();

        if (userId != null) {
            List<String> userPromptHistories = promptRagService.findRelatedUserPrompts(Constants.USER_PROMPTS_COLLECTION_NAME, userId, userPrompt, 20, SortType.TIMESTAMP);
            userPromptHistories = promptHistoryService.compressUserPromptHistories(userPromptHistories, userId);
            if (CollectionUtils.isNotEmpty(userPromptHistories)) {
                userPromptBuilder.append("\n").append("Here are some of your previous related conversations:");
                for (String historyPrompt : userPromptHistories) {
                    userPromptBuilder.append("\n").append(historyPrompt);
                }
            }
        }
        userPromptBuilder.append("\n").append("### User Prompt:");
        userPromptBuilder.append("\n").append(userPrompt);
        currentMessages.add(UserMessage.builder().text(userPromptBuilder.toString()).build());

        log.info("\n>>> System prompt: \n{}", systemPrompt);
        log.info("\n>>> User prompt: \n{}", userPromptBuilder);

        Prompt promptObj = new Prompt(currentMessages);
        ChatClient.CallResponseSpec aiResponse;

        if (includesTools) {

            ToolCallback[] toolCallbacks = mcpService.findRelatedToolCallbacks(userPrompt, 5);

            log.info("\n>>> [{} tools] registered.", toolCallbacks.length);
            for (ToolCallback callback : toolCallbacks) {
                log.info("\n>>> Tool: {}", callback.getToolDefinition());
            }

            aiResponse = chatClient
                    .prompt(promptObj)
                    .toolCallbacks(toolCallbacks)
                    .call();
        } else {
            aiResponse = chatClient
                    .prompt(promptObj)
                    .call();
        }

        String response = aiResponse.content();

        log.info("\n>>> [AI response]: \n{}", response);

        return response;
    }

    public String streamChat(long userId, String systemPrompt, String userPrompt, SseEmitter emitter) throws IOException {
        return streamChat(userId, systemPrompt, userPrompt, emitter, true);
    }

    public String streamChat(long userId, String systemPrompt, String userPrompt, SseEmitter emitter, boolean includesTools) throws IOException {
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
        userPromptBuilder.append("\n").append("### User Prompt:");
        userPromptBuilder.append("\n").append(userPrompt);
        currentMessages.add(UserMessage.builder().text(userPromptBuilder.toString()).build());

        log.info("\n>>> System prompt: \n{}", systemPrompt);
        log.info("\n>>> User prompt: \n{}", userPromptBuilder);

        Prompt promptObj = new Prompt(currentMessages);
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = chatClient.prompt(promptObj);

        if (includesTools) {

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
                sseService.sendToolsEvent(emitter, tools);
            }

            chatClientRequestSpec.toolCallbacks(toolCallbacks);
        }


        // 使用流式响应
        StringBuilder responseBuilder = new StringBuilder();

        //                        String accumulated = responseBuilder.toString();
        //                        sseService.sendEvent(emitter, "content", Map.of(
        //                                "delta", chunk,
        //                                "accumulated", accumulated
        //                        ));
        chatClientRequestSpec
                .stream()
                .content()
                .doOnNext(responseBuilder::append)
                .doOnError(error -> {
                    log.error("Error in stream", error);
                    sseService.sendErrorEvent(emitter, error.getMessage());
                })
                .blockLast(); // 等待流完成

        String response = responseBuilder.toString();
        log.info("\n>>> [AI response]: \n{}", response);

        return response;
    }


}
