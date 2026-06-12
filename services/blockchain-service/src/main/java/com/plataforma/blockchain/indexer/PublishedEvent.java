package com.plataforma.blockchain.indexer;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro local de qué eventId ya publicamos en Kafka.
 *
 * <p>Los consumers downstream (wallet-service, project-service) ya hacen
 * idempotencia por {@code externalEventId}. Esta tabla protege el upstream:
 * si crasheamos entre publicar y commitear el checkpoint, al reanudar
 * detectamos que el evento ya salio y lo saltamos.
 *
 * <p>Clave: {@code txHash:logIndex} (unico on-chain).
 */
@Entity
@Table(name = "published_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedEvent {

    @Id
    @Column(name = "event_id", length = 80)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "contract_address", nullable = false, length = 42)
    private String contractAddress;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;
}
