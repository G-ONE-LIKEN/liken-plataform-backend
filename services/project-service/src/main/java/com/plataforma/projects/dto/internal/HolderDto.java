package com.plataforma.projects.dto.internal;

import java.math.BigDecimal;

/**
 * Snapshot de un holder de un proyecto para el calculo de dividendos por
 * parque. Devuelto por {@code GET /internal/projects/{id}/holders} al
 * invest-dividend-service.
 */
public record HolderDto(
        Long userId,
        String walletAddress,
        BigDecimal tokensAmount
) {}
