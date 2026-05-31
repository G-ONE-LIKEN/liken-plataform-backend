package com.plataforma.projects.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private String message;
    private T data;
    private int status;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(String msg, T data) {
        return ApiResponse.<T>builder()
                .message(msg)
                .timestamp(LocalDateTime.now())
                .data(data)
                .status(200)
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("Operación exitosa", data);
    }

    public static <T> ApiResponse<T> error(String msg, int statusCode) {
        return ApiResponse.<T>builder()
                .message(msg)
                .timestamp(LocalDateTime.now())
                .data(null)
                .status(statusCode)
                .build();
    }
}
