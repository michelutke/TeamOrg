---
phase: 04-attendance-tracking
plan: "04"
subsystem: shared/data
tags: [kotlin, kmp, ktor, sqldelight, koin, offline, cache, repository]
dependency_graph:
  requires: ["04-02", "04-03"]
  provides: ["04-05", "04-06", "04-07"]
  affects: ["shared/data/repository", "shared/di"]
tech_stack:
  added: []
  patterns:
    - "cache-aside: save on HTTP success, return cached on IOException/timeout"
    - "optimistic offline: enqueue mutation + optimistic cache update on IOException"
    - "409 Conflict propagates to caller (deadline passed)"
key_files:
  created:
    - shared/src/commonMain/kotlin/ch/teamorg/data/AttendanceCacheManager.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/MutationQueueManager.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/repository/AttendanceRepositoryImpl.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/repository/AbwesenheitRepositoryImpl.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/AttendanceStatsCalculator.kt
  modified:
    - shared/src/androidMain/kotlin/ch/teamorg/di/SharedModule.android.kt
    - shared/src/iosMain/kotlin/ch/teamorg/di/SharedModule.ios.kt
decisions:
  - "AttendanceCacheManager.deleteRule() added (not in plan spec) — needed by AbwesenheitRepositoryImpl.deleteRule() to keep cache consistent"
  - "submitResponse offline branch uses empty userId string for optimistic response — caller must update when sync resolves"
  - "IOException typealias deprecation warnings left as-is — pre-existing pattern across EventRepositoryImpl, out of scope"
metrics:
  duration: "2m 26s"
  completed_date: "2026-03-24"
  tasks_completed: 2
  files_created: 5
  files_modified: 2
---

# Phase 04 Plan 04: KMP Repository Implementations Summary

**One-liner:** HTTP + SQLDelight offline-first repository impls for attendance and abwesenheit with Ktor mutation queue and client-side stats calculator.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | AttendanceCacheManager + MutationQueueManager | 83a1a92 | AttendanceCacheManager.kt, MutationQueueManager.kt |
| 2 | Repository impls + stats calculator + Koin DI | 06ab350 | AttendanceRepositoryImpl.kt, AbwesenheitRepositoryImpl.kt, AttendanceStatsCalculator.kt, SharedModule.android.kt, SharedModule.ios.kt |

## What Was Built

### AttendanceCacheManager
SQLDelight-backed cache for attendance responses and abwesenheit rules. Methods: `saveResponses`, `getCachedResponses`, `getCachedResponse` (single), `saveRules`, `getCachedRules`, `deleteRule`.

### MutationQueueManager
Offline mutation queue backed by `pending_mutation` SQLDelight table. `flushQueue()` processes all pending mutations in FIFO order — HTTP success removes mutation, 409 Conflict removes + returns `FlushResult.Conflict`, other errors increment retry count.

### AttendanceRepositoryImpl
Implements `AttendanceRepository` (7 methods). Cache-aside pattern for reads. `submitResponse` tries HTTP PUT; on IOException enqueues to `MutationQueueManager` and returns optimistic response; 409 propagates to caller.

### AbwesenheitRepositoryImpl
Implements `AbwesenheitRepository` (5 methods). `listRules` caches on success, falls back to cache on network error. Write operations (create/update/delete) fail fast offline — no queue for CRUD ops.

### AttendanceStatsCalculator
Pure `object` function. Computes `presencePct`, `trainingPresencePct`, `matchPresencePct` from raw `AttendanceResponse` list + eventId→type map (ADR-007).

### Koin DI
Both `SharedModule.android.kt` and `SharedModule.ios.kt` now register: `AttendanceCacheManager`, `MutationQueueManager`, `AttendanceRepositoryImpl bind AttendanceRepository`, `AbwesenheitRepositoryImpl bind AbwesenheitRepository`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing functionality] Added `deleteRule()` to AttendanceCacheManager**
- **Found during:** Task 2 (AbwesenheitRepositoryImpl needed it)
- **Issue:** Plan spec for AttendanceCacheManager omitted `deleteRule()` but the Abwesenheit delete flow requires removing a single rule from cache
- **Fix:** Added `deleteRule(ruleId: String)` calling `db.attendanceQueries.deleteRule(ruleId)`
- **Files modified:** AttendanceCacheManager.kt

None others — plan executed cleanly.

## Self-Check: PASSED

All 5 created files verified on disk. Both task commits (83a1a92, 06ab350) confirmed in git log.
