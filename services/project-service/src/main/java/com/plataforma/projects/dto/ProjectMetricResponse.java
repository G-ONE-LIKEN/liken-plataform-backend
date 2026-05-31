package com.plataforma.projects.dto;

import com.plataforma.projects.model.ProjectMetric;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ProjectMetricResponse {
    private Long id;
    private Long projectId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal energyGeneratedKwh;
    private BigDecimal revenueGenerated;
    private String source;
    private LocalDateTime recordedAt;

    public static ProjectMetricResponse from(ProjectMetric m) {
        return ProjectMetricResponse.builder()
                .id(m.getId())
                .projectId(m.getProject().getId())
                .periodStart(m.getPeriodStart())
                .periodEnd(m.getPeriodEnd())
                .energyGeneratedKwh(m.getEnergyGeneratedKwh())
                .revenueGenerated(m.getRevenueGenerated())
                .source(m.getSource())
                .recordedAt(m.getRecordedAt())
                .build();
    }
}
