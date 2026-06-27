package com.plataforma.invest.event;

import com.plataforma.invest.service.EnergyAccrualService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consume {@code dividends.deposit_failed} publicado por blockchain-service
 * cuando la tx contra el DividendDistributor revierte (gas, USDC insuficiente,
 * lo que sea). Rollbackea el saldo in-flight a pending para que el flujo de
 * acumulacion normal lo vuelva a intentar en el proximo cruce de umbral.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendDepositFailedConsumer {

    private final EnergyAccrualService accrualService;

    @KafkaListener(topics = "dividends.deposit_failed", groupId = "invest-dividend-service")
    public void consume(Map<String, Object> payload) {
        try {
            Long projectId = toLong(payload.get("projectId"));
            String reason = str(payload.get("reason"));
            if (projectId == null) {
                log.warn("dividends.deposit_failed sin projectId, se ignora: {}", payload);
                return;
            }
            log.warn("dividends.deposit_failed projectId={} reason={}", projectId, reason);
            accrualService.rollbackInFlight(projectId);
        } catch (Exception e) {
            log.error("Error procesando dividends.deposit_failed: {}", payload, e);
            throw e;
        }
    }

    private static String str(Object v) { return v == null ? null : v.toString(); }
    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }
}
