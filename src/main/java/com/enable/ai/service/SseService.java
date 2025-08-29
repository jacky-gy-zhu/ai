package com.enable.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SseService {

    @Autowired
    private ObjectMapper objectMapper;

    // 用于跟踪每次聊天中已发送的推理步骤
    private final ThreadLocal<Set<String>> sentReasoningSteps = ThreadLocal.withInitial(HashSet::new);

    public ThreadLocal<Set<String>> getSentReasoningSteps() {
        return sentReasoningSteps;
    }

    public void sendEvent(SseEmitter emitter, String eventType, Map<String, Object> data) throws IOException {
        Map<String, Object> event = new HashMap<>(data);
        if (!event.containsKey("type")) {
            event.put("type", eventType);
        }
        event.put("event_type", eventType);
        event.put("timestamp", System.currentTimeMillis());

        String jsonData = objectMapper.writeValueAsString(event);
        emitter.send(SseEmitter.event()
                .name(eventType)
                .data(jsonData));
    }

    public void parseAndSendReasoningSteps(String content, SseEmitter emitter) throws IOException {
        Set<String> sent = sentReasoningSteps.get();

        log.debug("Parsing content for reasoning steps. Content length: {}", content.length());

        // 定义各种推理步骤的正则表达式 - 只匹配完整的标签对，避免流式重复
        String[] stepTypes = {"task", "thought", "plan", "action", "observation", "final_answer"};

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
