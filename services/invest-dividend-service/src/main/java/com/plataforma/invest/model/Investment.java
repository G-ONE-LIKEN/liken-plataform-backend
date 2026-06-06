package com.plataforma.invest.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Compra primaria de LKN registrada desde el evento on-chain
 * {@code OfferingContract.TokensPurchased}.
 *
 * <p>Los montos están normalizados:
 * <ul>
 *   <li>{@code usdcAmount}: USDC con escala 6 (matchea el on-chain).</li>
 *   <li>{@code lknAmount}: LKN con escala 8 (de 18 dec on-chain → escala 8 BigDecimal).</li>
 * </ul>
 */
@Entity
@Table(name = "investment", uniqueConstraints = @UniqueConstraint(columnNames = {"tx_hash", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "wallet_address", nullable = false, length = 42)
    private String walletAddress;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "offering_contract_address", length = 42)
    private String offeringContractAddress;

    @Column(name = "usdc_amount", nullable = false, precision = 20, scale = 6)
    private BigDecimal usdcAmount;

    @Column(name = "lkn_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal lknAmount;

    @Column(name = "tx_hash", nullable = false, length = 66)
    private String txHash;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
