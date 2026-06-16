// services/auth-service/src/main/java/com/plataforma/auth/dto/RegisterRequest.java
package com.plataforma.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterRequest {

    @NotBlank(message = "El email es obligatorio.")
    @Email(message = "El email no tiene un formato válido.")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria.")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres.")
    private String password;

    @NotBlank(message = "El rol es obligatorio.")
    private String roleName;

    @NotBlank(message = "El nombre es obligatorio.")
    private String firstName;

    @NotBlank(message = "El apellido es obligatorio.")
    private String lastName;

    @NotBlank(message = "El DNI es obligatorio.")
    private String dni;

    @NotNull(message = "La fecha de nacimiento es obligatoria.")
    @Past(message = "La fecha de nacimiento debe ser en el pasado.")
    private LocalDate birthDate;

    @NotBlank(message = "El teléfono es obligatorio.")
    private String phone;

    @NotBlank(message = "El país es obligatorio.")
    private String country;

    private String province;
    private String address;

    @AssertTrue(message = "Debes aceptar los términos y condiciones.")
    private boolean termsAccepted;
}