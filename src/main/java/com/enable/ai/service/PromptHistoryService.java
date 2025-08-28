package com.enable.ai.service;

import com.enable.ai.util.Constants;
import com.enable.ai.util.PromptConstants;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptHistoryService {

    @Autowired
    private ChatClient chatClient;

    public List<String> compressUserPromptHistories(List<String> userPromptHistories) {
        if (CollectionUtils.isEmpty(userPromptHistories)) {
            return userPromptHistories;
        }

        if (userPromptHistories.size() > Constants.MAX_USER_PROMPT_HISTORIES_SESSION_SIZE) {
            return compressUserPromptHistoriesToString(userPromptHistories);
        }
        long totalSize = userPromptHistories.stream().mapToLong(String::length).sum();
        if (totalSize > Constants.MAX_USER_PROMPT_HISTORIES_TOKEN_SIZE) {
            return compressUserPromptHistoriesToString(userPromptHistories);
        }
        return userPromptHistories;
    }

    private List<String> compressUserPromptHistoriesToString(List<String> userPromptHistories) {
        List<String> compressedList = Lists.newArrayList();
        StringBuilder sb = new StringBuilder();
        for (String history : userPromptHistories) {
            sb.append(history).append("\n");
        }
        String combinedHistories = sb.toString();

        String compressionPrompt = PromptConstants.PROMPT_COMPRESS_HISTORY + "\n" + combinedHistories;

        ChatClient.CallResponseSpec aiResponse = chatClient
                .prompt(compressionPrompt)
                .call();

        compressedList.add(aiResponse.content());

        return compressedList;
    }
}
