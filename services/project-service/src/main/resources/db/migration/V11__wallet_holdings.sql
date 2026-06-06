ALTER TABLE user_holdings ALTER COLUMN user_id DROP NOT NULL;
CREATE INDEX idx_holdings_wallet_project ON user_holdings(wallet_address, project_id);
