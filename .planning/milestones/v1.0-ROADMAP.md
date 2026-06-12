# ROADMAP.md — Playbook

**6 phases** | **67 requirements mapped** | Each phase ships a working product

---

## Phase 1 — Foundation + Auth
**Goal:** Runnable app on Android + iOS with working auth flow. The skeleton everything else builds on.

**Requirements:** AUTH-01–06

**Deliverables:**
- KMP monorepo scaffolded (shared, composeApp, androidApp, iosApp, server, admin)
- Material 3 theme wired with custom design tokens (dark + light)
- Navigation3 shell with placeholder screens
- Ktor server bootstrapped (DB, Flyway, JWT auth)
- Register, login, logout flows — fully tested
- Role system in place (Coach, Player, ClubManager, SuperAdmin — DB-checked per request)
- SQLDelight schema initialized

**Success criteria:**
1. App runs on Android and iOS without crash
2. User can register, log in, and log out — verified by automated test
3. JWT issued; role loaded from DB on each protected request
4. Theme matches design tokens (dark mode default)
5. CI runs tests on every commit

---

## Phase 2 — Team Management
**Goal:** Clubs and teams work. Coaches and players can be invited and manage their rosters.

**Requirements:** TM-01–19

**Plans:** 6/6 plans complete

Plans:
- [ ] 02-12-PLAN.md — Club edit UI (TM-01 gap closure)
- [ ] 02-13-PLAN.md — Avatar upload for player profiles (TM-11 gap closure)

**Deliverables:**
- Club setup flow (ClubManager creates club, logo upload)
- Team creation + editing
- Invite system (email + shareable link, 7-day expiry)
- Role assignment: coach → team, player → team
- Sub-groups within teams
- Player profiles (avatar, jersey, position, contact)
- Team roster screen
- Automated tests for all invite + membership flows

**Success criteria:**
1. ClubManager can create a club and a team
2. Coach invited by email can accept and manage the team
3. Player can be invited, join, and leave a team
4. Sub-groups created and players assigned
5. All membership flows covered by tests

---

## Phase 3 — Event Scheduling
**Goal:** Coaches can create, edit, and cancel events. Players see and filter their schedule.

**Requirements:** ES-01–16

**Plans:** 4/4 plans complete

Plans:
- [ ] 03-00-PLAN.md -- Wave 0 test stubs (Nyquist compliance)
- [ ] 03-01-PLAN.md — Backend data foundation (migration, Exposed tables, EventRepository)
- [ ] 03-02-PLAN.md — Server API routes + materialisation job + integration tests
- [ ] 03-03-PLAN.md — Shared KMP contracts (domain models, repository interface, screen routes, calendar dep)
- [ ] 03-04-PLAN.md — Shared KMP repository impl + SQLDelight offline cache
- [ ] 03-05-PLAN.md — Event list screen + event detail screen + navigation wiring
- [ ] 03-06-PLAN.md — Create/edit event form + recurring/scope bottom sheets
- [ ] 03-07-PLAN.md — Calendar screen with kizitonwose month + week views

**Deliverables:**
- Event creation form (all types, multi-day, recurring)
- Recurring event server-side expansion
- Edit/cancel flows (this / this+future / all for recurring)
- Event duplication
- Event list (chronological, filterable)
- Calendar view (month + week)
- Sub-group targeting for events
- Timezone handling (UTC storage, local display)
- Automated tests: create, edit, cancel, recurrence

**Success criteria:**
1. Coach can create a one-off and a recurring event
2. Player sees events filtered by team and type
3. Calendar view renders correctly
4. Editing a recurring event with all 3 scope options works correctly
5. All event flows covered by tests

---

## Phase 4 — Attendance Tracking
**Goal:** Core attendance loop works end-to-end including offline, Abwesenheit, and coach overrides.

**Requirements:** AT-01–16

**Plans:** 7/7 plans complete

Plans:
- [ ] 04-01-PLAN.md — Backend data foundation (V8 migration, Exposed tables, server repositories)
- [ ] 04-02-PLAN.md — Server API routes (attendance, abwesenheit, check-in) + background jobs
- [ ] 04-03-PLAN.md — Shared KMP contracts (domain models, repository interfaces, SQLDelight offline schema)
- [ ] 04-04-PLAN.md — Shared KMP repository impls + offline mutation queue + stats calculator
- [ ] 04-05-PLAN.md — Event list + detail attendance UI (RSVP buttons, member list, Begrundung sheet)
- [ ] 04-06-PLAN.md — Coach override sheet + absence management UI (AddAbsenceSheet, profile integration)
- [ ] 04-07-PLAN.md — End-to-end wiring + automated tests (server integration + stats unit tests)

