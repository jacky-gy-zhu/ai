package com.enable.ai.web.controller;

import com.enable.ai.agents.PlanAndExecuteAgent;
import com.enable.ai.agents.ReActAgent;
import com.enable.ai.service.PromptRagService;
import com.enable.ai.service.SseService;
import com.enable.ai.util.Constants;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ChatController {

    @Autowired
    private ReActAgent reActAgent;

    @Autowired
    private SseService sseService;

    @Autowired
    private PlanAndExecuteAgent planAndExecuteAgent;

    @Autowired
    private PromptRagService promptRagService;

    @GetMapping(value = "/chat/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public String chatSession(
            @RequestParam("prompt") String prompt,
            @RequestParam("userId") long userId,
            HttpSession session) {

        try {
            return reActAgent.chat(userId, prompt);
        } catch (Exception e) {
            return ExceptionUtils.getStackTrace(e);
        }
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestParam("prompt") String prompt,
            @RequestParam("userId") long userId) {

        log.info("Starting stream chat for userId: {}, prompt: {}", userId, prompt);

        // 创建SSE发射器，设置超时时间为5分钟
        SseEmitter emitter = new SseEmitter(300_000L);

        // 设置超时和完成回调
        emitter.onTimeout(() -> {
            log.info("SSE connection timed out for userId: {}", userId);
            emitter.complete();
        });

        emitter.onCompletion(() -> {
            log.info("SSE connection completed for userId: {}", userId);
        });

        emitter.onError((ex) -> {
            log.error("SSE connection error for userId: " + userId, ex);
        });

        // 异步处理聊天请求
        new Thread(() -> {
            try {
                String finalAnswer = reActAgent.streamChat(userId, prompt, emitter);
                sseService.sendFinalAnswerEvent(emitter, finalAnswer);
                promptRagService.addUserPromptToCollection(Constants.USER_PROMPTS_COLLECTION_NAME, userId, "Question: " + prompt + "\nAnswer: " + finalAnswer);
            } catch (Exception e) {
                log.error("Error in async chat processing", e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception completeError) {
                    log.error("Error completing emitter with error", completeError);
                }
            } finally {
                sseService.getSentReasoningSteps().remove();
                emitter.complete();
            }
        }).start();

        return emitter;
    }

}