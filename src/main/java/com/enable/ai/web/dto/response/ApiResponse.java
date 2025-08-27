package com.enable.ai.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一API响应格式
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * 响应状态码 (200=成功, 400=客户端错误, 500=服务器错误)
     */
    private int code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 响应时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 请求是否成功
     */
    private boolean success;

    /**
     * 创建成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "Success", data, LocalDateTime.now(), true);
    }

    /**
     * 创建成功响应（仅消息）
     */
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(200, message, null, LocalDateTime.now(), true);
    }

    /**
     * 创建错误响应
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(400, message, null, LocalDateTime.now(), false);
    }

    /**
     * 创建错误响应（自定义状态码）
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now(), false);
    }

    /**
     * 创建错误响应（带数据）
     */
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, LocalDateTime.now(), false);
    }
}
