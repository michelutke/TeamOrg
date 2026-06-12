---
status: awaiting_human_verify
trigger: "team-membership-not-reflecting: member count 0, no teams in event creation, player sees no teams after joining"
created: 2026-03-20T00:00:00Z
updated: 2026-03-20T00:00:00Z
---

## Current Focus

hypothesis: Three separate but related bugs all stem from incomplete role/membership handling
test: all three fixes applied, server tests pass including 2 new regression tests
expecting: user verification that all three bugs are resolved in the app
next_action: await human verification

## Symptoms

expected:
  1. Team list should show updated member count
  2. Create event should list the manager's teams for selection
  3. Player should see the team they just joined in their Teams page
actual:
  1. Member count stays at 0
  2. "No teams available" on event creation
  3. Player sees "No teams yet" after joining
errors: No crashes, just wrong data displayed
reproduction: Manager creates club+team, invites player. Player joins. Both see wrong team data.
started: After invite/join flow was implemented

## Eliminated

## Evidence

- timestamp: 2026-03-20
  checked: TeamRepositoryImpl.rowToTeam()
  found: memberCount hardcoded to 0 (line 168)
  implication: GET /teams/{id} always returns memberCount=0 regardless of actual members

- timestamp: 2026-03-20
  checked: ClubRepositoryImpl.listTeams()
  found: This method correctly computes memberCount from TeamRolesTable — so GET /clubs/{id}/teams would be correct
  implication: Bug 1 only affects individual team fetch, not the club teams list

- timestamp: 2026-03-20
  checked: CreateEditEventViewModel
  found: availableTeams is only populated in loadForEdit() (edit flow), never in create flow
  implication: Create event screen always shows "No teams available" because loadAvailableTeams was never implemented

- timestamp: 2026-03-20
  checked: TeamsListViewModel.loadTeams()
  found: Only checks roles.clubRoles.firstOrNull() — players who join via invite only have team roles, not club roles
  implication: Player sees empty teams because clubRole is null so the else branch fires with no teams

## Resolution

root_cause: Three bugs:
  1. TeamRepositoryImpl.rowToTeam() hardcodes memberCount=0 instead of counting from TeamRolesTable
  2. CreateEditEventViewModel has no loadAvailableTeams() for the create flow — only edit flow populates teams
  3. TeamsListViewModel only checks clubRoles to find the user's club — players with team roles only are ignored

fix:
  1. Added countMembers() helper to TeamRepositoryImpl; all rowToTeam calls now pass actual count
  2. Added ClubRepository + TeamRepository deps to CreateEditEventViewModel; init calls loadAvailableTeams() which fetches teams from all clubs the user is associated with (via both club and team roles)
  3. TeamsListViewModel.loadTeams() now derives clubId from teamRoles when clubRoles is empty

verification: All existing server tests pass + 2 new regression tests added. Client compiles and unit tests pass.

files_changed:
  - server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepositoryImpl.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/CreateEditEventViewModel.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamsListViewModel.kt
  - server/src/test/kotlin/ch/teamorg/routes/TeamRoutesTest.kt
