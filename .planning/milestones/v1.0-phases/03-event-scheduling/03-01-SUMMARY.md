---
phase: 03-event-scheduling
plan: 01
subsystem: database
tags: [kotlin, exposed, flyway, postgresql, ktor, koin, events, recurring-series]

requires:
  - phase: 02-team-management
    provides: teams, sub_groups tables and Exposed ORM objects referenced by event FKs

provides:
  - Flyway V7 migration: event_series, events, event_teams, event_subgroups tables
  - Exposed ORM objects: EventSeriesTable, EventsTable, EventTeamsTable, EventSubgroupsTable
  - Domain models: Event, EventSeries, EventWithTeams, MatchedTeam, CreateEventRequest, EditEventRequest, RecurringPattern, RecurringScope
  - EventRepository interface with CRUD, series management, materialisation, and bulk update/cancel methods
  - EventRepositoryImpl using Exposed DSL with transaction pattern
  - Koin registration: single<EventRepository> { EventRepositoryImpl() }

affects: [03-02, 03-03, 03-04, 04-attendance-tracking]

tech-stack:
  added: []
  patterns:
    - "Exposed ORM with enumerationByName for CHECK-constrained text columns"
    - "array<Short> for SMALLINT[] PostgreSQL column (Exposed 0.54.0 supported)"
    - "transaction { } block pattern (not dbQuery) for repository methods"
    - "series_override boolean flag gates bulk series operations"

key-files:
  created:
    - server/src/main/resources/db/migrations/V7__create_events.sql
    - server/src/main/kotlin/ch/teamorg/db/tables/EventsTable.kt
    - server/src/main/kotlin/ch/teamorg/domain/models/Event.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/EventRepository.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/EventRepositoryImpl.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/plugins/Koin.kt

key-decisions:
  - "Used enumerationByName (stores enum as TEXT) to match V7 CHECK constraints — avoids separate Postgres enum type"
  - "Used array<Short> for weekdays column (available in Exposed 0.54.0 via resolveColumnType)"
  - "materialiseUpcomingOccurrences generates occurrences up to 12 months ahead; skips already-materialised rows by tracking maxSeq"
  - "findEventsForUser deduplicates by event_id via distinct uniqueEventIds list before building EventWithTeams"
  - "series_override=false guard on cancelFutureInSeries/updateFutureInSeries protects individually-edited events"

patterns-established:
  - "EventType/EventStatus/PatternType enums defined in EventsTable.kt — shared via import by repository"
  - "rowToEvent() helper extracts basic fields; teamIds/subgroupIds added via copy() after separate JOIN queries"

requirements-completed: [ES-01, ES-02, ES-03, ES-04, ES-05, ES-06, ES-15, ES-16]

duration: ~25min
completed: 2026-03-19
---

# Phase 3 Plan 01: Event Scheduling — DB Foundation Summary

**Flyway V7 migration with 4 event tables + Exposed ORM objects + EventRepository (interface + impl with recurring series materialisation) registered in Koin**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-03-19T00:00:00Z
- **Completed:** 2026-03-19
- **Tasks:** 3/3
- **Files modified:** 6

## Accomplishments

- Created V7 Flyway migration with event_series, events, event_teams, event_subgroups tables including all FK constraints and 4 indexes
- Defined 4 Exposed ORM table objects and 8 domain model types covering all event API shapes
- Implemented EventRepository with 15 methods including multi-team deduplication, subgroup audience filtering, and recurring series materialisation

## Task Commits

1. **Task 1: Flyway migration V7** - `9578ddc` (feat)
2. **Task 2: Exposed ORM tables + domain models** - `2981be5` (feat)
3. **Task 3: EventRepository interface + implementation + Koin** - `5826e18` (feat)

## Files Created/Modified

- `server/src/main/resources/db/migrations/V7__create_events.sql` - 4 event tables with indexes and FK constraints
- `server/src/main/kotlin/ch/teamorg/db/tables/EventsTable.kt` - 4 Exposed table objects + 3 enums (EventType, EventStatus, PatternType)
- `server/src/main/kotlin/ch/teamorg/domain/models/Event.kt` - Event, EventSeries, EventWithTeams, MatchedTeam, CreateEventRequest, EditEventRequest, RecurringPattern, RecurringScope
- `server/src/main/kotlin/ch/teamorg/domain/repositories/EventRepository.kt` - Interface with 15 suspend methods
- `server/src/main/kotlin/ch/teamorg/domain/repositories/EventRepositoryImpl.kt` - Full Exposed DSL implementation
- `server/src/main/kotlin/ch/teamorg/plugins/Koin.kt` - Added EventRepository binding

## Decisions Made

- Used `enumerationByName` for EventType/EventStatus/PatternType to store as TEXT matching V7 CHECK constraints — avoids maintaining a separate Postgres enum type
- Used `array<Short>()` for weekdays column after confirming Exposed 0.54.0 supports it via `resolveColumnType(Short::class)` -> `ShortColumnType`
- `materialiseUpcomingOccurrences` tracks max existing sequence per series and only appends new rows beyond that point
- `findEventsForUser` queries team membership via TeamRolesTable, then deduplicates by event_id before building EventWithTeams list
- `series_override = false` guard ensures bulk cancel/update operations skip events that were individually modified

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed invalid Kotlin wildcard assignment and missing return statement**
- **Found during:** Task 3 (EventRepositoryImpl compilation)
- **Issue:** `_ = eventId` is not valid Kotlin syntax; `materialiseUpcomingOccurrencesInternal` block body needed explicit `return created`
- **Fix:** Removed unused variable capture from insert statement; added `return created` to block-body function
- **Files modified:** server/src/main/kotlin/ch/teamorg/domain/repositories/EventRepositoryImpl.kt
- **Verification:** `./gradlew :server:compileKotlin` exits 0
- **Committed in:** 5826e18 (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 bug — Kotlin syntax errors in generated code)
**Impact on plan:** Fix was necessary for compilation. No scope creep.

## Issues Encountered

None beyond the auto-fixed compile errors above.

## Next Phase Readiness

- DB schema and repository layer complete; Plans 03-02 onward can build API routes and UI using EventRepository
- No blockers

---
*Phase: 03-event-scheduling*
*Completed: 2026-03-19*
