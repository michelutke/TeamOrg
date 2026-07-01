-- Unified attendance: retire the separate check-in records; responses are the single model.
DROP TABLE IF EXISTS attendance_records;

ALTER TABLE attendance_responses ADD COLUMN unexcused BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE events ADD COLUMN check_in_completed_at TIMESTAMPTZ NULL;
ALTER TABLE events ADD COLUMN default_response TEXT NOT NULL DEFAULT 'none'
  CHECK (default_response IN ('none','accepted','declined'));

ALTER TABLE event_series ADD COLUMN template_default_response TEXT NOT NULL DEFAULT 'none'
  CHECK (template_default_response IN ('none','accepted','declined'));

-- check_in_enabled is retired by the unified lifecycle; leave the column in place (ignored) to
-- avoid churn, or drop if unused elsewhere:
-- ALTER TABLE events DROP COLUMN check_in_enabled;
