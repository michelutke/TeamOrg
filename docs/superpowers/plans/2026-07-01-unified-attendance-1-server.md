# Unified Attendance — Plan 1: Server

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Make `attendance_responses` the single attendance model on the server: add a finalize/reopen lifecycle, coach editing with an `unexcused` flag, a per-event `default_response`, drop `attendance_records` + check-in routes, re-point NDS export/import at responses, and add an awaiting-check-in reminder.

**Architecture:** One table (`attendance_responses`) holds attendance. Event lifecycle is derived from `now`, `response_deadline ?? start_at`, `end_at`, and a new `events.check_in_completed_at`. Players edit only before the cutoff; team coaches/club-managers edit any time until finalized; finalize freezes and requires no `unsure` (and resolves `no-response` via `default_response`).

**Tech Stack:** Ktor + Exposed + Postgres (Flyway migrations), kotlinx.serialization, testcontainers integration tests.

**Design spec:** `docs/superpowers/specs/2026-07-01-unified-attendance-design.md` (read it — it holds the locked decisions and the exact lifecycle table).

## Global Constraints

- Branch: create `feat/unified-attendance` off `main` (after PR #40 merges). Git: merge only, never rebase; no AI authorship trailers; commit per logical unit.
- Gate before push: `./gradlew :server:test` green (Docker/testcontainers required).
- **Security (no-IDOR — memory `security-no-idor`):** finalize/reopen/coach-edit require the caller to be `coach` or `club_manager` of one of the event's teams (`requireEventAccess(eventId, "coach", "club_manager", ...)`, whose `hasRole` already resolves club_manager via the team's club). Player self-edit requires membership of an event team.
- Attendance statuses (plain text) stay: `no-response` (default) | `confirmed` | `unsure` | `declined` | `declined-auto`. `unexcused` is a separate bool, never a status.
- `default_response` enum-like text: `none` | `accepted` | `declined`; new-event default `none`.
- Lifecycle status string exposed on the event DTO: `open` | `locked` | `awaiting_checkin` | `done`.
- DB time convention: events store start/end as UTC (see `EventRepositoryImpl`); do not change it.
- Do NOT preserve `attendance_records` data — the migration drops the table (NDS import will be re-run).

## File Structure

- Migration: `server/src/main/resources/db/migrations/V15__unified_attendance.sql` (verify next version number; V14 is the NDS migration).
- Modify: `server/.../db/tables/AttendanceTables.kt` (drop `AttendanceRecordsTable`, add `unexcused` to `AttendanceResponsesTable`), `EventsTable.kt` (add `checkInCompletedAt`, `defaultResponse`; `EventSeriesTable` add `templateDefaultResponse`).
- Modify: `server/.../domain/models/Event.kt` (DTO: `checkInStatus`, `checkInCompletedAt`, `defaultResponse`).
- Modify: `server/.../domain/repositories/AttendanceRepository.kt` (+Impl) — remove check-in methods, add `setResponseByCoach`, `finalize`, `reopen`, `listAwaitingCheckInForUser`; add `unexcused` to response read/write; enforce lock in `upsertResponse`.
- Modify: `server/.../domain/repositories/EventRepositoryImpl.kt` — attach derived `checkInStatus` + new fields to the DTO.
- Modify: `server/.../routes/EventRoutes.kt` (or AttendanceRoutes) — coach-edit, finalize, reopen, awaiting-list endpoints; **delete** `CheckInRoutes.kt` + its `Routing.kt` registration.
- Modify: `server/.../infra/nds/NdsExportService.kt`, `NdsRepository.kt` (`listExportAttendances`, `claimMember`), `NdsEventImporter.kt`.
- Modify: `server/.../infra/ReminderSchedulerJob.kt` — awaiting-check-in push.
- Tests: `AttendanceRoutesTest`, `EventRoutesTest`, `NdsRoutesTest`, a new `AttendanceFinalizeTest`, `ReminderSchedulerJob` test if one exists.

---

### Task 1: Migration + table/DTO schema

**Files:** `V15__unified_attendance.sql`; `AttendanceTables.kt`; `EventsTable.kt`; `domain/models/Event.kt`.

**Interfaces produced:** `attendance_records` gone; `attendance_responses.unexcused BOOLEAN NOT NULL DEFAULT false`; `events.check_in_completed_at TIMESTAMPTZ NULL`; `events.default_response TEXT NOT NULL DEFAULT 'none'`; `event_series.template_default_response TEXT NOT NULL DEFAULT 'none'`. Server `Event` model gains `checkInCompletedAt: Instant?`, `defaultResponse: String = "none"`, `checkInStatus: String = "open"`.

