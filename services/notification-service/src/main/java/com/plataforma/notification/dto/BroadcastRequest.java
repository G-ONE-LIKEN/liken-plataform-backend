package com.plataforma.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BroadcastRequest {

    public enum Audience {
        ALL,
        ADMINS,
        DEVELOPERS,
        INVESTORS,
        USER
    }

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 4000)
    private String body;

    @NotNull
    private Audience audience;

    /** Solo requerido cuando audience = USER */
    private Long userId;

    /** Si true, ademas del in-app envia email. */
    private boolean sendEmail;
}
