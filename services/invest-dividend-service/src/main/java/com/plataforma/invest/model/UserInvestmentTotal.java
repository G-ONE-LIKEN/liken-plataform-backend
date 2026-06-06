package com.plataforma.invest.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agregado: total USDC invertido por usuario y tier actual derivado.
 *
 * <p>Lo mantiene actualizado {@code TokensPurchasedConsumer} al procesar cada
 * compra. Si el tier cambia (cruza un umbral), se publica {@code user.tier_changed}
 * que ya consume {@code user-service} para actualizar {@code users.tier}.
 */
@Entity
@Table(name = "user_investment_total")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInvestmentTotal {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "total_usdc_invested", nullable = false, precision = 20, scale = 6)
    @Builder.Default
    private BigDecimal totalUsdcInvested = BigDecimal.ZERO;

    @Column(name = "current_tier", nullable = false, length = 20)
    @Builder.Default
    private String currentTier = "BRONZE";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
