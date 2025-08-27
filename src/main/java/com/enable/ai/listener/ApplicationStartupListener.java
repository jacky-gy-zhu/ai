package com.enable.ai.listener;

import com.enable.ai.service.RagService;
import com.enable.ai.rag.vo.RagChunk;
import com.enable.ai.service.McpService;
import com.enable.ai.util.Constants;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApplicationStartupListener {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationStartupListener.class);

    @Autowired
    private RagService ragService;

    @Autowired
    private McpService mcpService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application startup completed, starting RAG system initialization...");

        try {
            String collectionName = Constants.MCP_COLLECTION_NAME;
            // Clear existing collection if it exists
            ragService.deleteCollection(collectionName);
            logger.info("Cleared existing RAG collection: {}", collectionName);
            // Load all tool schemas from MCP service
            List<String> toolSchemas = mcpService.findAllToolSchemas();
            if (CollectionUtils.isNotEmpty(toolSchemas)) {
                toolSchemas.forEach(schema -> {
                    ragService.addChunkToCollection(collectionName, new RagChunk(schema));
                    logger.info("Added tool schema to RAG collection 'MCP_TOOLS': {}", schema);
                });
            }

        } catch (Exception e) {
            logger.error("RAG system initialization failed", e);
            // Do not terminate application startup, let the application continue running
            logger.warn("RAG system initialization failed, application will continue running without RAG functionality");
        }
    }
}