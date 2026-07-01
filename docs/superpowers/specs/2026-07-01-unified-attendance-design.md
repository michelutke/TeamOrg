# Unified Attendance — Design Spec

**Date:** 2026-07-01
**Supersedes:** the two-model attendance system (RSVP `attendance_responses` + separate check-in `attendance_records`) and the dedicated check-in screens.
**Scope:** app-wide (all teams/events), across server (Ktor), web admin (SvelteKit), and mobile (Compose/KMP).

## Problem

Attendance today is split across two data models and two UIs:
- `attendance_responses` (RSVP): player intent — `no-response` / `confirmed` / `unsure` / `declined` / `declined-auto`, with a `responseDeadline` cutoff and Abwesenheit auto-decline rules.
- `attendance_records` (check-in): coach-recorded actual presence — `present` / `absent` / `excused`, entered on a **separate** check-in screen. NDS export reads this.

This is overcomplicated: two screens, two truths, and a real bug (a club manager without a per-team role gets 403 on the check-in page because the web guard can't resolve the team's club). Users want **one attendance model** on the existing RSVP view, with a coach "finalize" step.

## Solution overview

`attendance_responses` becomes the single source of truth. The `attendance_records` table and all check-in screens are removed. Attendance has a lifecycle; a coach finalizes an event's attendance ("CheckIn abschliessen"). NDS export/import read/write responses.

## Decisions (locked)

1. **App-wide** — one model for every team/event.
2. **Cutoff** for player self-editing = `responseDeadline` if set, else the event **start** time.
3. **Existing data** — drop `attendance_records` (including the already-run NDS import); the NDS import is re-run afterward and writes responses directly.
4. **Reminders** — push only; **all** non-cancelled team events await check-in until finalized.
5. **No-response at finalize** — resolved by a per-event `defaultResponse` setting: `accepted` → confirmed, `declined` → declined (excused), `none` → coach must resolve. `unsure` **always** must be resolved by the coach. System default for new events = **`none`**.
6. **Finalize** — available after event **end**; a finalized event is **reopenable by any coach/club-manager of that team** (clears the finalized state → back to awaiting).
7. **Inline RSVP buttons on the event list** stay for players (pre-cutoff), as today.

## Data model changes

### Drop
- Table `attendance_records` (migration drops it).

### `attendance_responses` (keep, extend)
- Statuses unchanged: `no-response` (default) / `confirmed` / `unsure` / `declined` / `declined-auto`.
- **Add** `unexcused: bool default false` — coach-only flag, set when a coach marks a declined member as "Nicht entschuldigt". Internal analysis only: never shown to players, never affects NDS export. Self-declines, `declined-auto` (Abwesenheit rules), and default-applied declines are all excused (`unexcused = false`).

### `events` (extend)
- **Add** `check_in_completed_at: timestamptz null` — null until finalized; set on "CheckIn abschliessen"; cleared on reopen.
- **Add** `default_response: text` (enum-like: `none` | `accepted` | `declined`), default `none`. Chosen at event creation. Also on `event_series` template (`template_default_response`) so recurring events inherit it.
- `response_deadline` stays (the cutoff). `check_in_enabled` is retired (folded into the lifecycle; drop or ignore).

## Lifecycle (derived, one stored field)

Given `now`, `cutoff = response_deadline ?? start_at`, `end_at`, `check_in_completed_at`:

| State | Condition | Who can edit |
|---|---|---|
| **Open** | `now < cutoff` | players + team coaches/CM |
| **Locked** | `cutoff ≤ now < end_at`, not finalized | team coaches/CM only (players see "Zeit zum An-/Abmelden abgelaufen") |
| **Awaiting check-in** | `now ≥ end_at`, not finalized | team coaches/CM only; appears in coach filter + reminders |
| **Done** | `check_in_completed_at != null` | frozen; a team coach/CM may reopen |

Only `check_in_completed_at` is stored; the rest is derived server-side and exposed as a status string on the event payload for the UIs.

## Coach editing & finalize

