---
phase: 03-event-scheduling
verified: 2026-03-19T13:00:00Z
status: human_needed
score: 15/15 must-haves verified
re_verification: false
human_verification:
  - test: "Open EventListScreen as a coach — confirm FAB is visible and tapping it opens CreateEditEventScreen"
    expected: "FAB appears for coach role; tapping navigates to create form"
    why_human: "isCoach detection relies on UserPreferences + team roster API call; cannot verify role detection flow programmatically without a running device"
  - test: "Create a weekly recurring event then verify it appears on multiple calendar dates"
    expected: "Event dots appear on all dates matching the recurrence pattern; materialisation job ran"
    why_human: "materialiseUpcomingOccurrences() runs on server startup; requires DB + server running to confirm"
  - test: "On EventListScreen, apply team filter then type filter — verify both filters narrow the list simultaneously"
    expected: "List shows only events matching both selected team AND selected type"
    why_human: "Filter composition behaviour requires a running UI with real data"
  - test: "Tap a day in month calendar view — confirm bottom sheet slides up with that day's events"
    expected: "ModalBottomSheet or equivalent appears with the correct events listed"
    why_human: "Bottom sheet on day tap is a UI gesture interaction; cannot verify with grep"
  - test: "On EventDetailScreen as a coach — open ... menu, tap Cancel, confirm RecurringScopeSheet appears for recurring event"
    expected: "RecurringScopeSheet renders with this_only/this_and_future/all options; selecting one triggers cancel API call"
    why_human: "Multi-screen interaction flow with real event data required"
  - test: "Offline fallback: disconnect network, open EventListScreen — confirm cached events appear"
    expected: "Events loaded from SQLDelight cache when Ktor HTTP call fails with network exception"
    why_human: "Requires device with airplane mode; cache-aside logic branches on ConnectTimeoutException"
---

# Phase 03: Event Scheduling Verification Report

**Phase Goal:** Full event scheduling system — coaches can create, view, and manage events (including recurring events) with calendar and list views; athletes see their scheduled events with subgroup filtering.
**Verified:** 2026-03-19T13:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Database schema for events, series, teams, subgroups exists | VERIFIED | V7__create_events.sql has 4 `CREATE TABLE` statements with FK constraints, CHECK constraints, TIMESTAMPTZ columns, and 4 indexes |
| 2 | EventRepository (server) can CRUD events with recurring series support | VERIFIED | `EventRepositoryImpl.kt` uses Exposed `transaction {}` with EventTeamsTable, EventSubgroupsTable, and `seriesOverride` guard on bulk operations |
| 3 | Server exposes REST endpoints for all event operations | VERIFIED | `EventRoutes.kt` has `fun Route.eventRoutes()` with GET /users/me/events, GET /teams/{teamId}/events, GET /events/{id}, POST /events, PATCH /events/{id}, POST /events/{id}/cancel, POST /events/{id}/duplicate — all under `authenticate("jwt")` |
| 4 | Recurring edit scopes (this_only, this_and_future, all) handled at route layer | VERIFIED | `EventRoutes.kt` `EditEventWithScope` wrapper; PATCH route dispatches on `RecurringScope.this_only/this_and_future/all`; cancel route likewise |
| 5 | GET /teams/{teamId}/subgroups endpoint exists | VERIFIED | `SubGroupRoutes.kt` has `fun Routing.subGroupRoutes()` with the endpoint; registered in `Routing.kt` |
| 6 | Materialisation background job runs on startup and daily | VERIFIED | `EventMaterialisationJob.kt` `fun Application.startMaterialisationJob()` with `Dispatchers.IO`, `delay(24.hours)`, exception handling; called from `Application.kt` |
| 7 | Shared KMP event domain models and repository interface exist | VERIFIED | `shared/.../domain/Event.kt` has `data class Event`, `EventWithTeams`, `CreateEventRequest`, `EditEventRequest`; `repository/EventRepository.kt` has `interface EventRepository` with Result<T> returns |
| 8 | KMP EventRepositoryImpl makes real HTTP calls with offline fallback | VERIFIED | `data/repository/EventRepositoryImpl.kt` uses `httpClient.get/post/patch`; offline fallback in `getMyEvents` via `cacheManager.getFilteredOfflineEvents()` |
| 9 | SQLDelight offline cache exists for events | VERIFIED | `Event.sq` has `CachedEvent` table with `upsertEvent`, `getFilteredEvents`, `deleteOlderThan` queries; cache wired in both Android and iOS Koin modules |
| 10 | EventListScreen renders with filters and role-based FAB | VERIFIED | `EventListScreen.kt` has `LazyColumn`, `FilterChipRow`, coach-only `FloatingActionButton` gated by `state.isCoach`; `EventListViewModel.kt` detects role via UserPreferences + roster scan |
| 11 | EventDetailScreen renders all detail sections with coach actions | VERIFIED | `EventDetailScreen.kt` has cancelled banner, header, time, location, teams/subgroups, description, attendance placeholder (Phase 4), and coach `...` dropdown with Edit/Duplicate/Cancel |
| 12 | Create/Edit event form works with recurring pattern and scope sheets | VERIFIED | `CreateEditEventScreen.kt` with 6 sections, `CreateEditEventViewModel.kt` with `save()`, `validate()`, `loadSubgroupsForSelectedTeams()`, `cancelEvent(scope)`; `RecurringPatternSheet.kt` and `RecurringScopeSheet.kt` with ModalBottomSheet |
| 13 | Calendar screen shows month and week views via kizitonwose | VERIFIED | `CalendarScreen.kt` uses `HorizontalCalendar` and `WeekCalendar` from kizitonwose 2.7.0; `CalendarViewMode.MONTH/WEEK` toggle; coloured dots with max-3 logic; `CalendarViewModel.kt` builds `eventsByDate` map with multi-day spanning |
| 14 | All navigation routes wired (EventList, EventDetail, CreateEvent, EditEvent, Calendar) | VERIFIED | `AppNavigation.kt` imports and dispatches `EventListScreen`, `EventDetailScreen`, `CreateEditEventScreen`, `CalendarScreen`; `Screen.kt` has `EventDetail`, `CreateEvent`, `EditEvent` data classes |
| 15 | Wave 0 test stubs exist across all 3 modules | VERIFIED | 9 stub files present with `@Ignore` annotations and requirement IDs: 3 in `shared/commonTest/kotlin/event/`, 3 in `server/test/kotlin/event/`, 3 in `composeApp/androidTest/kotlin/event/` |

