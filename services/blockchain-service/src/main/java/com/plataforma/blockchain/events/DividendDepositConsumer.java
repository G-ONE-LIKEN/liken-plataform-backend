package com.plataforma.blockchain.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.blockchain.service.TokenTransferService;
import com.plataforma.blockchain.service.TokenTransferService.DepositResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consume {@code dividends.deposit_requested} (publicado por
 * invest-dividend-service cuando el saldo acumulado por el oracle cruza el
 * umbral). Ejecuta {@code DividendDistributor.depositDividends} on-chain y, si
 * sale OK, publica {@code dividends.deposited} con el txHash.
 *
 * <p>Si la tx falla la excepcion se propaga: el DefaultErrorHandler reintenta
 * y, agotados los retries, manda el evento al DLT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendDepositConsumer {

    private final ObjectMapper objectMapper;
    private final TokenTransferService tokenTransferService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "dividends.deposit_requested", groupId = "blockchain-service")
    public void consume(String payload) {
        log.info("Recibido dividends.deposit_requested: {}", payload);
        Long projectId = null;
        BigDecimal amountUsdc = BigDecimal.ZERO;
        String requestEventId = null;
        try {
            JsonNode node = objectMapper.readTree(payload);
            requestEventId = node.path("eventId").asText(null);
            projectId = node.has("projectId") && !node.get("projectId").isNull()
                    ? node.get("projectId").asLong() : null;
            amountUsdc = new BigDecimal(node.path("amountUsdc").asText("0"));

            if (projectId == null || amountUsdc.signum() <= 0) {
                log.warn("Payload invalido, se ignora: {}", payload);
                return;
            }

            DepositResult result = tokenTransferService.executeDepositDividends(amountUsdc);
            publishDeposited(requestEventId, projectId, amountUsdc, result);

        } catch (Exception e) {
            // No re-lanzamos: el reintento on-chain lo maneja el flujo de negocio.
            // Publicamos deposit_failed para que invest devuelva el in-flight a
            // pending; cuando se acumule de nuevo y cruce el umbral, se va a
            // re-emitir un deposit_requested fresco. Asi un error transitorio no
            // congela el saldo del proyecto en in-flight para siempre.
            log.error("Deposit on-chain fallo projectId={} amount={}: {}",
                    projectId, amountUsdc, e.getMessage(), e);
            if (projectId != null) {
                publishFailed(requestEventId, projectId, amountUsdc, e.getMessage());
            }
        }
    }

    private void publishDeposited(String requestEventId, Long projectId,
                                  BigDecimal amountUsdc, DepositResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "deposited:" + result.txHash());
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version", 1);
        payload.put("requestEventId", requestEventId);
        payload.put("projectId", projectId);
        payload.put("amountUsdc", amountUsdc.toPlainString());
        payload.put("txHash", result.txHash());
        payload.put("blockNumber", result.blockNumber().toString());
        kafkaTemplate.send("dividends.deposited", String.valueOf(projectId), payload);
        log.info("Publicado dividends.deposited projectId={} amount={} tx={}",
                projectId, amountUsdc, result.txHash());
    }

    private void publishFailed(String requestEventId, Long projectId,
                               BigDecimal amountUsdc, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "deposit_failed:" + requestEventId);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version", 1);
        payload.put("requestEventId", requestEventId);
        payload.put("projectId", projectId);
        payload.put("amountUsdc", amountUsdc.toPlainString());
        payload.put("reason", reason == null ? "unknown" : reason);
        kafkaTemplate.send("dividends.deposit_failed", String.valueOf(projectId), payload);
        log.warn("Publicado dividends.deposit_failed projectId={} amount={}", projectId, amountUsdc);
    }
}
