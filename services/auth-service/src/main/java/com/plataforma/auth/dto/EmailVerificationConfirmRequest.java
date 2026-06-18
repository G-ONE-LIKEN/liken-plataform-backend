// services/auth-service/src/main/java/com/plataforma/auth/dto/EmailVerificationConfirmRequest.java
package com.plataforma.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class EmailVerificationConfirmRequest {

    @NotBlank(message = "El email es obligatorio.")
    @Email(message = "El email no tiene un formato válido.")
    private String email;

    @NotBlank(message = "El código es obligatorio.")
    @Pattern(regexp = "\\d{6}", message = "El código debe ser de 6 dígitos.")
    private String code;
}