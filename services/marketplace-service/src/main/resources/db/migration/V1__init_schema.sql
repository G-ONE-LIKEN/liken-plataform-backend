-- =============================================================================
-- V1: Schema inicial del marketplace-service.
--
-- Modelo:
--   * `orders`          — ordenes de venta (y futuro compra) del marketplace P2P.
--   * `trades`          — transacciones completadas (un match = un trade).
--   * `processed_event` — idempotencia de eventos Kafka consumidos.
--
-- Decisiones de diseño (ver ADR-0014):
--   * Matching FIFO con price-time priority, sin matching parcial en MVP.
--   * Fee configurable (default 1%) descontado del lado vendedor.
--   * TTL configurable (default 30 dias), @Scheduled marca EXPIRED.
--   * Holdings NO se bloquean al crear la orden; se validan en el momento del match.
-- =============================================================================

CREATE TABLE orders (
    id              BIGSERIAL    PRIMARY KEY,
    seller_id       BIGINT       NOT NULL,
    project_id      BIGINT       NOT NULL,
    side            VARCHAR(4)   NOT NULL DEFAULT 'SELL'
                    CHECK (side IN ('SELL', 'BUY')),
    tokens_amount   NUMERIC(20,8) NOT NULL CHECK (tokens_amount > 0),
    price_per_token NUMERIC(20,6) NOT NULL CHECK (price_per_token > 0),
    status          VARCHAR(10)  NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN', 'MATCHED', 'CANCELLED', 'EXPIRED')),
    expires_at      TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_seller      ON orders(seller_id);
CREATE INDEX idx_orders_project     ON orders(project_id);
CREATE INDEX idx_orders_status      ON orders(status);
-- indice compuesto para el query mas frecuente: ordenes OPEN de un proyecto,
-- ordenadas por precio (price-time priority).
CREATE INDEX idx_orders_open_project ON orders(project_id, price_per_token)
    WHERE status = 'OPEN';

-- Transacciones completadas del marketplace.
CREATE TABLE trades (
    id              BIGSERIAL    PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES orders(id),
    seller_id       BIGINT       NOT NULL,
    buyer_id        BIGINT       NOT NULL,
    project_id      BIGINT       NOT NULL,
    tokens_amount   NUMERIC(20,8) NOT NULL CHECK (tokens_amount > 0),
    price_per_token NUMERIC(20,6) NOT NULL CHECK (price_per_token > 0),
    total_price     NUMERIC(20,6) NOT NULL CHECK (total_price > 0),
    fee_amount      NUMERIC(20,6) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trades_seller  ON trades(seller_id);
CREATE INDEX idx_trades_buyer   ON trades(buyer_id);
CREATE INDEX idx_trades_project ON trades(project_id);

-- Idempotencia: registra qué eventId ya procesamos (Kafka es at-least-once).
CREATE TABLE processed_event (
    event_id     VARCHAR(80)  PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
