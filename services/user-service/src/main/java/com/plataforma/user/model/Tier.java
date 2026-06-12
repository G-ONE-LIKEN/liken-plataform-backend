package com.plataforma.user.model;

import java.math.BigDecimal;

/**
 * Nivel del usuario en funcion del monto invertido acumulado (ver DD013).
 * El tier se actualiza automaticamente mediante el evento user.tier_changed
 * publicado por invest-dividend-service.
 *
 * Umbrales:
 *   BRONZE: >= 0
 *   SILVER: >= 1000
 *   GOLD:   >= 5000
 */
public enum Tier {
    BRONZE(new BigDecimal("0")),
    SILVER(new BigDecimal("1000")),
    GOLD  (new BigDecimal("5000"));

    private final BigDecimal threshold;

    Tier(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    /**
     * Calcula el tier que corresponde a un monto total invertido.
     * Usado por invest-dividend-service para decidir si publicar user.tier_changed.
     */
    public static Tier forAmount(BigDecimal totalInvested) {
        if (totalInvested == null) return BRONZE;
        if (totalInvested.compareTo(GOLD.threshold) >= 0)   return GOLD;
        if (totalInvested.compareTo(SILVER.threshold) >= 0) return SILVER;
        return BRONZE;
    }
}
