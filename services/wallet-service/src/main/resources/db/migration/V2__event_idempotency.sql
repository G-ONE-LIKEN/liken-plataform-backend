-- V2: Idempotencia en consumers de Kafka.
--
-- Agrega `external_event_id` a `wallet_movements` para detectar y descartar
-- eventos duplicados (at-least-once delivery de Kafka — ver DD010).
--
-- El UNIQUE INDEX es parcial: solo aplica cuando `external_event_id` no es NULL.
-- Los movimientos de deposit/withdraw del endpoint público no tienen eventId
-- (no son originados por Kafka), por eso la columna admite NULL.

ALTER TABLE wallet_movements ADD COLUMN external_event_id VARCHAR(64);

CREATE UNIQUE INDEX uk_wallet_movements_event_id
    ON wallet_movements(external_event_id)
    WHERE external_event_id IS NOT NULL;
