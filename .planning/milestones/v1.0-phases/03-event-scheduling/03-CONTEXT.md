# Phase 3: Event Scheduling — Context

**Gathered:** 2026-03-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Coaches can create, edit, and cancel events (one-off and recurring). Players see and filter their schedule via a list view and a calendar view. Attendance responses are Phase 4 — not in scope here.

</domain>

<decisions>
## Implementation Decisions

### Calendar UI Library
- Use **kizitonwose/calendar-compose** for month and week views
- Style with M3 tokens (TeamorgTheme colours, typography)
- Do not build a custom calendar grid — the library handles layout; we handle styling and data

### Timezone Handling
- Events stored UTC (server); displayed in device local timezone (client-side conversion via kotlinx-datetime)
- Create/edit form: show timezone abbreviation label (e.g. "CET") next to time pickers — informational only, no picker
- Event list and detail screens: show local time only — no timezone label displayed

### Offline Event Browsing
- Cache fetched events in SQLDelight for read-only offline access
- Cache window: **3 months** of upcoming events (not the full 12-month server materialisation window)
- Read-only in Phase 3: no mutation queue (deferred to Phase 4)
- If offline and cache empty: show empty state, not error

### Attendance Card on Event Detail
- UX spec S3 includes an attendance summary card — Phase 4 owns attendance
- Phase 3 renders a **placeholder card** ("Attendance — Coming soon") in the correct layout position
- Keeps event detail screen layout stable so Phase 4 can fill it in without restructuring

### Claude's Discretion
- Exact kizitonwose API configuration (HorizontalCalendar vs VerticalCalendar, etc.)
- SQLDelight schema structure for events cache (mapping from API response)
- Background job implementation details (materialisation service)
- Error state designs for network failures

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Event Scheduling Specs
- `.project/specs/event-scheduling/tech.md` — Full data model, API endpoints, recurring strategy, multi-team deduplication, KMP architecture
- `.project/specs/event-scheduling/ux.md` — All screens (S1–S7), user flows (F1–F7), interaction patterns, edge cases

### Design System
- `pencil/design.md` — Design tokens (colours, typography, spacing) for M3 theme

### Project Architecture
- `.planning/PROJECT.md` — Tech stack, offline-first principle, architecture principles, UX standards
- `.planning/REQUIREMENTS.md` §ES — ES-01 through ES-16 (all event scheduling requirements)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `composeApp/src/commonMain/kotlin/ch/teamorg/navigation/Screen.kt` — `Screen.Events` and `Screen.Calendar` routes already exist (sealed class, @Serializable)
- `composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt` — Lines 88–89 are `PlaceholderScreen()` calls for Events + Calendar; replace with real screens
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/components/TeamorgBottomBar.kt` — Bottom bar already wired for Events and Calendar tabs
- `TeamRosterScreen` + `TeamRosterViewModel` — established LazyColumn + Card + FAB pattern; reuse for event list
- `ClubSetupScreen` — form layout pattern (sectioned Column scroll) for event create/edit form
- `AlertDialog` pattern (member removal) — reuse for cancel event confirmation dialogs

### Established Patterns
- ViewModel: `data class *State` + `MutableStateFlow` + `MutableSharedFlow` for one-off events
- Repository: interface in `shared/domain/repository/`, impl in `shared/data/repository/`; `suspend fun (): Result<T>`
- Koin DI: `factory { ViewModel(get()) }` in UiModule; `single { RepositoryImpl(get()) }` in SharedModule
- Server routes: entity-scoped functions (`eventRoutes()`) plugged into `Routing.kt`; wrapped in `authenticate("jwt")`
- Server DB: Exposed ORM with UUID PKs, `java.time.Instant` (UTC) for timestamps
- Flyway migrations: `server/src/main/resources/db/migrations/`

### Integration Points
- Navigation3 graph in `AppNavigation.kt` — add EventListScreen, EventDetailScreen, CreateEventScreen, CalendarScreen as destinations
- Koin modules: add `EventRepository`, `SubgroupRepository`, event ViewModels
- `kotlinx-datetime` (pinned 0.6.1) — use for UTC ↔ local timezone conversion in shared domain
- `event_subgroups` and `subgroups` tables: `sub_groups` + `sub_group_members` already created in Phase 2 migrations (verify schema name match before creating Phase 3 migrations)

</code_context>

<specifics>
## Specific Requirements

- Calendar library: **kizitonwose/calendar-compose** — verify KMP compatibility and add to `libs.versions.toml`
- Multi-team badge: when `matched_teams.length > 1`, show badge on list item and detail screen (spec: "2 teams" indicator)
- Recurring indicator: ⟳ icon on list items (spec S1) and calendar blocks (spec S2)
- Cancelled state: greyed row + "Cancelled" chip on list; greyed block + strikethrough on calendar; banner on detail
- FAB "+" visible to coaches only (role check in ViewModel state)
- Event type colour coding: Training=Blue, Match=Green, Other=Purple (from ux.md)

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-event-scheduling*
*Context gathered: 2026-03-19*
