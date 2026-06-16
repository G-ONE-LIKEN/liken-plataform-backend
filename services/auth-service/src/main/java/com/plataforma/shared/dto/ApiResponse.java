// services/auth-services/src/main/java/com/plataforma/shared/dto/ApiResponse.java
package com.plataforma.shared.dto;

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
    private String code;
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
        return success("Operacion exitosa", data);
    }

    public static <T> ApiResponse<T> error(String msg, int statusCode) {
        return error(msg, statusCode, null);
    }

    public static <T> ApiResponse<T> error(String msg, int statusCode, String code) {
        return ApiResponse.<T>builder()
                .message(msg)
                .timestamp(LocalDateTime.now())
                .data(null)
                .code(code)
                .status(statusCode)
                .build();
    }
}
