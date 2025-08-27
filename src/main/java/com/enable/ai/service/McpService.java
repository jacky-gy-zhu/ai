package com.enable.ai.service;

import com.google.common.collect.Maps;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class McpService {

    private Map<String, ToolCallback> toolCallbackMap;

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

    public List<String> findAllToolSchemas() {
        return toolCallbackMap.values().stream()
                .map(callback -> callback.getToolDefinition().toString())
                .toList();
    }
}
