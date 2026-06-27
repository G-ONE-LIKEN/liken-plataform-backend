package com.plataforma.invest.event;

import com.plataforma.invest.service.EnergyAccrualService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consume {@code dividends.deposited} publicado por blockchain-service tras una
 * tx exitosa contra {@code DividendDistributor.depositDividends}. Resetea el
 * saldo in-flight del proyecto (el deposit fue confirmado on-chain).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendDepositedConsumer {

    private final EnergyAccrualService accrualService;

    @KafkaListener(topics = "dividends.deposited", groupId = "invest-dividend-service")
    public void consume(Map<String, Object> payload) {
        try {
            Long projectId = toLong(payload.get("projectId"));
            String txHash = str(payload.get("txHash"));
            if (projectId == null) {
                log.warn("dividends.deposited sin projectId, se ignora: {}", payload);
                return;
            }
            log.info("dividends.deposited recibido: projectId={} txHash={}", projectId, txHash);
            accrualService.clearInFlight(projectId);
        } catch (Exception e) {
            log.error("Error procesando dividends.deposited: {}", payload, e);
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