- [ ] **Step 1: Write the migration**

`V15__unified_attendance.sql`:
```sql
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
```
(Grep `check_in_enabled` / `checkInEnabled` usages first; drop the column only if nothing reads it.)

- [ ] **Step 2: Update Exposed tables** — delete `AttendanceRecordsTable` and `RecordStatus` from `AttendanceTables.kt`; add `val unexcused = bool("unexcused").default(false)` to `AttendanceResponsesTable`. In `EventsTable.kt` add `val checkInCompletedAt = timestamp("check_in_completed_at").nullable()` and `val defaultResponse = text("default_response").default("none")`; in `EventSeriesTable` add `templateDefaultResponse`.

- [ ] **Step 3: Update the server `Event` DTO** — add `checkInCompletedAt: Instant? = null`, `defaultResponse: String = "none"`, `checkInStatus: String = "open"` (all defaulted for backward-compat serialization).

- [ ] **Step 4: Compile** — `./gradlew :server:compileKotlin :server:compileTestKotlin`. Expect failures in code referencing `AttendanceRecordsTable`/`RecordStatus` (check-in repo methods, NDS export/import, presentCounts) — those are fixed in later tasks; this step just confirms the schema/DTO compile in isolation is not the goal yet. Instead: expect the build to break ONLY at the known record-referencing call sites; note them.

- [ ] **Step 5: Commit** — `feat(server): unified-attendance schema (drop records, add lifecycle columns)`. (Build may be red until Task 6; if your workflow requires green commits, fold Tasks 1–8 into one branch and commit once compiling — but keep them as separate reviewable steps.)

> NOTE for the executor: because dropping `attendance_records` breaks several call sites at once, implement Tasks 1–8 as a connected unit and only run the full `:server:test` after Task 8. Commit per task, but expect intermediate red compiles until the record references are all removed (Tasks 3,6,7,8). Each task below says what it must leave compiling.

---

### Task 2: Derived lifecycle status on the event DTO

**Files:** `EventRepositoryImpl.kt`; `domain/models/Event.kt` (helper).

**Interfaces produced:** every event DTO from `GET /events/{id}` and `GET /users/me/events` carries `checkInStatus` computed as: `done` if `check_in_completed_at != null`; else `open` if `now < (response_deadline ?? start_at)`; else `awaiting_checkin` if `now >= end_at`; else `locked`. Also carries `checkInCompletedAt` + `defaultResponse`.

- [ ] **Step 1: Add a pure function** `deriveCheckInStatus(now, cutoff, endAt, completedAt): String` in `Event.kt` (or a small `AttendanceLifecycle.kt`), with a unit test covering all four branches + the deadline-null (cutoff=startAt) case.
- [ ] **Step 2: Run the unit test** — RED (function absent) → implement → GREEN. `./gradlew :server:test --tests '*Lifecycle*'`.
- [ ] **Step 3: Wire into `rowToEvent`/attach path** — set `checkInCompletedAt`, `defaultResponse` from the row, and compute `checkInStatus` using the server clock (mirror how `presentCount` was attached earlier).
- [ ] **Step 4: Integration test** — seed an event ended in the past with no `check_in_completed_at`; assert `GET /events/{id}` returns `checkInStatus == "awaiting_checkin"`. Seed a future event → `open`.
- [ ] **Step 5: Commit** — `feat(server): expose derived checkInStatus on event payloads`.

---

### Task 3: Lock enforcement + coach edit + unexcused in the response API

**Files:** `AttendanceRepository.kt` (+Impl); `AttendanceRoutes.kt`.

**Interfaces produced:**
- `upsertResponse(eventId, userId, status, reason)` — for PLAYER self-edit — now rejects when the event is past its cutoff (throws/returns a sentinel the route maps to 403 "Zeit abgelaufen"). (The route already checks `isDeadlinePassed`; extend to cutoff = deadline ?? start.)
- New `setResponseByCoach(eventId, targetUserId, status, unexcused, setBy): AttendanceResponseRow` — coach edit; allowed while not finalized; sets `unexcused` (only meaningful when `status = declined`; force false otherwise).
- Response read rows (`getEventAttendance`, `getMyResponse`) include `unexcused`.
- New route `PUT /events/{id}/attendance/{userId}` (coach) → `setResponseByCoach`, guarded `requireEventAccess(id, "coach", "club_manager", ...)`, rejected if event `done`.

