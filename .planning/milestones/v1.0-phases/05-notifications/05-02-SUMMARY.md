---
phase: 05-notifications
plan: 02
subsystem: api
tags: [kotlin, ktor, notifications, reminders, koin, coroutines]

# Dependency graph
requires:
  - phase: 05-01
    provides: NotificationRepository, PushService, NotificationModels, DB tables

provides:
  - 8 REST endpoints for notification inbox, settings, reminder overrides
  - NotificationDispatcher helper (dedup + eligibility + push)
  - Event create/edit/cancel triggers push notifications + manages reminder rows
  - RSVP triggers per-response coach notification
  - Absence change triggers coach notification
  - ReminderSchedulerJob — polls every minute, fires due reminders + coach summaries (NO-06)
affects: [05-03, 05-04, client notification UI]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "call.application.launch(Dispatchers.IO) for fire-and-forget notifications from route handlers"
    - "NotificationDispatcher wraps dedup/eligibility/push for all team-level notifications"
    - "ReminderSchedulerJob follows EventMaterialisationJob coroutine pattern"

key-files:
  created:
    - server/src/main/kotlin/ch/teamorg/infra/NotificationDispatcher.kt
    - server/src/main/kotlin/ch/teamorg/routes/NotificationRoutes.kt
    - server/src/main/kotlin/ch/teamorg/infra/ReminderSchedulerJob.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/plugins/Routing.kt
    - server/src/main/kotlin/ch/teamorg/plugins/Koin.kt
    - server/src/main/kotlin/ch/teamorg/routes/EventRoutes.kt
    - server/src/main/kotlin/ch/teamorg/routes/AttendanceRoutes.kt
    - server/src/main/kotlin/ch/teamorg/routes/AbwesenheitRoutes.kt
    - server/src/main/kotlin/ch/teamorg/Application.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/NotificationRepository.kt

key-decisions:
  - "Used call.application.launch(Dispatchers.IO) for non-blocking notification dispatch from route handlers — avoids deprecated top-level launch()"
  - "Added reminder row management methods to NotificationRepository interface — getDueReminders, insertReminderRows, deleteReminderRowsForEvent, getCoachIdsForTeam, getEventAttendanceSummary, getUpcomingEventsForCoachSummary"
  - "getUserTeamRoles() used in AbwesenheitRoutes to find which teams to notify coaches in — reuses existing TeamRepository method"
  - "Coach summary idempotency key coach_summary:{coachId}:{eventId} ensures exactly one summary per coach per event even with 1-min polling"

patterns-established:
  - "Fire-and-forget: call.application.launch(Dispatchers.IO) { ... } from route handlers"
  - "NotificationDispatcher for all team-member notification paths (eligibility + dedup built-in)"
  - "Scheduler job pattern: delay(1.minutes) loop in Application.launch(IO)"

requirements-completed: [NO-01, NO-02, NO-03, NO-04, NO-05, NO-06, NO-07, NO-08, NO-09, NO-11, NO-12]

# Metrics
duration: 8min
completed: 2026-03-26
---

# Phase 05 Plan 02: Server Notification Routes + Triggers Summary

**8 REST notification endpoints, NotificationDispatcher, event/RSVP/absence triggers, and ReminderSchedulerJob with coach summary mode (NO-06)**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-03-26T08:17:27Z
- **Completed:** 2026-03-26T08:31:38Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments

- Created NotificationDispatcher with dedup + eligibility check + push delivery for team-wide notifications
- Created 8 REST endpoints covering the full notification inbox (read, mark-read, settings, reminder overrides)
- Wired event_new, event_edit, event_cancel triggers into EventRoutes with reminder row management
- RSVP handler notifies per_response coaches immediately; summary coaches deferred to scheduler
- AbwesenheitRoutes notifies team coaches on absence create/update
- ReminderSchedulerJob polls every minute firing due reminders and coach pre-event summaries (NO-06)

