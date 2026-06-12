---
phase: 04-attendance-tracking
plan: 01
subsystem: database
tags: [postgres, flyway, exposed, koin, kotlin, attendance]

requires:
  - phase: 03-event-scheduling
    provides: EventsTable, events DB table, EventRepository pattern

provides:
  - V8 Flyway migration: attendance_responses, attendance_records, abwesenheit_rules tables + events columns + team_roles FK fix
  - AttendanceTables.kt: AttendanceResponsesTable, AttendanceRecordsTable, AbwesenheitRulesTable Exposed objects
  - AttendanceRepository interface + AttendanceRepositoryImpl (CRUD + deadline check + bulk auto-decline)
  - AbwesenheitRepository interface + AbwesenheitRepositoryImpl (CRUD)
  - Koin wiring for both attendance repositories

affects:
  - 04-02-attendance-api
  - 04-03-shared-contracts
  - 04-04-attendance-ui
  - 04-05-abwesenheit-ui
  - 04-06-checkin-ui
  - 04-07-stats-ui

tech-stack:
  added: []
  patterns:
    - "Exposed plain text column for status values with hyphens (declined-auto, no-response)"
    - "upsert() with explicit keys array for composite-PK tables"
    - "Pre-capture previous_status/previous_set_by before upsert for audit trail"

key-files:
  created:
    - server/src/main/resources/db/migrations/V8__create_attendance.sql
    - server/src/main/kotlin/ch/teamorg/db/tables/AttendanceTables.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepository.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepositoryImpl.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/AbwesenheitRepository.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/AbwesenheitRepositoryImpl.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/db/tables/EventsTable.kt
    - server/src/main/kotlin/ch/teamorg/plugins/Koin.kt

key-decisions:
  - "Used plain text() column for attendance_responses.status — declined-auto and no-response contain hyphens which are invalid Kotlin enum identifiers; enumerationByName not usable"
  - "team_roles.user_id FK changed from CASCADE to SET NULL (TM-19) — user_id column made nullable to preserve historical attendance data on member removal"
  - "abwesenheit_rule_id FK added as separate ALTER after CREATE TABLE abwesenheit_rules to avoid forward-reference in SQL"
  - "Exposed array column accepts List<Short>? directly — toTypedArray() causes type mismatch, pass list as-is"

requirements-completed: [AT-01, AT-02, AT-09, AT-15, AT-16, TM-19]

duration: 8min
completed: 2026-03-24
---

# Phase 4 Plan 1: Attendance Tracking DB Foundation Summary

**V8 Flyway migration with 3 attendance tables + Exposed ORM objects + AttendanceRepository and AbwesenheitRepository (interface + impl) wired in Koin**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-03-24T14:47:00Z
- **Completed:** 2026-03-24T14:48:56Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- V8 migration: attendance_responses, attendance_records, abwesenheit_rules tables with correct PKs, FKs, CHECKs
- Events table extended with response_deadline + check_in_enabled columns
- team_roles.user_id FK fixed: CASCADE -> SET NULL (TM-19) to preserve historical data
- All 3 Exposed table objects, 2 repository interfaces, 2 repository implementations compiling cleanly
- Koin wired; server compiles with `./gradlew :server:compileKotlin`

## Task Commits

1. **Task 1: V8 Flyway migration** - `5871833` (feat)
2. **Task 2: Exposed ORM + repositories + Koin** - `e64e6a2` (feat)

## Files Created/Modified

- `server/src/main/resources/db/migrations/V8__create_attendance.sql` - 3 new tables, 2 ALTER, 4 indexes, FK fix
- `server/src/main/kotlin/ch/teamorg/db/tables/AttendanceTables.kt` - 3 Exposed table objects + RecordStatus/PresetType/RuleType enums
- `server/src/main/kotlin/ch/teamorg/db/tables/EventsTable.kt` - added responseDeadline + checkInEnabled
- `server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepository.kt` - interface + AttendanceResponseRow, CheckInRow, RawAttendanceRow data classes
- `server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepositoryImpl.kt` - full CRUD, deadline check, bulk auto-decline
- `server/src/main/kotlin/ch/teamorg/domain/repositories/AbwesenheitRepository.kt` - interface + data classes
- `server/src/main/kotlin/ch/teamorg/domain/repositories/AbwesenheitRepositoryImpl.kt` - full CRUD
- `server/src/main/kotlin/ch/teamorg/plugins/Koin.kt` - attendance + abwesenheit repository bindings

## Decisions Made

- Used `text("status")` (plain text) instead of `enumerationByName` for attendance_responses.status — `declined-auto` and `no-response` have hyphens that are invalid Kotlin identifiers
- team_roles.user_id changed from NOT NULL + CASCADE to nullable + SET NULL for TM-19 compliance
- Exposed `array<Short>` column takes `List<Short>?` directly; `toTypedArray()` causes a type mismatch

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Exposed array column type mismatch**
- **Found during:** Task 2 (compile verification)
- **Issue:** `rule.weekdays?.toTypedArray()` caused type mismatch — Exposed array column expects `List<Short>?` not `Array<Short>?`
- **Fix:** Pass `rule.weekdays` directly (already `List<Short>?`)
- **Files modified:** `AbwesenheitRepositoryImpl.kt`
- **Verification:** `./gradlew :server:compileKotlin` BUILD SUCCESSFUL
- **Committed in:** e64e6a2 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug)
**Impact on plan:** Necessary correctness fix; no scope changes.

## Issues Encountered

None beyond the array column type fix above.

## Next Phase Readiness

- V8 migration ready for Flyway apply on next server boot
- AttendanceRepository + AbwesenheitRepository ready for API layer (Plan 04-02)
- Shared KMP contracts (Plan 04-03) can now define domain models matching the DB schema

---
*Phase: 04-attendance-tracking*
*Completed: 2026-03-24*
