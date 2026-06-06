-- =============================================================================
-- V8: Simplificación del modelo de fechas del proyecto
-- -----------------------------------------------------------------------------
-- Decisión de negocio (ver Fase 6 del plan):
--
--   * `expectedOpenDate` queda como ÚNICA fecha clave. Es la apertura del parque
--     Y el deadline implícito del soft cap (se usa también como
--     `OfferingContract.deadline` al deployar el contrato on-chain).
--
--   * Si el softCap se supera antes de `expectedOpenDate` → el contrato dispara
--     `RoundFinalized` → backend pasa de PRE_OPEN a OPEN.
--   * Si llega `expectedOpenDate` sin softCap → el primer `refund()` dispara
--     `RoundFailed` → backend pasa de PRE_OPEN a CANCELLED.
--
--   * `startDate` no aplica: la ronda arranca cuando admin/owner transiciona a
--     PRE_OPEN (manual), no por fecha.
--   * `endDate` no aplica: `CLOSED` es una decisión del owner/admin (baja del
--     proyecto), no una fecha programada.
--   * `softCapDeadline` lo reemplaza `expectedOpenDate` (eran redundantes).
-- =============================================================================

ALTER TABLE projects
    DROP COLUMN start_date,
    DROP COLUMN end_date,
    DROP COLUMN soft_cap_deadline;