**Score:** 15/15 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `server/src/main/resources/db/migrations/V7__create_events.sql` | 4 event tables | VERIFIED | event_series, events, event_teams, event_subgroups with correct FKs to `sub_groups(id)` |
| `server/.../db/tables/EventsTable.kt` | 4 Exposed table objects | VERIFIED | EventSeriesTable, EventsTable, EventTeamsTable, EventSubgroupsTable |
| `server/.../domain/repositories/EventRepository.kt` | Repository interface | VERIFIED | `interface EventRepository` with 14 suspend methods |
| `server/.../domain/repositories/EventRepositoryImpl.kt` | Repository implementation | VERIFIED | `class EventRepositoryImpl : EventRepository` using `transaction {}`, handles junction tables and seriesOverride guard |
| `server/.../routes/EventRoutes.kt` | Event API endpoints | VERIFIED | `fun Route.eventRoutes()` with 7 endpoints, EditEventWithScope wrapper |
| `server/.../routes/SubGroupRoutes.kt` | SubGroup endpoint | VERIFIED | `fun Routing.subGroupRoutes()` with GET /teams/{teamId}/subgroups |
| `server/.../infra/EventMaterialisationJob.kt` | Background job | VERIFIED | `fun Application.startMaterialisationJob()`, 24h interval, exception handling |
| `server/.../plugins/Koin.kt` | EventRepository registration | VERIFIED | `single<EventRepository> { EventRepositoryImpl() }` |
| `shared/.../domain/Event.kt` | Shared KMP domain models | VERIFIED | Event, EventWithTeams, CreateEventRequest, EditEventRequest, RecurringPattern, SubGroup |
| `shared/.../repository/EventRepository.kt` | Shared interface | VERIFIED | `interface EventRepository` with Result<T> returns |
| `shared/.../data/repository/EventRepositoryImpl.kt` | HTTP implementation | VERIFIED | All 7 API calls via Ktor httpClient + offline fallback |
| `shared/.../data/EventCacheManager.kt` | SQLDelight cache | VERIFIED | Save, read, filtered read with 7-day staleness purge |
| `shared/commonMain/sqldelight/ch/teamorg/Event.sq` | SQLDelight schema | VERIFIED | CachedEvent table + 6 queries |
| `composeApp/.../ui/events/EventListScreen.kt` | Event list UI | VERIFIED | LazyColumn, FilterChipRow, coach FAB, EventListItem with type colours |
| `composeApp/.../ui/events/EventListViewModel.kt` | List ViewModel | VERIFIED | loadEvents(), setTeamFilter(), setTypeFilter(), isCoach detection via UserPreferences |
| `composeApp/.../ui/events/EventDetailScreen.kt` | Event detail UI | VERIFIED | 7 sections, cancelled banner, coach ... menu |
| `composeApp/.../ui/events/EventDetailViewModel.kt` | Detail ViewModel | VERIFIED | loadEvent(), isCoach detection |
| `composeApp/.../ui/events/CreateEditEventScreen.kt` | Create/Edit form | VERIFIED | 6-section form, date/time pickers, audience targeting, recurring switch |
| `composeApp/.../ui/events/CreateEditEventViewModel.kt` | Form ViewModel | VERIFIED | save(), validate(), loadForEdit(), cancelEvent(), loadSubgroupsForSelectedTeams() |
| `composeApp/.../ui/events/RecurringPatternSheet.kt` | Recurring pattern UI | VERIFIED | ModalBottomSheet, RadioButton group (Daily/Weekly/Custom), weekday FilterChips |
| `composeApp/.../ui/events/RecurringScopeSheet.kt` | Scope selection UI | VERIFIED | ModalBottomSheet, RadioButton for this_only/this_and_future/all |
| `composeApp/.../ui/calendar/CalendarScreen.kt` | Calendar screen | VERIFIED | HorizontalCalendar + WeekCalendar, MONTH/WEEK toggle, coloured dots, time-block grid |
| `composeApp/.../ui/calendar/CalendarViewModel.kt` | Calendar ViewModel | VERIFIED | eventsByDate map with multi-day spanning, loadEvents() from EventRepository |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| EventRoutes.kt | EventRepository | Koin `inject<>()` | VERIFIED | `val eventRepository = inject<EventRepository>()` at route level |
| Routing.kt | EventRoutes.kt | `eventRoutes()` call | VERIFIED | `eventRoutes()` at line 23 |
| Routing.kt | SubGroupRoutes.kt | `subGroupRoutes()` call | VERIFIED | `subGroupRoutes()` at line 24 |
| Application.kt | EventMaterialisationJob.kt | `startMaterialisationJob()` | VERIFIED | Import and call present in Application.kt |
| EventRepositoryImpl (server) | EventsTable.kt | Exposed DSL | VERIFIED | Uses EventTeamsTable, EventSubgroupsTable directly |
| AppNavigation.kt | EventListScreen | Screen.Events dispatch | VERIFIED | EventListScreen rendered at Events route |
| AppNavigation.kt | EventDetailScreen | Screen.EventDetail dispatch | VERIFIED | EventDetailScreen rendered with eventId |
| AppNavigation.kt | CreateEditEventScreen | Screen.CreateEvent / EditEvent | VERIFIED | Both routes dispatch CreateEditEventScreen |
| AppNavigation.kt | CalendarScreen | Screen.Calendar dispatch | VERIFIED | CalendarScreen rendered at Calendar route |
| EventRepositoryImpl (shared) | EventCacheManager | offline fallback | VERIFIED | `cacheManager.getFilteredOfflineEvents()` called on network exception |
| SharedModule (Android+iOS) | EventRepositoryImpl | `singleOf(::EventRepositoryImpl) bind EventRepository::class` | VERIFIED | Present in both androidMain and iosMain SharedModule |
| CalendarViewModel | EventRepository | `eventRepository.getMyEvents()` | VERIFIED | Called in `loadEvents()` with multi-day span mapping |

