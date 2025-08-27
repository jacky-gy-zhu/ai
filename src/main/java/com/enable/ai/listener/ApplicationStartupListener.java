package com.enable.ai.listener;

import com.enable.ai.rag.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupListener {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationStartupListener.class);

    @Autowired
    private RagService ragService;


    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application startup completed, starting RAG system initialization...");

        try {
            // Use progressive initialization to ensure backward compatibility
//            ragService.initializeDocumentsWithCompatibility();
            logger.info("RAG system initialization completed");

        } catch (Exception e) {
            logger.error("RAG system initialization failed", e);
            // Do not terminate application startup, let the application continue running
            logger.warn("RAG system initialization failed, application will continue running without RAG functionality");
        }
    }
}