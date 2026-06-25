# SwissVolley Game-Sync — Design

Status: **planned** · Author: design session 2026-06-25 · Scope: backend (Ktor) + admin web

Import a club's indoor game schedule from the Swiss Volley (VolleyManager) API into
TeamOrg `events`, keep it in sync, and let coaches enrich + reconcile changes.

---

## 1. Feature model

- **API key is per-club.** A club manager sets it; it is stored in the DB. At-rest
  protection is a deployment concern (see §9), not an app-level key.
- **Teams are created from the API.** Before creating teams, the manager sets the key,
  then picks some/all of the club's SwissVolley teams → TeamOrg teams are created and
  linked to their `sv_team_id`. Manually-created teams have no SV link and cannot track
  games.
- **Invite flow is unchanged.** Coaches/players are invited to those teams as today.
- **Coach opts in per team.** A coach toggles "Sync games with SwissVolley" on an
  SV-linked team. When on, that team's games appear as `events(type='match')`.
- **Coaches enrich games.** Meetup time, notes, min-attendees, etc. These are
  coach-owned and never overwritten by sync.
- **On SwissVolley reschedule:** match facts (date/time/venue/opponent) are updated
  **instantly**, the event is flagged `needs_review`, and the coach is notified. The
  coach reviews, adjusts the coach-owned extras, and **at save chooses whether to keep
  or reset already-collected player availability**.
- **Field ownership:** SwissVolley owns match facts (always synced). Coach owns the
  extras (always preserved).

### Why SwissVolley over a generic calendar link
Confirmed live against `https://api.volleyball.ch` with a club-scoped token:
- `/indoor/teams` returns the club's teams (31 for VBC Thun), each with a stable
  `teamId`, league, gender, club, and a per-team `iCalUrl`.
- `/indoor/games` returns the **full-season** schedule in one call (441 games,
  Oct 2025 → May 2026 for the test token), richly structured (geo-located halls,
  groups, referees, results).
- There is **no `lastModified`/`etag`/`version`** field — change detection is by
  content hash (see §6).

A generic Google-Calendar ICS path remains a possible later fallback for non-SwissVolley
calendars, but is out of scope here.

---

## 2. Data model (migration V13 — V12 is taken by `team_appearance`; see §13)

```sql
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
VALUES ('00000000-0000-0000-0000-0000000000sv'::uuid,  -- placeholder; pick a real fixed UUID
        'volleymanager@system.teamorg.ch', '!', 'VolleyManager', FALSE)
ON CONFLICT (email) DO NOTHING;
-- password_hash '!' is an unusable hash → this account can never log in.

-- Coach-facing SV game notifications (per user/team). Manager alerts (sv_key_invalid,
-- sv_team_available) are operational/club-level and stay always-on (no per-team toggle).
ALTER TABLE notification_settings
  ADD COLUMN sv_games BOOLEAN NOT NULL DEFAULT TRUE;  -- covers sv_game_new + sv_game_changed
```

Notes:
- `status` (`active`/`cancelled`) stays for the app's own manual cancels. SwissVolley
  removal sets `external_status='postponed'` (UI shows a "Verschoben" chip) and keeps
  the event visible — it does **not** flip `status`.
- A team is "SV-linked" iff it has ≥1 `team_sv_links` row.

---

## 3. Server components (Ktor)

- **`infra/SwissVolleyClient.kt`** — Ktor `HttpClient` (same shape as `PushService`).
  Base `https://api.volleyball.ch`, header `Authorization: <token>`.
  - `listTeams(key): List<SVTeam>` → `GET /indoor/teams`
  - `listGames(key): List<SVGame>` → `GET /indoor/games` (full current season)
  - DTOs in §10. On `401`/`{"errors":[{"message":"Valid API-Key required"}]}` throw
    `InvalidApiKeyException`.
- **`domain/repositories/IntegrationRepository`** — CRUD on `club_integrations`,
  `team_sv_links`, `sv_sync_state`.
