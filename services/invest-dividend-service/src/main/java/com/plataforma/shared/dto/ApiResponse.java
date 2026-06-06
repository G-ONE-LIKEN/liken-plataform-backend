package com.plataforma.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Envoltorio de respuesta uniforme.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private String message;
    private T data;
    private Integer status;
    private String code;

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder().message(message).data(data).status(200).build();
    }
}
