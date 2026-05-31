package com.plataforma.shared.client.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GoogleUserRequest {
    private String email;
    private String firstName;
    private String lastName;
    private String googleSubject;
    private String pictureUrl;
    private String roleName;
}
