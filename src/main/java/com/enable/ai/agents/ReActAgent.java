package com.enable.ai.agents;

import com.enable.ai.service.ChatService;
import com.enable.ai.service.PromptRagService;
import com.enable.ai.service.SseService;
import com.enable.ai.util.Constants;
import com.enable.ai.util.PromptConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
public class ReActAgent {

    @Autowired
    public ChatService chatService;

    @Autowired
    private PromptRagService promptRagService;

    @Autowired
    private SseService sseService;

    public String chat(long userId, String userPrompt) {
        String finalAnswer = chatInternal(userId, userPrompt, 1, "task");
        promptRagService.addUserPromptToCollection(Constants.USER_PROMPTS_COLLECTION_NAME, userId, "Question: " + userPrompt + "\nAnswer: " + finalAnswer);
        return finalAnswer;
    }

    private String chatInternal(long userId, String userPrompt, int depth, String promptXmlTag) {
        if (depth > 20) {
            return "Error: Exceeded maximum reasoning depth.";
        }
        log.info("\n### [CHAT BEGIN {}] #############################################################################", depth);
        String answer = chatService.chat(userId, PromptConstants.SYSTEM_PROMPT_REACT_MODE, addXmlTagToUserPrompt(userPrompt, promptXmlTag));
        log.info("\n### [CHAT END {}] #############################################################################", depth);
        if (isFinalAnswerPresent(answer)) {
            return convertToFinalAnswer(answer);
        } else {
            return chatInternal(userId, answer, depth + 1, null);
        }
    }

    public void streamChat(long userId, String userPrompt, SseEmitter emitter) {
        // 清空之前的推理步骤记录
        sseService.getSentReasoningSteps().get().clear();
        try {
            String finalAnswer = streamChatInternal(userId, userPrompt, 1, "task", emitter);

            // 保存到RAG
            promptRagService.addUserPromptToCollection(Constants.USER_PROMPTS_COLLECTION_NAME, userId,
                    "Question: " + userPrompt + "\nAnswer: " + finalAnswer);

            // 发送完成事件
            sseService.sendEvent(emitter, "done", Map.of("final_content", finalAnswer));
            emitter.complete();
        } catch (Exception e) {
            log.error("Error in stream chat", e);
            try {
                sseService.sendEvent(emitter, "error", Map.of("message", "处理聊天时出错: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                log.error("Error sending error event", ioException);
            }
        } finally {
            // 清理ThreadLocal
            sseService.getSentReasoningSteps().remove();
        }
    }

    private String streamChatInternal(long userId, String userPrompt,
                                      int depth, String promptXmlTag, SseEmitter emitter) throws IOException {
        if (depth > 20) {
            sseService.sendEvent(emitter, "error", Map.of("message", "超过最大推理深度"));
            return "Error: Exceeded maximum reasoning depth.";
        }

        log.info("\n### [STREAM CHAT BEGIN {}] #########################################################################", depth);

        String answer = chatService.streamChat(userId, PromptConstants.SYSTEM_PROMPT_REACT_MODE, addXmlTagToUserPrompt(userPrompt, promptXmlTag), emitter);

        log.info("\n### [STREAM CHAT END {}] ###########################################################################", depth);

        if (isFinalAnswerPresent(answer)) {
            return convertToFinalAnswer(answer);
        } else {
            return streamChatInternal(userId, answer, depth + 1, null, emitter);
        }
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

    private boolean isFinalAnswerPresent(String answer) {
        return answer.contains("<final_answer>");
    }

    private String addXmlTagToUserPrompt(String userPrompt, String promptXmlTag) {
        return promptXmlTag != null ? ("<" + promptXmlTag + ">" + userPrompt + "</" + promptXmlTag + ">") : userPrompt;
    }
}
