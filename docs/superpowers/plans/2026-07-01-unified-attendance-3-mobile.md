# Unified Attendance — Plan 3: Mobile (composeApp + shared)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax. Do Plan 1 (server) first; Plan 2 (web) can run in parallel. This consumes the server API.

**Goal:** Bring the unified attendance model to the KMP mobile app: event detail shows lockable player buttons + a coach edit sheet (with "Nicht entschuldigt") + "CheckIn abschliessen"/reopen; the event list gets an awaiting-check-in filter; create/edit gets `defaultResponse`; the separate mobile check-in screen is removed.

**Architecture:** Shared `Event` gains `checkInStatus`, `checkInCompletedAt`, `defaultResponse`; the shared attendance repo gains coach-edit/finalize/reopen calls; Compose screens read `checkInStatus` to gate editing.

**Tech Stack:** Kotlin Multiplatform, Compose, Ktor client, Koin. **Design spec:** `docs/superpowers/specs/2026-07-01-unified-attendance-design.md`.

## Global Constraints
- Branch `feat/unified-attendance`. Gate: `./gradlew :shared:compileCommonMainKotlinMetadata :composeApp:compileCommonMainKotlinMetadata` (and `:shared` tests where runnable — JVM target may lack iOS expect/actual; use iOS-sim test compile as the prior NDS work did). Merge only; no AI trailers; commit per unit.
- Player buttons editable only when `checkInStatus == "open"`; else show "Zeit zum An-/Abmelden abgelaufen". Coach edits while `!= "done"`. Finalize when `awaiting_checkin`; reopen when `done`.
- Coach gating must resolve club-manager (a CM without a per-team role still manages) — mirror how the app already decides coach/manage capability for events.
- Remove the mobile check-in screen entirely.

## File Structure
- Modify: `shared/src/commonMain/kotlin/ch/teamorg/domain/Event.kt` — add `checkInStatus: String = "open"`, `checkInCompletedAt: Instant? = null`, `defaultResponse: String = "none"`.
- Modify: shared attendance repo (`shared/.../repository/AttendanceRepository.kt` + impl) — add `setMemberResponse(eventId,userId,status,unexcused)`, `finalize(eventId)`, `reopen(eventId)`, `awaitingCheckIn()`; add `unexcused` to the response model.
- Modify: `composeApp/.../ui/events/EventDetailScreen.kt` + `EventDetailViewModel.kt` — lock, coach edit sheet, finalize/reopen.
- Modify: `composeApp/.../ui/events/EventListScreen.kt` + `EventListViewModel.kt` — awaiting filter + status badge.
- Modify: `composeApp/.../ui/events/CreateEditEventScreen.kt` + `CreateEditEventViewModel.kt` — `defaultResponse` picker.
- **Delete**: the mobile check-in screen + its route/VM (search `checkin`/`CheckIn` under `composeApp/.../ui`), and remove its nav entry in `Screen.kt`/`AppNavigation.kt` + the entry point (button) that opens it. Update/remove any fakes referencing removed repo methods.

---

### Task 1: Shared model + repo
**Files:** `shared/.../domain/Event.kt`; shared attendance repo interface + impl; fakes.
- [ ] Add the three `Event` fields (defaulted). Add a serialization test asserting an event JSON with `checkInStatus`/`defaultResponse`/`checkInCompletedAt` decodes (mirror the existing `EventSerializationTest`).
- [ ] Add repo methods (mirror the `Result<T>` Ktor pattern): `setMemberResponse` → `PUT /events/$id/attendance/$userId {status,unexcused}`; `finalize` → `POST /events/$id/attendance/finalize` (return a result distinguishing 200 vs 409-with-blocked-ids); `reopen` → `POST /events/$id/attendance/reopen`; `awaitingCheckIn()` → the server awaiting endpoint. Add `unexcused` to the response row model.
- [ ] Remove any shared check-in repo methods (getCheckInEntries/upsertCheckIn) and their fakes.
- [ ] Build shared + compose metadata; run shared serialization test.
- [ ] Commit: `feat(shared): unified-attendance event fields + attendance repo methods`.

---

### Task 2: Event detail — lock, coach edit sheet, finalize/reopen
**Files:** `EventDetailScreen.kt` + `EventDetailViewModel.kt`.
- [ ] Player RSVP buttons: disabled + "Zeit abgelaufen" note when `checkInStatus != "open"`.
- [ ] Coach (manage-capable) view: per-member edit via a bottom sheet → `confirmed`/`declined`; when `declined`, a "Nicht entschuldigt" toggle → `unexcused=true`; calls `setMemberResponse`. Editable while `!= "done"`. Show `unexcused` marker on declined rows (coach only).
- [ ] "CheckIn abschliessen" button when `awaiting_checkin` + manage → `finalize()`; on 409 show a dialog listing members to resolve. "CheckIn wieder öffnen" when `done` → `reopen()`.
- [ ] Build compose metadata + iOS test compile. Add a VM test if practical (finalize success updates state; 409 surfaces blocked list) using the fake repo.
- [ ] Commit: `feat(mobile): unified attendance on event detail`.

---

### Task 3: Event list — awaiting filter + status badge
**Files:** `EventListScreen.kt` + `EventListViewModel.kt`.
- [ ] Coach-only "Check-in offen" filter (uses `awaitingCheckIn()` or filters loaded events by `checkInStatus == "awaiting_checkin"`). Status badge on awaiting rows. Keep the existing NDS presence chip (now confirmed-based).
- [ ] Build; VM test for the filter if practical.
- [ ] Commit: `feat(mobile): awaiting-check-in filter on events list`.

---

### Task 4: Create/edit — defaultResponse
**Files:** `CreateEditEventScreen.kt` + `CreateEditEventViewModel.kt`; shared `CreateEventRequest`/`EditEventRequest` (add `defaultResponse` if Plan 1 added it server-side — confirm and mirror in shared).
- [ ] Add a `defaultResponse` picker (none/accepted/declined, default none) to the create/edit form; include it in the request.
- [ ] Build.
- [ ] Commit: `feat(mobile): default attendance response on event create/edit`.

---

### Task 5: Remove mobile check-in screen
**Files:** delete the check-in screen + VM; remove route/nav/entry point; clean fakes.
- [ ] Delete the mobile check-in screen, its `Screen` route, `AppNavigation` composable, and the button that opened it. Remove leftover references.
- [ ] Build compose metadata + iOS test compile green.
- [ ] Commit: `refactor(mobile): remove standalone check-in screen`.

---

### Task 6: Mobile gate
- [ ] `:shared` + `:composeApp` commonMain compile green; shared tests (runnable targets) green. Commit touch-ups.

## Self-review vs spec
- Shared fields + repo ✓ T1. Detail lock/coach-edit/unexcused/finalize/reopen ✓ T2. Awaiting filter ✓ T3. defaultResponse ✓ T4. Check-in screen removed ✓ T5. Manage gating club-manager-aware ✓ T2.
