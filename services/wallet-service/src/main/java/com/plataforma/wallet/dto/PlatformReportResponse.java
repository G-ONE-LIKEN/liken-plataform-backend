package com.plataforma.wallet.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reporte de ingresos de la plataforma.
 *
 * El revenue de la plataforma proviene exclusivamente de comisiones (fees)
 * cobradas sobre las transacciones que opera:
 *   - Inversión primaria (TOKEN_PURCHASE): fee = volumen × primaryFeeRate
 *   - Mercado secundario P2P (P2P_PURCHASE): fee = volumen × p2pFeeRate
 *
 * Dividendos, compras/ventas de tokens y movimientos de billetera NO son
 * revenue de la plataforma — son flujos entre proyectos e inversores, o
 * entre el inversor y su cuenta externa. Se muestran solo como contexto.
 */
@Data
@Builder
public class PlatformReportResponse {

    private LocalDate from;
    private LocalDate to;

    // ── Fees configurados ───────────────────────────────────────────────
    /** Comisión aplicada sobre inversión primaria. Entre 0 y 1. */
    private BigDecimal primaryFeeRate;
    /** Comisión aplicada sobre mercado P2P. Entre 0 y 1. */
    private BigDecimal p2pFeeRate;

    // ── Revenue (lo que efectivamente gana la plataforma) ───────────────
    private BigDecimal primaryFees;
    private BigDecimal p2pFees;
    private BigDecimal totalRevenue;

    // ── Volúmenes que generaron ese revenue ─────────────────────────────
    private BigDecimal primaryVolume;
    private long       primaryOperations;
    private BigDecimal p2pVolume;
    private long       p2pOperations;

    // ── Contexto financiero (no es revenue, solo informativo) ───────────
    private BigDecimal totalDeposits;
    private BigDecimal totalWithdrawals;

    /** Serie temporal mensual para gráficos. */
    private List<MonthlyPoint> monthly;

    @Data
    @Builder
    public static class MonthlyPoint {
        /** Etiqueta YYYY-MM. */
        private String period;
        private BigDecimal primaryVolume;
        private BigDecimal p2pVolume;
        private BigDecimal primaryFees;
        private BigDecimal p2pFees;
        private BigDecimal revenue;
    }
}
