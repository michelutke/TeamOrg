-- NDS (J+S / Nationale Datenbank Sport) import & export.
-- See docs/nds-import-export-design.md.

-- Team <-> NDS course (Kurs) link. 1 team = 1 NDS Kurs/season is enough today.
ALTER TABLE teams
  ADD COLUMN nds_angebot_id    TEXT NULL,   -- the 'Angebot' number, e.g. '753813'
  ADD COLUMN nds_kurs_name     TEXT NULL,
  ADD COLUMN nds_hauptsportart TEXT NULL,
  ADD COLUMN nds_nutzergruppe  TEXT NULL;   -- NG 1/2/4/5 — needed to validate DAUER
CREATE UNIQUE INDEX uq_teams_nds_angebot ON teams(nds_angebot_id) WHERE nds_angebot_id IS NOT NULL;

-- Provisional accounts: imported members get a real users row (so they can hold attendance)
-- with an unusable password and a synthetic email, exactly like the V13 VolleyManager user.
-- The flag lets us hide them from user lists; claiming an invite adopts the row (flag -> false).
ALTER TABLE users ADD COLUMN provisional BOOLEAN NOT NULL DEFAULT FALSE;

-- Per-member NDS identity. One row per (team, person). Holds the Anwesenheitsliste data plus
-- the PERSONENNUMMER captured later. user_id links to the (provisional or real) account.
CREATE TABLE nds_members (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  team_id        UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  user_id        UUID NULL REFERENCES users(id) ON DELETE SET NULL,
  last_name      TEXT NOT NULL,
  first_name     TEXT NOT NULL,
  birth_date     DATE NULL,
  person_number  TEXT NULL,        -- J+S PERSONENNUMMER (9 digits); required for export
  funktion       TEXT NOT NULL,    -- 'Teilnehmer/in' | 'Leiter/in'
  source         TEXT NOT NULL DEFAULT 'nds_import',  -- 'nds_import' | 'manual'
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (team_id, last_name, first_name, birth_date)
);
CREATE INDEX idx_nds_members_team ON nds_members(team_id);
CREATE INDEX idx_nds_members_user ON nds_members(user_id);

-- Bind a per-member invite link to the roster row it claims.
ALTER TABLE invite_links ADD COLUMN nds_member_id UUID NULL REFERENCES nds_members(id) ON DELETE CASCADE;

-- Tag events that map to an NDS activity (raw symbol; informational + re-import idempotency).
ALTER TABLE events ADD COLUMN nds_symbol TEXT NULL;

-- Widen the V13 external_source CHECK so NDS-imported events can set external_source='nds'.
ALTER TABLE events DROP CONSTRAINT IF EXISTS events_external_source_check;
ALTER TABLE events ADD CONSTRAINT events_external_source_check
  CHECK (external_source IN ('swissvolley', 'nds'));
