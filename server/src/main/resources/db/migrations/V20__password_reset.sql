-- Password reset: only the SHA-256 hash of the emailed token is stored. token_version is bumped
-- on reset and carried as a claim in every JWT, so all previously issued tokens stop working.
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);
