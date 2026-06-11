package com.plataforma.marketplace.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registro de un eventId ya procesado para garantizar idempotencia en los
 * consumers Kafka (at-least-once delivery).
 */
@Entity
@Table(name = "processed_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 80)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();
}
