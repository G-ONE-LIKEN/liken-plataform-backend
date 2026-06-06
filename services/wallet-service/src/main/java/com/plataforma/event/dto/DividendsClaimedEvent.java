package com.plataforma.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Publicado por el Blockchain Service en el topic {@code dividends.claimed}
 * cuando indexa el evento on-chain {@code DividendDistributor.DividendsWithdrawn}.
 *
 * <p>Bajo el modelo PULL de dividendos, el holder retira sus dividendos llamando
 * {@code claimDividends()} con su MetaMask. El wallet-service NO inicia el pago;
 * sólo registra el movimiento contable que ya ocurrió on-chain.
 *
 * <p>Reemplaza al deprecated {@code DividendDistributedEvent}, que asumía un modelo
 * push (la plataforma distribuye a cada usuario individualmente). Ver
 * implementar-con-blockchain.md §5.
 *
 * <h3>Resolución walletAddress → userId</h3>
 * El Blockchain Service consulta {@code user-service /internal/users/by-wallet/{address}}
 * antes de publicar, y deja el {@code userId} resuelto en el evento. Si la wallet
 * todavía no está vinculada a ningún usuario, {@code userId} es {@code null} y el
 * consumer lo descarta (el evento queda en el log para un eventual replay manual).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendsClaimedEvent {
    /** UUID v4 — base de la idempotencia (DD010). */
    private String eventId;
    /** ISO 8601 UTC — timestamp del bloque on-chain. */
    private String occurredAt;
    /** Versión del schema, empieza en 1. */
    private int version;

    /** Dirección on-chain del holder que retiró (EIP-55). */
    private String walletAddress;
    /** userId resuelto del walletAddress por el puente. Puede ser null. */
    private Long userId;
    /** USDC retirado (escala 6, el DividendDistributor lo paga en USDC). */
    private BigDecimal amount;
    /** Hash de la tx on-chain. */
    private String txHash;
    /** Número de bloque on-chain. */
    private Long blockNumber;
}
