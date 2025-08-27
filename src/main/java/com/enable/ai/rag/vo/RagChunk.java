package com.enable.ai.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagChunk {

    private String text;

    public String getNameFromText() {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        
        // Look for pattern "name=" in the text
        String namePrefix = "name=";
        int nameStartIndex = text.indexOf(namePrefix);
        
        if (nameStartIndex == -1) {
            return null;
        }
        
        // Move to the position after "name="
        int valueStartIndex = nameStartIndex + namePrefix.length();
        
        // Find the end of the name value (look for comma, bracket, or end of string)
        int valueEndIndex = valueStartIndex;
        boolean inValue = false;
        
        for (int i = valueStartIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (!inValue && (c == ' ' || c == '\t' || c == '\n')) {
                // Skip whitespace before value starts
                continue;
            }
            
            if (!inValue) {
                inValue = true;
                valueStartIndex = i;
            }
            
            // End conditions: comma, closing bracket, or whitespace after the value
            if (c == ',' || c == ']' || c == '}' || c == ' ' || c == '\t' || c == '\n') {
                valueEndIndex = i;
                break;
            }
            
            // If we reach the end of string
            if (i == text.length() - 1) {
                valueEndIndex = text.length();
                break;
            }
        }
        
        if (valueEndIndex > valueStartIndex) {
            return text.substring(valueStartIndex, valueEndIndex).trim();
        }
        
        return null;
    }
}
