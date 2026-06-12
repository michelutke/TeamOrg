---
phase: 04-attendance-tracking
plan: "07"
subsystem: attendance
tags: [wiring, testing, coach-override, stats, integration-tests]
dependency_graph:
  requires: [04-05, 04-06]
  provides: [coach-override-e2e, attendance-test-coverage, stats-test-coverage]
  affects: [EventDetailScreen, CheckInRoutes, AttendanceStatsCalculator]
tech_stack:
  added: []
  patterns: [ModalBottomSheet state wiring, IntegrationTestBase pattern, kotlin.test assertions]
key_files:
  created:
    - server/src/test/kotlin/ch/teamorg/routes/AttendanceRoutesTest.kt
    - server/src/test/kotlin/ch/teamorg/routes/AbwesenheitRoutesTest.kt
    - shared/src/commonTest/kotlin/ch/teamorg/data/AttendanceStatsCalculatorTest.kt
  modified:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventDetailScreen.kt
    - server/src/main/kotlin/ch/teamorg/routes/CheckInRoutes.kt
decisions:
  - "Coach role enforcement added to PUT /events/{id}/check-in/{userId}: uses getUserTeamRoles + getUserClubRoles to check coach or club_manager"
  - "onOverrideTap now opens CoachOverrideSheet (state-driven) instead of directly calling submitOverride"
metrics:
  duration_minutes: 7
  completed_date: "2026-03-24"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 5
---

# Phase 4 Plan 7: End-to-End Wiring + Automated Tests Summary

**One-liner:** CoachOverrideSheet wired end-to-end in EventDetail via state pattern; attendance + abwesenheit integration tests and stats unit tests added; coach role enforcement added to check-in route.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | End-to-end wiring — CoachOverrideSheet in EventDetail | 60bc2fb | EventDetailScreen.kt |
| 2 | Automated tests — server integration + stats unit tests | fb88fd3 | AttendanceRoutesTest.kt, AbwesenheitRoutesTest.kt, AttendanceStatsCalculatorTest.kt, CheckInRoutes.kt |

## What Was Built

### Task 1: CoachOverrideSheet Wiring

`EventDetailScreen` now manages two state variables: `showOverrideSheet` and `overrideTarget: CheckInEntry?`. When a coach taps override on a member row, `onOverrideTap` sets these variables and opens `CoachOverrideSheet`. The sheet's `onSave` callback calls `viewModel.submitOverride(userId, status, note)` with note support (previously wired without note).

### Task 2: Test Coverage

**AttendanceRoutesTest** (7 tests):
- submit confirmed → 200
- submit unsure without reason → 400
- submit unsure with reason → 200
- submit after past deadline → 409
- get event attendance with 2 responses → list of 2
- coach overrides player → 200 with present status
- player (no coach role) overrides → 403
- double override captures previousStatus audit trail

**AbwesenheitRoutesTest** (6 tests):
- create recurring rule → 201
- create period rule → 201
- list returns own rules only (isolation)
- update rule label → 200
- delete rule → 204, verified gone
- backfill-status returns pending/done/failed after creation

**AttendanceStatsCalculatorTest** (5 tests):
- empty input → zero stats
- all confirmed → 100% presence
- 2 confirmed / 2 declined → 50% across types
- training confirmed / match declined → correct per-type split
- no training events → trainingPresencePct = 0

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Auth] Added coach role enforcement to CheckInRoutes**
- **Found during:** Task 2 (when writing the non-coach 403 test, the route had no role check)
- **Issue:** `PUT /events/{id}/check-in/{userId}` accepted any authenticated user
- **Fix:** Added `getUserTeamRoles` + `getUserClubRoles` check; returns 403 if caller has neither coach nor club_manager role
- **Files modified:** `server/src/main/kotlin/ch/teamorg/routes/CheckInRoutes.kt`
- **Commit:** fb88fd3

## Verification

- `./gradlew :composeApp:compileDebugKotlinAndroid` — PASSED (warnings only, no errors)
- `./gradlew :shared:jvmTest --tests "ch.teamorg.data.AttendanceStatsCalculatorTest"` — PASSED (5 tests)
- `./gradlew :server:compileTestKotlin` — PASSED (integration tests compile)

## Self-Check: PASSED

All created files verified present. Both task commits (60bc2fb, fb88fd3) confirmed in git log.
