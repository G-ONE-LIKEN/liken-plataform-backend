-- =============================================================================
-- V7: Colapso a token global (LKN unico) + identificadores on-chain
-- -----------------------------------------------------------------------------
-- Decisiones que esta migracion refleja (ver doc implementar-con-blockchain.md):
--
--   * El token LKN es GLOBAL para toda la plataforma; no se mintea por proyecto.
--     `total_tokens` deja de ser un cap de mint y pasa a representar el tramo
--     asignado a la ronda (los LKN que el emisor deposito en escrow del
--     OfferingContract de ese proyecto). El nombre se mantiene por compatibilidad.
--
--   * El precio unico `token_price` se desdobla en `early_bird_price` (etapa
--     FUNDING) y `standard_price` (etapa ACTIVE), reflejando el modelo del
--     ProjectRegistry.sol (con la constraint `early_bird < standard`).
--
--   * Cada proyecto puede tener un OfferingContract desplegado (la direccion
--     y el projectId del Registry on-chain). Eso se setea desde el Blockchain
--     Service tras un deploy exitoso.
--
--   * Se incorpora el estado de la ronda on-chain (refleja
--     OfferingContract.state: PENDING/OPEN/FINALIZED/FAILED). Es ortogonal al
--     ProjectState del backend, que sigue manejando el ciclo de vida del
--     negocio.
-- =============================================================================

-- 1) Renombrar token_price → early_bird_price ────────────────────────────────
ALTER TABLE projects
    RENAME COLUMN token_price TO early_bird_price;

-- 2) Agregar standard_price (default = early_bird * 1.25 para no romper filas) ─
ALTER TABLE projects
    ADD COLUMN standard_price NUMERIC(15,4);

UPDATE projects
SET standard_price = early_bird_price * 1.25
WHERE standard_price IS NULL;

ALTER TABLE projects
    ALTER COLUMN standard_price SET NOT NULL,
    ADD CONSTRAINT projects_standard_gt_earlybird
        CHECK (standard_price > early_bird_price);

-- Las constraints del campo viejo (token_price > 0) viajan con el rename;
-- la dejamos vivir sobre early_bird_price (mismo nombre interno de constraint).

-- 3) Identificadores on-chain del proyecto ───────────────────────────────────
ALTER TABLE projects
    ADD COLUMN registry_project_id BIGINT,
    ADD COLUMN offering_contract_address VARCHAR(42),
    ADD COLUMN deploy_tx_hash VARCHAR(66),
    ADD COLUMN deploy_block_number BIGINT,
    ADD COLUMN on_chain_status VARCHAR(20) NOT NULL DEFAULT 'NOT_DEPLOYED',
    ADD COLUMN round_state VARCHAR(20);

ALTER TABLE projects
    ADD CONSTRAINT projects_offering_address_format
        CHECK (offering_contract_address IS NULL
               OR offering_contract_address ~ '^0x[a-fA-F0-9]{40}$');

ALTER TABLE projects
    ADD CONSTRAINT projects_deploy_tx_hash_format
        CHECK (deploy_tx_hash IS NULL
               OR deploy_tx_hash ~ '^0x[a-fA-F0-9]{64}$');

-- Un OfferingContract a la vez por proyecto, unico en la plataforma.
CREATE UNIQUE INDEX projects_offering_address_unique
    ON projects (offering_contract_address)
    WHERE offering_contract_address IS NOT NULL;

CREATE UNIQUE INDEX projects_registry_project_id_unique
    ON projects (registry_project_id)
    WHERE registry_project_id IS NOT NULL;

-- 4) UserHolding: reinterpretacion bajo token global ─────────────────────────
-- El holding ahora es un iNDICE de compras por proyecto (analitica). El balance
-- real de LKN del usuario esta on-chain (LinkenToken.balanceOf(wallet)).
-- Se mantiene `tokens_amount` como "LKN comprados acumulados de este proyecto"
-- por trazabilidad y para conservar la API/tests existentes.
--
-- Se agrega `wallet_address` para que el Blockchain Service pueda registrar
-- compras antes incluso de que el backend conozca el userId (lookup posterior).
ALTER TABLE user_holdings
    ADD COLUMN wallet_address VARCHAR(42),
    ADD COLUMN usdc_invested NUMERIC(20,6) NOT NULL DEFAULT 0;

ALTER TABLE user_holdings
    ADD CONSTRAINT user_holdings_wallet_address_format
        CHECK (wallet_address IS NULL
               OR wallet_address ~ '^0x[a-fA-F0-9]{40}$');

CREATE INDEX idx_holdings_wallet ON user_holdings(wallet_address);
