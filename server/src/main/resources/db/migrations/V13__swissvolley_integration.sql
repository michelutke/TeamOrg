-- Per-club API key. Stored as TEXT; at-rest encryption handled at the volume level (§9).
CREATE TABLE club_integrations (
  club_id            UUID PRIMARY KEY REFERENCES clubs(id) ON DELETE CASCADE,
  provider           TEXT NOT NULL DEFAULT 'swissvolley',
  api_key            TEXT NOT NULL,
  key_valid          BOOLEAN,
  last_validated_at  TIMESTAMPTZ,
  sync_paused_reason TEXT NULL,             -- set when key is revoked/invalid
  created_by         UUID REFERENCES users(id),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Team <-> SwissVolley link. Separate table => 1:N ready (a team can play >1 league).
CREATE TABLE team_sv_links (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  team_id             UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  sv_team_id          INT  NOT NULL,        -- stable across seasons (identity)
  sv_seasonal_team_id INT,                  -- per-season, refreshed at rollover
  sv_league_caption   TEXT,
  sv_gender           TEXT,
  deprecated_at       TIMESTAMPTZ NULL,     -- set when sv_team_id vanishes (rollover) or integration deleted; team becomes migratable (§14)
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (team_id, sv_team_id)
);
CREATE INDEX idx_team_sv_links_team ON team_sv_links(team_id);
CREATE INDEX idx_team_sv_links_svid ON team_sv_links(sv_team_id);

-- Coach opt-in toggle (per team; covers all of that team's SV links).
ALTER TABLE teams ADD COLUMN games_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE;
-- Lineage: target team a deprecated team was migrated into (§14); enables schedule carry-over (§15).
ALTER TABLE teams ADD COLUMN predecessor_team_id UUID NULL REFERENCES teams(id);

-- External-game linkage + review/lifecycle state on events.
ALTER TABLE events
  ADD COLUMN external_source    TEXT   NULL CHECK (external_source IN ('swissvolley')),
  ADD COLUMN external_game_id   BIGINT NULL,         -- SwissVolley gameId (stable)
  ADD COLUMN external_hash      TEXT   NULL,         -- hash of synced facts (change detect)
  ADD COLUMN external_synced_at TIMESTAMPTZ NULL,
  ADD COLUMN external_status    TEXT   NULL          -- 'synced' | 'postponed' (vanished from feed)
                                CHECK (external_status IN ('synced','postponed')),
  ADD COLUMN needs_review       BOOLEAN NOT NULL DEFAULT FALSE;  -- facts changed; coach must reconcile
CREATE UNIQUE INDEX uq_events_external
  ON events(external_source, external_game_id) WHERE external_game_id IS NOT NULL;

-- Per-club sync bookkeeping.
CREATE TABLE sv_sync_state (
  club_id        UUID PRIMARY KEY REFERENCES clubs(id) ON DELETE CASCADE,
  last_synced_at TIMESTAMPTZ,
  last_status    TEXT,                       -- 'ok' | 'error' | 'paused'
  last_error     TEXT
);

-- Dedicated system author for synced events (events.created_by is NOT NULL). Fixed UUID.
INSERT INTO users (id, email, password_hash, display_name, is_super_admin)
VALUES ('00000000-0000-4000-a000-0000000000a1'::uuid,
        'volleymanager@system.teamorg.ch', '!', 'VolleyManager', FALSE)
ON CONFLICT (id) DO NOTHING;
-- Conflict target is `id`, not `email`: synced events set created_by to this exact UUID, so the
-- row MUST exist under it. A pre-existing different row with the same email surfaces loudly here
-- rather than as silent FK failures at sync time. password_hash '!' is unusable → never logs in.

-- Coach-facing SV game notifications (per user/team). Manager alerts (sv_key_invalid,
-- sv_team_available) are operational/club-level and stay always-on (no per-team toggle).
ALTER TABLE notification_settings
  ADD COLUMN sv_games BOOLEAN NOT NULL DEFAULT TRUE;  -- covers sv_game_new + sv_game_changed
