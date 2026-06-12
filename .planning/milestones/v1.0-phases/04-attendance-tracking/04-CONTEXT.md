# Phase 4: Attendance Tracking — Context

**Gathered:** 2026-03-24
**Status:** Ready for planning

<domain>
## Phase Boundary

Core attendance loop: players respond to events (confirm/decline/unsure), declare absences (Abwesenheit), coaches review and override attendance. Offline mutation queue + sync. Client-side stats on profile. Audit log for overrides (backend-only).

Requirements: AT-01–16, plus deferred TM-17 (profile attendance stats) and TM-19 (SET NULL on member removal).

</domain>

<decisions>
## Implementation Decisions

### Response flow UX
- Response buttons on **both** Event List cards and Event Detail screen
- Event list cards: 3 compact RSVP buttons with icon + count (✓ 12 | ? 2 | ✗ 4) — player's own selection highlighted (green fill). Matches Pencil design "V1-C — Event List"
- Event detail: 3 tall buttons "Going" (✓), "Maybe" (?), "Can't Go" (✗) — matches Pencil "V1 - Event Detail"
- Event detail also shows full member response list grouped by status: CONFIRMED (green), MAYBE (yellow), DECLINED (red) — with count per group
- "Unsure"/"Maybe" triggers a **bottom sheet** with text field for mandatory Begründung
- Response deadline shown as **countdown label**: "Respond by [date/time]" near buttons. After deadline: buttons disabled with "Response closed"
- Player can **freely change response** until deadline — current response shown as selected state, no confirmation dialog

### Abwesenheit management
- Absences managed in **"My Absences" section on Profile screen** — matches Pencil "V1 — Profile"
- Profile hero card shows avatar + attendance % bar, then absences section below with cards (icon + title + date range + status)
- "View All" link in section header for full absence list
- FAB on profile to add new absence
- Auto-declined events shown **greyed with "Auto-declined" / "Absent" badge** in event list. Player can still tap to manually override
- Absence creation via **bottom sheet** — matches Pencil "V1 - Add Absence (Bottom Sheet)"
- **Preset types with icons**: Holidays (sun), Injury (zap), Work (briefcase), School (book-open), Travel (plane), Other (ellipsis)
- **Affected area section** for injuries: body part selector (Head, Shoulder, Chest, Back, Arm, Hip, Thigh, Knee, Shin, Foot)
- Toggle between **Recurring** (weekday selector M-Su) and **Period** (date range)
- "Save Rule" button at bottom

### Coach attendance view
- **Single unified view** — same EventDetailScreen shows member responses + override buttons always (no pre/post-event mode distinction)
- Coach override: tapping a ✓/?/✗ button on a member row **opens a bottom sheet** with status pre-selected + optional note field
- Coach can **save immediately without entering a note** — note is optional but sheet always appears for intentional action
- Matches Pencil design: each member row shows avatar + name on left, 3 small status buttons on right

### Audit log
- Coach overrides logged in DB with `previous_status`, `previous_set_by`, `set_at` — **backend-only for Phase 4**
- No in-app audit viewer — deferred to Phase 6 (Super Admin panel)

### Statistics display
- **Profile card only** — hero card shows overall attendance % bar (matches Pencil "V1 — Profile")
- Client-side aggregation per ADR-007: no server stats endpoint
- Stats computed from raw attendance data: overall %, by event type (training/match/other), by date range, per-team breakdown
- **Team stats screen deferred** — user will provide design later (separate phase or follow-up)

### Claude's Discretion
- Offline mutation queue implementation details (SQLDelight schema, sync strategy)
- Background job for auto-decline on Abwesenheit rule create/update
- Post-event auto-present job (confirmed → present when check_in_enabled=false)
- Body part selector UI implementation (grid layout matching design)
- Error handling for 409 conflict on late sync
- Loading states and skeleton patterns

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Attendance Specs
- `.project/specs/attendance-tracking/tech.md` — Full data model (attendance_responses, attendance_records, abwesenheit_rules tables), API endpoints, background jobs, offline sync strategy
- `.project/specs/attendance-tracking/handover.md` — Architecture decisions, integration points
- `.project/specs/attendance-tracking/ux.md` — UX flows if exists
- `.project/specs/attendance-tracking/req.md` — Detailed requirements if exists
- `.project/specs/attendance-tracking/tasks.md` — Task breakdown if exists

