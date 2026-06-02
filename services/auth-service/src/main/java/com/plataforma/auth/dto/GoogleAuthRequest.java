package com.plataforma.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class GoogleAuthRequest {
    @JsonAlias("credential")
    private String idToken;
    private String roleName;
}
