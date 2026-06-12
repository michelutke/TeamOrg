---
phase: 05-notifications
plan: 06
subsystem: server/testing
tags: [notifications, integration-tests, unit-tests, push-service, onesignal]
dependency_graph:
  requires: [05-01, 05-02]
  provides: [test-coverage-NO-01-to-NO-12]
  affects: []
tech_stack:
  added: [ktor-client-mock]
  patterns: [IntegrationTestBase, MockEngine, fireCoachSummaries internal]
key_files:
  created:
    - server/src/test/kotlin/ch/teamorg/routes/NotificationRoutesTest.kt
    - server/src/test/kotlin/ch/teamorg/infra/PushServiceTest.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/infra/PushService.kt
    - server/src/main/kotlin/ch/teamorg/infra/ReminderSchedulerJob.kt
    - server/src/main/kotlin/ch/teamorg/routes/AbwesenheitRoutes.kt
    - server/src/main/kotlin/ch/teamorg/routes/AttendanceRoutes.kt
    - server/src/main/kotlin/ch/teamorg/routes/EventRoutes.kt
    - gradle/libs.versions.toml
    - server/build.gradle.kts
decisions:
  - "fireCoachSummaries made internal (not private) to allow direct invocation in integration test"
  - "addCoachToTeam helper: coach must redeem a coach-role invite to appear in team_roles for getCoachIdsForTeam()"
  - "delay(300ms) used after async notification launches to allow Dispatchers.IO coroutines to complete before assertion"
  - "try-catch added to all background notification coroutines to prevent ClosedScopeException leaking across test boundaries"
metrics:
  duration: "~35 minutes"
  completed: "2026-03-26"
  tasks_completed: 2
  files_created: 2
  files_modified: 7
---

# Phase 05 Plan 06: Notification Tests Summary

Comprehensive integration and unit tests verifying all NO-01 through NO-12 notification requirements.

## What Was Built

**NotificationRoutesTest** (19 tests): Full integration coverage of the notification system using `withTeamorgTestApplication` + PostgreSQL test container.

**PushServiceTest** (3 tests): Unit tests for `PushServiceImpl` using Ktor `MockEngine` to verify OneSignal REST API call shape.

## Test Coverage

| Category | Tests | Requirements |
|---|---|---|
| Inbox CRUD | 5 | NO-11 |
| Settings (per-team) | 3 | NO-08 |
| Reminder overrides | 3 | NO-09 |
| Event triggers | 3 | NO-01, NO-03, NO-04 |
| Coach response modes | 2 | NO-05, NO-06 |
| Absence notifications | 1 | NO-07 |
| Dedup guard | 1 | NO-12 |
| Removed-member guard | 1 | NO-12 |
| PushService unit | 3 | (infrastructure) |

**Total: 22 tests, all green.**

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] PushServiceImpl used `mapOf` with mixed value types**
- Found during: Task 2 (PushServiceTest)
- Issue: `mapOf("include_aliases" to mapOf(...), "contents" to mapOf(...))` — mixing Map and String values fails kotlinx.serialization with "Serializing collections of different element types is not yet supported"
- Fix: Replaced with `buildJsonObject { putJsonObject(...) { ... } }` from `kotlinx.serialization.json`
- Files modified: `server/src/main/kotlin/ch/teamorg/infra/PushService.kt`
- Commit: a29a62e

**2. [Rule 1 - Bug] AbwesenheitRoutes destructure bug: clubId sent as teamId**
- Found during: Task 1 (`absence_notifiesCoach` test failure)
- Issue: `getUserTeamRoles()` returns `Triple(teamId, clubId, role)`. The destructure `for ((_, teamId, _) in teamRoles)` assigned `clubId` to `teamId`. `getTeamMemberIds(clubId)` returns empty, so no absence notifications were ever sent.
- Fix: Changed to `for (roleTriple in teamRoles)` and used `roleTriple.first` as teamId
- Files modified: `server/src/main/kotlin/ch/teamorg/routes/AbwesenheitRoutes.kt`
- Commit: e6cfa4f

**3. [Rule 2 - Missing error handling] Background coroutines had no try-catch**
- Found during: Task 1 (`inbox_markAllRead` ClosedScopeException)
- Issue: Background `launch(Dispatchers.IO)` coroutines in EventRoutes, AbwesenheitRoutes, AttendanceRoutes had no try-catch. When one test's Koin instance closed while a background coroutine was still running, the unhandled exception propagated to the next test.
- Fix: Wrapped all background coroutine bodies in `try-catch(Exception)` with warn logging
- Files modified: EventRoutes.kt, AbwesenheitRoutes.kt, AttendanceRoutes.kt
- Commit: e6cfa4f

**4. [Rule 3 - Blocking] `fireCoachSummaries` was private — tests can't call it**
- Found during: Task 1 (plan requires direct call from test)
- Fix: Changed from `private` to `internal` visibility in ReminderSchedulerJob.kt
- Files modified: `server/src/main/kotlin/ch/teamorg/infra/ReminderSchedulerJob.kt`
- Commit: e6cfa4f

**5. [Rule 3 - Blocking] `ktor-client-mock` not in build deps**
- Found during: Task 2
- Fix: Added `ktor-clientMock` to `gradle/libs.versions.toml` and `server/build.gradle.kts` testImplementation
- Commit: a29a62e

### Key Design Decisions During Implementation

- Coach must be added to `team_roles` as `coach` role (via invite+redeem) for `getCoachIdsForTeam()` to find them — club_manager role (from creating club) is in `club_roles`, not `team_roles`
- `delay(300ms)` after triggering actions accounts for `Dispatchers.IO` background coroutines completing before assertions
- `application.get<T>()` (Koin-Ktor 4.x extension) used inside `withTeamorgTestApplication` to get bean instances for direct `fireCoachSummaries` invocation

## Commits

| Task | Hash | Description |
|---|---|---|
| Task 1 | e6cfa4f | feat(05-06): NotificationRoutesTest — 19 integration tests |
| Task 2 | a29a62e | feat(05-06): PushServiceTest — 3 unit tests with MockEngine |

## Self-Check: PASSED

- NotificationRoutesTest.kt: FOUND
- PushServiceTest.kt: FOUND
- Commit e6cfa4f: FOUND
- Commit a29a62e: FOUND
- All 22 tests green (19 integration + 3 unit)
