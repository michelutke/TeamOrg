---
phase: 02-team-management
plan: 08
subsystem: api
tags: [ktor, kmp, kotlin, role-management, team-roster, viewmodel]

requires:
  - phase: 02-team-management
    provides: Club/Team DB tables, TeamRepository interface, TeamRolesTable, ClubRolesTable

provides:
  - PATCH /teams/{teamId}/members/{userId}/role — promotes player to coach (club_manager only)
  - DELETE /teams/{teamId}/members/{userId} — removes member from team (club_manager only)
  - GET /auth/me/roles — returns all club + team roles for current user
  - UserRoles/ClubRoleEntry/TeamRoleEntry domain models in shared KMP
  - getMyRoles() + updateMemberRole() on shared TeamRepository
  - isCoach detection via getMyRoles() in EventListVM, EventDetailVM, CalendarVM

affects: [03-event-scheduling, plan-09-role-management-ui, plan-10-remove-coach-ui]

tech-stack:
  added: []
  patterns:
    - "getMyRoles() as single source of truth — avoids N roster fetches, covers club_manager via clubRoles"
    - "Server injects TeamRepository in AuthRoutes via Koin for /me/roles"

key-files:
  created: []
  modified:
    - server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepository.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepositoryImpl.kt
    - server/src/main/kotlin/ch/teamorg/routes/TeamRoutes.kt
    - server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt
    - shared/src/commonMain/kotlin/ch/teamorg/domain/Club.kt
    - shared/src/commonMain/kotlin/ch/teamorg/repository/TeamRepository.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/repository/TeamRepositoryImpl.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventListViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventDetailViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/calendar/CalendarViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt

key-decisions:
  - "getMyRoles() replaces roster-scan approach — one API call instead of N roster fetches, correctly includes club_manager role"
  - "Op.build{} used in Exposed deleteWhere for compound conditions — required for correct lambda scope"
  - "CalendarViewModel gets teamRepository + userPreferences injected; UiModule factory updated to get(), get(), get()"

requirements-completed: [TM-04, TM-05, TM-07]

duration: 25min
completed: 2026-03-19
---

# Phase 2 Plan 8: Role Management Backend + isCoach Fix Summary

**PATCH/DELETE member role endpoints + GET /me/roles, replacing N-roster-scan with single getMyRoles() call that detects club_manager across EventListVM, EventDetailVM, CalendarVM**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-03-19
- **Completed:** 2026-03-19
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments
- Added `updateMemberRole`, `removeMember`, `getUserClubRoles`, `getUserTeamRoles` to server `TeamRepository` + impl
- Added `PATCH /teams/{teamId}/members/{userId}/role` and `DELETE /teams/{teamId}/members/{userId}` guarded by `club_manager` role
- Added `GET /auth/me/roles` endpoint returning all club and team roles for authenticated user
- Added `UserRoles`, `ClubRoleEntry`, `TeamRoleEntry` domain models to shared KMP `Club.kt`
- Added `getMyRoles()` and `updateMemberRole()` to shared `TeamRepository` interface + `TeamRepositoryImpl`
- Fixed isCoach detection in all 3 ViewModels: single `getMyRoles()` call checks both `coach` (team) and `club_manager` (club)
- Injected `teamRepository` + `userPreferences` into `CalendarViewModel`, updated Koin factory

## Task Commits

1. **Task 1: Backend role management endpoints + /me/roles** - `40cd6c1` (feat)
2. **Task 2: Shared repo + client isCoach fix** - `b0ac3ca` (feat)

## Files Created/Modified
- `server/.../TeamRepository.kt` — +4 methods: updateMemberRole, removeMember, getUserClubRoles, getUserTeamRoles
- `server/.../TeamRepositoryImpl.kt` — implementations of 4 new methods
- `server/.../TeamRoutes.kt` — PATCH + DELETE members endpoints, UpdateRoleRequest
- `server/.../AuthRoutes.kt` — GET /me/roles, UserRolesResponse/ClubRoleEntry/TeamRoleEntry, TeamRepository injection
- `shared/.../Club.kt` — UserRoles, ClubRoleEntry, TeamRoleEntry data classes
- `shared/.../repository/TeamRepository.kt` — getMyRoles(), updateMemberRole() interface methods
- `shared/.../data/repository/TeamRepositoryImpl.kt` — getMyRoles() + updateMemberRole() implementations
- `composeApp/.../EventListViewModel.kt` — replaced loadUserRole with checkCoachRole()
- `composeApp/.../EventDetailViewModel.kt` — replaced loadCoachRole with checkCoachRole()
- `composeApp/.../CalendarViewModel.kt` — added teamRepository + userPreferences, checkCoachRole()
- `composeApp/.../UiModule.kt` — CalendarViewModel factory: get() -> get(), get(), get()

## Decisions Made
- `getMyRoles()` replaces roster-scan: one API call, correct club_manager detection, no N+1 roster fetches
- `Op.build{}` wrapper in Exposed `deleteWhere` lambda — required for compound conditions without explicit import conflicts
- `CalendarViewModel` receives `userPreferences` in constructor (consistent with EventList/EventDetailViewModel pattern) even though only `teamRepository` is used by `checkCoachRole()` directly

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Exposed deleteWhere compound condition import**
- **Found during:** Task 1 (TeamRepositoryImpl.removeMember)
- **Issue:** `(table.col eq val) and (table.col2 eq val2)` inside `deleteWhere` gave "Unresolved reference: eq" because `SqlExpressionBuilder.and` doesn't exist
- **Fix:** Wrapped condition in `Op.build { ... }` which provides the correct `SqlExpressionBuilder` receiver scope
- **Files modified:** server/.../TeamRepositoryImpl.kt
- **Verification:** `:server:compileKotlin` passes
- **Committed in:** 40cd6c1 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Necessary compiler fix, no scope change.

## Issues Encountered
- `:shared:compileKotlinJvm` fails with pre-existing `DatabaseDriverFactory` expect/actual error (no JVM actual). Not caused by this plan — used `:shared:compileDebugKotlinAndroid` instead per KMP Android target.

## Next Phase Readiness
- Backend endpoints ready for Plan 09 (promote player UI) and Plan 10 (remove coach UI)
- All 3 ViewModels now correctly show FAB to ClubManagers
- No blockers

---
*Phase: 02-team-management*
*Completed: 2026-03-19*
