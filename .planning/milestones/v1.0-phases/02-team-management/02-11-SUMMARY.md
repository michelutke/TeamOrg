---
phase: 02-team-management
plan: 11
subsystem: testing
tags: [testcontainers, ktor-test, integration-tests, postgresql, jwt, kotlin-test]

# Dependency graph
requires:
  - phase: 02-team-management
    provides: clubs, teams, invites, roles, sub-groups, /me/roles endpoint

provides:
  - 11 full-stack flow tests for complete team management lifecycle
  - 4 role detection tests (isCoach/isClubManager logic via /me/roles)
  - SubGroupRoutes serialization bug fix

affects: [03-event-scheduling, regression-guard]

# Tech tracking
tech-stack:
  added: []
  patterns: [shared helper funs registerAndLogin/setupClubAndTeam/inviteAndJoin in test class, SubGroupResponse typed DTO replacing untyped mapOf]

key-files:
  created:
    - server/src/test/kotlin/ch/teamorg/flows/TeamManagementFlowTest.kt
    - server/src/test/kotlin/ch/teamorg/flows/RoleDetectionTest.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/routes/SubGroupRoutes.kt

key-decisions:
  - "SubGroupResponse data class added to SubGroupRoutes — mapOf with mixed String/Long types caused kotlinx.serialization crash"
  - "Player profile update (jerseyNumber/position) tested with CM token — endpoint requires coach/club_manager role, not player"
  - "Expired invite GET returns 200 (details only), 410 enforced on redeem — matches InviteRepositoryImpl behavior"
  - "player leaves team via DELETE /teams/{teamId}/leave (not /members/me) — matches TeamRoutes.kt leave endpoint"

patterns-established:
  - "Flow test helpers: registerAndLogin, setupClubAndTeam, inviteAndJoin as private suspend funs reduce boilerplate"

requirements-completed: [TM-02, TM-03, TM-04, TM-05, TM-06, TM-07, TM-08, TM-09, TM-10, TM-13, TM-15, TM-16, TM-18]

# Metrics
duration: 15min
completed: 2026-03-19
---

# Phase 02 Plan 11: Team Management Integration Tests Summary

**15 Testcontainers integration tests covering full team lifecycle — club setup, invite/redeem, role promotion/removal, sub-groups, /me/roles detection**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-19T13:18:00Z
- **Completed:** 2026-03-19T13:26:20Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- 11 flow tests in TeamManagementFlowTest covering all team management scenarios (club setup, player/coach invite, promote, remove, leave, edit, profile, sub-group, expired invite, idempotent redeem)
- 4 role detection tests in RoleDetectionTest covering GET /me/roles for CM, plain player, promoted player, and removed member
- Fixed SubGroupRoutes serialization crash (mixed-type mapOf → SubGroupResponse DTO)

## Task Commits

1. **Task 11-01: TeamManagementFlowTest** - `60336a5` (test)
2. **Task 11-02: RoleDetectionTest** - `bff414d` (test)

## Files Created/Modified
- `server/src/test/kotlin/ch/teamorg/flows/TeamManagementFlowTest.kt` - 11 full-stack flow scenarios against real PostgreSQL via Testcontainers
- `server/src/test/kotlin/ch/teamorg/flows/RoleDetectionTest.kt` - 4 /me/roles detection scenarios
- `server/src/main/kotlin/ch/teamorg/routes/SubGroupRoutes.kt` - Added SubGroupResponse typed DTO; fixed serialization crash

## Decisions Made
- Player profile update tested with CM token (not player token) — `PATCH /members/{userId}/profile` requires coach/club_manager role per existing implementation
- `GET /invites/{token}` returns 200 even for expired invites — expiry enforcement happens at redeem only (matches InviteRepositoryImpl)
- Player self-removal uses `DELETE /teams/{teamId}/leave` endpoint, not `DELETE /members/{userId}` (which is CM-only)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed SubGroupRoutes POST serialization crash**
- **Found during:** Task 11-01 (coach creates sub-group scenario)
- **Issue:** `mapOf("id" to id.toString(), ..., "memberCount" to 0L)` — mixed String+Long types in Map<String, Any> cause kotlinx.serialization to crash with "Serializing collections of different element types is not yet supported"
- **Fix:** Added `SubGroupResponse(@Serializable data class)` replacing both the POST and GET mapOf responses
- **Files modified:** server/src/main/kotlin/ch/teamorg/routes/SubGroupRoutes.kt
- **Verification:** All 11 TeamManagementFlowTest tests pass including sub-group scenario
- **Committed in:** 60336a5 (Task 11-01 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Required for correctness — sub-group endpoint was broken in production. No scope creep.

## Issues Encountered
None beyond the SubGroupRoutes serialization bug documented above.

## Next Phase Readiness
- All team management flows verified end-to-end against real PostgreSQL
- SubGroupRoutes now correctly serializes responses
- Phase 3 (Event Scheduling) tests can rely on team management flow helpers as patterns

---
*Phase: 02-team-management*
*Completed: 2026-03-19*
