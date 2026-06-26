package com.plataforma.projects.event;

import com.plataforma.projects.service.UserHoldingService;
import com.plataforma.projects.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserHoldingEventConsumer {

    private final UserHoldingService userHoldingService;
    private final ProjectRepository projectRepository;

    /**
     * Consume {@code investment.token_purchased}: una wallet compro LKN de un proyecto.
     *
     * <p>Payload (publicado por el Blockchain Service desde el evento on-chain
     * {@code OfferingContract.TokensPurchased}):
     * <pre>
     * {
     *   eventId       : UUID,
     *   walletAddress : 0x... (EIP-55),
     *   userId        : Long (resuelto del walletAddress por el puente; opcional),
     *   projectId     : Long (id local del proyecto en este backend),
     *   lknAmount     : BigDecimal (LKN comprados, escala 8),
     *   usdcAmount    : BigDecimal (USDC pagado,     escala 6),
     *   tokenCount    : Number (LEGACY — alias de lknAmount para compat retrocompat)
     * }
     * </pre>
     */
    @KafkaListener(topics = "investment.token_purchased", groupId = "service-projects")
    public void onTokenPurchased(Map<String, Object> payload) {
        try {
            String eventId = str(payload.get("eventId"));
            String walletAddress = str(payload.get("walletAddress"));
            Long userId    = toLongOrNull(payload.get("userId"));
            Long projectId = resolveProjectId(payload);
            BigDecimal lknAmount = bigDecimal(
                    payload.getOrDefault("tokens", payload.getOrDefault("lknAmount", payload.get("tokenCount"))));
            BigDecimal usdcAmount = bigDecimal(payload.getOrDefault("amount", payload.getOrDefault("usdcAmount", "0")));

            // Camino nuevo (datos on-chain completos): registra wallet + USDC invertido.
            // Camino legacy (sin wallet ni USDC, solo tokenCount): mantiene compat con tests.
            if (walletAddress != null || usdcAmount.signum() > 0) {
                userHoldingService.recordTokenPurchase(walletAddress, userId, projectId,
                        lknAmount, usdcAmount, eventId);
                log.info("Compra on-chain registrada: wallet={} userId={} projectId={} lkn=+{} usdc={}",
                        walletAddress, userId, projectId, lknAmount, usdcAmount);
            } else {
                userHoldingService.updateHolding(userId, projectId, lknAmount, eventId);
                log.info("Holding actualizado (legacy): userId={} projectId={} +{}",
                        userId, projectId, lknAmount);
            }
        } catch (Exception e) {
            log.error("Error procesando investment.token_purchased: {}", payload, e);
        }
    }

    /**
     * Consume marketplace.trade_settled: se completo y liquido una venta P2P.
     * Payload esperado: { eventId, sellerId, buyerId, projectId, tokenCount }
     */
    @KafkaListener(topics = "marketplace.trade_settled", groupId = "service-projects")
    public void onOrderMatched(Map<String, Object> payload) {
        try {
            String eventId = payload.get("eventId") != null ? payload.get("eventId").toString() : null;
            Long sellerId  = toLong(payload.get("sellerId"));
            Long buyerId   = toLong(payload.get("buyerId"));
            Long projectId = toLong(payload.get("projectId"));
            BigDecimal amount = new BigDecimal(payload.get("tokenCount").toString());

            userHoldingService.processOrderMatched(sellerId, buyerId, projectId, amount, eventId);
            log.info("Holdings actualizados por orden liquidada: seller={} buyer={} projectId={} amount={}", sellerId, buyerId, projectId, amount);
        } catch (Exception e) {
            log.error("Error procesando marketplace.trade_settled: {}", payload, e);
        }
    }

    private Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }

    private Long resolveProjectId(Map<String, Object> payload) {
        Object directProjectId = payload.get("projectId");
        if (directProjectId != null) {
            return toLong(directProjectId);
        }

        String offeringAddress = str(payload.get("offeringContractAddress"));
        if (offeringAddress == null || offeringAddress.isBlank()) {
            throw new IllegalArgumentException("Evento sin projectId ni offeringContractAddress");
        }

        return projectRepository.findByActiveTrueAndOfferingContractAddressIgnoreCase(offeringAddress)
                .map(p -> p.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe proyecto local para offeringContractAddress=" + offeringAddress));
    }

    private Long toLongOrNull(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        String s = val.toString();
        return s.isBlank() ? null : Long.parseLong(s);
    }

    private static String str(Object val) {
        return val == null ? null : val.toString();
    }

    private static BigDecimal bigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(val.toString());
    }
}
