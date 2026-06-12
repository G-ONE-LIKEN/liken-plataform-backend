package com.plataforma.marketplace.event;

import com.plataforma.marketplace.model.ProcessedEvent;
import com.plataforma.marketplace.repository.ProcessedEventRepository;
import com.plataforma.marketplace.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Consume {@code projects.state_changed} para cancelar las ordenes OPEN de un
 * proyecto que se cancela o cierra.
 *
 * <p>Este topic lo publica tanto {@code blockchain-service} (desde el evento
 * on-chain {@code ProjectRegistry.StageChanged}) como {@code project-service}
 * (al cambiar manualmente el estado). Hasta ahora no tenia consumidor (ver
 * implementar.md #10); el marketplace lo necesita para invalidar ordenes de
 * proyectos que dejan de ser tradeables.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectStateChangedConsumer {

    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "projects.state_changed", groupId = "marketplace-service")
    public void consume(Map<String, Object> payload) {
        try {
            String eventId = str(payload.get("eventId"));

            // Idempotencia.
            if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
                log.debug("Evento {} ya procesado, ignorando (idempotencia)", eventId);
                return;
            }

            Long projectId = toLong(payload.get("projectId"));
            String newState = str(payload.get("newState"));

            if (projectId == null || newState == null) {
                log.warn("projects.state_changed con datos incompletos: {}", payload);
                return;
            }

            log.info("[event] projects.state_changed projectId={} newState={}", projectId, newState);

            // Cancelar ordenes si el proyecto ya no permite trading.
            // Un proyecto es tradeable solo en CLOSED (ronda exitosa). Si pasa a
            // CANCELLED, PAUSED u otro estado, las ordenes abiertas se invalidan.
            if ("CANCELLED".equalsIgnoreCase(newState) ||
                "PAUSED".equalsIgnoreCase(newState) ||
                "FAILED".equalsIgnoreCase(newState)) {

                int cancelled = orderService.cancelOrdersForProject(
                        projectId, "Proyecto cambio a estado " + newState);
                log.info("Canceladas {} ordenes del proyecto {} por cambio de estado a {}",
                        cancelled, projectId, newState);
            }

            // Marcar como procesado.
            if (eventId != null) {
                processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(eventId)
                        .topic("projects.state_changed")
                        .processedAt(LocalDateTime.now())
                        .build());
            }
        } catch (Exception e) {
            log.error("Error procesando projects.state_changed: {}", payload, e);
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
}
