CREATE TABLE processed_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (event_id)
);

CREATE INDEX idx_processed_events_event_id ON processed_events(event_id);
