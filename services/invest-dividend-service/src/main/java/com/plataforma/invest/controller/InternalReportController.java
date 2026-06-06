package com.plataforma.invest.controller;

import com.plataforma.invest.dto.InternalPrimarySalesReportResponse;
import com.plataforma.invest.service.PrimarySalesReportService;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/internal/reports")
@RequiredArgsConstructor
public class InternalReportController {

    private final PrimarySalesReportService primarySalesReportService;

    @PermitAll
    @GetMapping("/primary-sales")
    public ResponseEntity<InternalPrimarySalesReportResponse> primarySales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(primarySalesReportService.generate(from, to));
    }
}
