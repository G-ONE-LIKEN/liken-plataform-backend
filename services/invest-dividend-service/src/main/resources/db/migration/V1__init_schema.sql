-- =============================================================================
-- V1: Schema inicial del invest-dividend-service.
--
-- Modelo:
--   * `investment` — una fila por compra primaria on-chain (evento TokensPurchased).
--   * `user_investment_total` — agregado: total USDC invertido por usuario y
--     tier actual derivado. Lo mantiene actualizado el consumer.
--   * `dividend_claim` — una fila por reclamo de dividendos on-chain
--     (evento DividendsWithdrawn).
--   * `processed_event` — idempotencia: registra qué `eventId` ya procesamos
--     (Kafka es at-least-once).
-- =============================================================================

CREATE TABLE investment (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    project_id BIGINT NOT NULL,
    offering_contract_address VARCHAR(42),
    usdc_amount NUMERIC(20,6) NOT NULL CHECK (usdc_amount > 0),
    lkn_amount NUMERIC(20,8) NOT NULL CHECK (lkn_amount > 0),
    tx_hash VARCHAR(66) NOT NULL,
    block_number BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    -- Una compra on-chain (txHash + buyer) no se duplica. Defense in depth ademas
    -- del eventId idempotente de processed_event.
    CONSTRAINT uq_investment_tx_user UNIQUE (tx_hash, user_id)
);

CREATE INDEX idx_investment_user ON investment(user_id);
CREATE INDEX idx_investment_project ON investment(project_id);
CREATE INDEX idx_investment_wallet ON investment(wallet_address);

CREATE TABLE user_investment_total (
    user_id BIGINT PRIMARY KEY,
    total_usdc_invested NUMERIC(20,6) NOT NULL DEFAULT 0,
    current_tier VARCHAR(20) NOT NULL DEFAULT 'BRONZE',
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE dividend_claim (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(42) NOT NULL,
    amount NUMERIC(20,6) NOT NULL CHECK (amount > 0),
    tx_hash VARCHAR(66) NOT NULL,
    block_number BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dividend_claim_user ON dividend_claim(user_id);
CREATE INDEX idx_dividend_claim_wallet ON dividend_claim(wallet_address);

CREATE TABLE processed_event (
    event_id VARCHAR(80) PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);
