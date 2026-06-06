package com.plataforma.wallet.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class PrimarySalesReportClient {

    private final RestTemplate restTemplate;
    private final String investServiceUrl;

    public PrimarySalesReportClient(RestTemplateBuilder builder,
                                    @Value("${services.invest-service-url:http://invest-dividend-service:8083}") String investServiceUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.investServiceUrl = investServiceUrl;
    }

    public PrimarySalesReport fetch(LocalDate from, LocalDate to) {
        try {
            return restTemplate.getForObject(
                    investServiceUrl + "/internal/reports/primary-sales?from={from}&to={to}",
                    PrimarySalesReport.class,
                    from,
                    to);
        } catch (Exception ex) {
            log.warn("invest-dividend-service /internal/reports/primary-sales falló: {}", ex.getMessage());
            return null;
        }
    }

    @Data
    public static class PrimarySalesReport {
        private LocalDate from;
        private LocalDate to;
        private BigDecimal primaryVolume;
        private long primaryOperations;
        private List<MonthlyPoint> monthly;
    }

    @Data
    public static class MonthlyPoint {
        private String period;
        private BigDecimal primaryVolume;
        private long primaryOperations;
    }
}
