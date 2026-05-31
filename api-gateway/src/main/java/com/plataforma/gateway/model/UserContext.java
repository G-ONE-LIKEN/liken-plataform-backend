package com.plataforma.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UserContext {
    private Long userId;
    private String role;
    private List<String> permissions;
    private String tier;
    private String kycStatus;
    private String developerStatus;
}
