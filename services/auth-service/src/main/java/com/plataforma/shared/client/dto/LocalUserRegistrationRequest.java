package com.plataforma.shared.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
