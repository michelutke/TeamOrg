---
plan: "05"
wave: 3
phase: 2
title: "Team Management UI — Club setup, roster, invite flow"
depends_on: ["03", "04"]
autonomous: true
files_modified:
  - shared/src/commonMain/kotlin/ch/teamorg/domain/Club.kt
  - shared/src/commonMain/kotlin/ch/teamorg/domain/Team.kt
  - shared/src/commonMain/kotlin/ch/teamorg/domain/Invite.kt
  - shared/src/commonMain/kotlin/ch/teamorg/repository/ClubRepository.kt
  - shared/src/commonMain/kotlin/ch/teamorg/data/repository/ClubRepositoryImpl.kt
  - shared/src/commonMain/kotlin/ch/teamorg/repository/TeamRepository.kt
  - shared/src/commonMain/kotlin/ch/teamorg/data/repository/TeamRepositoryImpl.kt
  - shared/src/commonMain/kotlin/ch/teamorg/repository/InviteRepository.kt
  - shared/src/commonMain/kotlin/ch/teamorg/data/repository/InviteRepositoryImpl.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/club/ClubSetupScreen.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/club/ClubSetupViewModel.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterScreen.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterViewModel.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/invite/InviteScreen.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/invite/InviteViewModel.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/emptystate/EmptyStateViewModel.kt
requirements:
  - TM-01
  - TM-02
  - TM-10
  - TM-11
  - TM-12
  - TM-15
  - TM-17
  - TM-18
  - TM-19
---

# Plan 05 — Team Management UI

## Goal
Club setup flow, team roster screen, and invite acceptance flow all working end-to-end. Navigation from EmptyState correctly routes to Club Setup for new club managers.

## Screens

### ClubSetupScreen (ClubManager flow)
- Club name, sport type, location fields
- Optional logo upload (image picker → `POST /clubs/{id}/logo`)
- On success: navigate to main app (Teams tab)

### TeamRosterScreen (Teams tab for Coach)
- List of players with name, avatar, jersey number, position
- FAB: "Invite player" → copies invite link to clipboard + shows share sheet
- Long press on player: remove from team (with confirmation dialog)

### InviteScreen (deep link handler)
- Shown when user opens `teamorg://invite/team/{token}`
- Displays: team name, club name, "invited by", role
- CTA: "Join [team name]" button
- If not logged in: "Create account first" button → navigates to Register with token in nav args
- After registration/login: auto-redeems token, navigates to team

### EmptyStateScreen update
- "Set up your club" button: navigates to ClubSetupScreen
- "Join a team" paste field: triggers invite redemption flow

## Tasks

<task id="05-01" title="Domain models (shared)">
```kotlin
data class Club(val id: String, val name: String, val logoUrl: String?, val sportType: String)
data class Team(val id: String, val clubId: String, val name: String, val memberCount: Int)
data class TeamMember(val userId: String, val displayName: String, val avatarUrl: String?, val role: String, val jerseyNumber: Int?, val position: String?)
data class InviteDetails(val token: String, val teamName: String, val clubName: String, val role: String, val invitedBy: String, val expiresAt: String, val alreadyRedeemed: Boolean)
```
</task>

<task id="05-02" title="Client repositories (shared)">
ClubRepository + impl (Ktor calls to server)
TeamRepository + impl
InviteRepository + impl (includes redeem())
</task>

<task id="05-03" title="ClubSetupScreen + VM">
State: `{ name, sportType, location, logoUri?, isLoading, error }`
Logo upload: image picker → read bytes → `POST /clubs/{id}/logo`
On success: emit `ClubCreated` event → navigate to Teams tab
</task>

<task id="05-04" title="TeamRosterScreen + VM">
State: `{ members: List<TeamMember>, isLoading, error }`
FAB: calls `POST /teams/{id}/invites` → returns link → copy to clipboard + share sheet
Remove: calls `DELETE /teams/{id}/members/{userId}` (with confirmation)
Pull-to-refresh
</task>

<task id="05-05" title="InviteScreen + VM — deep link handler">
State: `{ inviteDetails?, isLoading, error, isRedeeming }`
VM: on init with token → `GET /invites/{token}` → show details
Join button: `POST /invites/{token}/redeem` → on success → navigate to team
If unauthenticated: navigate to Register with token passed as nav arg
EmptyStateViewModel: update to handle token passed from nav args after registration
</task>

<task id="05-06" title="Navigation wiring">
- Deep link: `teamorg://invite/team/{token}` → InviteScreen (with token arg)
- EmptyState "Set up club" → ClubSetupScreen
- InviteScreen "Join" (unauthenticated) → RegisterScreen (with token arg)
- Post-register: AuthViewModel redeems pending token if present
</task>

<task id="05-07" title="Koin DI updates">
Add ClubRepository, TeamRepository, InviteRepository, ClubSetupViewModel, TeamRosterViewModel, InviteViewModel to DI modules.
</task>

## must_haves
- [ ] Token passed through Register flow without being stored to disk
- [ ] Invite auto-redeemed after successful registration
- [ ] Roster shows live data (pull-to-refresh works)
- [ ] Logo upload shows progress and error states