- **`infra/SwissVolleySyncJob.kt`** — coroutine polling job (copy `ReminderSchedulerJob`
  pattern), started in `Application.kt`. See §6.

No new crypto util (key stored as TEXT — see §9).

---

## 4. API endpoints

All guarded; permission column = required role on the path's club/team (no-IDOR).

| Method   | Path                                              | Role               | Action |
|----------|---------------------------------------------------|--------------------|--------|
| `PUT`    | `/clubs/{id}/integrations/swissvolley`            | club_manager(id)   | Validate key via `listTeams`, store, set `key_valid` |
| `DELETE` | `/clubs/{id}/integrations/swissvolley`            | club_manager(id)   | Remove key + links + sync state |
| `GET`    | `/clubs/{id}/integrations/swissvolley/teams`      | club_manager(id)   | Live SV team list for the import picker |
| `POST`   | `/clubs/{id}/teams/import`                         | club_manager(id)   | Body `{svTeamIds:[...]}` → create `teams` + `team_sv_links` |
| `PATCH`  | `/teams/{id}/game-sync`                            | coach(id)          | Body `{enabled:bool}`; on enable → immediate sync of that team |
| `POST`   | `/events/{id}/reconcile`                          | coach (of linked team) | Body `{meetupAt?, notes?, minAttendees?, resetAvailability:bool}` → clear `needs_review`; if reset → attendance → `no-response` + notify players |
| `POST`   | `/clubs/{clubId}/teams/{teamId}/migrate-to`       | club_manager(clubId) | Body `{targetTeamId}` → merge deprecated team into target (§14) |

Normal coach edits to coach-owned fields go through the existing event `PATCH`.

---

## 5. UX touch points (admin web)

- **Club manager**
  - Integration settings: set/replace/remove key, validity badge, paused banner if key
    invalid.
  - "Import teams from SwissVolley": list SV teams (caption + league + gender), select
    some/all → creates TeamOrg teams (linked).
- **Coach**
  - Per-team toggle "Sync games with SwissVolley" (shown only if the team is SV-linked).
  - Event view: a **needs-review** banner on changed games → reconcile form with the
    keep/reset-availability choice; a **"Verschoben"** chip when `external_status='postponed'`.

---

## 6. Sync job logic

Single job loops clubs. Per club with a valid key and ≥1 `games_sync_enabled` team:

1. `games = client.listGames(key)`.
2. `syncedSvIds = team_sv_links.sv_team_id for teams where games_sync_enabled`.
3. Keep games where `home.teamId ∈ syncedSvIds || away.teamId ∈ syncedSvIds`.
4. Upsert by `external_game_id`:
   - **New** → create `event(type='match')`: `playDateUtc`→`start_at`, derived
     `end_at`, hall→`location`, `"<home.caption> vs <away.caption>"`→`title`,
     `external_status='synced'`, compute `external_hash`. Link via `event_teams` to the
     matching TeamOrg team(s). Notify coach (*new game*).
   - **Existing, hash changed**, event not finished/live → update facts **instantly**,
     recompute hash, set `needs_review=TRUE`, notify coach (*game changed — review*).
   - **Finished/live** (`status` codes from results present) → skip fact writes.
   - **In DB but absent from feed** → set `external_status='postponed'` (keep event,
     "Verschoben" chip). Do **not** cancel or delete.
5. Write `sv_sync_state`.

**Change hash:** `SHA-256({playDateUtc, end, hallId, home.teamId, away.teamId})`. This is
the change-detection mechanism because the API exposes no modified timestamp.

**Derby / two linked teams in one game:** unique `external_game_id` → one event, multiple
`event_teams` rows. No duplicates.

**Cadence (per club):** if the club has a synced game within ±14 days → poll every
**12 h**; otherwise every **48 h**.

**Invalid/revoked key:** on `InvalidApiKeyException`, set `key_valid=false`,
`sync_paused_reason`, `sv_sync_state.last_status='paused'`, notify the club manager, and
stop syncing that club until the key is replaced.