## Task Commits

1. **Task 1: NotificationDispatcher + NotificationRoutes + Routing wiring** - `0bb39e9` (feat)
2. **Task 2: Notification triggers + ReminderSchedulerJob** - `1118a58` (feat)

## Files Created/Modified

- `server/src/main/kotlin/ch/teamorg/infra/NotificationDispatcher.kt` — dedup/eligibility/push helper for team notifications
- `server/src/main/kotlin/ch/teamorg/routes/NotificationRoutes.kt` — 8 REST endpoints for inbox, settings, reminders
- `server/src/main/kotlin/ch/teamorg/infra/ReminderSchedulerJob.kt` — 1-min polling job, fireDueReminders + fireCoachSummaries
- `server/src/main/kotlin/ch/teamorg/plugins/Routing.kt` — added notificationRoutes()
- `server/src/main/kotlin/ch/teamorg/plugins/Koin.kt` — registered NotificationDispatcher
- `server/src/main/kotlin/ch/teamorg/routes/EventRoutes.kt` — event_new/edit/cancel triggers + reminder row management
- `server/src/main/kotlin/ch/teamorg/routes/AttendanceRoutes.kt` — per_response coach RSVP notification
- `server/src/main/kotlin/ch/teamorg/routes/AbwesenheitRoutes.kt` — absence coach notification
- `server/src/main/kotlin/ch/teamorg/Application.kt` — startReminderSchedulerJob() wired
- `server/src/main/kotlin/ch/teamorg/domain/repositories/NotificationRepository.kt` — reminder row + coach summary query methods added

## Decisions Made

- `call.application.launch(Dispatchers.IO)` used in route handlers instead of top-level `launch()` — the global `launch` without scope is deprecated in Ktor; `call.application` provides the Application's CoroutineScope.
- Added reminder management methods directly to `NotificationRepository` interface/impl rather than a separate repo — keeps notification infrastructure cohesive.
- `getUserTeamRoles()` from `TeamRepository` reused in AbwesenheitRoutes to find teams for absence notifications — avoids new query.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added reminder row management methods to NotificationRepository**
- **Found during:** Task 2 (Notification triggers + ReminderSchedulerJob)
- **Issue:** Plan required inserting/deleting/querying notification_reminders rows from both EventRoutes and ReminderSchedulerJob, but NotificationRepository interface had no methods for these operations
- **Fix:** Added insertReminderRows, deleteReminderRowsForEvent, getDueReminders, markReminderSent, getCoachIdsForTeam, getEventAttendanceSummary, getUpcomingEventsForCoachSummary to the interface and impl
- **Files modified:** NotificationRepository.kt
- **Verification:** compileKotlin succeeds, all callers typecheck
- **Committed in:** 1118a58 (Task 2 commit)

**2. [Rule 1 - Bug] Fixed deprecated top-level launch() in route handlers**
- **Found during:** Task 2 verification (compileKotlin)
- **Issue:** `launch(Dispatchers.IO)` in route handler context fails — deprecated, requires CoroutineScope
- **Fix:** Changed all 5 occurrences to `call.application.launch(Dispatchers.IO)` to use Application's scope
- **Files modified:** EventRoutes.kt, AttendanceRoutes.kt, AbwesenheitRoutes.kt
- **Verification:** compileKotlin succeeds
- **Committed in:** 1118a58 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 bug fix)
**Impact on plan:** Both required for compilation and correct operation. No scope creep.

## Issues Encountered

None beyond the compile errors fixed above.

## Next Phase Readiness

- All 11 notification requirements (NO-01 through NO-12 minus NO-10) implemented server-side
- Ready for 05-03 (KMP shared notification models + client repository) and 05-04 (push SDK integration)

## Self-Check: PASSED

All created files verified present. All task commits verified in git log.

---
*Phase: 05-notifications*
*Completed: 2026-03-26*
