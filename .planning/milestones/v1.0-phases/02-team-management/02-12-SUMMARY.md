---
phase: 02-team-management
plan: 12
subsystem: ui
tags: [compose, kotlin, kmp, ktor, club-management]

requires:
  - phase: 02-team-management
    provides: TeamsListScreen + TeamsListViewModel + ClubRepository interface + impl

provides:
  - Club domain model with location field
  - ClubRepository.getClub(clubId) — GET /clubs/{clubId}
  - ClubRepository.updateClub(clubId, name, location) — PATCH /clubs/{clubId}
  - ClubEditSheet bottom sheet composable in TeamsListScreen
  - Edit club icon in TeamsListScreen TopAppBar (ClubManager only)
  - TeamsListViewModel with updateClub() + club state

affects:
  - Phase 3 event scheduling (Club domain model)

tech-stack:
  added: []
  patterns:
    - ModalBottomSheet for inline edit flows (reuses TeamEditSheet pattern)
    - ViewModel loads related entity (club) alongside list (teams) in single loadTeams() coroutine

key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/ch/teamorg/domain/Club.kt
    - shared/src/commonMain/kotlin/ch/teamorg/repository/ClubRepository.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/repository/ClubRepositoryImpl.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamsListScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamsListViewModel.kt

key-decisions:
  - "getClub() called alongside getClubTeams() in loadTeams() — no separate trigger needed"
  - "ClubEditSheet added as private composable in TeamsListScreen.kt — too small to warrant new file"

patterns-established:
  - "Load entity + list in same coroutine: call getClub then getClubTeams sequentially"

requirements-completed: [TM-01]

duration: 8min
completed: 2026-03-19
---

# Phase 02 Plan 12: Club Edit UI Summary

**ClubManager can edit club name and location from TeamsListScreen via ModalBottomSheet — pre-fills current values, saves via PATCH /clubs/{clubId}, updates TopAppBar title**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-03-19T09:00:00Z
- **Completed:** 2026-03-19T09:08:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Added `location: String?` field to shared Club domain model (backward-compatible default null)
- Added `getClub()` + `updateClub()` to ClubRepository interface and ClubRepositoryImpl
- ClubManager sees edit pencil icon in TeamsListScreen TopAppBar
- ClubEditSheet opens with pre-filled club name and location; saves via repository
- TopAppBar title dynamically shows club name from loaded club state

## Task Commits

Each task was committed atomically:

1. **Task 1: Add location to shared Club model + updateClub/getClub to ClubRepository** - `661a05e` (feat)
2. **Task 2: Add club edit UI in TeamsListScreen + ViewModel** - `1c33798` (feat)

## Files Created/Modified
- `shared/src/commonMain/kotlin/ch/teamorg/domain/Club.kt` - Added `val location: String? = null`
- `shared/src/commonMain/kotlin/ch/teamorg/repository/ClubRepository.kt` - Added `getClub()` + `updateClub()` methods
- `shared/src/commonMain/kotlin/ch/teamorg/data/repository/ClubRepositoryImpl.kt` - Implemented both methods + `UpdateClubRequest`
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamsListScreen.kt` - Added edit icon in TopAppBar, `ClubEditSheet` composable, club name in title
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamsListViewModel.kt` - Added `club` state, `showEditClubSheet`/`hideEditClubSheet`/`updateClub()`, `getClub()` call in `loadTeams()`

## Decisions Made
- `getClub()` called alongside `getClubTeams()` in same `loadTeams()` coroutine — no separate loading trigger needed
- `ClubEditSheet` added as private composable in `TeamsListScreen.kt` — ~45 lines, not worth a separate file

## Deviations from Plan

None — plan executed exactly as written. The plan already specified `getClub()` as an addition needed in Task 2.

## Issues Encountered
- `:shared:compileKotlinJvm` was already failing before this plan (pre-existing `DatabaseDriverFactory` expect/actual issue for JVM target — no JVM actual declaration). Verified pre-existing via git stash test. Used `compileCommonMainKotlinMetadata` to verify shared changes compile; used `compileDebugKotlinAndroid` for UI changes.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- TM-01 gap closed: ClubManager can edit club profile from TeamsListScreen
- Club domain model now has `location` field — event scheduling phase can use it if needed
- No blockers

---
*Phase: 02-team-management*
*Completed: 2026-03-19*
