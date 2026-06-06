package com.plataforma.oracle.event;

import com.plataforma.oracle.model.EnergyReading;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OracleEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEnergyReading(EnergyReading reading) {
        Map<String, Object> payload = Map.of(
                "projectId", reading.getProjectId(),
                "readingTimestamp", reading.getReadingTimestamp().toString(),
                "energyKWh", reading.getEnergyKWh(),
                "timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("oracle.energy_reading", reading.getProjectId().toString(), payload);
        log.info("Evento oracle.energy_reading publicado: projectId={} energyKWh={}",
                reading.getProjectId(), reading.getEnergyKWh());
    }
}