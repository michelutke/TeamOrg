---
phase: 03-event-scheduling
plan: 05
subsystem: ui-events
tags: [compose, event-list, event-detail, navigation, koin, role-detection]
dependency_graph:
  requires: [03-03, 03-04]
  provides: [EventListScreen, EventDetailScreen, EventListViewModel, EventDetailViewModel]
  affects: [AppNavigation, UiModule]
tech_stack:
  added: [kotlinx-datetime in composeApp commonMain]
  patterns: [ViewModel + StateFlow, Koin factory, Nav3 backstack push, UserPreferences role detection]
key_files:
  created:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventListScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventListViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventDetailScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventDetailViewModel.kt
  modified:
    - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt
    - composeApp/build.gradle.kts
decisions:
  - "Used UserPreferences.getUserId() for coach role detection — avoids extra getMe() API call since userId is cached at login"
  - "kotlinx-datetime added as direct composeApp dependency — shared module uses implementation (not api) so it was not transitive"
  - "Local val captures for nullable Event fields (location, description, meetupAt, minAttendees) — required for Kotlin smart cast from cross-module public API"
metrics:
  duration: "~25 minutes"
  completed: "2026-03-19T11:25:55Z"
  tasks_completed: 2
  tasks_total: 2
  files_created: 4
  files_modified: 3
---

# Phase 3 Plan 5: Event List + Detail Screens Summary

Event list (S1) and detail (S3) screens implemented with full navigation wiring, role-based FAB, and cross-screen refresh trigger for Plan 06.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | EventListScreen + EventListViewModel | 62c565a | EventListScreen.kt, EventListViewModel.kt |
| 2 | EventDetailScreen + EventDetailViewModel + navigation | a9b4d4e | EventDetailScreen.kt, EventDetailViewModel.kt, AppNavigation.kt, UiModule.kt, build.gradle.kts |

## What Was Built

### EventListViewModel
- `EventListState`: events, isLoading, error, selectedTeamId, selectedType, isCoach, teams
- `loadEvents()`: calls `eventRepository.getMyEvents(type, teamId)`, extracts unique team filter list
- `loadUserRole()`: gets `userId` from `UserPreferences`, iterates team rosters, sets `isCoach = true` on first coach match
- `setTeamFilter()` / `setTypeFilter()`: update state + reload

### EventListScreen
- `Scaffold` + `TopAppBar` + coach-only `FloatingActionButton`
- `FilterChipRow`: horizontal `LazyRow` with All Teams + per-team chips, `|` separator, All/Training/Match/Other type chips
- `EventListItem`: type colour indicator, title + Cancelled chip, date (single-day or multi-day range), multi-team badge, recurring icon
- Cancelled state: `Modifier.alpha(0.4f)` on entire row
- Empty state: role-aware (coach shows Create CTA, player shows "No upcoming events")

### EventDetailViewModel
- `EventDetailState`: event, isLoading, error, isCoach
- `loadEvent(eventId)`: fetches detail, then calls `loadCoachRole` on matched teams
- `loadCoachRole()`: same UserPreferences + roster approach as list VM

### EventDetailScreen
- Full 7-section body per UI-SPEC S3:
  1. Cancelled banner (colorError strip)
  2. Header card: type chip + title + recurring indicator
  3. Time section: single-day or multi-day formatting + meetup time
  4. Location section + "Open in Maps" button (if set)
  5. Team chips + subgroup chips
  6. Description with collapsible "Show more" toggle
  7. Attendance placeholder card ("Attendance — Coming soon", dashed border)
- Coach `...` dropdown: Edit / Duplicate / Cancel

### AppNavigation
- `Screen.Events` → `EventListScreen` (replaces `PlaceholderScreen("Events List")`)
- `Screen.EventDetail` → `EventDetailScreen` with `LaunchedEffect(screen.eventId, detailRefreshTrigger)`
- `detailRefreshTrigger by remember { mutableIntStateOf(0) }` at navigation scope — Plan 06 increments from EditEvent `onSaved`
- `Screen.CreateEvent` + `Screen.EditEvent` → placeholders for Plan 06

### UiModule + build.gradle.kts
- `EventListViewModel(get(), get(), get())` — EventRepository, TeamRepository, UserPreferences
- `EventDetailViewModel(get(), get(), get())` — same
- `kotlinx-datetime` added to composeApp commonMain (was not transitive from shared)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added kotlinx-datetime to composeApp commonMain**
- **Found during:** Task 1 compile
- **Issue:** `shared` module uses `implementation` for kotlinx-datetime (not `api`), so the dependency was not visible to composeApp's commonMain. `Unresolved reference 'datetime'` errors.
- **Fix:** Added `implementation(libs.kotlinx.datetime)` to `composeApp/build.gradle.kts` commonMain dependencies.
- **Files modified:** composeApp/build.gradle.kts
- **Commit:** a9b4d4e

**2. [Rule 1 - Bug] Fixed smart cast on cross-module nullable properties**
- **Found during:** Task 2 compile
- **Issue:** Kotlin cannot smart cast nullable properties from public API of a different module (event.location, event.description, event.meetupAt, event.minAttendees). Compiler error "Smart cast to X is impossible".
- **Fix:** Captured all nullable fields in local vals at top of composable function before use in `if` guards.
- **Files modified:** EventDetailScreen.kt
- **Commit:** a9b4d4e

**3. [Rule 2 - Missing] Used UserPreferences.getUserId() instead of roster scan without user matching**
- **Found during:** Task 1 implementation
- **Issue:** The plan's skeleton `loadUserRole()` checked `member.role == "coach"` without filtering by current userId — would match ANY coach in the roster, not just the current user.
- **Fix:** Injected `UserPreferences` as third constructor param; calls `getUserId()` to identify the current user before roster role check.
- **Files modified:** EventListViewModel.kt, EventDetailViewModel.kt, UiModule.kt
- **Commit:** 62c565a, a9b4d4e

## Self-Check: PASSED

All 4 created files present. Both commits (62c565a, a9b4d4e) verified in git log.
