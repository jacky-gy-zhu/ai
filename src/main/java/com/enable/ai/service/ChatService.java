package com.enable.ai.service;

import com.enable.ai.util.Constants;
import com.enable.ai.util.PromptConstants;
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

import java.util.List;

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

    public String chatWithReactMode(long userId, String userPrompt) {
        return chatWithReactModeInternal(userId, userPrompt, 1, "task");
    }

    private String chatWithReactModeInternal(long userId, String userPrompt, int depth, String promptXmlTag) {
        if (depth > 20) {
            return "Error: Exceeded maximum reasoning depth.";
        }
        log.info("\n### [CHAT BEGIN {}] #############################################################################", depth);
        String answer = chatWithUserHistory(userId, PromptConstants.SYSTEM_PROMPT_REACT_MODE, addXmlTagToUserPrompt(userPrompt, promptXmlTag));
        log.info("\n### [CHAT END {}] #############################################################################", depth);
        if (isFinalAnswerPresent(answer)) {
            String finalAnswer = convertToFinalAnswer(answer);
            promptRagService.addUserPromptToCollection(Constants.USER_PROMPTS_COLLECTION_NAME, userId, "Question: " + userPrompt + "\nAnswer: " + finalAnswer);
            return finalAnswer;
        } else {
            return chatWithReactModeInternal(userId, answer, depth + 1, null);
        }
    }

    private static String addXmlTagToUserPrompt(String userPrompt, String promptXmlTag) {
        return promptXmlTag != null ? ("<" + promptXmlTag + ">" + userPrompt + "</" + promptXmlTag + ">") : userPrompt;
    }

    private boolean isFinalAnswerPresent(String answer) {
        return answer.contains("<final_answer>");
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

    public String chatWithPlanMode(long userId, String userPrompt) {
        String answer = chatWithUserHistory(userId, PromptConstants.SYSTEM_PROMPT_PLAN_MODE, userPrompt);
        promptRagService.addUserPromptToCollection(Constants.USER_PROMPTS_COLLECTION_NAME, userId, "Q: " + userPrompt + "\nA: " + answer);
        return answer;
    }

    private String chatWithNoHistory(String systemPrompt, String userPrompt) {
        return chat(null, systemPrompt, userPrompt);
    }

    private String chatWithUserHistory(long userId, String systemPrompt, String userPrompt) {
        return chat(userId, systemPrompt, userPrompt);
    }

    private String chat(Long userId, String systemPrompt, String userPrompt) {
        List<Message> currentMessages = Lists.newArrayList();

        currentMessages.add(SystemMessage.builder().text(systemPrompt).build());

        StringBuilder userPromptBuilder = new StringBuilder();

        if (userId != null) {
            List<String> userPromptHistories = promptRagService.findRelatedUserPrompts(Constants.USER_PROMPTS_COLLECTION_NAME, userId, userPrompt, 20);
            userPromptHistories = promptHistoryService.compressUserPromptHistories(userPromptHistories);
            if (CollectionUtils.isNotEmpty(userPromptHistories)) {
                userPromptBuilder.append("\n").append("Here are some of your previous related conversations:");
                for (int i = userPromptHistories.size() - 1; i >= 0; i--) {
                    userPromptBuilder.append("\n").append(userPromptHistories.get(i));
                }
            }
        }
        userPromptBuilder.append("\n").append("Now, please help to complete the following task or conversation:");
        userPromptBuilder.append("\n").append(userPrompt);
        currentMessages.add(UserMessage.builder().text(userPromptBuilder.toString()).build());

        log.info("\n>>> System prompt: \n{}", systemPrompt);
        log.info("\n>>> User prompt: \n{}", userPromptBuilder);

        Prompt promptObj = new Prompt(currentMessages);
        ToolCallback[] toolCallbacks = mcpService.findRelatedToolCallbacks(userPrompt, 5);

        log.info("\n>>> [{} tools] registered.", toolCallbacks.length);
        for (ToolCallback callback : toolCallbacks) {
            log.info("\n>>> Tool: {}", callback.getToolDefinition());
        }

        ChatClient.CallResponseSpec aiResponse = chatClient
                .prompt(promptObj)
                .toolCallbacks(toolCallbacks)
                .call();

        String response = aiResponse.content();

        log.info("\n>>> [AI response]: \n{}", response);

        return response;
    }

}
