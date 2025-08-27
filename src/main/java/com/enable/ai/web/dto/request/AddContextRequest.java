package com.enable.ai.web.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 添加上下文的请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddContextRequest {

    /**
     * 上下文内容，将自动分块处理
     */
    @NotBlank(message = "Context cannot be blank")
    @Size(max = 100000, message = "Context length cannot exceed 100000 characters")
    private String context;
}
