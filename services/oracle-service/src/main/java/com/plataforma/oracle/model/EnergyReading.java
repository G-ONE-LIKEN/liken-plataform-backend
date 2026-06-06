package com.plataforma.oracle.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lectura de energia generada por un proyecto, simulada por el oracle
 * en base a su capacidad instalada y la curva solar (ver SolarCurveSimulator).
 */
@Entity
@Table(name = "energy_readings", uniqueConstraints = {
        @UniqueConstraint(name = "uq_project_reading_timestamp", columnNames = { "project_id", "reading_timestamp" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnergyReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "reading_timestamp", nullable = false)
    private LocalDateTime readingTimestamp;

    @Column(name = "energy_kwh", nullable = false, precision = 18, scale = 4)
    private BigDecimal energyKWh;
}