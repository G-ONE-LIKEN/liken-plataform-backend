package com.plataforma.invest.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Calcula el tier del inversor según el total USDC invertido.
 *
 * <p>Espejo de {@code com.plataforma.user.model.Tier} del user-service:
 * <pre>
 *   BRONZE: $0     (default)
 *   SILVER: ≥ $1000
 *   GOLD:   ≥ $5000
 * </pre>
 *
 * <p>Los umbrales se mantienen acá en string para no atar este servicio a la
 * definición exacta del enum del user-service: ambos lados deben moverse en
 * conjunto si los umbrales cambian.
 */
@Component
public class TierCalculator {

    private static final BigDecimal SILVER_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal GOLD_THRESHOLD = new BigDecimal("5000");

    public String tierFor(BigDecimal totalInvested) {
        if (totalInvested == null) return "BRONZE";
        if (totalInvested.compareTo(GOLD_THRESHOLD) >= 0) return "GOLD";
        if (totalInvested.compareTo(SILVER_THRESHOLD) >= 0) return "SILVER";
        return "BRONZE";
    }
}
