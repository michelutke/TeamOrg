---
phase: 02-team-management
plan: "09"
subsystem: ui/team-management
tags: [compose, kmp, viewmodel, repository, team, club-manager]
dependency_graph:
  requires: [02-08]
  provides: [TeamsListScreen, TeamEditSheet, TeamRosterRoleManagement]
  affects: [AppNavigation, UiModule, ClubRepository, TeamRosterViewModel]
tech_stack:
  added: []
  patterns: [ModalBottomSheet, combinedClickable-long-press, AlertDialog-action-menu]
key_files:
  created:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamsListScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamsListViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamEditSheet.kt
  modified:
    - shared/src/commonMain/kotlin/ch/teamorg/repository/ClubRepository.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/repository/ClubRepositoryImpl.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt
    - shared/src/commonMain/kotlin/ch/teamorg/domain/Club.kt
decisions:
  - "TeamsListViewModel derives clubId from getMyRoles() clubRoles — avoids storing clubId separately"
  - "TeamEditSheet reused for both create (TeamsListScreen) and edit (TeamRosterScreen)"
  - "ClubManager role management via long-press action dialog — promotes/removes in two steps"
metrics:
  duration_seconds: 353
  completed_date: "2026-03-19"
  tasks_completed: 2
  files_changed: 9
---

# Phase 02 Plan 09: ClubManager Team Management Screens Summary

ClubManager team CRUD + roster role management via TeamsListScreen, TeamEditSheet, and extended TeamRosterScreen.

## Tasks Completed

| # | Name | Commit | Key Files |
|---|------|--------|-----------|
| 1 | Shared repo additions + TeamsListScreen + TeamsListViewModel | 50bdb27 | TeamsListScreen.kt, TeamsListViewModel.kt, TeamEditSheet.kt, ClubRepository.kt |
| 2 | TeamRosterScreen role management | c3b68dc | TeamRosterScreen.kt, TeamRosterViewModel.kt, AppNavigation.kt |

## What Was Built

- **ClubRepository** extended with `createTeam` (POST /clubs/{id}/teams) and `updateTeam` (PATCH /teams/{id})
- **TeamsListViewModel**: loads club teams via `getMyRoles()` to resolve clubId, exposes `isClubManager` flag
- **TeamsListScreen**: LazyColumn of team cards, create-team FAB for ClubManagers, empty state, loading/error states
- **TeamEditSheet**: reusable ModalBottomSheet for create/edit team (name + optional description)
- **TeamRosterViewModel**: extended with `promoteMember`, `createCoachInvite`, `editTeam`, `checkClubManagerRole`; added `ClubRepository` dependency
- **TeamRosterScreen**: edit icon in TopAppBar (ClubManager only), long-press action dialog with promote/remove options, TeamEditSheet for editing team details
- `Screen.Teams` wired to `TeamsListScreen` in AppNavigation (replaces placeholder)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SubGroup redeclaration**
- **Found during:** Task 2 compile
- **Issue:** `SubGroup` data class declared in both `Club.kt` and `Event.kt` (same package `ch.teamorg.domain`) — stash from Phase 03 WIP added a duplicate with `memberCount: Long`
- **Fix:** Removed duplicate from `Club.kt`; canonical definition remains in `Event.kt` with `memberCount: Int`
- **Files modified:** `shared/src/commonMain/kotlin/ch/teamorg/domain/Club.kt`
- **Commit:** c3b68dc

**2. [Rule 3 - Blocking] PlayerProfile screen unhandled in when**
- **Found during:** Task 2 compile
- **Issue:** `Screen.PlayerProfile` added by Phase 03 stash but not handled in `AppNavigation.kt` `when` expression
- **Fix:** Added `PlayerProfile` branch with `PlayerProfileViewModel` wired via Koin; registered in `UiModule`
- **Files modified:** `AppNavigation.kt`, `UiModule.kt`
- **Commit:** c3b68dc

**3. [Rule 1 - Bug] SubGroupSheet Long vs Int comparison**
- **Found during:** Task 2 compile
- **Issue:** `SubGroupSheet.kt` compared `memberCount == 1L` after `SubGroup.memberCount` type changed from `Long` to `Int`
- **Fix:** Changed `1L` to `1`
- **Files modified:** `SubGroupSheet.kt`
- **Commit:** c3b68dc

## Self-Check: PASSED

- TeamsListScreen.kt: FOUND
- TeamsListViewModel.kt: FOUND
- TeamEditSheet.kt: FOUND
- Commit 50bdb27: FOUND
- Commit c3b68dc: FOUND
