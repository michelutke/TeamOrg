---
phase: 03-event-scheduling
plan: "02"
subsystem: api
tags: [ktor, event-scheduling, background-job, testcontainers, kotlinx-serialization]

requires:
  - phase: 03-01
    provides: EventRepository interface + EventRepositoryImpl + DB tables

provides:
  - "8 event REST endpoints (GET /users/me/events, GET /teams/{teamId}/events, GET /events/{id}, POST /events, PATCH /events/{id}, POST /events/{id}/cancel, POST /events/{id}/duplicate)"
  - "GET /teams/{teamId}/subgroups endpoint with member count"
  - "Background materialisation job: runs on startup + every 24h"
  - "Custom KSerializers for UUID, Instant, LocalDate, LocalTime (java.time)"
  - "@Serializable on all Event domain models"
  - "9 integration tests for event routes via Testcontainers PostgreSQL"

affects: [04-event-repository-impl, 05-notifications, mobile-client]

tech-stack:
  added: []
  patterns:
    - "Route extension function pattern: fun Route.xyzRoutes() with inject<>()"
    - "EditEventWithScope: route-local wrapper class for scope deserialization (avoids polluting domain model)"
    - "Custom KSerializer objects for java.time types (not contextual — explicit @Serializable(with=) annotations)"
    - "Application extension function for background job: fun Application.startMaterialisationJob()"

key-files:
  created:
    - server/src/main/kotlin/ch/teamorg/routes/EventRoutes.kt
    - server/src/main/kotlin/ch/teamorg/routes/SubGroupRoutes.kt
    - server/src/main/kotlin/ch/teamorg/infra/EventMaterialisationJob.kt
    - server/src/main/kotlin/ch/teamorg/domain/models/Serializers.kt
    - server/src/test/kotlin/ch/teamorg/routes/EventRoutesTest.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/plugins/Routing.kt
    - server/src/main/kotlin/ch/teamorg/Application.kt
    - server/src/main/kotlin/ch/teamorg/domain/models/Event.kt

key-decisions:
  - "EditEventWithScope is a route-local @Serializable data class in EventRoutes.kt — scope field handled at route layer, EditEventRequest stays clean"
  - "JWT userId extracted from payload.subject (not custom claim) — matches existing auth pattern"
  - "Custom KSerializer objects for java.time types rather than contextual serialization — more explicit, no SerializersModule needed"
  - "this_and_future scope creates a new single event (not series) from updated fields — simplified implementation"
  - "CreateEventRequest optional fields (meetupAt, location, description, minAttendees, teamIds, subgroupIds, recurring) given null/empty defaults for lenient deserialization"

requirements-completed: [ES-01, ES-03, ES-05, ES-07, ES-08, ES-09, ES-10, ES-15, ES-16]

duration: 20min
completed: "2026-03-19"
---

# Phase 03 Plan 02: Event API Routes + Materialisation Job Summary

**Ktor REST API for all 7 event operations + GET /teams/{teamId}/subgroups + background recurring-event materialisation job with 9 passing integration tests**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-03-19T12:06:49Z
- **Completed:** 2026-03-19T12:26:00Z
- **Tasks:** 3
- **Files modified:** 7 (3 created new + 4 modified)

## Accomplishments
- All 7 event API endpoints implemented with JWT auth and full recurring-scope logic (this_only, this_and_future, all)
- GET /teams/{teamId}/subgroups endpoint with member count via Exposed left join
- Background materialisation job wired into Application startup via Dispatchers.IO coroutine with 24h repeat
- Custom KSerializers for java.time types + @Serializable on all Event domain models
- 9 integration tests all passing against Testcontainers PostgreSQL

## Task Commits

Each task was committed atomically:

1. **Task 1: Event API routes + SubGroup listing endpoint** - `3183e4d` (feat)
2. **Task 2: Materialisation background job + Application wiring** - `9998c3c` (feat)
3. **Task 3: Server integration tests for event routes** - `e4365d5` (test)

## Files Created/Modified
- `server/src/main/kotlin/ch/teamorg/routes/EventRoutes.kt` - 7 event endpoints, EditEventWithScope wrapper, RecurringScope handling
- `server/src/main/kotlin/ch/teamorg/routes/SubGroupRoutes.kt` - GET /teams/{teamId}/subgroups with member count
- `server/src/main/kotlin/ch/teamorg/infra/EventMaterialisationJob.kt` - Startup + daily materialisation coroutine
- `server/src/main/kotlin/ch/teamorg/domain/models/Serializers.kt` - UUIDSerializer, InstantSerializer, LocalDateSerializer, LocalTimeSerializer
- `server/src/main/kotlin/ch/teamorg/domain/models/Event.kt` - Added @Serializable + custom serializer annotations, defaults on optional fields
- `server/src/main/kotlin/ch/teamorg/plugins/Routing.kt` - Added eventRoutes() + subGroupRoutes() calls
- `server/src/main/kotlin/ch/teamorg/Application.kt` - Added startMaterialisationJob() after configureRouting()
- `server/src/test/kotlin/ch/teamorg/routes/EventRoutesTest.kt` - 9 integration tests

## Decisions Made
- `EditEventWithScope` is route-local — scope deserialization kept out of domain model
- JWT userId from `payload.subject` (not a custom claim) — consistent with existing auth
- Custom KSerializer objects for java.time types — explicit, no SerializersModule required
- `this_and_future` scope creates a new single event rather than a new series (simpler implementation, can be enhanced later)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added @Serializable + custom serializers to Event domain models**
- **Found during:** Task 3 (integration tests)
- **Issue:** Event.kt used java.time.Instant, java.util.UUID — not natively serializable by kotlinx.serialization. Routes would 500 when trying to respond with Event objects
- **Fix:** Created Serializers.kt with KSerializer objects for each java.time type; added @Serializable + @Serializable(with=) annotations to all Event models
- **Files modified:** Event.kt, Serializers.kt (new)
- **Verification:** All 9 integration tests pass
- **Committed in:** e4365d5 (Task 3 commit)

**2. [Rule 1 - Bug] Added defaults to CreateEventRequest optional fields**
- **Found during:** Task 3 (integration tests)
- **Issue:** kotlinx.serialization MissingFieldException — nullable fields (meetupAt, location, etc.) had no defaults so partial JSON payloads were rejected
- **Fix:** Added `= null` / `= emptyList()` defaults to all optional fields in CreateEventRequest
- **Files modified:** Event.kt
- **Verification:** POST /events with minimal payload returns 201
- **Committed in:** e4365d5 (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 missing critical serialization, 1 bug with missing defaults)
**Impact on plan:** Both fixes necessary for correctness. No scope creep.

## Issues Encountered
None beyond the auto-fixed deviations above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Event API fully implemented and tested
- EventRepository interface + impl ready for Plan 04 to build on
- GET /teams/{teamId}/subgroups available for Plan 04's getSubGroups() implementation
- All recurring scope logic (this_only, this_and_future, all) handled at route layer

## Self-Check: PASSED
- EventRoutes.kt: FOUND
- SubGroupRoutes.kt: FOUND
- EventMaterialisationJob.kt: FOUND
- Serializers.kt: FOUND
- EventRoutesTest.kt: FOUND
- Commits 3183e4d, 9998c3c, e4365d5: all verified in git log

---
*Phase: 03-event-scheduling*
*Completed: 2026-03-19*
