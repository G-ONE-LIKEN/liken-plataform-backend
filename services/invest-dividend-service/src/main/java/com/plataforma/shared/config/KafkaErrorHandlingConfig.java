package com.plataforma.shared.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Manejo de errores de los consumers Kafka (ADR-0024).
 *
 * Antes: cada consumer envolvía su lógica en try/catch + log.error → el
 * offset se commiteaba igual y el evento se perdía para siempre (p. ej. una
 * compra on-chain que llegaba mientras project-service estaba caído).
 *
 * Ahora: la excepción propaga, el contenedor reintenta 3 veces (cada 2s) y
 * si sigue fallando publica el registro a {@code <topic>.DLT} con headers de
 * diagnóstico. Nada se pierde: el DLT se puede reprocesar (los consumers son
 * idempotentes por eventId).
 */
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template,
                                                 io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        // Partición 0 fija: el DLT se auto-crea con 1 partición; usar la
        // partición original rompería si el topic fuente tiene más de una.
        // Cada derivación incrementa kafka.dlt.messages para alertar (ADR-0025).
        var recoverer = new DeadLetterPublishingRecoverer(template, (record, ex) -> {
            meterRegistry.counter("kafka.dlt.messages", "topic", record.topic()).increment();
            return new TopicPartition(record.topic() + ".DLT", 0);
        });

        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 3));
        handler.setCommitRecovered(true);
        return handler;
    }
}
