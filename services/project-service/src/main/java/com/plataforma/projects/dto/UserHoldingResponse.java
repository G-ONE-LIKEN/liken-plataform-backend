package com.plataforma.projects.dto;

import com.plataforma.projects.model.UserHolding;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class UserHoldingResponse {
    private Long userId;
    private Long projectId;
    private BigDecimal tokensAmount;
    private LocalDateTime lastUpdatedAt;

    public static UserHoldingResponse from(UserHolding h) {
        return UserHoldingResponse.builder()
                .userId(h.getUserId())
                .projectId(h.getProject().getId())
                .tokensAmount(h.getTokensAmount())
                .lastUpdatedAt(h.getLastUpdatedAt())
                .build();
    }
}