**Season rollover (automatic re-import):** periodically refresh `listTeams`:
- For existing `team_sv_links` (matched by stable `sv_team_id`) → update
  `sv_seasonal_team_id` + league/gender, then pull the new season's games for
  sync-enabled teams.
- For **new** SV teams not present in TeamOrg → **notify the manager** to import them
  (do not auto-create — manager stays in control).

---

## 7. Notifications (reuse `NotificationDispatcher`)

- `sv_game_new` → coach of the team.
- `sv_game_changed` → coach (game facts changed, review needed).
- `sv_team_available` → club manager (new SV team appeared at rollover).
- `sv_key_invalid` → club manager (sync paused).
- Player reschedule notice → existing `events_edit` setting, fired **only** when the
  coach chooses *reset* at reconcile.

---

## 8. Permissions (no-IDOR)

- Integration + team-import endpoints: caller must hold `club_manager` on that club
  (`club_roles`).
- Game-sync toggle: caller must hold `coach` on that team (`team_roles`).
- Reconcile: caller must hold `coach` on a team linked (`event_teams`) to the event.

---

## 9. Encryption at rest (deployment, not code)

Hosting: self-hosted Postgres in Docker via Coolify on Hetzner. There is no managed-PG
transparent-encryption toggle. The application stores the key as `TEXT`; protecting it on
disk is a deployment concern:

- **Recommended:** LUKS-encrypt the Hetzner volume that backs the Postgres Docker volume.
  One-time infra setup, no app key, covers the whole DB + backups. Protects against
  disk/backup theft only (not a live DB compromise).
- **Minimum:** keep the Postgres container off any public network (internal Docker
  network only), strong credentials, restricted backups.

Residual risk either way: anything that can issue live queries (leaked app creds, SQLi,
DBA) sees the token in cleartext. Acceptable for a 3rd-party **read** token whose worst
case is exposure of the club's public-ish schedule. Revisit if higher-value secrets land
in the same table.

---

## 10. SwissVolley API reference (confirmed live, 2026-06-25)

Base: `https://api.volleyball.ch` · Auth: `Authorization: <token>` (raw). Token is
club-scoped. No documented rate limits; cache/poll conservatively.

`GET /indoor/teams` → array of:
```
teamId, seasonalTeamId, caption, gender ('m'|'f'),
league { leagueId, caption, season, leagueCategoryId, translations },
club   { clubId, clubCaption, clublogo, website },
teamlogo, teamPicture, isTalentOrganisationTeam, isTalentDevelopmentTeam,
iCalUrl { translations: { d, f, i } }    // webcal:// per-team schedule feed
```

`GET /indoor/games` → array of (full current season; `season` query param is ignored):
```
gameId, playDate ('YYYY-MM-DD HH:mm:ss' local), playDateUtc (ISO Z),
gender, status (int; results present => played),
teams { home, away }:  { teamId, seasonalTeamId, caption, clubCaption, clubId, logo, rank }
league { leagueId, caption, season, ... }, phase { phaseId, caption, ... },
group  { groupId, caption ('4. Liga Männer Gruppe II'), ... },
hall   { hallId, caption, street, number, zip, city, latitude, longitude, plusCode } | null,
referees[], setResults[{home,away}], resultSummary { winner, wonSetsHomeTeam, wonSetsAwayTeam },
goldenSetResult[], isPartOfBestOfSeries, bestOfSeriesResult,
iCalUrl { translations: { d, f, i } }    // webcal:// per-game feed
```
- `GET /indoor/upcomingGames` exists but is a rolling window (empty off-season) — not used.
- No `lastModified` / `etag` / `version` anywhere → content-hash change detection.

Reference implementation to crib from: `scoring/src/lib/server/swiss-volley.ts`
(base URL, auth header, `SVGame`/`SVTeam` types, 5-min cache, skip-finished-on-sync).

