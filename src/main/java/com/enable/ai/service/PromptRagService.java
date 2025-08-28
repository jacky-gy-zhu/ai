package com.enable.ai.service;

import com.enable.ai.rag.vo.SortType;

import java.util.List;

public interface PromptRagService {

    void addUserPromptToCollection(String collectionName, long userId, String prompt);

    List<String> findRelatedUserPrompts(String collectionName, long userId, String query, int k);

    List<String> findRelatedUserPrompts(String collectionName, long userId, String query, int k, SortType sortType);

    List<String> findAllUserPrompts(String collectionName, long userId);

    void deleteUserPromptsCollection(String collectionName, long userId);
}
