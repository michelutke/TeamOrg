# Unified Attendance — Plan 2: Web (admin)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax. Do Plan 1 (server) first — this consumes its API.

**Goal:** Move attendance to a single view in the SvelteKit admin: event detail shows player buttons that lock at the cutoff, a coach edit popup (with "Nicht entschuldigt"), and "CheckIn abschliessen" / reopen; the event list gets an "Awaiting check-in" coach filter; event create/edit gets a `defaultResponse` field; the separate check-in page is removed.

**Architecture:** The event payload now carries `checkInStatus` (`open|locked|awaiting_checkin|done`), `checkInCompletedAt`, `defaultResponse`, and attendance rows carry `unexcused`. UI reads `checkInStatus` to decide who can edit and what to show.

**Tech Stack:** SvelteKit, Svelte 5 runes, Tailwind. **Design spec:** `docs/superpowers/specs/2026-07-01-unified-attendance-design.md`.

## Global Constraints
- Branch `feat/unified-attendance` (same as Plan 1). Gate: `cd admin && npm run check` clean (0 errors). Merge only; no AI trailers; commit per unit.
- Player buttons are editable only when `checkInStatus == "open"`. After that show "Zeit zum An-/Abmelden abgelaufen". Coach/CM edit while `checkInStatus != "done"`.
- "CheckIn abschliessen" visible to coach/CM only when `checkInStatus == "awaiting_checkin"`. Reopen visible when `done`.
- Manage checks must be club-manager-aware: reuse the event-detail `canManage` derivation (via `loadUserTeams`), NOT the naive `canManageTeam` that needs a per-team role (that was the 403 bug).
- `unexcused` is never shown to players; only in the coach edit popup / coach-facing lists.

## File Structure
- Modify: `admin/src/lib/server/events.ts` — `AppEvent` gains `checkInStatus`, `checkInCompletedAt`, `defaultResponse`; `AttendanceResponse` gains `unexcused`.
- Modify: `admin/src/routes/(shell)/app/events/[id]/+page.svelte` + `+page.server.ts` — locked banner, coach edit popup, finalize/reopen actions.
- Modify: `admin/src/routes/(shell)/app/events/+page.svelte` + `+page.server.ts` — awaiting-checkin filter tab; presence summary already present.
- Modify: the event create/edit form (shared component under `admin/src/lib/components/` or `app/events/new` + `[id]/edit`) — `defaultResponse` select.
- **Delete**: `admin/src/routes/(shell)/app/teams/[teamId]/checkin/` (page + server) and the "Anwesenheitskontrolle" link/button in `[id]/+page.svelte`.
- Modify: `admin/src/routes/(shell)/app/events/rsvp/+server.ts` if needed (player RSVP proxy) to respect lock (server already enforces; surface the 403 as the locked message).

---

### Task 1: Types + remove check-in page
**Files:** `events.ts`; delete `checkin/` route; remove the check-in link in event detail.
- [ ] Add `checkInStatus: string`, `checkInCompletedAt: string | null`, `defaultResponse: string` to `AppEvent`; add `unexcused: boolean` to `AttendanceResponse`.
- [ ] Delete `admin/src/routes/(shell)/app/teams/[teamId]/checkin/[eventId]/` (both files). Remove the `ClipboardCheck` "Anwesenheitskontrolle" `<a>` in `[id]/+page.svelte` (the `/checkin/` link).
- [ ] `cd admin && npm run check` clean (fix any dangling imports/refs to the deleted route).
- [ ] Commit: `refactor(web): drop check-in page; add unified-attendance event fields`.

---

### Task 2: Event detail — locked banner + coach edit popup + finalize/reopen
**Files:** `app/events/[id]/+page.svelte` + `+page.server.ts`.

**Consumes:** server routes `PUT /events/{id}/attendance/{userId}` (coach edit `{status, unexcused}`), `POST /events/{id}/attendance/finalize`, `POST /events/{id}/attendance/reopen`; `data.event.checkInStatus`, `data.canManage`, `data.responses[].unexcused`.

- [ ] **Player buttons state:** keep the existing "your response" buttons but disable them (and show a "Zeit zum An-/Abmelden abgelaufen" note) when `checkInStatus != "open"`. The player RSVP action already 403s server-side after cutoff — surface that as the same note.
- [ ] **Coach view:** when `canManage`, render the responses list with an edit control per member. Clicking a member opens a popup: choose `confirmed` (Anwesend) / `declined` (Abgemeldet); when `declined` is chosen show a **"Nicht entschuldigt"** checkbox → posts `unexcused=true`. Popup submits to a `?/setMemberStatus` action → `PUT /events/{id}/attendance/{userId}`. Editable only while `checkInStatus != "done"`. Show the `unexcused` marker on declined rows (coach view only).
- [ ] **Finalize:** when `checkInStatus == "awaiting_checkin"` and `canManage`, show a **"CheckIn abschliessen"** button → `?/finalize` → `POST …/finalize`. On 409 (blocked), show the dialog message listing the members that must be resolved (unsure, or no-response when `defaultResponse=none`). On success the page reloads showing `done`.
- [ ] **Reopen:** when `checkInStatus == "done"` and `canManage`, show a "CheckIn wieder öffnen" button → `?/reopen`.
- [ ] **canManage:** ensure `+page.server.ts` derives `canManage` via `loadUserTeams` (already does) — no `canManageTeam` naive check.
- [ ] `npm run check` clean; manual smoke if dev server available (open a past event as CM → edit a member, finalize, reopen).
- [ ] Commit: `feat(web): unified attendance on event detail (lock, coach edit, finalize/reopen)`.

---

### Task 3: Event list — awaiting-check-in filter tab
**Files:** `app/events/+page.svelte` + `+page.server.ts`.

**Consumes:** server awaiting list (`GET /users/me/events/awaiting-checkin` or `?filter=awaiting_checkin`); `event.checkInStatus`.

- [ ] Add a coach-only filter chip/tab "Check-in offen" next to the existing team filter, visible when the user coaches/manages ≥1 team. Selecting it lists only awaiting-checkin events (fetch the awaiting endpoint, or filter the loaded list by `checkInStatus == "awaiting_checkin"`).
- [ ] Show a small `checkInStatus` badge on awaiting rows (e.g. "Check-in offen") so coaches spot them. Keep the existing "N anwesend" presence chip (now counts confirmed responses).
- [ ] `npm run check` clean.
- [ ] Commit: `feat(web): awaiting-check-in filter on events list`.

---

### Task 4: Create/edit event — defaultResponse
**Files:** the event form (shared component or `new` + `[id]/edit` pages) + their `+page.server.ts` actions.
- [ ] Add a `defaultResponse` `<select>` — options: `none` ("Keine Vorgabe – Trainer muss auflösen"), `accepted` ("Standard: Anwesend"), `declined` ("Standard: Abgemeldet"); default `none`. Include it in the create + edit form submissions (the server create/edit endpoints must accept it — confirm Plan 1 wired the field into `CreateEventRequest`/`EditEventRequest`; if not, add it there).
- [ ] `npm run check` clean.
- [ ] Commit: `feat(web): default attendance response on event create/edit`.

---

### Task 5: Web gate
- [ ] `cd admin && npm run check` — 0 errors. Commit any touch-ups.

## Self-review vs spec
- Player lock + banner ✓ T2. Coach edit + unexcused ✓ T2. Finalize (unsure/no-response dialog) + reopen ✓ T2. Awaiting filter ✓ T3. defaultResponse ✓ T4. Check-in page removed ✓ T1. canManage club-manager-aware (403 fix) ✓ T2.
