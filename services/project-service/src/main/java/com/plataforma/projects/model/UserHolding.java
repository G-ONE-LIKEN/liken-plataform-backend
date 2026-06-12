package com.plataforma.projects.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * indice de compras de LKN por proyecto (token global).
 *
 * <p>Bajo el modelo de token global, el balance real de LKN del usuario esta on-chain
 * ({@code LinkenToken.balanceOf(wallet)}). Este entity NO es la fuente de verdad de
 * cuantos LKN tiene un inversor — sirve para analitica/historial: cuanta plata
 * invirtio cada usuario en cada proyecto y cuantos LKN compro en la ronda primaria.
 *
 * <p>Los movimientos los puebla el Blockchain Service desde el evento
 * {@code TokensPurchased} (compra primaria) y desde {@code Transfer} entre wallets
 * cuando se integre el marketplace (analitica de holdings derivados).
 */
@Entity
@Table(
    name = "user_holdings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "project_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /**
     * LKN acumulados comprados de este proyecto en la ronda primaria (indice).
     * NO es el balance actual on-chain del usuario — eso se consulta a la chain.
     */
    @Column(name = "tokens_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal tokensAmount;

    /**
     * USDC invertido acumulado en este proyecto (suma de los {@code usdcAmount}
     * de los eventos TokensPurchased de la wallet del usuario).
     */
    @Builder.Default
    @Column(name = "usdc_invested", nullable = false, precision = 20, scale = 6)
    private BigDecimal usdcInvested = BigDecimal.ZERO;

    /**
     * Direccion on-chain (EIP-55) del comprador. Puede ser null en holdings legacy
     * pre-blockchain; los holdings creados por eventos on-chain siempre la traen.
     */
    @Column(name = "wallet_address", length = 42)
    private String walletAddress;

    @Builder.Default
    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt = LocalDateTime.now();
}
