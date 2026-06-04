package com.plataforma.auth.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisterRequest {
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
}