- [ ] **Step 1: Extend `isDeadlinePassed`** to `isPastCutoff(eventId)` = `now >= (response_deadline ?? start_at)`. Update the player `PUT /events/{id}/attendance/me` to use it and to also reject if the event is `done`.
- [ ] **Step 2: Add `unexcused` to `AttendanceResponseRow`** and its DTO; populate in reads.
- [ ] **Step 3: Add `setResponseByCoach`** (upsert into `attendance_responses`, set `manual_override = true`, `unexcused = (status == "declined") && unexcusedArg`). Write the coach-edit route.
- [ ] **Step 4: Tests** — player edit after cutoff → 403; coach edit after cutoff → 200 and persists; coach sets declined+unexcused → row has `unexcused=true`; coach edit on a `done` event → 409/403. `./gradlew :server:test --tests 'ch.teamorg.routes.AttendanceRoutesTest'`.
- [ ] **Step 5: Commit** — `feat(server): coach attendance edit + cutoff lock + unexcused flag`.

---

### Task 4: Finalize + reopen

**Files:** `AttendanceRepository.kt` (+Impl); `AttendanceRoutes.kt`.

**Interfaces produced:**
- `finalize(eventId, byUser): FinalizeResult` where `FinalizeResult = Ok | BlockedUnsure(userIds) | BlockedNoResponse(userIds)`. Logic: gather roster (team_roles across event teams). If any roster member is `unsure` → `BlockedUnsure`. Else resolve `no-response` per event `default_response`: `accepted`→write `confirmed`, `declined`→write `declined` (excused), `none`→ if any `no-response` remain, `BlockedNoResponse`. On success set `events.check_in_completed_at = now`.
- `reopen(eventId)` — set `check_in_completed_at = null`.
- Routes: `POST /events/{id}/attendance/finalize` (coach; only when `now >= end_at` and not already done) → 200 or 409 with the blocking userIds; `POST /events/{id}/attendance/reopen` (coach) → 200.

- [ ] **Step 1: Failing tests** — (a) finalize with an `unsure` member → 409 + that userId listed, `check_in_completed_at` still null; (b) `default_response=accepted`, a `no-response` member → finalize 200, that member becomes `confirmed`, event done; (c) `default_response=none` with a `no-response` member → 409 BlockedNoResponse; (d) finalize before `end_at` → 409/400; (e) reopen a done event → `checkInStatus` back to `awaiting_checkin`.
- [ ] **Step 2: Run RED** — `./gradlew :server:test --tests '*Finalize*'`.
- [ ] **Step 3: Implement** repo `finalize`/`reopen` + routes.
- [ ] **Step 4: GREEN.**
- [ ] **Step 5: Commit** — `feat(server): finalize + reopen attendance (CheckIn abschliessen)`.

---

### Task 5: Remove check-in routes + repo methods

**Files:** delete `CheckInRoutes.kt`; remove its registration in `Routing.kt`; remove `getCheckIn`, `getCheckInEntries`, `upsertCheckIn`, `CheckInRow`, `CheckInEntryResponse`, `presentCounts` (records-based) from `AttendanceRepository`(+Impl).

**Interfaces produced:** no more `/events/{id}/check-in*` endpoints; `presentCounts` (records) removed — the event-list presence indicator now derives from `confirmed` responses (Task 6 handles the count source).

- [ ] **Step 1: Delete** `CheckInRoutes.kt` + registration; remove the record-based repo methods and the now-dead `RecordStatus` imports.
- [ ] **Step 2: Replace `presentCounts`** with a `confirmedCounts(eventIds): Map<UUID,Int>` over `attendance_responses.status = 'confirmed'` (same shape). Update `EventRepositoryImpl` to use it for the event DTO's presence count (keep the field the web/mobile chip reads; it now counts confirmed responses).
- [ ] **Step 3: Compile** — `./gradlew :server:compileKotlin` should now pass (all record references gone except NDS, fixed next).
- [ ] **Step 4: Commit** — `refactor(server): remove check-in screen API; presence = confirmed responses`.

---

### Task 6: NDS export → confirmed responses

**Files:** `NdsRepository.listExportAttendances`, `NdsExportService`.

**Interfaces produced:** `listExportAttendances(teamId)` joins `attendance_responses` (`status='confirmed'`) to `nds_members` by `user_id`; AWK export emits present rows for confirmed attendance.

