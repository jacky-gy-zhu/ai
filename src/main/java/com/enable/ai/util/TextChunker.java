package com.enable.ai.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for splitting text into chunks
 */
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_OVERLAP = 200;

    /**
     * Split text into chunks with default size and overlap
     */
    public static List<String> chunkText(String text) {
        return chunkText(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * Split text into chunks with specified size and overlap
     * 
     * @param text The text to chunk
     * @param chunkSize Maximum size of each chunk
     * @param overlap Number of characters to overlap between chunks
     * @return List of text chunks
     */
    public static List<String> chunkText(String text, int chunkSize, int overlap) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        text = text.trim();

        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            // Try to break at sentence boundaries if possible
            if (end < text.length()) {
                int lastSentenceEnd = findLastSentenceEnd(text, start, end);
                if (lastSentenceEnd > start + chunkSize / 2) { // Only if we find a reasonable break point
                    end = lastSentenceEnd;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // Move start position with overlap
            start = end - overlap;
            if (start <= 0) {
                start = end;
            }
        }

        return chunks;
    }

    /**
     * Find the last sentence ending within the range
     */
    private static int findLastSentenceEnd(String text, int start, int end) {
        String[] sentenceEnders = {".", "!", "?", "\n"};
        
        for (int i = end - 1; i > start; i--) {
            for (String ender : sentenceEnders) {
                if (text.charAt(i) == ender.charAt(0)) {
                    return i + 1;
                }
            }
        }
        return end;
    }
}
