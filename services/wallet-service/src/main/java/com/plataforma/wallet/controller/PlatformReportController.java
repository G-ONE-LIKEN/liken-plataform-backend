package com.plataforma.wallet.controller;

import com.plataforma.shared.dto.ApiResponse;
import com.plataforma.wallet.dto.PlatformReportResponse;
import com.plataforma.wallet.service.PlatformReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/wallets/admin")
@RequiredArgsConstructor
public class PlatformReportController {

    private final PlatformReportService reportService;

    /**
     * Informe agregado de ingresos y actividad financiera de la plataforma.
     * Solo accesible para administradores.
     */
    @GetMapping("/platform-report")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PlatformReportResponse>> getReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(reportService.generate(from, to)));
    }
}
