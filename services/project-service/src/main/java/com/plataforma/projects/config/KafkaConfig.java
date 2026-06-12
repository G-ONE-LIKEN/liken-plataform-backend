package com.plataforma.projects.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    /**
     * Retries + DLT para los consumers (ADR-0024): 3 reintentos (cada 2s);
     * si sigue fallando, el registro va a {@code <topic>.DLT} para reproceso
     * (los consumers son idempotentes por eventId).
     * Spring Boot lo enchufa automáticamente en la container factory.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", 0));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 3));
        handler.setCommitRecovered(true);
        return handler;
    }

    // Tópicos que publica este servicio
    @Bean
    public NewTopic topicProjectsCreated() {
        return TopicBuilder.name("projects.created").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic topicProjectsStateChanged() {
        return TopicBuilder.name("projects.state_changed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic topicProjectsMetricsUpdated() {
        return TopicBuilder.name("projects.metrics_updated").partitions(3).replicas(1).build();
    }
}
