package com.plataforma.invest.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tabla de idempotencia. Los consumers chequean si {@code eventId} ya está acá
 * antes de procesar. Kafka es at-least-once.
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
    private LocalDateTime processedAt;
}
