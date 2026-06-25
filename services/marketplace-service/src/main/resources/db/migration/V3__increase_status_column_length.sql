-- =============================================================================
-- V3: Aumentar la longitud de la columna status en orders para permitir PENDING_SETTLEMENT
-- =============================================================================

ALTER TABLE orders ALTER COLUMN status TYPE VARCHAR(30);
