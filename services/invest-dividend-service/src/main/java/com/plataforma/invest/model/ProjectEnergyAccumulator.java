package com.plataforma.invest.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Saldo de USDC pendiente de depositar para un proyecto. Se incrementa con
 * cada lectura del oracle; cuando {@code pendingUsdc} cruza el umbral se mueve
 * a {@code inFlightUsdc} y se publica un deposit_requested. Al recibir la
 * confirmacion on-chain ({@code dividends.deposited}) se resetea
 * {@code inFlightUsdc} a cero.
 */
@Entity
@Table(name = "project_energy_accumulator")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectEnergyAccumulator {

    @Id
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "pending_kwh", nullable = false, precision = 20, scale = 4)
    private BigDecimal pendingKwh;

    @Column(name = "pending_usdc", nullable = false, precision = 20, scale = 6)
    private BigDecimal pendingUsdc;

    @Column(name = "in_flight_usdc", nullable = false, precision = 20, scale = 6)
    private BigDecimal inFlightUsdc;

    @Column(name = "last_flushed_at")
    private LocalDateTime lastFlushedAt;
}