### Design Files (Pencil)
- `pencil/teamorg.pen` screen "V1-C — Event List" (id: S5I1w) — Event cards with RSVP buttons (✓ count | ? count | ✗ count)
- `pencil/teamorg.pen` screen "V1 - Event Detail" (id: 2uF5C) — RSVP buttons + member response list grouped by status + coach override buttons
- `pencil/teamorg.pen` screen "V1 — Profile" (id: 5pAXJ) — Profile hero card with attendance % bar + "My Absences" section with absence cards
- `pencil/teamorg.pen` screen "V1 - Add Absence (Bottom Sheet)" (id: FcxGd) — Absence creation: reason presets, body part selector, recurring/period toggle, weekday selector

### Project Architecture
- `.planning/PROJECT.md` — Offline-first principle, client-side stats (ADR-007), mutation queue, audit log immutable at DB level
- `.planning/REQUIREMENTS.md` §AT — AT-01 through AT-16 (all attendance requirements)
- `.planning/REQUIREMENTS.md` §TM — TM-17 (attendance stats on profile), TM-19 (SET NULL on member removal)

### Design System
- `pencil/design.md` — Design tokens (colours, typography, spacing) for M3 theme

### Prior Phase Context
- `.planning/phases/03-event-scheduling/03-CONTEXT.md` — Offline cache pattern (3-month window), attendance placeholder card on EventDetailScreen

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `EventDetailScreen.kt` — Has placeholder card "Attendance — Coming soon" at line ~416; replace with real attendance section
- `EventListScreen.kt` — Event cards need RSVP buttons added below existing card content
- `RecurringPatternSheet.kt` / `RecurringScopeSheet.kt` — ModalBottomSheet pattern for Begründung sheet and coach override sheet
- `EventCacheManager` — SQLDelight cache pattern to replicate for AttendanceCacheManager
- `EventRepositoryImpl` (shared) — HTTP + offline fallback pattern via Result<T>
- `EventRepositoryImpl` (server) — Exposed ORM pattern with transactions and UUID PKs

### Established Patterns
- ViewModel: `data class *State` + `MutableStateFlow` + `MutableSharedFlow` for one-off events
- Repository: interface in `shared/domain/repository/`, impl in `shared/data/repository/`; `suspend fun (): Result<T>`
- Koin DI: `factory { ViewModel(get()) }` in UiModule; `single { RepositoryImpl(get()) }` in SharedModule
- Server routes: entity-scoped functions under `authenticate("jwt")` with role guards
- Exposed tables: UUID PKs, `enumerationByName` for enums, `timestamp()` for dates
- Flyway migrations: latest is V7 (events); attendance starts at V8

### Integration Points
- Navigation: add attendance-related screens/sheets to AppNavigation.kt
- Profile screen: replace/extend PlayerProfileScreen with attendance stats + absences section
- EventDetailScreen: replace placeholder with real attendance section (RSVP buttons + member list)
- EventListScreen: add RSVP buttons to event cards
- Koin modules: add AttendanceRepository, AbwesenheitRepository, attendance ViewModels
- Server Application.kt: add attendanceRoutes(), abwesenheitRoutes()

</code_context>

<specifics>
## Specific Ideas

- Event list cards must match Pencil "V1-C" exactly: colored cards with 3 RSVP buttons showing icon + response count
- Event detail must match Pencil "V1 - Event Detail": tall RSVP buttons + member list grouped by CONFIRMED/MAYBE/DECLINED with counts
- Profile screen must match Pencil "V1 — Profile": hero card with attendance % bar, "My Absences" section with cards
- Add Absence sheet must match Pencil "V1 - Add Absence": 6 reason preset tiles (2 rows of 3), body part grid (2 rows of 5), Recurring/Period toggle, weekday selector
- Coach override: always opens bottom sheet (even for quick override) — optional note field, can save empty
- Team stats screen will come later with separate design — not in Phase 4 scope

</specifics>

<deferred>
## Deferred Ideas

- Team stats screen — user will provide design later (separate phase or follow-up)
- In-app audit log viewer — Phase 6 (Super Admin panel)

</deferred>

---

*Phase: 04-attendance-tracking*
*Context gathered: 2026-03-24*
