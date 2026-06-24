-- V12: Campos para integración KYC automatizado con Didit (ver feature/kyc).
--
-- didit_session_id: ID de sesión generado por Didit al iniciar verificación.
--   Se usa para vincular el webhook entrante con el usuario correspondiente.
--   El webhook de Didit incluye este ID en el payload.
--
-- kyc_verified_at: Timestamp de cuándo Didit aprobó la verificación.
--   Complementa kyc_status=APPROVED con auditoría temporal.
--
-- Ambos campos son opcionales (nullable): solo se populan cuando el usuario
-- usa el flujo automatizado con Didit. El flujo manual con documentos (DD013)
-- sigue funcionando igual — esos usuarios tendrán estos campos en NULL.
-- ────────────────────────────────────────────────────────────────────────

ALTER TABLE users ADD COLUMN didit_session_id  VARCHAR(255);
ALTER TABLE users ADD COLUMN kyc_verified_at   TIMESTAMP;

CREATE UNIQUE INDEX idx_users_didit_session_id
    ON users(didit_session_id)
    WHERE didit_session_id IS NOT NULL;
