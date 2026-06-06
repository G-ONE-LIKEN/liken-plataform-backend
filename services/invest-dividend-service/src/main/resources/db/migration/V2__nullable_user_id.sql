ALTER TABLE investment ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE dividend_claim ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE investment DROP CONSTRAINT uq_investment_tx_user;
ALTER TABLE investment ADD CONSTRAINT uq_investment_tx UNIQUE (tx_hash);
