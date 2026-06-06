package com.plataforma.invest.event;

import com.plataforma.invest.service.InvestmentService;
import com.plataforma.invest.service.ProjectClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consume {@code investment.token_purchased} publicado por el Blockchain Service
 * desde el evento on-chain {@code OfferingContract.TokensPurchased}.
 *
 * <p>Payload esperado:
 * <pre>
 * {
 *   eventId, occurredAt, version,
 *   walletAddress, userId (resuelto), projectId,
 *   lknAmount (escala 8), usdcAmount (escala 6),
 *   offeringContractAddress, txHash, blockNumber
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokensPurchasedConsumer {

    private final InvestmentService investmentService;
    private final ProjectClient projectClient;

    @KafkaListener(topics = "investment.token_purchased", groupId = "invest-dividend-service")
    public void consume(Map<String, Object> payload) {
        try {
            String eventId = str(payload.get("eventId"));
            String walletAddress = str(payload.get("walletAddress"));
            Long userId = toLong(payload.get("userId"));
            Long projectId = toLong(payload.get("projectId"));
            String offering = str(payload.get("offeringContractAddress"));
            BigDecimal usdcAmount = bigDecimal(payload.get("usdcAmount"));
            BigDecimal lknAmount = bigDecimal(payload.get("lknAmount"));
            String txHash = str(payload.get("txHash"));
            Long blockNumber = toLong(payload.get("blockNumber"));

            // ProjectId desde el evento puede ser null si el Blockchain Service
            // todavía no resuelve offeringContractAddress → projectId. Si está
            // null, descartamos (re-procesable cuando se complete el mapeo).
            if (projectId == null && offering != null && !offering.isBlank()) {
                projectId = projectClient.resolveProjectIdByOffering(offering);
            }

            if (projectId == null) {
                log.warn("token_purchased sin projectId resuelto. offering={} txHash={}. " +
                        "Se descarta hasta que el puente resuelva la address.",
                        offering, txHash);
                return;
            }

            investmentService.recordPurchase(
                    eventId, userId, walletAddress, projectId, offering,
                    usdcAmount, lknAmount, txHash, blockNumber);
        } catch (Exception e) {
            log.error("Error procesando investment.token_purchased: {}", payload, e);
        }
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = v.toString();
        return s.isBlank() ? null : Long.parseLong(s);
    }

    private static BigDecimal bigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }
}
