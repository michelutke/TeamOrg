---
status: in_progress
trigger: "Multiple bugs in invite-to-team flow: post-login shows welcome screen instead of join flow, 'Join a team' box shows Phase 2 snackbar, join API possibly not implemented"
created: 2026-03-20T00:00:00Z
updated: 2026-03-20T00:00:00Z
---

## Current Focus

hypothesis: Multiple bugs across the invite flow - stubbed join handler, broken post-auth navigation, possibly missing backend endpoint
test: Read full codebase flow end-to-end
expecting: Identify all broken points in the flow
next_action: Read deep link handling, auth flow, join team handler, and backend routes

## Symptoms

expected: After login via deep link intent, user should be taken to the team join flow and actually join the team. Pasting invite link in "Join a team" box should also work.
actual: 1) Welcome screen shows after login via intent instead of team join flow. 2) "Join a team" box shows "Team joining will be available in Phase 2" snackbar. 3) Unknown if join team API endpoint even works.
errors: No crash errors - just wrong navigation and stubbed functionality
reproduction: 1) Generate invite link, open on device, login -> see welcome screen. 2) Copy invite link, paste in "Join a team" box -> Phase 2 snackbar.
started: Recent commits show deep link fixes were attempted (4a350e0, 3d06efc, 04a2d48).

## Eliminated

## Evidence

- timestamp: 2026-03-20T00:01:00Z
  checked: EmptyStateViewModel.onJoinTeamClick()
  found: Lines 53-55 have early return with Phase 2 message. Real join logic below (lines 57-77) is dead code.
  implication: Bug 2 confirmed - "Join a team" box is stubbed out.

- timestamp: 2026-03-20T00:02:00Z
  checked: AppNavigation.kt login/register success callbacks
  found: onLoginSuccess = backStack.add(Screen.Events), onRegisterSuccess = backStack.add(Screen.EmptyState). Neither calls AuthViewModel.checkAuthState().
  implication: After login/register, authState stays Unauthenticated, so TeamorgApp LaunchedEffect never fires to check pendingToken. Deep link token is ignored.

- timestamp: 2026-03-20T00:03:00Z
  checked: AuthViewModel.checkAuthState()
  found: hasTeam is hardcoded to false. No hasTeam API call exists in AuthRepository.
  implication: Even if auth state was refreshed, hasTeam=false means user always goes to EmptyState. But this is acceptable for now if deep link handling works.

- timestamp: 2026-03-20T00:04:00Z
  checked: Server InviteRoutes.kt + InviteRepositoryImpl.redeem()
  found: Backend is fully implemented. GET /invites/{token} returns details. POST /invites/{token}/redeem marks invite as redeemed AND inserts TeamRolesTable entry.
  implication: Bug 3 ruled out - backend is complete.

- timestamp: 2026-03-20T00:05:00Z
  checked: AuthRepository.getMe() and /auth/me/roles endpoint
  found: /auth/me/roles returns team roles, but AuthRepository has no method to check team membership. hasTeam could be derived from /auth/me/roles.
  implication: Need to add hasTeam check to AuthViewModel.checkAuthState() using roles endpoint.

## Resolution

root_cause: |
  Three bugs:
  1) EmptyStateViewModel.onJoinTeamClick() has early return with Phase 2 stub message. Real logic is dead code.
  2) AppNavigation login/register success callbacks directly navigate instead of refreshing AuthViewModel state. This means TeamorgApp LaunchedEffect never fires after login, so pendingToken from deep links is never consumed.
  3) AuthViewModel.checkAuthState() hardcodes hasTeam=false. After joining a team, user still sees EmptyState instead of Events.
fix: |
  1) Removed Phase 2 stub from EmptyStateViewModel.onJoinTeamClick() - was early-returning with info message, making real join logic dead code.
  2) Changed login/register/joinSuccess callbacks in AppNavigation to call onAuthSuccess() (-> AuthViewModel.checkAuthState()) instead of directly navigating. This allows TeamorgApp's LaunchedEffect to fire with updated authState, detecting pendingToken from deep links.
  3) Added hasTeam() to AuthRepository interface + AuthRepositoryImpl (calls /auth/me/roles endpoint). Updated AuthViewModel.checkAuthState() to use real hasTeam check instead of hardcoded false.
  4) Updated both FakeAuthRepository classes with hasTeam() method.
  5) Replaced Phase 2 stub tests with proper join flow tests.
additional_fixes_post_agent: |
  4) Double-submit race in RegisterViewModel + LoginViewModel: isLoading=true was set inside viewModelScope.launch (async), allowing two rapid clicks to both pass the guard. Fixed by adding `if (currentState.isLoading) return` at top and moving isLoading=true before the launch.
  5) Post-auth navigation not re-triggering: checkAuthState() could emit the same AuthState.Authenticated value, so LaunchedEffect(authState) wouldn't re-run. Fixed by resetting to AuthState.Loading first in checkAuthState(), ensuring the LaunchedEffect always fires.
  6) Test compilation: Fixed FakeClubRepository (missing getClub, createTeam, updateTeam, updateClub), FakeTeamRepository (missing getMyRoles, updateMemberRole, updateMemberProfile, leaveTeam, getSubGroups, createSubGroup, deleteSubGroup, addSubGroupMember, removeSubGroupMember, uploadAvatar), and TeamRosterViewModelTest (missing clubRepository constructor param).
verification: compileCommonMainKotlinMetadata + compileDebugKotlinAndroid + testDebugUnitTest all pass.
testing_status: |
  Deployed to both iOS simulator (iPhone 17 Pro) and Android emulator (Pixel 9).
  User still needs to verify:
  1. Register → should navigate to EmptyState (no double-submit 409)
  2. Login → should navigate properly (not stuck)
  3. Deep link → login → should see Invite screen
  4. "Join a team" box → paste link → should navigate to Invite screen
  5. After joining team → should see Events screen
files_changed:
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/emptystate/EmptyStateViewModel.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/TeamorgApp.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/auth/AuthViewModel.kt
  - shared/src/commonMain/kotlin/ch/teamorg/repository/AuthRepository.kt
  - shared/src/commonMain/kotlin/ch/teamorg/data/repository/AuthRepositoryImpl.kt
  - composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeAuthRepository.kt
  - composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/fakes/FakeAuthRepository.kt
  - composeApp/src/commonTest/kotlin/ch/teamorg/ui/emptystate/EmptyStateViewModelTest.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/login/LoginViewModel.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/register/RegisterViewModel.kt
  - composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeClubRepository.kt
  - composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeTeamRepository.kt
  - composeApp/src/commonTest/kotlin/ch/teamorg/ui/team/TeamRosterViewModelTest.kt
