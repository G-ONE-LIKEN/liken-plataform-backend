-- =============================================================================
-- V1: Estado del indexer y trazabilidad de eventos publicados
-- -----------------------------------------------------------------------------
-- El indexer hace polling de bloques con `eth_getLogs` y va guardando el último
-- bloque ya procesado por dirección de contrato. Tras un restart, reanuda desde
-- ese punto (no re-procesa eventos antiguos).
--
-- El identificador único de un evento on-chain es `txHash:logIndex` — usamos eso
-- como `event_id` Kafka, y lo guardamos también acá para evitar reenvíos en caso
-- de doble publicación si crash entre eth_getLogs y commit del checkpoint.
-- =============================================================================

CREATE TABLE indexer_checkpoint (
    contract_address VARCHAR(42) PRIMARY KEY,
    last_processed_block BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE published_event (
    event_id VARCHAR(80) PRIMARY KEY, -- "txHash:logIndex"
    topic VARCHAR(100) NOT NULL,
    contract_address VARCHAR(42) NOT NULL,
    block_number BIGINT NOT NULL,
    published_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_published_event_block ON published_event(block_number);
CREATE INDEX idx_published_event_topic ON published_event(topic);
