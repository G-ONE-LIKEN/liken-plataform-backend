package com.plataforma.projects.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "energy_generated_kwh", nullable = false, precision = 20, scale = 4)
    private BigDecimal energyGeneratedKwh;

    @Column(name = "revenue_generated", nullable = false, precision = 20, scale = 4)
    private BigDecimal revenueGenerated;

    private String source;

    @Builder.Default
    @Column(name = "recorded_at")
    private LocalDateTime recordedAt = LocalDateTime.now();
}
