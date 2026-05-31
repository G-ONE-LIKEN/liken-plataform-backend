package com.plataforma.user.dto;

import lombok.Data;

@Data
public class GoogleUserRequest {
    private String email;
    private String firstName;
    private String lastName;
    private String googleSubject;
    private String pictureUrl;
    private String roleName;
}