**Deliverables:**
- Per-event response UI (confirm / decline / unsure + Begründung)
- Response deadline enforcement (client UX + server 409)
- Abwesenheit: recurring weekly + period absence, auto-decline
- Manual coach override (present/absent/excused + note)
- Attendance list per event (all members + statuses)
- Attendance statistics (% presence, filters) — including player profile stats section (TM-17 deferred from Phase 2)
- Offline mutation queue + sync
- Audit log for coach overrides
- Automated tests: response flows, deadline enforcement, offline sync, Abwesenheit

**Deferred from Phase 2:**
- TM-17: Attendance stats section in PlayerProfileScreen (requires attendance table)
- TM-19: When adding attendance table, ensure `team_roles` FK uses `SET NULL` on member removal (not CASCADE) — `TeamRepositoryImpl.removeMember()` currently hard-deletes

**Success criteria:**
1. Player can confirm/decline/unsure; coach sees response immediately
2. Abwesenheit auto-declines events; manual override takes precedence
3. Late sync rejected with 409 (server authoritative)
4. Offline responses sync correctly when back online
5. Statistics correct for a player with mixed attendance history

---

## Phase 4.1 — Attendance Integration Fixes
**Goal:** Fix P0 runtime breaks and P1 degraded features identified by milestone audit.

**Gap Closure:** Closes BREAK-01, PARTIAL-01, PARTIAL-02, PARTIAL-03 from v1.0 audit.

**Requirements:** AT-04, AT-05, AT-06, AT-08, AT-11, AT-12, AT-13, TM-17, TM-19

**Plans:** 2/2 plans complete

Plans:
- [ ] 04.1-01-PLAN.md — Server fixes: CheckInEntry shape mismatch + TeamRolesTable nullable alignment
- [ ] 04.1-02-PLAN.md — Client fixes: Stats eventTypes + Profile tab navigation

**Deliverables:**
- CheckInEntry server response aligned with client model (BREAK-01)
- TeamRolesTable Exposed object updated to nullable user_id + SET NULL (PARTIAL-01)
- PlayerProfileViewModel passes real eventTypes to stats calculator (PARTIAL-02)
- Profile tab wired to PlayerProfileScreen instead of PlaceholderScreen (PARTIAL-03)

**Success criteria:**
1. Coach sees member response list on EventDetailScreen
2. Coach can open and use CoachOverrideSheet
3. Member removal doesn't crash subsequent role queries
4. Stats bar shows correct per-type breakdown
5. Player reaches attendance stats + absence management from Profile tab

---

## Phase 5 — Notifications
**Goal:** Players and coaches receive timely, relevant notifications. Users control what they get.

**Requirements:** NO-01–12

**Plans:** 6/6 plans complete

Plans:
- [ ] 05-01-PLAN.md — Server DB foundation (V9 migration, Exposed tables, PushService, NotificationRepository)
- [ ] 05-02-PLAN.md — Server routes + notification triggers + ReminderSchedulerJob
- [ ] 05-03-PLAN.md — Shared KMP contracts (domain models, repository interface, SQLDelight cache, Ktor impl)
- [ ] 05-04-PLAN.md — OneSignal SDK integration (Android + iOS init, login/logout hooks)
- [ ] 05-05-PLAN.md — Notification UI (InboxScreen, settings, ReminderPickerSheet, badge, EventDetail reminder row)
- [ ] 05-06-PLAN.md — Integration + unit tests (NotificationRoutesTest, PushServiceTest)

**Deliverables:**
- OneSignal integration (iOS + Android, behind PushService abstraction)
- Event notifications: new event, edits, cancellations
- Reminder scheduler (configurable lead time per user)
- Coach response notifications (per-response + summary modes)
- Abwesenheit change notifications to coach
- In-app notification inbox
- Per-user notification settings screen
- No-duplicate + removed-member guards
- Automated tests: notification triggers, settings, dedup

**Success criteria:**
1. Player receives push on new event and cancellation
2. Coach receives response notification (both modes configurable)
3. Reminder fires at configured lead time
4. In-app inbox shows all notifications without push enabled
5. No duplicate notifications sent

---

