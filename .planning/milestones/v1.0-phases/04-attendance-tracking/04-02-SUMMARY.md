---
phase: 04-attendance-tracking
plan: 02
subsystem: api
tags: [ktor, routing, attendance, abwesenheit, check-in, background-jobs, kotlin]

requires:
  - phase: 04-attendance-tracking
    plan: 01
    provides: AttendanceRepository, AbwesenheitRepository, DB tables

provides:
  - GET/PUT /events/{id}/attendance and /attendance/me with 409 deadline enforcement
  - GET /users/{userId}/attendance and GET /teams/{teamId}/attendance raw data endpoints
  - Full CRUD /users/me/abwesenheit with backfill trigger
  - GET /users/me/abwesenheit/backfill-status
  - GET/PUT /events/{id}/check-in with audit trail (previous_status)
  - AbwesenheitBackfillJob: background auto-decline generation per rule
  - AutoPresentJob: post-event auto-present for check_in_enabled=false events

affects:
  - 04-03-shared-contracts (KMP side calls these endpoints)
  - 04-04-attendance-ui
  - 04-05-abwesenheit-ui
  - 04-06-checkin-ui

tech-stack:
  added: []
  patterns:
    - "Route-local @Serializable DTOs keep domain models free of serialization annotations"
    - "AbwesenheitBackfillJob uses ConcurrentHashMap per-userId status for non-blocking polling"
    - "AutoPresentJob checks alreadyRecorded before insert (no ON CONFLICT needed in Exposed)"
    - "backfill-status returns status.name.lowercase() for pending/done/failed strings"

key-files:
  created:
    - server/src/main/kotlin/ch/teamorg/routes/AttendanceRoutes.kt
    - server/src/main/kotlin/ch/teamorg/routes/AbwesenheitRoutes.kt
    - server/src/main/kotlin/ch/teamorg/routes/CheckInRoutes.kt
    - server/src/main/kotlin/ch/teamorg/infra/AbwesenheitBackfillJob.kt
    - server/src/main/kotlin/ch/teamorg/infra/AutoPresentJob.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/plugins/Routing.kt
    - server/src/main/kotlin/ch/teamorg/plugins/Koin.kt
    - server/src/main/kotlin/ch/teamorg/Application.kt

key-decisions:
  - "AbwesenheitBackfillJob.enqueue() is non-suspend — takes Application scope, launches coroutine internally; routes call it without suspend penalty"
  - "AutoPresentJob reads existing records before insert rather than using DB ON CONFLICT — avoids overwriting coach-set records"
  - "SYSTEM_UUID = UUID(0L, 0L) used as set_by for auto-present records — avoids nullable FK column"

requirements-completed: [AT-01, AT-02, AT-09, AT-10, AT-11, AT-12, AT-13, AT-15, AT-16]

duration: 3min
completed: 2026-03-24
---

# Phase 4 Plan 2: Attendance API Routes + Background Jobs Summary

**3 Ktor route files (10 endpoints) + 2 background jobs wired into application lifecycle, compiling cleanly**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-03-24T14:51:10Z
- **Completed:** 2026-03-24T14:53:37Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- AttendanceRoutes: 5 endpoints — GET/PUT /events/{id}/attendance(/me) with 409 deadline + 400 unsure-without-reason, plus raw attendance endpoints for user and team
- AbwesenheitRoutes: 5 endpoints — GET/POST/PUT/DELETE /users/me/abwesenheit + backfill-status; enqueues backfill job on create/update
- CheckInRoutes: 2 endpoints — GET/PUT /events/{id}/check-in, PUT captures previous_status for audit trail (AT-16)
- AbwesenheitBackfillJob: enqueue() launches coroutine, runBackfill() matches recurring/period rules against future events, skips manual_override entries
- AutoPresentJob: 15-min poll, inserts present records for confirmed responses on past events (check_in_enabled=false), skips coach overrides
- Koin singleton for AbwesenheitBackfillJob; Application.kt calls startAutoPresentJob()
- `./gradlew :server:compileKotlin` BUILD SUCCESSFUL

## Task Commits

1. **Task 1: Attendance + Abwesenheit + CheckIn routes** — `1e18e37` (feat)
2. **Task 2: Background jobs — Abwesenheit backfill + auto-present** — `7ccb094` (feat)

## Files Created/Modified

- `AttendanceRoutes.kt` — GET/PUT /events/{id}/attendance, GET /events/{id}/attendance/me, GET /users/{userId}/attendance, GET /teams/{teamId}/attendance
- `AbwesenheitRoutes.kt` — full CRUD + backfill-status, route-local DTOs, backfill enqueue on write
- `CheckInRoutes.kt` — GET/PUT /events/{id}/check-in with coachId from JWT
- `AbwesenheitBackfillJob.kt` — ConcurrentHashMap status, coroutine-based runBackfill, recurring + period rule matching
- `AutoPresentJob.kt` — 15-min polling job, auto-present for check_in_enabled=false events
- `Routing.kt` — attendanceRoutes(), abwesenheitRoutes(), checkInRoutes() added
- `Koin.kt` — single { AbwesenheitBackfillJob() } added
- `Application.kt` — startAutoPresentJob() called after startMaterialisationJob()

## Decisions Made

- `AbwesenheitBackfillJob.enqueue()` is non-suspend — takes Application scope and launches internally; avoids suspend complexity in route handlers
- AutoPresentJob checks for existing records before inserting (SELECT count > 0) rather than relying on DB-level ON CONFLICT — simpler in Exposed, preserves coach records
- `SYSTEM_UUID = UUID(0L, 0L)` as sentinel for auto-present `set_by` — avoids making the FK nullable

## Deviations from Plan

None — plan executed exactly as written. All files match the specified interfaces and contracts.

## Issues Encountered

None. Server compiled first attempt.

## Self-Check: PASSED

All 5 created files verified on disk. Both task commits (1e18e37, 7ccb094) confirmed in git log.

## Next Phase Readiness

- All attendance API endpoints available for KMP shared contracts (Plan 04-03)
- Background jobs running in application lifecycle
- Backfill status polling endpoint ready for client integration
