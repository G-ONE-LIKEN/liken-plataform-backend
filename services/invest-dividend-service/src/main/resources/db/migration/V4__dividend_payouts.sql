-- =============================================================================
-- V4: Dividendos por proyecto via transferencia directa USDC.
--
-- * `dividend_batch` — agrupa los payouts disparados por un mismo cruce de
--   umbral del acumulador del proyecto. Contador para saber cuando todos los
--   payouts confirmaron (o fallaron) y resetear el in_flight del acumulador.
-- * `dividend_payout` — un registro por holder al que se le pago USDC en un
--   batch. eventId UNIQUE para idempotencia del consumer dividends.paid.
-- =============================================================================

CREATE TABLE dividend_batch (
    batch_id VARCHAR(80) PRIMARY KEY,
    project_id BIGINT NOT NULL,
    total_amount_usdc NUMERIC(20,6) NOT NULL CHECK (total_amount_usdc > 0),
    total_payouts INT NOT NULL CHECK (total_payouts > 0),
    confirmed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMP
);

CREATE INDEX idx_dividend_batch_project ON dividend_batch(project_id);
CREATE INDEX idx_dividend_batch_status ON dividend_batch(status);

CREATE TABLE dividend_payout (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(80) NOT NULL REFERENCES dividend_batch(batch_id),
    project_id BIGINT NOT NULL,
    user_id BIGINT,
    wallet_address VARCHAR(42) NOT NULL,
    amount NUMERIC(20,6) NOT NULL CHECK (amount > 0),
    tx_hash VARCHAR(66),
    block_number BIGINT,
    paid_at TIMESTAMP NOT NULL DEFAULT NOW(),
    payout_event_id VARCHAR(120) NOT NULL UNIQUE
);

CREATE INDEX idx_dividend_payout_user ON dividend_payout(user_id);
CREATE INDEX idx_dividend_payout_project ON dividend_payout(project_id);
CREATE INDEX idx_dividend_payout_wallet ON dividend_payout(wallet_address);
CREATE INDEX idx_dividend_payout_batch ON dividend_payout(batch_id);
