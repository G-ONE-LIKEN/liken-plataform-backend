package com.plataforma.invest.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reclamo de dividendos del holder, registrado desde el evento on-chain
 * {@code DividendDistributor.DividendsWithdrawn}.
 */
@Entity
@Table(name = "dividend_claim")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "wallet_address", nullable = false, length = 42)
    private String walletAddress;

    @Column(name = "amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal amount;

    @Column(name = "tx_hash", nullable = false, length = 66)
    private String txHash;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
