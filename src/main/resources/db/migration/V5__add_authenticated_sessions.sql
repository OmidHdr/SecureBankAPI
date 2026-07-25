CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
);

CREATE INDEX idx_auth_sessions_user_id_revoked_expires_at
    ON auth_sessions (user_id, revoked, expires_at);

ALTER TABLE refresh_tokens
    ADD COLUMN session_id UUID;

INSERT INTO auth_sessions (
    id,
    user_id,
    revoked,
    created_at,
    last_activity_at,
    expires_at
)
SELECT
    md5('legacy-refresh-token:' || id)::UUID,
    user_id,
    true,
    created_at,
    COALESCE(last_used_at, created_at),
    expires_at
FROM refresh_tokens;

UPDATE refresh_tokens
SET
    session_id = md5('legacy-refresh-token:' || id)::UUID,
    revoked = true;

ALTER TABLE refresh_tokens
    ALTER COLUMN session_id SET NOT NULL,
    ADD CONSTRAINT fk_refresh_tokens_session
        FOREIGN KEY (session_id)
        REFERENCES auth_sessions(id);

CREATE INDEX idx_refresh_tokens_session_id_revoked
    ON refresh_tokens (session_id, revoked);
