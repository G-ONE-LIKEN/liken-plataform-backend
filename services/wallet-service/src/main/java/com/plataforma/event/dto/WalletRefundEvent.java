package com.plataforma.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Publicado por el Blockchain Service en el topic {@code wallet.refund} cuando indexa
 * el evento on-chain {@code OfferingContract.Refunded}.
 *
 * <p>Una ronda primaria falló (deadline expirado sin alcanzar soft cap). El inversor
 * llamó {@code refund()} con su MetaMask y recibió su USDC de vuelta del treasury.
 * El wallet-service registra el movimiento contable {@link com.plataforma.wallet.model.MovementType#REFUND}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletRefundEvent {
    /** UUID v4 — base de la idempotencia. */
    private String eventId;
    /** ISO 8601 UTC — timestamp del bloque on-chain. */
    private String occurredAt;
    /** Versión del schema. */
    private int version;

    /** Dirección on-chain del inversor que recuperó su USDC. */
    private String walletAddress;
    /** userId resuelto del walletAddress por el puente. Puede ser null. */
    private Long userId;
    /** Id local del proyecto cuya ronda falló. */
    private Long projectId;
    /** USDC devuelto (escala 6). */
    private BigDecimal usdcAmount;
    /** Hash de la tx on-chain. */
    private String txHash;
    /** Número de bloque on-chain. */
    private Long blockNumber;
}
