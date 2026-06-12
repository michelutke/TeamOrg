---
phase: 03-event-scheduling
plan: "03"
subsystem: domain
tags: [kotlin-multiplatform, kotlinx-datetime, kotlinx-serialization, koin, navigation, calendar]

requires:
  - phase: 03-00
    provides: wave-0 test stubs and shared module structure

provides:
  - Event, EventWithTeams, MatchedTeam, CreateEventRequest, EditEventRequest, RecurringPattern, SubGroup data classes with @Serializable
  - EventType enum
  - EventRepository interface with Result<T> return types
  - EventDetail, CreateEvent, EditEvent navigation screen routes
  - kizitonwose-calendar 2.10.0 in version catalog

affects:
  - 03-04 (EventRepositoryImpl coding against EventRepository interface)
  - 03-05 (navigation wiring uses Screen.EventDetail/CreateEvent/EditEvent)
  - 03-06 (EventListScreen/EventDetailScreen coding against EventRepository + domain types)
  - 03-07 (calendar UI uses kizitonwose-calendar-compose from version catalog)

tech-stack:
  added:
    - kizitonwose-calendar 2.10.0 (compose-multiplatform artifact, catalog only)
  patterns:
    - "@Serializable data classes in commonMain using kotlinx.datetime.Instant for timestamps"
    - "Repository interface returns Result<T> — consistent with ClubRepository/TeamRepository pattern"
    - "Screen sealed class entries follow route-string convention established in Phase 1"

key-files:
  created:
    - shared/src/commonMain/kotlin/ch/teamorg/domain/Event.kt
    - shared/src/commonMain/kotlin/ch/teamorg/repository/EventRepository.kt
  modified:
    - shared/src/commonMain/kotlin/ch/teamorg/di/SharedModule.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/Screen.kt
    - gradle/libs.versions.toml

key-decisions:
  - "Use kotlinx.datetime.Instant not java.time.Instant — required for KMP commonMain compilation on iOS"
  - "kizitonwose-calendar added to version catalog only; Plan 07 adds to build.gradle.kts when calendar UI is implemented"
  - "SharedModule.kt TODO comment placed for Plan 04 impl registration — no premature wiring"

patterns-established:
  - "Event domain model pattern: @Serializable + kotlinx.datetime.Instant + String IDs"
  - "Repository interface returns Result<T> with suspend funs — consistent across all shared repositories"

requirements-completed: [ES-01, ES-02, ES-03, ES-04, ES-05, ES-06, ES-07, ES-08, ES-09, ES-10, ES-11, ES-12, ES-16]

duration: 8min
completed: 2026-03-19
---

# Phase 3 Plan 03: Shared KMP Contracts Summary

**@Serializable Event domain model with kotlinx.datetime.Instant, EventRepository interface with Result<T> return types, 3 navigation screen routes, and kizitonwose calendar 2.10.0 in version catalog**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-03-19T11:23:10Z
- **Completed:** 2026-03-19T11:31:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Defined full shared event domain: Event, EventWithTeams, CreateEventRequest, EditEventRequest, RecurringPattern, SubGroup, EventType enum — all @Serializable with kotlinx.datetime.Instant
- Created EventRepository interface matching API surface with Result<T> pattern used across the codebase
- Added EventDetail, CreateEvent, EditEvent to Screen sealed class — Plan 05 navigation wiring has all routes it needs
- Added kizitonwose-calendar 2.10.0 to version catalog (compose-multiplatform artifact) — Plan 07 ready

## Task Commits

1. **Task 1: Shared KMP domain models + repository interface** - `ed0641c` (feat)
2. **Task 2: Navigation screen entries + calendar dependency** - `8ebc9d3` (feat)

**Plan metadata:** _(docs commit follows)_

## Files Created/Modified
- `shared/src/commonMain/kotlin/ch/teamorg/domain/Event.kt` — Event, EventWithTeams, MatchedTeam, CreateEventRequest, EditEventRequest, RecurringPattern, SubGroup, EventType
- `shared/src/commonMain/kotlin/ch/teamorg/repository/EventRepository.kt` — EventRepository interface with 7 suspend operations
- `shared/src/commonMain/kotlin/ch/teamorg/di/SharedModule.kt` — TODO comment for Plan 04 impl registration
- `composeApp/src/commonMain/kotlin/ch/teamorg/navigation/Screen.kt` — EventDetail, CreateEvent, EditEvent screen routes added
- `gradle/libs.versions.toml` — kizitonwose-calendar 2.10.0 + compose-multiplatform artifact

## Decisions Made
- `kotlinx.datetime.Instant` used for all timestamps — java.time.Instant is JVM-only and breaks iOS compilation
- Calendar library added to catalog only (not to build.gradle.kts) — YAGNI: Plan 07 adds it when implementing the calendar UI
- SharedModule TODO comment only — no premature impl wiring before Plan 04 creates EventRepositoryImpl

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
- `./gradlew :shared:compileKotlinAndroid` failed due to ambiguous task name — used `:shared:compileDebugKotlinAndroid` instead. Build passed successfully.

## Next Phase Readiness
- Plan 04 (EventRepositoryImpl) has the interface to implement against
- Plan 05 (navigation) has all 3 screen routes
- Plan 07 (calendar UI) has the kizitonwose library in catalog ready to add
- All downstream plans (04-07) can code against shared contracts without ambiguity

---
*Phase: 03-event-scheduling*
*Completed: 2026-03-19*
