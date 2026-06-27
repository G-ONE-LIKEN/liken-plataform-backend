package com.plataforma.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetConfirmRequest {

    @NotBlank(message = "El email es obligatorio.")
    @Email(message = "Ingresa un email valido.")
    private String email;

    @NotBlank(message = "El codigo es obligatorio.")
    @Pattern(regexp = "\\d{6}", message = "El codigo debe tener 6 digitos.")
    private String code;

    @NotBlank(message = "La nueva contrasena es obligatoria.")
    private String newPassword;
}