-- wallet-service: schema inicial

CREATE TABLE wallets (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL UNIQUE,
    balance     DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    currency    VARCHAR(3) NOT NULL DEFAULT 'USD',
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE TABLE wallet_movements (
    id              BIGSERIAL PRIMARY KEY,
    wallet_id       BIGINT NOT NULL REFERENCES wallets(id),
    type            VARCHAR(20) NOT NULL,
    amount          DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    balance_before  DECIMAL(19, 4) NOT NULL,
    balance_after   DECIMAL(19, 4) NOT NULL,
    description     VARCHAR(255),
    reference_id    VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wallet_movements_wallet_id ON wallet_movements(wallet_id);
CREATE INDEX idx_wallet_movements_created_at ON wallet_movements(wallet_id, created_at DESC);
