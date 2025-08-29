package com.enable.ai.agents;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiAgent {

    String chat(long userId, String userPrompt);

    String streamChat(long userId, String userPrompt, SseEmitter emitter);
}