## Phase 5.1 — Milestone Gap Fixes
**Goal:** Close P1/P2 integration gaps and security issues from v1.0 milestone audit.

**Gap Closure:** Closes AT-03/AT-04 offline sync, NO-08 UX, check-in role check, dead code cleanup.

**Requirements:** AT-03, AT-04, AT-14, NO-08

**Plans:** 2/2 plans complete

Plans:
- [ ] 05.1-01-PLAN.md — Offline sync wiring + check-in security fix (AT-03, AT-04, AT-14)
- [ ] 05.1-02-PLAN.md — Team name resolution in settings + dead code cleanup (NO-08)

**Deliverables:**
- Connectivity observer wiring `MutationQueueManager.flushQueue()` on reconnect/foreground
- `NotificationSettingsViewModel` resolves team names instead of showing UUIDs
- Role check added to `GET /events/{id}/check-in`
- Dead `CalendarScreen.kt`, `CalendarViewModel.kt`, and Koin factory removed

**Success criteria:**
1. Offline RSVP responses sync to server on reconnect
2. Notification settings screen shows team names, not UUIDs
3. Non-coach users cannot read check-in list
4. No dead CalendarScreen/CalendarViewModel references remain

---

## Phase 5.2 — Auth Retroactive Verification
**Goal:** Retroactively verify Phase 01 auth requirements and close remaining checkbox gaps.

**Gap Closure:** Closes AUTH-01–06 orphaned verification, TM-14 partial status.

**Requirements:** AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, TM-14

**Plans:** 1/1 plans complete

Plans:
- [ ] 05.2-01-PLAN.md — Create auth verification evidence + update requirement checkboxes

**Deliverables:**
- `01-VERIFICATION.md` created with evidence for all 6 AUTH requirements
- TM-14 verified (backend multi-team support confirmed)
- REQUIREMENTS.md checkboxes updated

**Success criteria:**
1. All AUTH requirements have verification evidence
2. TM-14 checkbox reflects actual implementation status
3. Re-audit passes with 62/62 requirements satisfied

---

## Phase 6 — Super Admin
**Goal:** Platform operators can manage clubs and monitor the platform via the SvelteKit admin panel.

**Requirements:** SA-01–12

**Plans:** 8 plans (7 complete, 1 gap closure)

Plans:
- [x] 06-01-PLAN.md — Backend DB foundation (V10 migration, Exposed tables, repositories, middleware)
- [x] 06-02-PLAN.md — SvelteKit project scaffold + auth + layout shell
- [x] 06-03-PLAN.md — Admin API routes (club CRUD, impersonation endpoints)
- [x] 06-04-PLAN.md — SvelteKit dashboard + club management pages
- [x] 06-05-PLAN.md — SvelteKit user search + audit log viewer
- [x] 06-06-PLAN.md — Impersonation UI (banner, countdown, start/end flow)
- [x] 06-07-PLAN.md — Integration tests + human verification
- [ ] 06-08-PLAN.md — Gap closure: fix frontend TS interfaces (dashboard stats, clubs pagination, manager IDs)

**Deliverables:**
- SvelteKit admin panel (fresh start, failed example as reference)
- Club management (create, deactivate, delete)
- ClubManager invites + removal
- Impersonation (1h limit, audit-logged, clearly shown in UI)
- Platform overview dashboard
- User search
- Immutable audit log viewer (DB role restricted — no UPDATE/DELETE)
- Automated tests: club CRUD, impersonation flow, audit log immutability

**Success criteria:**
1. SuperAdmin can create a club and invite a ClubManager
2. Impersonation session clearly indicated and auto-expires at 1h
3. All SA actions appear in audit log; log cannot be modified
4. Platform dashboard shows correct aggregates
5. All SA flows covered by tests

---

## Phase Order Rationale

Auth -> Teams -> Events -> Attendance -> Notifications -> Admin

Each phase depends on the previous. At the end of each phase, the product is working and testable for everything shipped so far.

---

## Milestones

| Phase | Feature | Status |
|---|---|---|
| 1 | Foundation + Auth | Done |
| 2 | Team Management | Done |
| 3 | Event Scheduling | Done |
| 4 | Attendance Tracking | Done |
| 4.1 | Attendance Integration Fixes | Done |
| 5 | Notifications | Done |
| 5.1 | Milestone Gap Fixes | Done |
| 5.2 | Auth Retroactive Verification | Done |
| 6 | Super Admin | In progress |
