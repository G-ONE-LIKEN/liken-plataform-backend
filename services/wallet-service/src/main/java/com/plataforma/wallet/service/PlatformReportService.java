package com.plataforma.wallet.service;

import com.plataforma.wallet.dto.PlatformReportResponse;
import com.plataforma.wallet.dto.PlatformReportResponse.MonthlyPoint;
import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.repository.PendingWalletMovementRepository;
import com.plataforma.wallet.repository.WalletMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Genera el reporte de ingresos de la plataforma.
 *
 * Definición de ingresos: comisiones cobradas en las dos operaciones que la
 * plataforma intermedia — inversión primaria y mercado P2P. El resto de los
 * movimientos (dividendos, depósitos, retiros) son flujos entre el usuario y
 * proyectos / cuentas externas y no constituyen revenue de la plataforma.
 */
@Service
@RequiredArgsConstructor
public class PlatformReportService {

    private final WalletMovementRepository movementRepository;
    private final PendingWalletMovementRepository pendingRepository;
    private final PrimarySalesReportClient primarySalesReportClient;

    /** Comisión sobre inversión primaria. Default 2%. */
    @Value("${platform.primary-fee-rate:0.02}")
    private BigDecimal primaryFeeRate;

    /** Comisión sobre mercado P2P. Default 1%. */
    @Value("${platform.p2p-fee-rate:0.01}")
    private BigDecimal p2pFeeRate;

    public PlatformReportResponse generate(LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1).minusMonths(11);
        if (to == null)   to   = LocalDate.now().plusDays(1);
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("La fecha 'to' debe ser posterior a 'from'");
        }

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.atStartOfDay();

        // 1) Totales por tipo
        Map<MovementType, BigDecimal> amountByType = new EnumMap<>(MovementType.class);
        Map<MovementType, Long> countByType        = new EnumMap<>(MovementType.class);
        for (MovementType t : MovementType.values()) {
            amountByType.put(t, BigDecimal.ZERO);
            countByType.put(t, 0L);
        }
        mergeAmountRows(amountByType, countByType, movementRepository.sumAmountByTypeBetween(fromDt, toDt));
        mergeAmountRows(amountByType, countByType, pendingRepository.sumAmountByTypeBetween(fromDt, toDt));

        PrimarySalesReportClient.PrimarySalesReport primarySalesReport = primarySalesReportClient.fetch(from, to);
        BigDecimal primaryVolume = primarySalesReport != null && primarySalesReport.getPrimaryVolume() != null
                ? primarySalesReport.getPrimaryVolume()
                : amountByType.get(MovementType.TOKEN_PURCHASE);
        BigDecimal p2pVolume     = amountByType.get(MovementType.P2P_PURCHASE);

        BigDecimal primaryFees = primaryVolume.multiply(primaryFeeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal p2pFees     = p2pVolume.multiply(p2pFeeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRevenue = primaryFees.add(p2pFees);

        // 2) Serie temporal mensual
        Map<String, Map<MovementType, BigDecimal>> byMonth = new TreeMap<>();
        mergeMonthlyRows(byMonth, movementRepository.monthlyAmountByType(fromDt, toDt));
        mergeMonthlyRows(byMonth, pendingRepository.monthlyAmountByType(fromDt, toDt));

        // Rellenar meses vacíos para que el gráfico no tenga huecos
        YearMonth cursor = YearMonth.from(from);
        YearMonth end    = YearMonth.from(to.minusDays(1));
        while (!cursor.isAfter(end)) {
            String key = String.format("%04d-%02d", cursor.getYear(), cursor.getMonthValue());
            byMonth.computeIfAbsent(key, k -> new HashMap<>());
            cursor = cursor.plusMonths(1);
        }

        List<MonthlyPoint> monthly = byMonth.entrySet().stream()
                .map(e -> {
                    Map<MovementType, BigDecimal> m = e.getValue();
                    BigDecimal pv  = primaryVolumeForPeriod(primarySalesReport, e.getKey(), m.getOrDefault(MovementType.TOKEN_PURCHASE, BigDecimal.ZERO));
                    BigDecimal p2v = m.getOrDefault(MovementType.P2P_PURCHASE,   BigDecimal.ZERO);
                    BigDecimal pf  = pv.multiply(primaryFeeRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal p2f = p2v.multiply(p2pFeeRate).setScale(2, RoundingMode.HALF_UP);
                    return MonthlyPoint.builder()
                            .period(e.getKey())
                            .primaryVolume(pv)
                            .p2pVolume(p2v)
                            .primaryFees(pf)
                            .p2pFees(p2f)
                            .revenue(pf.add(p2f))
                            .build();
                })
                .toList();

        return PlatformReportResponse.builder()
                .from(from)
                .to(to)
                .primaryFeeRate(primaryFeeRate)
                .p2pFeeRate(p2pFeeRate)
                .primaryFees(primaryFees)
                .p2pFees(p2pFees)
                .totalRevenue(totalRevenue)
                .primaryVolume(primaryVolume)
                .primaryOperations(primarySalesReport != null ? primarySalesReport.getPrimaryOperations() : countByType.get(MovementType.TOKEN_PURCHASE))
                .p2pVolume(p2pVolume)
                .p2pOperations(countByType.get(MovementType.P2P_PURCHASE))
                .totalDeposits(amountByType.get(MovementType.DEPOSIT))
                .totalWithdrawals(amountByType.get(MovementType.WITHDRAWAL))
                .monthly(monthly)
                .build();
    }

    private static void mergeAmountRows(Map<MovementType, BigDecimal> amountByType,
                                        Map<MovementType, Long> countByType,
                                        List<Object[]> rows) {
        for (Object[] row : rows) {
            MovementType type = (MovementType) row[0];
            amountByType.put(type, amountByType.get(type).add(toBigDecimal(row[1])));
            countByType.put(type, countByType.get(type) + ((Number) row[2]).longValue());
        }
    }

    private static void mergeMonthlyRows(Map<String, Map<MovementType, BigDecimal>> byMonth,
                                         List<Object[]> rows) {
        for (Object[] row : rows) {
            int year   = ((Number) row[0]).intValue();
            int month  = ((Number) row[1]).intValue();
            MovementType type = (MovementType) row[2];
            BigDecimal amt    = toBigDecimal(row[3]);
            String period = String.format("%04d-%02d", year, month);
            byMonth.computeIfAbsent(period, k -> new HashMap<>())
                    .merge(type, amt, BigDecimal::add);
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private static BigDecimal primaryVolumeForPeriod(PrimarySalesReportClient.PrimarySalesReport report,
                                                     String period,
                                                     BigDecimal fallback) {
        if (report == null || report.getMonthly() == null) {
            return fallback;
        }
        return report.getMonthly().stream()
                .filter(point -> period.equals(point.getPeriod()))
                .map(PrimarySalesReportClient.MonthlyPoint::getPrimaryVolume)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(fallback);
    }
}