- A team coach or club-manager (of a team the event belongs to) edits any member's status via a popup.
- Setting a member to **declined** shows a **"Nicht entschuldigt"** checkbox → `unexcused = true`. Otherwise excused.
- **"CheckIn abschliessen"** (coach, only when `now ≥ end_at` and not finalized):
  - If any roster member is `unsure` → block with dialog: *"Unsichere Spieler müssen zuerst als anwesend oder abwesend markiert werden."*
  - `no-response` members resolved by `default_response`: `accepted`→`confirmed`, `declined`→`declined` (excused); if `none` → also block until the coach resolves them.
  - On success: set `check_in_completed_at = now`, freeze editing.
- **Reopen** (any team coach/CM): clears `check_in_completed_at` → event returns to awaiting; editing re-enabled.

Roster = union of `team_roles.userId` across the event's teams (same source the old check-in used). Abwesenheit `declined-auto` members are already declined/excused at finalize.

## Reminders & events page

- **Events page coach filter/tab** (web + mobile): "Awaiting check-in" = events with a team, `end_at < now`, `check_in_completed_at is null`, not cancelled, on a team the coach/CM manages.
- **Push-only reminder every 2 days**: while a coach has ≥1 awaiting-check-in event, send a push every 2 days; stop when the list is empty. Extend the existing `ReminderSchedulerJob` (which already fires coach summaries). No email.

## NDS export / import (re-pointed at responses)

- **Export** (`NdsExportService` / `NdsRepository.listExportAttendances`): source becomes `attendance_responses` where `status = confirmed`, joined to `nds_members` by `user_id`; emit one AWK present row each. Declined/unsure/no-response are not exported. (Was: `attendance_records.present`.)
- **Import** (`NdsEventImporter`): for the `attendanceMode = keep` path, write `attendance_responses` for the provisional users — attended date → `confirmed`, otherwise `declined` — instead of `attendance_records`. `attendanceImported` counts written `confirmed` responses.
  - **Past** imported events (date < today at import time): set `check_in_completed_at` (auto-finalized). **Future** imported events: leave open.
- The NDS member claim/attendance-migration logic (`claimMember` moving rows off the provisional user) now moves `attendance_responses` rows (it already moves both responses and records; drop the records half).

## The 403 bug

Subsumed by this redesign: the separate check-in page is removed, imported attendance shows on the event detail as accepted players, and coach editing uses a club-manager-aware manage check (resolve the team's club, matching the pattern the event-detail `canManage` already uses via `loadUserTeams`). No standalone fix needed. (An interim patch to the current check-in page guard is possible if the user wants access before this ships — otherwise skip.)

## Surfaces touched

- **Server**: Flyway migration (drop `attendance_records`, add `events.check_in_completed_at`, `events.default_response`, `event_series.template_default_response`, `attendance_responses.unexcused`); `AttendanceRepository`/Impl (single-model reads, lock enforcement, finalize/reopen, unexcused, derived status); event DTO (`checkInStatus`, `checkInCompletedAt`, `defaultResponse`); `EventRepository`; `NdsExportService`/`NdsRepository`/`NdsEventImporter`; `ReminderSchedulerJob`; remove `CheckInRoutes` + check-in repo methods; event routes for finalize/reopen + coach edit.
- **Web** (`admin/`): event detail (player buttons + locked banner + coach edit popup w/ unexcused + finalize/reopen); event list (attendance summary + "Awaiting check-in" filter tab); event create/edit (`defaultResponse` select); remove check-in page + link.
- **Mobile** (`composeApp` + `shared`): same — event detail, list filter, create/edit `defaultResponse`, remove check-in screen; shared Event model (`checkInStatus`, `checkInCompletedAt`, `defaultResponse`) + attendance repo (finalize/reopen, coach edit, unexcused).

## Sequencing

Three plans on one branch, in order:
1. **Server** — schema + single-model attendance + finalize/reopen + export/import + reminders + remove check-in routes. Ships with tests; other layers build on its API.
2. **Web** — detail/list/create-edit changes + remove check-in page.
3. **Mobile** — shared model/repo + screens + remove check-in screen.

## Out of scope

- Abwesenheit-rule engine changes (auto-decline keeps working as-is, feeding `declined-auto`).
- Notification infrastructure beyond adding the awaiting-check-in push to the existing scheduler.
- Analytics UI for the `unexcused` flag (the flag is stored now; reporting is a later feature).