---

## 11. Phasing

1. `club_integrations` + key set/validate/delete endpoint + manager settings UI.
2. `SwissVolleyClient` + `team_sv_links` + team-import endpoint + manager picker UI.
3. `events` external columns + `SwissVolleySyncJob` (poll, upsert, hash-diff, cadence,
   pause-on-invalid) + coach game-sync toggle + coach new/changed notifications.
4. Reconcile flow (needs-review UI + keep/reset availability) + player notifications +
   "Verschoben" chip.
5. Season rollover auto-refresh + `sv_team_available` manager notification + link
   `deprecated_at` flagging + deprecated-team **migrate-to** endpoint & manager UI (§14)
   + coach **schedule carry-over** (`importable-series` read + prefilled create reuse) (§15).

---

## 13. Implementation hardening (verified against `main` @ 402f5e3, 2026-06-25)

Ground-truth pass over the live codebase. Everything below is a correction or
pre-flight that the implementer MUST apply — the design is otherwise structurally sound.

### 13.1 Confirmed-correct assumptions (no change needed)
- PKs are `UUID DEFAULT gen_random_uuid()`; `clubs(id)`, `teams(id)`, `events(id)`,
  `users(id)` all match.
- Role strings exact: `club_roles.role IN ('club_manager')`, `team_roles.role IN ('coach','player')`.
- `events.type` is `enumerationByName<EventType>` with `IN ('training','match','other')` — `'match'` valid.
- `event_teams(event_id, team_id)` join table exists exactly as assumed (PK both cols).
- `attendance_responses.status` includes `'no-response'` (+ `confirmed/declined/unsure/declined-auto`).
  `events.min_attendees INT NULL` exists. Reset-to-`no-response` is supported.
