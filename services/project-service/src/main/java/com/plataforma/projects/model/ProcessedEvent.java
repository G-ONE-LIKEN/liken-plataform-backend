package com.plataforma.projects.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 128, nullable = false, unique = true)
    private String eventId;

    @Builder.Default
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt = LocalDateTime.now();
}
