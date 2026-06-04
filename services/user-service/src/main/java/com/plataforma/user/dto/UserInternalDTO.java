package com.plataforma.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInternalDTO {
    private Long id;
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String dni;
    private LocalDate birthDate;
    private String phone;
    private String country;
    private String province;
    private String address;
    private boolean termsAccepted;
    private boolean profileCompleted;
    private boolean emailVerified;
    private String authProvider;
    private String googleSubject;
    private String pictureUrl;
    private String roleName;
    private List<String> permissions;
    private boolean active;
}
