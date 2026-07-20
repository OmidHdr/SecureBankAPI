CREATE INDEX idx_accounts_user_id
    ON accounts (user_id);

CREATE INDEX idx_transactions_from_account_created_at
    ON transactions (from_account_id, created_at DESC);

CREATE INDEX idx_transactions_to_account_created_at
    ON transactions (to_account_id, created_at DESC);
