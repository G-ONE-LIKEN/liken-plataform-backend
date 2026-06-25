-- =============================================================================
-- V2: Agregar estado PENDING_SETTLEMENT al constraint check de orders.
-- =============================================================================

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN ('OPEN', 'PENDING_SETTLEMENT', 'MATCHED', 'CANCELLED', 'EXPIRED'));
