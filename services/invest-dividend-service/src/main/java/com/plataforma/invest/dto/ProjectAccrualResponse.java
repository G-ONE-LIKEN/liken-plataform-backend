package com.plataforma.invest.dto;

import com.plataforma.invest.model.ProjectEnergyAccumulator;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record ProjectAccrualResponse(
        Long projectId,
        BigDecimal pendingKwh,
        BigDecimal pendingUsdc,
        BigDecimal inFlightUsdc,
        LocalDateTime lastFlushedAt
) {
    public static ProjectAccrualResponse from(ProjectEnergyAccumulator acc) {
        return ProjectAccrualResponse.builder()
                .projectId(acc.getProjectId())
                .pendingKwh(acc.getPendingKwh())
                .pendingUsdc(acc.getPendingUsdc())
                .inFlightUsdc(acc.getInFlightUsdc())
                .lastFlushedAt(acc.getLastFlushedAt())
                .build();
    }
}
