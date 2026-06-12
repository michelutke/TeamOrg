-- Add status column to clubs table
ALTER TABLE clubs ADD COLUMN status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'deactivated', 'deleted'));

-- Create audit_log table (immutable — INSERT + SELECT only for app role)
CREATE TABLE audit_log (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id             UUID NOT NULL REFERENCES users(id),
    actor_email          TEXT NOT NULL,
    action               TEXT NOT NULL,
    target_type          TEXT,
    target_id            TEXT,
    details              TEXT,
    impersonation_context TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_log_actor      ON audit_log(actor_id);
CREATE INDEX idx_audit_log_action     ON audit_log(action);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

-- SA-12: immutability requirement documented.
-- In production, run as superuser before app starts:
--   REVOKE UPDATE, DELETE ON audit_log FROM teamorg_app;
COMMENT ON TABLE audit_log IS 'Immutable audit log. Production DB role must have INSERT+SELECT only. No UPDATE/DELETE.';

-- Create impersonation_sessions table
CREATE TABLE impersonation_sessions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id   UUID NOT NULL REFERENCES users(id),
    target_id  UUID NOT NULL REFERENCES users(id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    ended_at   TIMESTAMPTZ,
    is_active  BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX idx_impersonation_actor  ON impersonation_sessions(actor_id);
CREATE INDEX idx_impersonation_active ON impersonation_sessions(is_active) WHERE is_active = true;
