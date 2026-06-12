package com.plataforma.user.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LocalUserRegistrationRequest {
    private String email;
    private String password;
    private String roleName;
    private String firstName;
    private String lastName;
    private String dni;
    private LocalDate birthDate;
    private String phone;
    private String country;
    private String province;
    private String address;
    private boolean termsAccepted;

    /**
     * true cuando {@code password} ya viene hasheada con BCrypt desde
     * auth-service (que la hashea antes de guardar el registro pendiente
     * en Redis, ADR-0026). En ese caso no se re-hashea ni se valida fortaleza.
     */
    private boolean passwordEncoded;
}
