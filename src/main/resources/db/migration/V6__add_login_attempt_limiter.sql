ALTER TABLE users
    ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN account_locked BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN lock_time TIMESTAMP;

ALTER TABLE users
    ADD CONSTRAINT chk_users_failed_attempts
        CHECK (failed_attempts BETWEEN 0 AND 5),
    ADD CONSTRAINT chk_users_account_lock
        CHECK (
            (account_locked = true AND lock_time IS NOT NULL)
            OR (account_locked = false AND lock_time IS NULL)
        );
