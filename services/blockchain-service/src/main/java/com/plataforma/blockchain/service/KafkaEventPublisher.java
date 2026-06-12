package com.plataforma.blockchain.service;

import com.plataforma.blockchain.indexer.PublishedEvent;
import com.plataforma.blockchain.indexer.PublishedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Publica eventos on-chain ya decodificados a Kafka, con guardia local de
 * idempotencia: si el {@code eventId} (txHash:logIndex) ya fue publicado en una
 * corrida previa, no lo reenvía.
 *
 * <p>Los consumers downstream también hacen idempotencia por el mismo {@code eventId}
 * → defense in depth: si crasheamos justo después de Kafka pero antes del commit
 * del checkpoint, al reanudar reescaneamos esos bloques pero ni la BD upstream ni
 * los movimientos downstream se duplican.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PublishedEventRepository publishedEventRepository;

    /**
     * @param topic nombre del topic Kafka (ej. {@code investment.token_purchased}).
     * @param eventId UUID-like único — convención {@code "<txHash>:<logIndex>"}.
     * @param contractAddress address del contrato que emitió el log.
     * @param blockNumber número de bloque del evento on-chain.
     * @param payload mapa que se serializa a JSON y se publica.
     */
    @Transactional
    public void publish(String topic, String eventId, String contractAddress,
                        long blockNumber, Map<String, Object> payload) {
        if (publishedEventRepository.existsByEventId(eventId)) {
            log.debug("Evento {} ya publicado, skip", eventId);
            return;
        }

        // Aseguramos que el payload trae el eventId al frente (los consumers lo usan).
        payload.putIfAbsent("eventId", eventId);
        payload.putIfAbsent("version", 1);
        payload.putIfAbsent("occurredAt", LocalDateTime.now().toString());

        // Envío SINCRONO (ADR-0024): si Kafka no confirma, la excepción
        // propaga, la transacción rollbackea y el indexer NO avanza el
        // checkpoint → el bloque se reescanea (at-least-once). Los duplicados
        // los absorbe la idempotencia por eventId en ambos extremos.
        try {
            kafkaTemplate.send(topic, eventId, payload).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido publicando " + eventId + " a " + topic, e);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka no confirmó " + eventId + " a " + topic, e);
        }

        publishedEventRepository.save(PublishedEvent.builder()
                .eventId(eventId)
                .topic(topic)
                .contractAddress(contractAddress)
                .blockNumber(blockNumber)
                .publishedAt(LocalDateTime.now())
                .build());

        log.info("Publicado {} → {} (block {})", topic, eventId, blockNumber);
    }

    /** Construye el eventId canónico para un log on-chain. */
    public static String eventIdOf(String txHash, long logIndex) {
        return txHash + ":" + logIndex;
    }
}
