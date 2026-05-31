package com.plataforma.projects.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProjectMetricRequest {
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal energyGeneratedKwh;
    private BigDecimal revenueGenerated;
    private String source;
}
