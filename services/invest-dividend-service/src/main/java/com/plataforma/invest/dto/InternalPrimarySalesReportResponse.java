package com.plataforma.invest.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class InternalPrimarySalesReportResponse {
    private LocalDate from;
    private LocalDate to;
    private BigDecimal primaryVolume;
    private long primaryOperations;
    private List<MonthlyPoint> monthly;

    @Data
    @Builder
    public static class MonthlyPoint {
        private String period;
        private BigDecimal primaryVolume;
        private long primaryOperations;
    }
}
