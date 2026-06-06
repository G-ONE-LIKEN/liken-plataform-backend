package com.plataforma.blockchain.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Convertidores entre las unidades on-chain (BigInteger en wei) y las
 * representaciones {@link BigDecimal} que viajan en los eventos Kafka.
 *
 * <p>Convenciones:
 * <ul>
 *   <li>USDC: 6 decimales (oficial Circle).</li>
 *   <li>LKN: 18 decimales (ERC-20 estándar).</li>
 *   <li>Precios USDC/LKN en {@code ProjectRegistry} / {@code OfferingContract.tokenPrice}: 6 decimales.</li>
 * </ul>
 */
@Component
public class UnitConverter {

    private static final int USDC_DECIMALS = 6;
    private static final int LKN_DECIMALS = 18;

    /** USDC on-chain (6 dec) → BigDecimal escala 6. Ej: 10_000_000 → 10.000000 */
    public BigDecimal usdcFromOnchain(BigInteger raw) {
        if (raw == null) return BigDecimal.ZERO;
        return new BigDecimal(raw).movePointLeft(USDC_DECIMALS).setScale(USDC_DECIMALS, RoundingMode.HALF_UP);
    }

    /** LKN on-chain (18 dec) → BigDecimal escala 8 (matchea project-service.user_holdings.tokens_amount). */
    public BigDecimal lknFromOnchain(BigInteger raw) {
        if (raw == null) return BigDecimal.ZERO;
        return new BigDecimal(raw).movePointLeft(LKN_DECIMALS).setScale(8, RoundingMode.HALF_UP);
    }
}
