CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(512) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    replaced_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by)
        REFERENCES refresh_tokens(id)
);

CREATE INDEX idx_refresh_tokens_user_id_revoked_expires_at
    ON refresh_tokens (user_id, revoked, expires_at);

CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);

CREATE INDEX idx_refresh_tokens_replaced_by
    ON refresh_tokens (replaced_by);
