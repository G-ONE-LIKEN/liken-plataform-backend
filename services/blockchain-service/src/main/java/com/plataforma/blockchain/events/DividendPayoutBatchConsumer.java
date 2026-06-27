package com.plataforma.blockchain.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.blockchain.service.TokenTransferService;
import com.plataforma.blockchain.service.TokenTransferService.TransferResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consume {@code dividends.payout_batch_requested} y procesa los payouts
 * secuencialmente con {@code usdc.transfer} directo desde el signer admin.
 *
 * <p>Flujo:
 * <ol>
 *   <li>Suma total del batch.</li>
 *   <li>Pre-check {@code signerUsdcBalance >= total}. Si no, publica
 *       {@code payout_batch_failed} y aborta (no quema gas en transfers
 *       imposibles).</li>
 *   <li>Loop secuencial. Cada payout: si OK publica {@code dividends.paid};
 *       si falla (max cap, revert, etc.) publica {@code dividends.paid_failed}
 *       y continua con los demas.</li>
 * </ol>
 *
 * <p>Procesamiento secuencial evita race de nonces con otras txs del mismo
 * signer (settleTrade, etc.).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendPayoutBatchConsumer {

    private final ObjectMapper objectMapper;
    private final TokenTransferService tokenTransferService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private record PayoutItem(Long userId, String walletAddress, BigDecimal amount, String payoutEventId) {}

    @KafkaListener(topics = "dividends.payout_batch_requested", groupId = "blockchain-service")
    public void consume(String payload) {
        log.info("Recibido dividends.payout_batch_requested: {}", payload);
        String batchId = null;
        Long projectId = null;
        try {
            JsonNode node = objectMapper.readTree(payload);
            batchId = node.path("batchId").asText(null);
            projectId = node.has("projectId") && !node.get("projectId").isNull()
                    ? node.get("projectId").asLong() : null;

            List<PayoutItem> payouts = new ArrayList<>();
            BigDecimal totalRequested = BigDecimal.ZERO;
            for (JsonNode p : node.path("payouts")) {
                Long userId = p.has("userId") && !p.get("userId").isNull() ? p.get("userId").asLong() : null;
                String wallet = p.path("walletAddress").asText(null);
                BigDecimal amount = new BigDecimal(p.path("amount").asText("0"));
                String payoutEventId = p.path("payoutEventId").asText(null);
                payouts.add(new PayoutItem(userId, wallet, amount, payoutEventId));
                totalRequested = totalRequested.add(amount);
            }

            if (batchId == null || projectId == null || payouts.isEmpty()) {
                log.warn("Batch incompleto, ignoro: {}", payload);
                return;
            }

            // Pre-check de saldo global del batch.
            BigInteger signerBalance = tokenTransferService.readSignerUsdcBalance();
            BigInteger totalRaw = totalRequested.movePointRight(6).toBigInteger();
            if (signerBalance.compareTo(totalRaw) < 0) {
                String reason = "insufficient_signer_balance " +
                        new BigDecimal(signerBalance).movePointLeft(6) +
                        " < " + totalRequested;
                log.warn("Batch {} abortado: {}", batchId, reason);
                publishBatchFailed(batchId, projectId, reason);
                return;
            }

            // Loop secuencial.
            for (PayoutItem item : payouts) {
                try {
                    TransferResult result = tokenTransferService.executeUsdcTransfer(
                            item.walletAddress(), item.amount());
                    publishPaid(batchId, item, projectId, result);
                } catch (Exception e) {
                    log.error("Payout fallo batch={} wallet={} amount={}: {}",
                            batchId, item.walletAddress(), item.amount(), e.getMessage());
                    publishPaidFailed(batchId, item, projectId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error procesando dividends.payout_batch_requested", e);
            if (batchId != null && projectId != null) {
                publishBatchFailed(batchId, projectId, "consumer_error: " + e.getMessage());
            }
        }
    }

    private void publishPaid(String batchId, PayoutItem item, Long projectId, TransferResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "paid:" + result.txHash());
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version", 1);
        payload.put("batchId", batchId);
        payload.put("payoutEventId", item.payoutEventId());
        payload.put("projectId", projectId);
        payload.put("userId", item.userId());
        payload.put("walletAddress", item.walletAddress());
        payload.put("amount", item.amount().toPlainString());
        payload.put("txHash", result.txHash());
        payload.put("blockNumber", result.blockNumber().toString());
        kafkaTemplate.send("dividends.paid", String.valueOf(projectId), payload);
        log.info("Publicado dividends.paid batch={} wallet={} amount={} tx={}",
                batchId, item.walletAddress(), item.amount(), result.txHash());
    }

    private void publishPaidFailed(String batchId, PayoutItem item, Long projectId, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "paid_failed:" + item.payoutEventId());
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version", 1);
        payload.put("batchId", batchId);
        payload.put("payoutEventId", item.payoutEventId());
        payload.put("projectId", projectId);
        payload.put("walletAddress", item.walletAddress());
        payload.put("amount", item.amount().toPlainString());
        payload.put("reason", reason == null ? "unknown" : reason);
        kafkaTemplate.send("dividends.paid_failed", String.valueOf(projectId), payload);
    }

    private void publishBatchFailed(String batchId, Long projectId, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "batch_failed:" + batchId);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version", 1);
        payload.put("batchId", batchId);
        payload.put("projectId", projectId);
        payload.put("reason", reason);
        kafkaTemplate.send("dividends.payout_batch_failed", String.valueOf(projectId), payload);
    }
}
