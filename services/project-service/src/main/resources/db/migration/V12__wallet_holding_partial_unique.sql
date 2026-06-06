CREATE UNIQUE INDEX IF NOT EXISTS uq_holdings_wallet_project_orphan
    ON user_holdings(wallet_address, project_id)
    WHERE user_id IS NULL AND wallet_address IS NOT NULL;
