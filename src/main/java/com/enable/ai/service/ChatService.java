package com.enable.ai.service;

import com.enable.ai.util.Constants;
import com.enable.ai.util.PromptConstants;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private McpService mcpService;

    @Autowired
    private PromptRagService promptRagService;

    public String chatWithReactMode(long userId, String userPrompt) {
        String answer = chat(userId, PromptConstants.SYSTEM_PROMPT_REACT_MODE, userPrompt);
        promptRagService.addUserPromptToCollection(Constants.USER_PROMPTS_COLLECTION_NAME, userId, "Q: " + userPrompt + "\nA: " + answer);
        return answer;
    }

    public String chatWithPlanMode(long userId, String userPrompt) {
        String answer = chat(userId, PromptConstants.SYSTEM_PROMPT_PLAN_MODE, userPrompt);
        promptRagService.addUserPromptToCollection(Constants.USER_PROMPTS_COLLECTION_NAME, userId, "Q: " + userPrompt + "\nA: " + answer);
        return answer;
    }

    private String chat(long userId, String systemPrompt, String userPrompt) {

        List<Message> currentMessages = Lists.newArrayList();

        currentMessages.add(SystemMessage.builder().text(systemPrompt).build());

        StringBuilder userPromptBuilder = new StringBuilder();

        List<String> userPromptHistories = promptRagService.findRelatedUserPrompts(Constants.USER_PROMPTS_COLLECTION_NAME, userId, userPrompt, 20);
        if (CollectionUtils.isNotEmpty(userPromptHistories)) {
            for (String history : userPromptHistories) {
                userPromptBuilder.append("\n").append(history);
            }
        }
        userPromptBuilder.append("\n").append(userPrompt);
        currentMessages.add(UserMessage.builder().text(userPromptBuilder.toString()).build());

        Prompt promptObj = new Prompt(currentMessages);
        ChatClient.CallResponseSpec aiResponse = chatClient
                .prompt(promptObj)
                .toolCallbacks(mcpService.findRelatedToolCallbacks(userPrompt, 5))
                .call();

        return aiResponse.content();
    }

}
