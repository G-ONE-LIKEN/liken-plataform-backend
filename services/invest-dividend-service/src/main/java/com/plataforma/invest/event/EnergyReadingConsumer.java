package com.plataforma.invest.event;

import com.plataforma.invest.service.EnergyAccrualService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Consume {@code oracle.energy_reading} publicado por oracle-service.
 *
 * <p>Payload (oracle-service/OracleEventPublisher):
 * <pre>
 * { projectId, readingTimestamp, energyKWh, timestamp }
 * </pre>
 * No trae eventId: se deriva determinísticamente de (projectId, readingTimestamp)
 * en {@link EnergyAccrualService#accrueReading} para idempotencia.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnergyReadingConsumer {

    private final EnergyAccrualService accrualService;

    @KafkaListener(topics = "oracle.energy_reading", groupId = "invest-dividend-service")
    public void consume(Map<String, Object> payload) {
        try {
            Long projectId = toLong(payload.get("projectId"));
            BigDecimal energyKwh = bigDecimal(payload.get("energyKWh"));
            LocalDateTime recordedAt = parseTimestamp(payload.get("readingTimestamp"));

            if (projectId == null || energyKwh == null || recordedAt == null) {
                log.warn("oracle.energy_reading incompleto, se ignora: {}", payload);
                return;
            }

            accrualService.accrueReading(projectId, energyKwh, recordedAt);
        } catch (Exception e) {
            log.error("Error procesando oracle.energy_reading: {}", payload, e);
            throw e; // dispara retry / DLT
        }
    }

    private static LocalDateTime parseTimestamp(Object v) {
        if (v == null) return null;
        return LocalDateTime.parse(v.toString());
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = v.toString();
        return s.isBlank() ? null : Long.parseLong(s);
    }

    private static BigDecimal bigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(v.toString());
    }
}
