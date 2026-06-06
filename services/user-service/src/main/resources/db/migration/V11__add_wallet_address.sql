-- ============================================================================
-- V11: Vínculo wallet on-chain (custodia híbrida)
-- ----------------------------------------------------------------------------
-- Cada usuario puede vincular UNA wallet (MetaMask). El backend no custodia
-- claves: el usuario firma un nonce con su wallet y este servicio verifica la
-- firma con ecrecover (ver WalletLinkingService).
--
-- Formato: dirección EIP-55 (checksum case-sensitive), 42 chars (0x + 40 hex).
-- Se persiste el checksum tal cual lo devuelve `Keys.toChecksumAddress` para
-- mantener la convención EIP-55. El UNIQUE es case-sensitive a propósito —
-- la app normaliza siempre a checksum antes de persistir.
-- ============================================================================

ALTER TABLE users
    ADD COLUMN wallet_address VARCHAR(42);

ALTER TABLE users
    ADD CONSTRAINT users_wallet_address_unique UNIQUE (wallet_address);

ALTER TABLE users
    ADD CONSTRAINT users_wallet_address_format
    CHECK (wallet_address IS NULL OR wallet_address ~ '^0x[a-fA-F0-9]{40}$');
