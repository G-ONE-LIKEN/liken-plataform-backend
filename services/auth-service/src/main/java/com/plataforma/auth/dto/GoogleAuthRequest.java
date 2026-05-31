package com.plataforma.auth.dto;

import lombok.Data;

@Data
public class GoogleAuthRequest {
    private String idToken;
    private String roleName;
}
