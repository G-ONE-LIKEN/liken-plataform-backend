// services/auth-service/src/main/java/com/plataforma/auth/dto/ChangePasswordRequest.java
package com.plataforma.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "La contraseña actual es obligatoria.")
    private String oldPassword;

    @NotBlank(message = "La nueva contraseña es obligatoria.")
    @Size(min = 6, message = "La nueva contraseña debe tener al menos 6 caracteres.")
    private String newPassword;
}