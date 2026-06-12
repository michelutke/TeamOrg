---
phase: 04-attendance-tracking
plan: "03"
subsystem: shared-kmp
tags: [domain-models, repository-interfaces, sqldelight, offline-cache, attendance, abwesenheit]
dependency_graph:
  requires: []
  provides:
    - AttendanceRepository interface
    - AbwesenheitRepository interface
    - Attendance domain models
    - Abwesenheit domain models
    - SQLDelight offline cache schema
  affects:
    - Plan 04 (AttendanceRepositoryImpl)
    - Plan 05 (Player attendance UI)
    - Plan 06 (Coach check-in UI)
    - Plan 07 (Stats UI)
tech_stack:
  added: []
  patterns:
    - "@Serializable data class with String IDs and Instant timestamps"
    - "interface returning Result<T> for all operations"
    - "SQLDelight INSERT OR REPLACE for upsert pattern"
    - "pending_mutation table for offline queue"
key_files:
  created:
    - shared/src/commonMain/kotlin/ch/teamorg/domain/Attendance.kt
    - shared/src/commonMain/kotlin/ch/teamorg/domain/Abwesenheit.kt
    - shared/src/commonMain/kotlin/ch/teamorg/repository/AttendanceRepository.kt
    - shared/src/commonMain/kotlin/ch/teamorg/repository/AbwesenheitRepository.kt
    - shared/src/commonMain/sqldelight/ch/teamorg/Attendance.sq
  modified: []
decisions:
  - "AttendanceStats is not @Serializable — client-side computed only (ADR-007: no server stats endpoint)"
  - "AbwesenheitRule uses String for date fields (ISO date string) to avoid kotlinx-datetime LocalDate serialization complexity"
  - "pending_mutation table uses AUTOINCREMENT id for stable ordering and deletion"
metrics:
  duration: "93s"
  completed_date: "2026-03-24"
  tasks_completed: 2
  files_created: 5
  files_modified: 0
---

# Phase 4 Plan 3: Shared KMP Contracts — Domain Models, Repository Interfaces, SQLDelight Schema

**One-liner:** KMP shared contracts for attendance tracking — serializable domain models, Result<T> repository interfaces, and 3-table SQLDelight offline cache with mutation queue.

## Tasks Completed

| Task | Name | Commit | Files |
|---|---|---|---|
| 1 | Shared domain models — Attendance + Abwesenheit + Stats | 808dda1 | Attendance.kt, Abwesenheit.kt |
| 2 | Repository interfaces + SQLDelight offline schema | bf81a34 | AttendanceRepository.kt, AbwesenheitRepository.kt, Attendance.sq |

## What Was Built

### Domain Models (Attendance.kt)
- `AttendanceResponse` — player RSVP with status, reason, abwesenheitRuleId, manualOverride
- `AttendanceRecord` — coach check-in record with previousStatus audit trail
- `CheckInEntry` — combined view for coach screen (response + record per user)
- `SubmitResponseRequest`, `SubmitCheckInRequest` — request DTOs
- `BackfillStatus` — abwesenheit backfill job status
- `AttendanceStats` — client-side computed stats (not @Serializable, no server endpoint)

### Domain Models (Abwesenheit.kt)
- `AbwesenheitRule` — recurring or period absence rule with weekdays/date range
- `CreateAbwesenheitRequest`, `UpdateAbwesenheitRequest` — CRUD DTOs

### Repository Interfaces
- `AttendanceRepository` — 7 suspend methods covering player responses, coach check-in, raw data for stats
- `AbwesenheitRepository` — 5 suspend methods covering CRUD + backfill status

### SQLDelight Schema (Attendance.sq)
- `cached_attendance_response` — composite PK (event_id, user_id), epoch millis timestamps
- `cached_abwesenheit_rule` — TEXT dates for ISO date strings
- `pending_mutation` — offline queue with AUTOINCREMENT, retry_count, endpoint+method+body
- Named queries: upsertResponse, getEventResponses, getMyResponse, upsertRule, getAllRules, enqueueMutation, getPendingMutations, deleteMutation, incrementRetryCount

## Verification

- `./gradlew :shared:generateSqlDelightInterface` — PASSED
- `./gradlew :shared:compileKotlinJvm` — PASSED (warnings in pre-existing EventRepositoryImpl.kt only)

## Self-Check: PASSED

All 5 files verified on disk. Both commits (808dda1, bf81a34) confirmed in git log.

## Deviations from Plan

None — plan executed exactly as written.
