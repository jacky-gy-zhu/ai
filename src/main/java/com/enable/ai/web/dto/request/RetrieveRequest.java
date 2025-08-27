package com.enable.ai.web.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 检索请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrieveRequest {

    /**
     * 查询文本
     */
    @NotBlank(message = "Query cannot be blank")
    @Size(max = 1000, message = "Query length cannot exceed 1000 characters")
    private String query;

    /**
     * 返回的top k个结果数量
     */
    @Min(value = 1, message = "K must be at least 1")
    @Max(value = 50, message = "K cannot exceed 50")
    private int k = 5; // 默认值为5
}
