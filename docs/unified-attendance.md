# Unified Attendance — as built

**Status:** shipped to `main` via PR [#41](https://github.com/michelutke/TeamOrg/pull/41) (feature) and PR [#42](https://github.com/michelutke/TeamOrg/pull/42) (wire-format fix + E2E suites).
**Design spec:** [superpowers/specs/2026-07-01-unified-attendance-design.md](superpowers/specs/2026-07-01-unified-attendance-design.md) — the locked decisions. This document is the as-built reference, including deviations discovered during implementation.

## What changed

`attendance_responses` is the single attendance model. The separate check-in model
(`attendance_records` table, `/events/{id}/check-in*` API, the web check-in page, the
mobile check-in plumbing, and the `AutoPresentJob`) was removed. Coaches finalize an
event's attendance ("CheckIn abschliessen"); NDS export/import read/write responses.

### Lifecycle (derived, one stored field)

Given `now`, `cutoff = response_deadline ?? start_at`, `end_at`, `check_in_completed_at`:

| `checkInStatus` | Condition | Who can edit |
|---|---|---|
| `open` | `now < cutoff` | players + team coaches/club-managers |
| `locked` | `cutoff ≤ now < end_at`, not finalized | coaches/CM only (players see "Zeit zum An-/Abmelden abgelaufen") |
| `awaiting_checkin` | `now ≥ end_at`, not finalized | coaches/CM; appears in the coach filter + push reminders |
| `done` | `check_in_completed_at != null` | frozen; any team coach/CM may reopen |

Only `check_in_completed_at` is stored; `checkInStatus` is derived server-side
(`AttendanceLifecycle.kt`) and carried on every event payload.

### Data model (migration `V15__unified_attendance.sql`)

- **Dropped** `attendance_records` (data intentionally discarded; NDS import re-run writes responses).
- `attendance_responses.unexcused BOOLEAN NOT NULL DEFAULT false` — coach-only flag on
  declined members ("Nicht entschuldigt"). Never shown to players, never affects NDS export.
- `events.check_in_completed_at TIMESTAMPTZ NULL`.
- `events.default_response TEXT NOT NULL DEFAULT 'none'` (`none | accepted | declined`) and
  `event_series.template_default_response` (series events inherit it). Settable on
  create/edit (web select, mobile picker).
- `events.check_in_enabled` retained but no longer read (follow-up: drop).

### API surface

| Endpoint | Purpose |
|---|---|
| `PUT /events/{id}/attendance/me` | player self-RSVP; **403** after cutoff or when `done` |
| `PUT /events/{id}/attendance/{userId}` | coach edit `{status, unexcused}`; guarded coach/club-manager; rejected when `done`; `declined-auto` not client-writable |
| `POST /events/{id}/attendance/finalize` | only when `now ≥ end_at` and not done. 409 body `{reason: "unsure"\|"no-response", userIds: []}` when blocked |
| `POST /events/{id}/attendance/reopen` | clears `check_in_completed_at` |
| `GET /users/me/events/awaiting-checkin` | events the caller coaches/manages with `end_at < now`, unfinalized, not cancelled |

Finalize semantics: any `unsure` roster member blocks; `no-response` members resolve per
`default_response` (`accepted`→confirmed, `declined`→declined/excused, `none`→blocks);
all writes atomic — a blocked finalize writes nothing.

Presence counts (`presentCount` on event payloads) now count **confirmed responses**.
NDS export emits confirmed responses; NDS import writes confirmed (attended) / declined
(not attended) and auto-finalizes past imported events. Coaches with ≥1 awaiting event
get a push reminder at most every 2 days (`ReminderSchedulerJob`, idempotency-key bucketed).

### Clients

- **Web admin:** event detail (RSVP lock + banner, coach edit popup with unexcused,
  finalize/reopen with blocked-members dialog), events list ("Check-in offen" coach filter
  + badge), create/edit `defaultResponse` select. Check-in page deleted. Manage checks are
  club-manager-aware via `loadUserTeams` (fixes the old check-in 403).
- **Mobile (KMP):** same surfaces — detail (lock, coach bottom sheet, finalize/reopen with
  member names resolved from team rosters), list filter + badge, `defaultResponse` picker.
  `FinalizeResult` sealed type distinguishes 200/409. Check-in repo methods + types removed.

## Notable deviations & fixes beyond the spec

| Change | Why |
|---|---|
| `AutoPresentJob` deleted | it only copied confirmed responses into `attendance_records`; obsolete once confirmed responses are the truth |
| `recordStatus` removed from attendance DTOs | always-null leftover of the records model |
| `EventDetailViewModel.finalize()` → `finalizeEvent()` | `finalize` shadowed `Object.finalize()`; the JVM GC invoked it during Android unit tests, leaking coroutines past `Dispatchers.resetMain()` |
| `UserPreferences(settings: Settings)` constructor injection | the Android `actual` required a `Context`, so common test code could not construct it; platform `Settings` now built in each Koin module, tests use `MapSettings` |
| **Server `encodeDefaults = true`** (`Serialization.kt`) | kotlinx omitted defaulted fields from JSON (`checkInStatus:"open"`, `defaultResponse:"none"`); Kotlin clients re-applied defaults on decode but the web reads raw JSON → every open event rendered locked, players could not RSVP. Guarded by a wire-format regression test (`CheckInStatusTest`, raw-body assertion) |
| a11y label on the attendance edit button | icon-only button had no accessible name |
| Attendance status write validation | player/coach writes reject statuses outside `no-response/confirmed/unsure/declined` (400); `declined-auto` is system-only |

## Testing

- **Server:** integration tests via testcontainers (`AttendanceRoutesTest`,
  `FinalizeAttendanceTest`, `CheckInStatusTest` incl. wire-format assertion, `NdsRoutesTest`,
  `AwaitingCheckInTest`). `./gradlew :server:test` (Docker required).
- **Mobile:** VM tests on `iosSimulatorArm64Test` / `testDebugUnitTest` (detail gating,
  finalize blocked/success, filters, defaultResponse round-trip).
- **Web E2E (Playwright, `admin/e2e/`):** read-only smoke suite + mutating persona
  scenarios (manager invites → player registers → RSVP → lock → coach edit → finalize →
  reopen). See [admin/e2e/README.md](../admin/e2e/README.md). The persona suite is what
  caught the `encodeDefaults` bug in production.

## Known follow-ups (non-blocking)

- Drop the dead `events.check_in_enabled` column.
- Consolidate the duplicated awaiting-event filter (`EventRepositoryImpl` vs
  `NotificationRepository`).
- Web: route finalize errors through `manageErr` (raw-fetch path shows a generic message);
  hardcoded German strings vs i18n on the new attendance surfaces; awaiting filter is
  client-scoped (mixed-role users see the badge on player-only teams — display noise only).
- `respondedAt` stays null for default-resolved/imported responses — UIs keying "has
  responded" off it treat those as not-responded.
