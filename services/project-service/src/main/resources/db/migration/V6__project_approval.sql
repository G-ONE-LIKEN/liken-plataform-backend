-- Soporte para aprobacion de proyectos por administrador.
-- Los proyectos creados por developers quedan en estado PENDING_APPROVAL
-- hasta que un admin los aprueba (transicion a DRAFT) o rechaza (CANCELLED).

ALTER TABLE projects
    ADD COLUMN approved_by       BIGINT,
    ADD COLUMN approved_at       TIMESTAMP,
    ADD COLUMN rejection_reason  TEXT;
