-- Notificaciones in-app por usuario.
-- También sirve como audit log de los emails enviados (un email = una row).

CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    type                VARCHAR(60) NOT NULL,
    title               VARCHAR(200) NOT NULL,
    body                TEXT,
    data                JSONB,
    read_at             TIMESTAMP,
    email_sent_at       TIMESTAMP,
    external_event_id   VARCHAR(64),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Idempotencia: el mismo evento Kafka no puede generar dos notificaciones
    -- al mismo usuario. Se permiten NULLs para broadcasts manuales del admin
    -- (que no tienen externalEventId).
    CONSTRAINT uk_notifications_event UNIQUE (user_id, external_event_id)
);

CREATE INDEX idx_notifications_user_created  ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread   ON notifications(user_id) WHERE read_at IS NULL;
CREATE INDEX idx_notifications_type          ON notifications(type);
