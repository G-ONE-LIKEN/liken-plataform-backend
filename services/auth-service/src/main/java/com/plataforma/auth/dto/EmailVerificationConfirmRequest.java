package com.plataforma.auth.dto;

import lombok.Data;

@Data
public class EmailVerificationConfirmRequest {
    private String email;
    private String code;
}
