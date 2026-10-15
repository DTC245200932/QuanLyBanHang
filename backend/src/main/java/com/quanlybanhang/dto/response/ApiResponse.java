package com.quanlybanhang.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO response chung cho tất cả API - giúp frontend xử lý nhất quán.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {
    private boolean success;
    private String message;
    private Object data;

    /**
     * Trường bổ trợ tương thích trực tiếp các giao diện đọc phân trang trên ApiResponse (S4-05).
     */
    private Long totalElements;
    private Integer totalPages;
    private Long total;
    private Object content;

    public static ApiResponse success(String message) {
        return ApiResponse.builder()
                .success(true)
                .message(message)
                .build();
    }

    public static ApiResponse success(String message, Object data) {
        return ApiResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static ApiResponse success(String message, Object data, long totalElements, int totalPages) {
        return ApiResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .content(data)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .total(totalElements)
                .build();
    }


    public static ApiResponse error(String message) {
        return ApiResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