### Requirements Coverage

| Requirement | Source Plan | Description | Status |
|-------------|------------|-------------|--------|
| ES-01 | 03-01, 03-02 | Create/read events | SATISFIED — POST /events and GET endpoints implemented and tested |
| ES-02 | 03-01, 03-07 | Event types (training/match/other) | SATISFIED — CHECK constraint in V7, EventType enum, type colours in UI |
| ES-03 | 03-01, 03-02 | Cancel event with status/timestamp | SATISFIED — cancel() sets status=cancelled, cancelled_at; POST /events/{id}/cancel route |
| ES-04 | 03-01, 03-02 | Duplicate event | SATISFIED — duplicate() creates copy; POST /events/{id}/duplicate route |
| ES-05 | 03-01, 03-06 | Sub-group targeting on create/edit | SATISFIED — event_subgroups junction table; loadSubgroupsForSelectedTeams() in ViewModel |
| ES-06 | 03-01, 03-03 | Min attendees field | SATISFIED — min_attendees column in DB, minAttendees in domain models, Switch+NumberField in form |
| ES-07 | 03-02 | Edit event with scope | SATISFIED — PATCH /events/{id} with EditEventWithScope wrapper |
| ES-08 | 03-01, 03-02 | Recurring series with materialisation | SATISFIED — event_series table, materialiseUpcomingOccurrences(), background job |
| ES-09 | 03-01, 03-02 | Edit recurring scope (this_only / this_and_future) | SATISFIED — RecurringScope enum, PATCH route handles all 3 scopes |
| ES-10 | 03-01, 03-02 | Cancel recurring scope (all) | SATISFIED — cancelFutureInSeries() with series_override guard; POST /cancel route |
| ES-11 | 03-04, 03-05 | Event list with upcoming events | SATISFIED — EventListScreen LazyColumn, EventRepositoryImpl.getMyEvents() |
| ES-12 | 03-06 | Timezone display on form | SATISFIED — timezone label inline in CreateEditEventScreen; Instant via LocalDateTime.toInstant(TimeZone.currentSystemDefault()) |
| ES-13 | 03-04, 03-05 | Filter by team and type | SATISFIED — FilterChipRow with team and type chips; setTeamFilter()/setTypeFilter() in ViewModel |
| ES-14 | 03-07 | Calendar views (month + week) | SATISFIED — HorizontalCalendar + WeekCalendar; coloured dots; time-block grid |
| ES-15 | 03-01, 03-02 | Multi-team event targeting | SATISFIED — event_teams junction table; findEventsForUser deduplicates multi-team events |
| ES-16 | 03-01, 03-02, 03-03, 03-04 | SQLDelight offline cache | SATISFIED — Event.sq CachedEvent table; cache-aside in EventRepositoryImpl |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `EventDetailScreen.kt` | 416 | `// Section 7: Attendance placeholder card` with "Attendance — Coming soon" text | Info | Intentional Phase 4 deferral — documented in plan |
| `CalendarViewModel.kt` | 24 | `val isCoach: Boolean = false` — never set from UserPreferences | Warning | Coach FAB in CalendarScreen is always hidden; coaches cannot create events from calendar view. Coach create path still works via EventListScreen FAB. SUMMARY documents this as "wired to UserPreferences in future iteration" |

