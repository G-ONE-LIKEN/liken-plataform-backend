-- src/main/resources/db/migration/V3__add_wallet.sql

-- =========================
-- WALLETS
-- =========================
-- El PK de wallets ES el mismo que users(id).
-- Esto se logra con @MapsId en JPA y con la FK que tambien es PK en SQL.
-- Garantiza: un usuario = un wallet, sin posibilidad de wallet huerfano.

CREATE TABLE wallets (
    id         BIGINT PRIMARY KEY,
    balance    NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    CONSTRAINT fk_wallet_user FOREIGN KEY (id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

-- indice no necesario: el PK ya indexa por id.
-- La constraint CHECK en balance es una defensa contra saldo negativo en D.B.
-- La otra, es la validacion en dominio (Wallet.hasSufficientFunds).