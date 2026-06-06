ALTER TABLE wallet_movements
    ALTER COLUMN external_event_id TYPE VARCHAR(128);

ALTER TABLE pending_wallet_movements
    ALTER COLUMN external_event_id TYPE VARCHAR(128);
