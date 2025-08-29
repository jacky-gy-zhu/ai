package com.enable.ai.service;

import com.enable.ai.agents.vo.LeadAgentResponse;
import com.enable.ai.agents.vo.ReActAgentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class SseService {

    @Autowired
    private ObjectMapper objectMapper;

    // 用于跟踪每次聊天中已发送的推理步骤
    @Getter
    private final ThreadLocal<Set<String>> sentReasoningSteps = ThreadLocal.withInitial(HashSet::new);

    public void sendEvent(SseEmitter emitter, String eventType, Map<String, Object> data) throws IOException {
        Map<String, Object> event = new HashMap<>(data);
        if (!event.containsKey("type")) {
            event.put("type", eventType);
        }
        event.put("event_type", eventType);
        event.put("timestamp", System.currentTimeMillis());

        String jsonData = objectMapper.writeValueAsString(event);
        try {
            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(jsonData));
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", e.getMessage(), e);
        }
    }

    public void sendEvent(SseEmitter emitter, ReActAgentResponse response) {
        try {
            if (response.hasTask()) {
                sendEvent(emitter, "reasoning_step", Map.of(
                        "type", "task",
                        "content", response.getTask()
                ));
            }
            if (response.hasThought()) {
                sendEvent(emitter, "reasoning_step", Map.of(
                        "type", "thought",
                        "content", response.getThought()
                ));
            }
            if (response.hasAction()) {
                sendEvent(emitter, "reasoning_step", Map.of(
                        "type", "action",
                        "content", response.getAction()
                ));
            }
            if (response.hasObservation()) {
                sendEvent(emitter, "reasoning_step", Map.of(
                        "type", "observation",
                        "content", response.getAction()
                ));
            }
            if (response.hasFinalAnswer()) {
                sendEvent(emitter, "reasoning_step", Map.of(
                        "type", "result",
                        "content", response.getFinalAnswer()
                ));
            }
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", e.getMessage(), e);
        }
    }

    public void sendPlanEvent(SseEmitter emitter, LeadAgentResponse response) {
        try {
            if (response.hasPlan()) {
                sendEvent(emitter, "reasoning_step", Map.of(
                        "type", "plan",
                        "content", response.getPlan()
                ));
            }
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", e.getMessage(), e);
        }
    }

    public void sendNextStepEvent(SseEmitter emitter, LeadAgentResponse response) {
        try {
            if (response.hasNextStep()) {
                sendEvent(emitter, "reasoning_step", Map.of(
                        "type", "task",
                        "content", response.getNextStep()
                ));
            }
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", e.getMessage(), e);
        }
    }

    public void sendResultEvent(SseEmitter emitter, String result) {
        try {
            if (result != null && !result.trim().isEmpty()) {
                sendEvent(emitter, "reasoning_step", Map.of(
                        "type", "result",
                        "content", result
                ));
            }
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", e.getMessage(), e);
        }
    }

    public void sendFinalAnswerEvent(SseEmitter emitter, String finalAnswer) {
        try {
            if (finalAnswer != null && !finalAnswer.trim().isEmpty()) {
                sendEvent(emitter, "done", Map.of("final_content", finalAnswer));
            }
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", e.getMessage(), e);
        }
    }

    public void sendToolsEvent(SseEmitter emitter, Map<String, String> tools) {
        try {
            sendEvent(emitter, "available_tools", Map.of("tools", tools));
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", e.getMessage(), e);
        }
    }

    public void sendErrorEvent(SseEmitter emitter, String error) {
        try {
            sendEvent(emitter, "error", Map.of("message", "流式响应错误: " + error));
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", e.getMessage(), e);
        }
    }

    public void sendMessageEvent(SseEmitter emitter, String message) {
        try {
            sendEvent(emitter, "error", Map.of("message", message));
        } catch (Exception e) {
            log.error("Error sending SSE event: {}", e.getMessage(), e);
        }
    }
}
