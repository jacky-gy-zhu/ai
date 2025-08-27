package com.enable.ai.web.controller;

import com.enable.ai.service.RagService;
import com.enable.ai.service.McpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.Builder;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class ChatController {

    private final ChatClient chatClient;
    private final SyncMcpToolCallbackProvider toolCallbackProvider;
    private final RagService ragService;
    private final McpService mcpService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ChatController(Builder chatClientBuilder,
                          SyncMcpToolCallbackProvider toolCallbackProvider,
                          RagService ragService, McpService mcpService) {
        this.chatClient = chatClientBuilder.build();
        this.toolCallbackProvider = toolCallbackProvider;
        this.ragService = ragService;
        this.mcpService = mcpService;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping(value = "/chat/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public String chatSession(
            @RequestParam("prompt") String prompt,
            @RequestParam("userId") long userId,
            HttpSession session) {

        try {
            // 1. Get RAG document context
            String ragDocumentContext = ragService.retrieveContext("mcp_schema", prompt, 3);

            // 2. Get relevant historical conversation context
//            String ragHistoryContext = ragService.getRelevantHistoryContext(prompt, userId);

            // 3. Build current conversation message list (excluding full history)
            List<Message> currentMessages = new ArrayList<>();

            // 4. Build system message with RAG context and relevant history
            String systemMessage = buildSystemMessageWithRagAndHistory(ragDocumentContext, "");
            currentMessages.add(SystemMessage.builder().text(systemMessage).build());

            // 5. Add current user message
            currentMessages.add(UserMessage.builder().text("User ID: " + userId + "\n" + prompt).build());

            // 6. Call AI to get response with selective tool loading
            Prompt promptObj = new Prompt(currentMessages);
            ChatClient.CallResponseSpec aiResponse = chatClient
                    .prompt(promptObj)
                    .toolCallbacks(mcpService.findRelatedToolCallbacks(prompt, 5))
                    .call();

            String content = aiResponse.content();

            // 7. Store this conversation (question + answer) to RAG system
            if (content != null && !content.isEmpty()) {
//                ragService.storeConversationToRag(userId, prompt, content);
            }

            // 8. Build structured response
            return buildChatResponse(content, prompt, userId);

        } catch (Exception e) {
            return buildErrorResponse("Processing error", "Failed to process chat request: " + e.getMessage());
        }
    }

    /**
     * Build system message containing RAG document content and relevant historical conversations
     */
    private String buildSystemMessageWithRagAndHistory(String ragDocumentContext, String ragHistoryContext) {
        StringBuilder systemMessage = new StringBuilder();

        systemMessage.append("""
                You must respond with ONLY a valid JSON object in the following format:
                {
                  "contentType": "text|list|table|code|error|long_text",
                  "title": "Response title (optional)",
                  "content": {
                    "text": "Main response text",
                    "items": [...] (for lists),
                    "headers": [...] (for structured content),
                    "codeBlocks": [...] (for code content),
                    "tables": [...] (for tabular data),
                    "sections": [...] (for long content)
                  },
                  "metadata": {
                    "hasError": false,
                    "dataSource": "mcp|rag|general",
                    "confidence": "high|medium|low"
                  }
                }
                
                Guidelines:
                - Always return valid JSON, no markdown or HTML
                - Use contentType to indicate the primary content type
                - For dates, use yyyy-MM-dd format
                - Today's date is: %s
                - When calling MCP server, set dataSource to "mcp"
                - For lists, put items in the "items" array
                - For errors, set hasError to true and contentType to "error"
                - For code, put code blocks in "codeBlocks" array
                - For tables, structure data in "tables" array with headers and rows
                
                """.formatted(new Date()));

        // Add document RAG context
        if (!ragDocumentContext.isEmpty()) {
            systemMessage.append("\n=== Relevant Documentation ===\n");
            systemMessage.append(ragDocumentContext);
        }

        // Add historical conversation RAG context
        if (!ragHistoryContext.isEmpty()) {
            systemMessage.append("\n=== Relevant History ===\n");
            systemMessage.append(ragHistoryContext);
        }

        if (!ragDocumentContext.isEmpty() || !ragHistoryContext.isEmpty()) {
            systemMessage.append("\nPlease answer user questions based on the above documentation and historical conversation information. Set dataSource to 'rag' when using this information.\n");
        }

        systemMessage.append("\nCRITICAL: Respond with ONLY the JSON object. No explanations, markdown, or additional text.");

        return systemMessage.toString();
    }

    /**
     * Build structured chat response in JSON format
     */
    private String buildChatResponse(String content, String prompt, long userId) {
        try {
            Map<String, Object> response = new HashMap<>();

            response.put("success", true);
            response.put("type", "chat");
            response.put("timestamp", new Date().toString());

            // Try to parse AI's JSON response
            JsonNode aiResponse = null;
            try {
                aiResponse = objectMapper.readTree(content);
            } catch (Exception e) {
                // If AI didn't return valid JSON, treat as plain text
                System.err.println("AI response is not valid JSON, treating as plain text: " + e.getMessage());
            }

            if (aiResponse != null) {
                // AI returned structured JSON - use it directly
                Map<String, Object> contentData = new HashMap<>();
                contentData.put("contentType", aiResponse.has("contentType") ? aiResponse.get("contentType").asText() : "text");
                contentData.put("title", aiResponse.has("title") ? aiResponse.get("title").asText() : null);

                // Extract content object
                if (aiResponse.has("content")) {
                    contentData.put("content", objectMapper.convertValue(aiResponse.get("content"), Map.class));
                } else {
                    contentData.put("content", Map.of("text", content));
                }

                // Extract AI metadata
                Map<String, Object> aiMetadata = new HashMap<>();
                if (aiResponse.has("metadata")) {
                    aiMetadata = objectMapper.convertValue(aiResponse.get("metadata"), Map.class);
                }

                response.put("content", contentData);
                response.put("aiMetadata", aiMetadata);
            } else {
                // Fallback: treat as plain text
                Map<String, Object> contentData = new HashMap<>();
                contentData.put("contentType", "text");
                contentData.put("content", Map.of("text", content != null ? content : ""));
                response.put("content", contentData);
            }

            // Add system metadata
            Map<String, Object> systemMetadata = new HashMap<>();
            systemMetadata.put("userId", userId);
            systemMetadata.put("promptLength", prompt.length());
            systemMetadata.put("responseLength", content != null ? content.length() : 0);
            systemMetadata.put("isStructured", aiResponse != null);
            response.put("systemMetadata", systemMetadata);

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return buildErrorResponse("Response formatting error", "Failed to format chat response: " + e.getMessage());
        }
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