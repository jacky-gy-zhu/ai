package com.enable.ai.web.controller;

import com.enable.ai.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping(value = "/chat/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public String chatSession(
            @RequestParam("prompt") String prompt,
            @RequestParam("userId") long userId,
            HttpSession session) {

        try {
            String content = chatService.chatWithReactMode(userId, prompt);

            return buildChatResponse(content);

        } catch (Exception e) {
            return buildErrorResponse("Processing error", "Failed to process chat request: " + e.getMessage());
        }
    }

    /**
     * Build structured chat response in JSON format
     */
    private String buildChatResponse(String content) {
        // TODO - make this as json and with timestamp, token usage, etc.
        return content;
    }

    /**
     * Build error response in JSON format
     */
    private String buildErrorResponse(String errorType, String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("type", "error");
            response.put("timestamp", new Date().toString());
            response.put("error", errorType);
            response.put("message", message);

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON formatting error\",\"message\":\"Failed to format error response\"}";
        }
    }
}