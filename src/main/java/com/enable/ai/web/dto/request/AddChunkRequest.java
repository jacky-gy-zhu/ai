package com.enable.ai.web.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 添加文本块的请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddChunkRequest {

    /**
     * 文本内容
     */
    @NotBlank(message = "Text cannot be blank")
    @Size(max = 10000, message = "Text length cannot exceed 10000 characters")
    private String text;
}
