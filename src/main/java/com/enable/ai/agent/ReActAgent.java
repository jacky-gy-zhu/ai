package com.enable.ai.agent;

import com.enable.ai.agent.vo.ReActAgentResponse;
import com.enable.ai.service.ChatService;
import com.enable.ai.service.PromptRagService;
import com.enable.ai.service.SseService;
import com.enable.ai.util.PromptConstants;
import com.enable.ai.util.XmlTagExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Component
public class ReActAgent implements AiAgent {

    @Autowired
    private ChatService chatService;

    @Autowired
    private PromptRagService promptRagService;

    @Autowired
    private SseService sseService;

    @Override
    public String chat(long userId, String userPrompt) {
        return chatInternal(userId, userPrompt, 1, "task");
    }

    private String chatInternal(long userId, String userPrompt, int depth, String promptXmlTag) {
        if (depth > 20) {
            return "Error: Exceeded maximum reasoning depth.";
        }
        log.info("\n### [CHAT BEGIN {}] #############################################################################", depth);
        String answer = chatService.chat(userId, PromptConstants.SYSTEM_PROMPT_REACT_MODE, XmlTagExtractor.addXmlTagToUserPrompt(userPrompt, promptXmlTag));
        log.info("\n### [CHAT END {}] #############################################################################", depth);
        if (isFinalAnswerPresent(answer)) {
            return convertToFinalAnswer(answer);
        } else {
            return chatInternal(userId, answer, depth + 1, null);
        }
    }

    @Override
    public String streamChat(long userId, String userPrompt, SseEmitter emitter) {
        // 清空之前的推理步骤记录
//        sseService.getSentReasoningSteps().get().clear();
        try {
            String finalAnswer = streamChatInternal(userId, userPrompt, 1, "task", emitter);

            // 发送完成事件
            sseService.sendResultEvent(emitter, finalAnswer);

            return finalAnswer;
        } catch (Exception e) {
            log.error("Error in stream chat", e);
            sseService.sendMessageEvent(emitter, "处理聊天时出错: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private String streamChatInternal(long userId, String userPrompt,
                                      int depth, String promptXmlTag, SseEmitter emitter) throws IOException {
        if (depth > 20) {
            sseService.sendMessageEvent(emitter, "超过最大推理深度");
            return "Error: Exceeded maximum reasoning depth.";
        }

        log.info("\n### [STREAM CHAT BEGIN {}] #########################################################################", depth);

        String answer = chatService.streamChat(userId, PromptConstants.SYSTEM_PROMPT_REACT_MODE, XmlTagExtractor.addXmlTagToUserPrompt(userPrompt, promptXmlTag), emitter);

        log.info("\n### [STREAM CHAT END {}] ###########################################################################", depth);

        ReActAgentResponse response = new ReActAgentResponse(answer);
        sseService.sendEvent(emitter, response);

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
        return XmlTagExtractor.containsTag(answer, "final_answer");
    }
}
