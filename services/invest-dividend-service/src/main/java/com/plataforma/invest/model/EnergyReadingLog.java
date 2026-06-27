package com.plataforma.invest.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lectura individual del oracle de energia, con el revenue USDC asociado
 * (kWh × tarifa PPA fija). Auditoria detallada: en {@code project_energy_accumulator}
 * vive solo el saldo agregado, aca vive cada medicion.
 */
@Entity
@Table(name = "energy_reading_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnergyReadingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "kwh", nullable = false, precision = 18, scale = 4)
    private BigDecimal kwh;

    @Column(name = "revenue_usdc", nullable = false, precision = 20, scale = 6)
    private BigDecimal revenueUsdc;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "event_id", nullable = false, length = 120, unique = true)
    private String eventId;
}
