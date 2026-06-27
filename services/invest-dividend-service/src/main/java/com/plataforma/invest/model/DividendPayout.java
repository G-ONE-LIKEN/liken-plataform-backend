package com.plataforma.invest.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pago de dividendos a un holder via {@code usdc.transfer} directo. Una fila
 * por holder en un batch. El {@code payoutEventId} hace idempotente al
 * consumer de {@code dividends.paid}.
 */
@Entity
@Table(name = "dividend_payout")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, length = 80)
    private String batchId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "wallet_address", nullable = false, length = 42)
    private String walletAddress;

    @Column(name = "amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal amount;

    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "payout_event_id", nullable = false, length = 120, unique = true)
    private String payoutEventId;
}
