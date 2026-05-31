package com.plataforma.auth.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GoogleTokenPayload {
    private String subject;
    private String email;
    private boolean emailVerified;
    private String firstName;
    private String lastName;
    private String pictureUrl;
}
