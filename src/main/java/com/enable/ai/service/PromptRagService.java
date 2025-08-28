package com.enable.ai.service;

import java.util.List;

public interface PromptRagService {

    void addUserPromptToCollection(String collectionName, long userId, String prompt);

    List<String> findRelatedUserPrompts(String collectionName, long userId, String query, int k);

    List<String> findAllUserPrompts(String collectionName, long userId);

    void deleteUserPromptsCollection(String collectionName, long userId);
}
