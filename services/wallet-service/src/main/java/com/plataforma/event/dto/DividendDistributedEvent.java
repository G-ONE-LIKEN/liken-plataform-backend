package com.plataforma.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @deprecated Reemplazado por {@link DividendsClaimedEvent} bajo el modelo PULL
 * de dividendos on-chain (DividendDistributor). El payload viejo asumía un push
 * desde {@code invest-dividend-service} (la plataforma distribuía a cada usuario);
 * en la integración Web3 el holder retira individualmente con MetaMask y el
 * Blockchain Service publica el evento contable.
 *
 * <p>Se mantiene la clase para que código viejo que la referencie (legacy event
 * stub no usado por ningún consumer activo) siga compilando. NO la consumen los
 * consumers actuales — ver {@code DividendsClaimedConsumer}.
 */
@Deprecated(forRemoval = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DividendDistributedEvent {
    private String eventId;
    private String occurredAt;
    private int version;

    private Long userId;
    private Long projectId;
    private BigDecimal amount;
    private String dividendId;
}
