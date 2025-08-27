package com.enable.ai.service;

import com.enable.ai.rag.vo.RagChunk;
import com.enable.ai.util.Constants;
import com.google.common.collect.Maps;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class McpService {

    private Map<String, ToolCallback> toolCallbackMap;

    @Autowired
    private RagService ragService;

    @Autowired
    private SyncMcpToolCallbackProvider toolCallbackProvider;

    @PostConstruct
    public void initToolCallbackMap() {
        if (toolCallbackMap == null) {
            toolCallbackMap = Maps.newHashMap();
            for (ToolCallback toolCallback : toolCallbackProvider.getToolCallbacks()) {
                toolCallbackMap.put(toolCallback.getToolDefinition().name(), toolCallback);
            }
        }
    }

    public ToolCallback[] findAllToolCallbacks() {
        return toolCallbackProvider.getToolCallbacks();
    }

    public ToolCallback[] findRelatedToolCallbacks(String query, int k) {
        return findRelatedToolNames(query, k).stream()
                .map(toolCallbackMap::get)
                .filter(Objects::nonNull)
                .toArray(ToolCallback[]::new);
    }

    public List<String> findAllToolSchemas() {
        return toolCallbackMap.values().stream()
                .map(callback -> callback.getToolDefinition().toString())
                .toList();
    }

    public List<String> findRelatedToolNames(String query, int k) {
        return ragService.retrieveTopKChunks(
                        Constants.MCP_COLLECTION_NAME, query, k).stream()
                .map(RagChunk::getNameFromText).toList();
    }
}
