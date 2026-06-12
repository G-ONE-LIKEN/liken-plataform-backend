-- V6: Tiers de inversores + KYC (ver DD013).
--
-- Tiers:
--   BASIC (default), SILVER, GOLD. Upgrade automatico por monto invertido,
--   publicado via evento user.tier_changed por invest-dividend-service.
--
-- KYC:
--   Estados NOT_STARTED (default), PENDING, APPROVED, REJECTED.
--   Tabla kyc_documents para guardar los archivos subidos por el usuario.
--   Los archivos fisicos viven en GCS bajo prefijo "kyc/{userId}/" (ver DD014).

-- ────────────────────────────────────────────────────────────────────────
-- USERS: nuevos campos
-- ────────────────────────────────────────────────────────────────────────

ALTER TABLE users ADD COLUMN tier VARCHAR(20) NOT NULL DEFAULT 'BASIC';
ALTER TABLE users ADD COLUMN kyc_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED';

-- ────────────────────────────────────────────────────────────────────────
-- KYC_DOCUMENTS
-- ────────────────────────────────────────────────────────────────────────

CREATE TABLE kyc_documents (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id),
    document_type    VARCHAR(50) NOT NULL,
    s3_key           VARCHAR(500) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    uploaded_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at      TIMESTAMP,
    reviewed_by      BIGINT,
    rejection_reason TEXT
);

CREATE INDEX idx_kyc_documents_user_id ON kyc_documents(user_id);
CREATE INDEX idx_kyc_documents_status  ON kyc_documents(status);

-- ────────────────────────────────────────────────────────────────────────
-- PERMISSION: kyc:review (para ADMIN aprobar/rechazar)
-- ────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, description) VALUES
    ('kyc:review', 'Permiso para aprobar o rechazar verificaciones KYC');

INSERT INTO roles_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ADMIN' AND p.name = 'kyc:review';
