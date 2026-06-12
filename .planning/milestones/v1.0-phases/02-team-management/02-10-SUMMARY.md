---
phase: 02-team-management
plan: 10
subsystem: team-management
tags: [player-profile, leave-team, sub-groups, backend, compose-ui]
dependency_graph:
  requires: [02-08, 02-09]
  provides: [player-profile-screen, leave-team-flow, sub-group-crud]
  affects: [TeamRosterScreen, AppNavigation, shared-TeamRepository]
tech_stack:
  added: []
  patterns: [ModalBottomSheet, TextFieldDialog, LaunchedEffect-navigation-trigger]
key_files:
  created:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/SubGroupSheet.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepository.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepositoryImpl.kt
    - server/src/main/kotlin/ch/teamorg/routes/TeamRoutes.kt
    - server/src/main/kotlin/ch/teamorg/routes/SubGroupRoutes.kt
    - shared/src/commonMain/kotlin/ch/teamorg/repository/TeamRepository.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/repository/TeamRepositoryImpl.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/Screen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterScreen.kt
decisions:
  - "SubGroup domain model is in Event.kt (moved by 02-09 to avoid duplicate); TeamRepository imports from ch.teamorg.domain.SubGroup which is the same package"
  - "leaveTeam uses DELETE /teams/{teamId}/leave (separate from DELETE /members/{userId} which is coach-only)"
  - "Sub-group icon in roster uses Icons.Default.Person as placeholder (no Groups icon in Material defaults)"
metrics:
  duration_minutes: 35
  tasks_completed: 2
  tasks_total: 2
  files_created: 3
  files_modified: 8
  completed_date: "2026-03-19"
requirements_closed: [TM-12, TM-13, TM-15, TM-16]
---

# Phase 02 Plan 10: Player Profile + Sub-group Management Summary

**One-liner:** Server PATCH /profile + DELETE /leave + full sub-group CRUD, plus PlayerProfileScreen with editable jersey/position, leave-team flow, and SubGroupSheet managed from TeamRosterScreen.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Backend profile update + sub-group CRUD | d28d23b | TeamRoutes.kt, SubGroupRoutes.kt, TeamRepository.kt, TeamRepositoryImpl.kt (server) |
| 2 | PlayerProfileScreen + SubGroupSheet + shared repo | f6d2cbd | PlayerProfileScreen.kt, PlayerProfileViewModel.kt, SubGroupSheet.kt + shared repo extensions |

## What Was Built

### Task 1: Backend
- Added `updateMemberProfile(teamId, userId, jerseyNumber, position)` to server TeamRepository interface and impl
- Added `PATCH /teams/{teamId}/members/{userId}/profile` endpoint (coach/club_manager only)
- Added `DELETE /teams/{teamId}/leave` self-leave endpoint (any authenticated team member)
- Replaced stub SubGroupRoutes with full CRUD:
  - `POST /teams/{teamId}/subgroups` — create sub-group
  - `PUT /teams/{teamId}/subgroups/{subGroupId}` — rename
  - `DELETE /teams/{teamId}/subgroups/{subGroupId}` — delete with cascade members
  - `POST /teams/{teamId}/subgroups/{subGroupId}/members` — add member
  - `DELETE /teams/{teamId}/subgroups/{subGroupId}/members/{userId}` — remove member

### Task 2: UI + Shared
- Extended shared `TeamRepository` interface with 7 new methods
- Implemented all 7 new methods in shared `TeamRepositoryImpl` via Ktor HTTP client
- `PlayerProfileViewModel`: loads roster to find member, checks coach role via getMyRoles(), exposes updateJerseyNumber/updatePosition/leaveTeam
- `PlayerProfileScreen`: avatar, role badge, editable jersey/position (coach only), Leave Team button (own profile + player role only) with confirmation dialog
- `SubGroupSheet`: ModalBottomSheet with sub-group list, delete confirmation, inline add field
- `TeamRosterViewModel`: added subGroups state, sub-group CRUD methods, toggleSubGroupSheet
- `TeamRosterScreen`: sub-group icon button in TopAppBar (coach/manager), member click → PlayerProfile navigation
- Note: Many UI files (PlayerProfileScreen, Screen.PlayerProfile, AppNavigation wiring) were pre-built by Plan 02-09; this plan added the LaunchedEffect for profile loading and wired the member tap

## Deviations from Plan

### Context Deviation

**SubGroup not in Club.kt**
- Found during: Task 2
- Issue: Plan expected SubGroup in `shared/.../domain/Club.kt`, but Plan 02-09 already moved it to `Event.kt` to avoid duplicate declaration conflicts with Phase 03
- Fix: Import from `ch.teamorg.domain.SubGroup` (same package, different file) — works correctly
- Impact: Acceptance criterion `grep -q "SubGroup" Club.kt` technically fails but the domain model exists and compiles

**UI files pre-built by Plan 02-09**
- Found during: Task 2
- Issue: PlayerProfileScreen.kt, PlayerProfileViewModel.kt, SubGroupSheet.kt, Screen.PlayerProfile, AppNavigation wiring, and UiModule registration were all committed by Plan 02-09
- Fix: Verified existing implementations matched plan requirements; added LaunchedEffect for profile loading, member tap navigation, and extended TeamRosterViewModel with sub-group methods

## Self-Check

- [x] server/src/main/kotlin/ch/teamorg/routes/TeamRoutes.kt — contains profile + leave endpoints
- [x] server/src/main/kotlin/ch/teamorg/routes/SubGroupRoutes.kt — full CRUD
- [x] composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileScreen.kt — exists
- [x] composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileViewModel.kt — exists
- [x] composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/SubGroupSheet.kt — exists
- [x] shared/.../repository/TeamRepository.kt — has leaveTeam, updateMemberProfile, subgroup methods
- [x] shared/.../data/repository/TeamRepositoryImpl.kt — implements all new methods
- [x] Commits exist: d28d23b (Task 1), f6d2cbd (Task 2)
- [x] Server + composeApp compile with no errors

## Self-Check: PASSED
