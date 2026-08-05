-- Account self-deletion is an anonymization: six FKs reference users(id) with ON DELETE
-- RESTRICT (event authorship, attendance_records.set_by, invites, audit_log.actor_id), so the
-- row must survive. deleted_at is what blocks login and invalidates existing tokens.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ NULL;
