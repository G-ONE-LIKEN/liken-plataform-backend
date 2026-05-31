package com.plataforma.user.kyc.dto;

import com.plataforma.user.model.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Payload del endpoint interno GET /internal/users/{id}/kyc-status.
 * Consumido por invest-dividend y marketplace antes de cada operación crítica.
 */
@Getter
@Builder
@AllArgsConstructor
public class KycInternalDto {
    private Long userId;
    private KycStatus status;
}
