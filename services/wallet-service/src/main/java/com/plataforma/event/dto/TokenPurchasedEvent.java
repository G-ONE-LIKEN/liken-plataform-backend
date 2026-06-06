package com.plataforma.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Publicado por el Blockchain Service en {@code investment.token_purchased} cuando
 * indexa el evento on-chain {@code OfferingContract.TokensPurchased(buyer, usdcAmount, lknAmount)}.
 *
 * <p>El inversor pagó USDC y recibió LKN en su wallet (todo on-chain). El
 * wallet-service registra el reflejo contable como {@link com.plataforma.wallet.model.MovementType#TOKEN_PURCHASE}.
 *
 * <h3>Resolución walletAddress → userId</h3>
 * El Blockchain Service consulta {@code user-service /internal/users/by-wallet/{address}}
 * y deja el {@code userId} resuelto. Si la wallet no estaba vinculada al momento del
 * evento, {@code userId} es null y el consumer descarta el evento.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenPurchasedEvent {
    /** UUID v4 — idempotencia. */
    private String eventId;
    /** ISO 8601 UTC — timestamp del bloque on-chain. */
    private String occurredAt;
    /** Versión del schema. */
    private int version;

    /** Dirección on-chain del comprador (EIP-55). */
    private String walletAddress;
    /** userId resuelto del walletAddress por el puente. Puede ser null. */
    private Long userId;
    /** Id local del proyecto. */
    private Long projectId;
    /** LKN comprados (escala 8 — convertido desde 18 dec on-chain). */
    private BigDecimal lknAmount;
    /** USDC pagado (escala 6 — convertido desde 6 dec on-chain). */
    private BigDecimal usdcAmount;
    /** Hash de la tx on-chain. */
    private String txHash;
    /** Número de bloque on-chain. */
    private Long blockNumber;
}
