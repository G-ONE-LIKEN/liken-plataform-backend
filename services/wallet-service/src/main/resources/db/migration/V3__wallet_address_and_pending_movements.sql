-- wallet-service: wallet_address en wallets + tabla pending_wallet_movements

ALTER TABLE wallets ADD COLUMN wallet_address VARCHAR(42) UNIQUE;

CREATE TABLE pending_wallet_movements (
    id                  BIGSERIAL PRIMARY KEY,
    wallet_address      VARCHAR(42) NOT NULL,
    type                VARCHAR(20) NOT NULL,
    amount              DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    description         VARCHAR(255),
    reference_id        VARCHAR(100),
    external_event_id   VARCHAR(64) UNIQUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pending_wallet_address ON pending_wallet_movements(wallet_address);
CREATE INDEX idx_pending_event_id ON pending_wallet_movements(external_event_id);
