package com.enable.ai.rag.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromptWithScore {

    private String promptText;
    private Long timestamp;
    private float score;

}