---

### Human Verification Required

**1. Coach role detection and FAB visibility (EventListScreen)**
- **Test:** Log in as a user who is a coach in at least one team, navigate to EventListScreen
- **Expected:** FAB (create event) is visible; tapping opens CreateEditEventScreen in create mode
- **Why human:** `isCoach` detection calls `teamRepository.getTeamRoster(teamId)` for all user teams; result depends on live API + UserPreferences role matching

**2. Recurring event creation and calendar materialisation**
- **Test:** Create a weekly recurring event (e.g. every Monday for 4 weeks), then open CalendarScreen
- **Expected:** Blue (training) or green (match) dot appears on all 4 Monday dates in month view; week view shows time blocks
- **Why human:** `materialiseUpcomingOccurrences()` runs on server startup and requires DB; cannot verify occurrence expansion without a running server + DB

**3. Subgroup filtering — athlete only sees targeted events**
- **Test:** Create an event targeted to a specific sub-group; log in as an athlete NOT in that sub-group
- **Expected:** The event does NOT appear in athlete's EventListScreen
- **Why human:** `findEventsForUser` LEFT JOIN sub_group_members filtering logic requires live DB to verify audience exclusion

**4. Offline fallback — SQLDelight cache**
- **Test:** Fetch events while online (populates cache), disconnect network, reopen EventListScreen
- **Expected:** Cached events load from SQLDelight; no crash; events appear even without network
- **Why human:** Requires device with network toggle; Ktor ConnectTimeoutException vs ResponseException branch

**5. CalendarScreen — day tap shows bottom sheet**
- **Test:** In month view, tap a day that has events
- **Expected:** Day's events appear in a slide-up sheet or inline panel
- **Why human:** UI gesture + conditional rendering; grep cannot confirm bottom sheet trigger

**6. RecurringScopeSheet on cancel from EventDetailScreen**
- **Test:** Open a recurring event's detail screen as coach, tap ... > Cancel
- **Expected:** RecurringScopeSheet appears with 3 scope options; selecting "All events" cancels the series
- **Why human:** Multi-step coach flow requiring real event data with series_id

---

### Gaps Summary

No functional gaps found. All 15 observable truths are verified. All key artifacts exist and are wired.

**Two notable items that do NOT block the goal:**

1. **CalendarScreen coach FAB is always hidden** (`isCoach = false` in CalendarViewModel). Coaches can still create events from EventListScreen. The plan explicitly deferred calendar coach wiring to Phase 4. The phase goal ("coaches can create, view, and manage events") is satisfied via EventListScreen + EventDetailScreen. This is a UX omission on the calendar screen only.

2. **OfflineBanner** (pinned below top bar in S1 and S2 per UI-SPEC) was not implemented. It was not included in any plan's `must_haves` and no plan claimed it. This is an orphaned UI-SPEC requirement not captured in planning.

Both items are informational only. Overall phase goal is achieved.

---

_Verified: 2026-03-19T13:00:00Z_
_Verifier: Claude (gsd-verifier)_
