package com.enable.ai.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagCollection {

    private List<RagChunk> chunks;

    public String topKText(int k) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(k, chunks.size()); i++) {
            sb.append(chunks.get(i).getText()).append("\n");
        }
        return sb.toString().trim();
    }
}