- [ ] **Step 1: Update the query** — swap `AttendanceRecordsTable … status eq present` for `AttendanceResponsesTable … status eq "confirmed"`. Keep the `ExportAttendance` shape.
- [ ] **Step 2: Test** — extend `NdsRoutesTest` export test: seed `confirmed` responses (via coach edit or import), assert the AWK CSV lists exactly those. (Adapt the existing export test that previously relied on present records.)
- [ ] **Step 3: GREEN** — `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'` (will still fail import tests until Task 7).
- [ ] **Step 4: Commit** — `feat(server): NDS export reads confirmed attendance responses`.

---

### Task 7: NDS import → responses + auto-finalize past

**Files:** `NdsEventImporter.kt`; `NdsRepository.claimMember`.

**Interfaces produced:** import writes `attendance_responses` (attended→`confirmed`, else→`declined`) for provisional users; `attendanceImported` counts written `confirmed`; imported events with date `< today` get `check_in_completed_at = now`; `claimMember` migrates `attendance_responses` (drop the records half).

- [ ] **Step 1: Rewrite the `attendanceMode == "keep"` block** to upsert `attendance_responses` (confirmed for attended dates; optionally declined for the rest of that member's team events — keep it minimal: write `confirmed` for attended, leave others as default `no-response`, OR write `declined` for non-attended — pick per spec "others to declined"; since roster×events is bounded, write `declined` for non-attended dated events of that member). Count confirmed for `attendanceImported`.
- [ ] **Step 2: Auto-finalize past events** — after creating events, set `check_in_completed_at = now` for imported events whose `start_at < now`.
- [ ] **Step 3: Fix `claimMember`** — remove the `AttendanceRecordsTable` move; keep the `AttendanceResponsesTable` move.
- [ ] **Step 4: Update `NdsRoutesTest`** — the import/claim/export tests now assert on responses; the large-fixture regression asserts `attendanceImported` = attended count; a past imported event reports `checkInStatus == "done"`.
- [ ] **Step 5: GREEN** — `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`.
- [ ] **Step 6: Commit** — `feat(server): NDS import writes responses; auto-finalize past events`.

---

### Task 8: Awaiting-check-in list + reminder

**Files:** `AttendanceRepository`/`EventRepository` (query); `AttendanceRoutes` or `EventRoutes` (endpoint); `ReminderSchedulerJob.kt`.

**Interfaces produced:**
- `listAwaitingCheckInForUser(userId): List<Event>` — events with a team the user coaches/manages, `end_at < now`, `check_in_completed_at is null`, not cancelled. Endpoint `GET /users/me/events/awaiting-checkin` (or a `?filter=awaiting_checkin` on the existing list) for the coach filter tab.
- Reminder: a `fireAwaitingCheckInReminders(...)` in `ReminderSchedulerJob` that, per coach with ≥1 awaiting event, sends a push at most every 2 days (track last-sent per user — reuse the notification/last-sent mechanism `fireCoachSummaries` uses; if none exists, add a simple `last_awaiting_reminder_at` per user or a notifications row check).

- [ ] **Step 1: Query + endpoint** with a test (seed a past unfinalized event on a coached team → appears; a finalized one → absent; a future one → absent; another club's event → absent).
- [ ] **Step 2: Reminder function** — extend the scheduler loop (mirror `fireCoachSummaries`, `pushService.sendToUsers`). Gate to every-2-days per coach. Add a focused test if the job is testable (mirror any existing scheduler test); otherwise a repo-level test of the "coaches with awaiting events" query + a manual note that the 2-day gate reuses the existing cadence mechanism.
- [ ] **Step 3: GREEN** for the query/endpoint test.
- [ ] **Step 4: Commit** — `feat(server): awaiting-check-in list + 2-day coach push reminder`.

---

### Task 9: Full-suite gate

- [ ] `./gradlew :server:test` green. Fix any remaining references. Commit any final touch-ups.

## Self-review vs spec
- Single model (responses) ✓ Tasks 3–7. Finalize/reopen ✓ Task 4. Cutoff lock (deadline??start) ✓ Task 3. unexcused ✓ Task 3. default_response resolution ✓ Task 4. Drop records + check-in screen API ✓ Tasks 1,5. NDS export/import re-point + auto-finalize ✓ Tasks 6,7. Awaiting list + push reminder ✓ Task 8. 403 subsumed (no check-in route; coach edit uses requireEventAccess which resolves club_manager) ✓.
