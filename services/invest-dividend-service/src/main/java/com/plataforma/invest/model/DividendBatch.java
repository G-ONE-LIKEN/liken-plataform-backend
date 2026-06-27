package com.plataforma.invest.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tracker de un batch de payouts. Se crea cuando el acumulador de un proyecto
 * cruza el umbral; se cierra cuando {@code confirmedCount + failedCount ==
 * totalPayouts}. Al cerrar dispara el reset del {@code in_flight_usdc} del
 * acumulador.
 */
@Entity
@Table(name = "dividend_batch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendBatch {

    public enum Status {
        PENDING,    // payouts emitidos, esperando confirmaciones
        COMPLETED,  // todos confirmaron OK
        PARTIAL,    // algunos confirmaron, algunos fallaron
        FAILED      // batch entero abortado (saldo insuficiente)
    }

    @Id
    @Column(name = "batch_id", length = 80)
    private String batchId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "total_amount_usdc", nullable = false, precision = 20, scale = 6)
    private BigDecimal totalAmountUsdc;

    @Column(name = "total_payouts", nullable = false)
    private Integer totalPayouts;

    @Builder.Default
    @Column(name = "confirmed_count", nullable = false)
    private Integer confirmedCount = 0;

    @Builder.Default
    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
