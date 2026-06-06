package com.plataforma.invest.service;

import com.plataforma.invest.dto.InternalPrimarySalesReportResponse;
import com.plataforma.invest.repository.InvestmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class PrimarySalesReportService {

    private final InvestmentRepository investmentRepository;

    public InternalPrimarySalesReportResponse generate(LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1).minusMonths(11);
        if (to == null) to = LocalDate.now().plusDays(1);

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atStartOfDay();

        Object[] totalRow = investmentRepository.sumVolumeBetween(fromDt, toDt);
        BigDecimal primaryVolume = toBigDecimal(totalRow[0]);
        long primaryOperations = ((Number) totalRow[1]).longValue();

        Map<String, InternalPrimarySalesReportResponse.MonthlyPoint.MonthlyPointBuilder> monthlyBuilders = new TreeMap<>();
        for (Object[] row : investmentRepository.monthlyVolumeBetween(fromDt, toDt)) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            String period = String.format("%04d-%02d", year, month);
            monthlyBuilders.put(period, InternalPrimarySalesReportResponse.MonthlyPoint.builder()
                    .period(period)
                    .primaryVolume(toBigDecimal(row[2]))
                    .primaryOperations(((Number) row[3]).longValue()));
        }

        YearMonth cursor = YearMonth.from(from);
        YearMonth end = YearMonth.from(to.minusDays(1));
        while (!cursor.isAfter(end)) {
            String period = String.format("%04d-%02d", cursor.getYear(), cursor.getMonthValue());
            monthlyBuilders.computeIfAbsent(period, key -> InternalPrimarySalesReportResponse.MonthlyPoint.builder()
                    .period(period)
                    .primaryVolume(BigDecimal.ZERO)
                    .primaryOperations(0));
            cursor = cursor.plusMonths(1);
        }

        List<InternalPrimarySalesReportResponse.MonthlyPoint> monthly = monthlyBuilders.values().stream()
                .map(InternalPrimarySalesReportResponse.MonthlyPoint.MonthlyPointBuilder::build)
                .toList();

        return InternalPrimarySalesReportResponse.builder()
                .from(from)
                .to(to)
                .primaryVolume(primaryVolume)
                .primaryOperations(primaryOperations)
                .monthly(monthly)
                .build();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