- `notification_settings.events_edit BOOLEAN` exists → player-reschedule notice path is real.
- Stack: Ktor + **Exposed** ORM + **Flyway** migrations + **Koin** DI + `HttpClient(CIO)` +
  `kotlinx.serialization` (`ignoreUnknownKeys=true` — good for SV's huge payloads).

### 13.2 BLOCKERS (break the build/insert if not handled)

1. **Migration number is V13, not V12.** `V12__team_appearance.sql` already exists
   (it added `teams.appearance_shape/appearance_color`). All §2 SQL goes in
   `V13__swissvolley_integration.sql`. (`server/build/resources/...` copies are generated — ignore.)

2. **`events.end_at` is `TIMESTAMPTZ NOT NULL`.** The API provides no end time.
   Cannot insert NULL. **Decision required now (was §12 open item):** derive
   `end_at = start_at + 2h` (indoor match default). Deterministic, so it is NOT part of
   the change-hash (see 13.3). Resolve before Phase 3.

3. **`events.created_by` is `UUID NOT NULL REFERENCES users(id)`.** Synced games have no
   human author. **Decided:** a dedicated **`VolleyManager` system user** is seeded in V13
   (fixed UUID, unusable password hash, `is_super_admin=FALSE`) and used as `created_by` for
   all synced events. Front-ends should render this author as the "VolleyManager" / SwissVolley
   source tag rather than a person. Pick the fixed UUID before writing the migration.

4. **Exposed table objects must be authored, not just SQL.** Flyway creates the tables but
   Exposed needs matching `object`s: new `ClubIntegrationsTable`, `TeamSvLinksTable`,
   `SvSyncStateTable`, plus new columns added to existing `EventsTable`
   (`server/.../db/tables/EventsTable.kt`) and `TeamsTable`. Column Kotlin-types MUST match
   the SQL (e.g. `external_game_id` → `long("external_game_id").nullable()`;
   keep `external_source`/`external_status` as `text(...).nullable()`, not enums, to avoid a
   second enum class). Use Exposed `upsert()` (already used in `AbwesenheitBackfillJob`) for the
   game upsert keyed on the `uq_events_external` index.

### 13.3 Sync-job corrections (§6)

- **Reference impl is a *sibling repo*, not in this tree.** `scoring/src/lib/server/swiss-volley.ts`
  lives at `/Users/miggi/miggisrc/scoring` (separate project, not a submodule). It only
  implements `/indoor/teams` + `/indoor/upcomingGames` — it does **NOT** fetch the full-season
  `/indoor/games`, and has **no skip-finished logic**. Crib auth header / type shapes / 5-min
  cache only; implement `listGames()` (`GET /indoor/games`) and finished-skip ourselves.
- **Skip-finished detection:** mark finished when `setResults` non-empty OR `resultSummary`
  present (don't rely on the undocumented `status` int). Finished/live → skip fact writes.
- **Change-hash = SV source facts only:** `SHA-256({playDateUtc, hallId, home.teamId, away.teamId})`.
  Drop derived `end` from the hash (it's a deterministic function of start → adds nothing).
- **Notify only on actual hash change**, and fold the new hash into the notification
  idempotency key, so re-polling an already-changed-but-unreconciled game does not re-notify
  every tick. (`notifications.idempotency_key` is `NOT NULL`.)
- **Per-club cadence needs state-gating.** Existing jobs (`ReminderSchedulerJob` 1min,
  `EventMaterialisationJob` 24h, `AutoPresentJob` 15min) use a single global
  `while(isActive){ delay(t) }` loop. Implement the 12h/48h rule by ticking the loop often
  (e.g. hourly) and, per club, skipping unless
  `now - sv_sync_state.last_synced_at >= dueInterval(club)`. Compute `dueInterval` from the
  ±14d-window rule. Run an initial pass on startup like `EventMaterialisationJob`.
- **Single-instance assumption:** all jobs assume one JVM (no advisory locks). Fine now;
  note that horizontal scaling would double-sync. Add a Postgres advisory lock only if/when scaled.
- **Cache key must include club** (per-club API key): cache by `(clubId, path)`, not `path`.

### 13.4 Notification targeting gap (§7)

`NotificationDispatcher.notifyTeamMembers(teamId, excludeUserId, type, …)` is **team-scoped
and notifies ALL eligible team members** (`NotificationRepository.isUserEligible` switch).
The four SV kinds don't fit it:
- `sv_game_new`, `sv_game_changed` → **coach(es) of the team only**, not all players.
- `sv_team_available`, `sv_key_invalid` → **club manager(s)**, not a team at all.

Required work (was implied "reuse", actually a gap):
- Add a user-targeted notify path (insert `notifications` rows for a given set of user ids +
  `PushService.sendToUsers`) for the manager-targeted kinds — `NotificationDispatcher` has no
  club/user-scoped method today.
- For coach-only kinds, either add a coach-filtered variant or resolve team coaches and use the
  user-targeted path.
- **Decided:** add one `notification_settings.sv_games BOOLEAN DEFAULT TRUE` column (covers
  `sv_game_new` + `sv_game_changed`) and add a `"sv_game_new","sv_game_changed" -> settings.svGames`
  branch to `isUserEligible`. Manager alerts (`sv_key_invalid`, `sv_team_available`) are
  operational/club-level → **always-on**, sent via the user-targeted path, no settings column.
- Player-reschedule notice correctly reuses the existing `event_edit` path (no new kind).

### 13.5 Endpoint / auth specifics (§4, §8)

- Helpers exist exactly: `call.requireClubRole(clubId, "club_manager", clubRepository)` (single-role),
  `call.requireTeamRole(teamId, "coach", teamRepository = …)` (varargs; **note it escalates —
  club_manager passes team checks too**, which is the desired behaviour),
  `call.requireEventAccess(eventId, "coach", "club_manager", …)`.
- `PATCH /events/{id}` (coach edit) **already exists** — reconcile is a *separate* endpoint, good.
- **Guard preconditions** (return 400/409, not 500):
  - `PATCH /teams/{id}/game-sync` → reject if team has **no** `team_sv_links` row (not SV-linked).
  - `POST /events/{id}/reconcile` → reject if event isn't `external_source='swissvolley'` or
    `needs_review=false`.
  - `POST /clubs/{id}/teams/import` → skip `svTeamIds` already linked in that club (dedupe on
    `sv_team_id` across the club) to avoid duplicate TeamOrg teams.
- `PUT /clubs/{id}/integrations/swissvolley` → **validate first, store only if valid**: call
  `listTeams`; on `InvalidApiKeyException` respond 422 and do NOT persist a known-bad key.

### 13.6 Lifecycle / cleanup semantics (make explicit)

- `DELETE` integration removes the key + sync state but **keeps** teams, links, and events.
  Instead of hard-deleting links it sets `team_sv_links.deprecated_at = NOW()` so the teams show
  as **deprecated** (badge) and become migration targets (§14). Events stay visible and frozen.
  Do not cascade-delete events or teams.
- Disabling `games_sync_enabled` or a deprecated link leaves existing synced events visible
  and frozen (stop touching, don't delete).
- Reset-availability on reconcile: reset only `confirmed/declined/unsure` → `no-response`; leave
  `declined-auto` (abwesenheit-rule) rows so auto-decline isn't clobbered.

### 13.7 Web slot-in map (§5 — SvelteKit 2 + Svelte 5 runes, Tailwind 4, no new routes)

API client: `admin/src/lib/server/api.ts` (GET/POST/PATCH/PUT/DELETE). Role guards `isClubManager`/`isCoach`/`canManageTeam` exist. German strings ("Verschoben") via `lib/i18n`.
- Manager integration card → `/(manager)/manage/[clubId]` (new "Integrations" section).
- Import picker (modal) → `/manage/[clubId]/teams`.
- Coach sync toggle → `/app/teams/[teamId]` (render only if SV-linked).
- needs-review banner + reconcile form → `/app/events/[id]`.
- "Verschoben" / "SwissVolley" chips → `/app/events` cards + `/app/events/[id]` header.
All five are new components plugged into existing pages — no new top-level routes.
Plus (§14): a **deprecated** badge on manager team cards and a "Migrate to new team" action.

---

## 14. Season team migration (deprecated-team merge)

Across seasons SV usually keeps the **stable `sv_team_id`** → a team just gets its
`sv_seasonal_team_id`/league refreshed at rollover (§6) and continues. But when SV restructures
(promotion/relegation, league merge, club reorg) the old `sv_team_id` can **vanish** and a
**new** SV team appears. The old TeamOrg team must not be lost — the club manager migrates it
into the team it became.

**Deprecation (automatic):** when sync sees a linked `sv_team_id` no longer in `/indoor/teams`
at rollover (or the integration is deleted, §13.6), set `team_sv_links.deprecated_at = NOW()`.
A team is **deprecated** iff it has ≥1 link and none are active (all `deprecated_at` set). Sync
skips deprecated links (no fact writes; existing events frozen). Manager gets `sv_team_available`
for the new team → imports it (creates the target team), then migrates.

**Migration (manual, manager-driven):**

| Method | Path | Role | Action |
|--------|------|------|--------|
| `POST` | `/clubs/{clubId}/teams/{teamId}/migrate-to` | club_manager(clubId) | Body `{targetTeamId}` → merge deprecated `teamId` into `targetTeamId` |

Merge steps (single transaction):
1. Validate: both teams in `clubId`; source is deprecated; target is a live SV-linked team; not the same team.
2. Move **members**: copy `team_roles` rows source→target, `INSERT … ON CONFLICT (user_id, team_id, role) DO NOTHING` (dedupe coaches/players already on target). Preserve `jersey_number`/`position` only when the target row is new.
3. Carry over **settings**: `target.games_sync_enabled = source.games_sync_enabled` if target still default; copy each member's `notification_settings` (source team→target team) where no target row exists yet.
4. **Archive source + record lineage**: set `teams.archived_at = NOW()` on the source and
   `target.predecessor_team_id = source.id`. Past events stay attached to the source team as
   historical record (they belong to last season) — they are **not** moved. Future games sync
   to the target team via its live link. The lineage link powers schedule carry-over (§15).
5. Notify migrated members (reuse `events`/team-membership notification or a lightweight in-app note — decide in Phase 5).

UI: manager team list shows a **"Deprecated"** badge on archived/deprecated SV teams with a
"Migrate to …" action → pick a live target team → confirm summary (N members moved). Lives in
`/manage/[clubId]/teams`.

Members-only migration (events stay as history) is the chosen semantics. The coach then
re-establishes the training schedule via carry-over (§15) — manual events are not auto-moved.

---

## 15. Schedule carry-over (coach re-creates last season's recurring trainings)

The manager migration (§14) moves **members**; it deliberately does not move events. The coach
of the new team re-establishes the recurring schedule by **cloning last season's series
templates** into new, editable recurring events. Example: 2024 team trained Mon+Wed weekly → on
the new team the coach picks those two series, adjusts the new season's start/end dates (and any
field), and creates them — far less re-entry than rebuilding by hand.

**Schema reality (verified):** `event_series` has **no `team_id`**. A series ties to a team only
through its materialised events' `event_teams`. So a team's recurring series =
`SELECT DISTINCT e.series_id FROM events e JOIN event_teams et ON et.event_id = e.id
WHERE et.team_id = :predecessorId AND e.series_id IS NOT NULL`, then load each `event_series`
template (`pattern_type`, `weekdays`, `interval_days`, `template_*` times/title/type/location,
…). Recurring-event creation + `EventMaterialisationJob` already exist — carry-over only *reads*
old templates and *reuses the existing create path*; no new materialisation logic.

**When offered:** on the target team's event setup, show "Take over previous schedule" only when
(a) `predecessor_team_id IS NOT NULL` and (b) the target has **no series yet**
(no event linked to it has a non-null `series_id`). Anchored on `predecessor_team_id` from §14.

| Method | Path | Role | Action |
|--------|------|------|--------|
| `GET` | `/teams/{teamId}/importable-series` | coach \| club_manager (teamId) | List predecessor team's series templates (id + pattern + template fields + human label e.g. "Training · Mon, Wed · weekly") |

**Create path (no new write endpoint):** the UI pre-fills the **existing** recurring-event
create form from each selected template; the coach edits `series_start_date`/`series_end_date`
and anything else, then submits via the existing `POST /events` (recurrence) flow. New series get
`created_by = the coach` (a real user — no system-user concern here). Old series/events on the
archived predecessor are untouched.

**UI:** target team's schedule view, empty state → "Take over previous schedule" → checklist of
predecessor series (labelled by type + weekdays + cadence) → per-selection opens the prefilled,
editable create form → confirm. Lives alongside the coach event setup on `/app/teams/[teamId]`
(or the team's events view).

**Open:** multi-select "create all" with one shared new start/end vs. edit-each-individually.
Default to edit-each (reuses the existing single create form, KISS); batch is a later nicety.

---

## 12. Open items / risks

- LUKS setup on the existing Hetzner host is one-time infra work (may need a maintenance
  window); confirm before relying on at-rest encryption.
- ~~`status` int mapping~~ → **resolved (§13.3):** finished = `setResults` non-empty OR
  `resultSummary` present; ignore the undocumented `status` int.
- ~~`end_at` derivation~~ → **resolved (§13.2):** `end_at = start_at + 2h`; `end_at` is
  `NOT NULL` so this is mandatory, not optional.
- **`created_by` source for synced events** → resolved (§13.2): dedicated `VolleyManager`
  system user (seeded in V13).
- Rollover detection trigger (date-based vs "new season seen in `/indoor/teams`") to be
  finalized in Phase 5.
