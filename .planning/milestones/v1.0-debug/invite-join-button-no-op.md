---
status: awaiting_human_verify
trigger: "Clicking Join on invite screen does nothing - no navigation, no error"
created: 2026-03-20T10:00:00Z
updated: 2026-03-20T10:00:00Z
---

## Current Focus

hypothesis: onJoinSuccess calls checkAuthState(), which triggers LaunchedEffect in TeamorgApp. But LaunchedEffect guard `backStack.none { it is Screen.Invite }` is false because Invite screen is still on backStack, so navigation is skipped entirely.
test: Trace code path from join button click through to LaunchedEffect
expecting: LaunchedEffect skips navigation when Invite screen is in backStack
next_action: Fix the navigation logic so post-redeem triggers proper navigation

## Symptoms

expected: Clicking "Join" on invite screen should redeem the invite and navigate to Events/team screen
actual: Clicking "Join" does nothing - no visible effect
errors: None visible to user
reproduction: Open invite link -> see invite details -> click Join -> nothing happens
started: After ATS fix for iOS. Server endpoints confirmed working via curl.

## Eliminated

## Evidence

- timestamp: 2026-03-20T10:00:00Z
  checked: InviteScreen.kt join button handler and LaunchedEffect
  found: Join button calls viewModel.redeemInvite(token). On success, state.isRedeemed=true triggers LaunchedEffect which calls onJoinSuccess().
  implication: The redeemInvite call chain works correctly up to onJoinSuccess.

- timestamp: 2026-03-20T10:01:00Z
  checked: AppNavigation.kt onJoinSuccess handler -> TeamorgApp.kt onAuthSuccess
  found: onJoinSuccess = { onAuthSuccess() } which calls viewModel.checkAuthState(). This updates authState, triggering LaunchedEffect(authState, pendingToken) in TeamorgApp.
  implication: checkAuthState fires correctly but the LaunchedEffect determines navigation.

- timestamp: 2026-03-20T10:02:00Z
  checked: TeamorgApp.kt LaunchedEffect(authState, pendingToken) lines 29-49
  found: When authState becomes Authenticated and pendingToken is null, line 44 checks `backStack.none { it is Screen.Invite }`. Since Invite screen is STILL in backStack (it was never removed), this is FALSE, so the else branch is skipped entirely. No navigation occurs.
  implication: ROOT CAUSE CONFIRMED - the guard condition prevents post-redeem navigation.

## Resolution

root_cause: |
  In TeamorgApp.kt, the LaunchedEffect guard `backStack.none { it is Screen.Invite }` prevents navigation
  after invite redeem. When onJoinSuccess -> checkAuthState() fires, the Invite screen is still in the backStack.
  The LaunchedEffect sees Authenticated + no pendingToken + Invite in backStack = skip navigation.
  User stuck on Invite screen with isRedeemed=true but no navigation.
fix: |
  In AppNavigation.kt, the onJoinSuccess callback now removes all Invite screens from the backStack
  before calling onAuthSuccess()/checkAuthState(). This ensures the LaunchedEffect guard
  `backStack.none { it is Screen.Invite }` passes, allowing navigation to proceed to Events/EmptyState.
verification: |
  - compileCommonMainKotlinMetadata: PASS
  - testDebugUnitTest: PASS (all unit tests including 4 new InviteViewModel tests + 7 navigation tests)
  - jvmTest: PASS (all integration tests including 2 new full-journey tests)
  - Navigation test explicitly reproduces the bug (without fix, navigation is skipped) and verifies the fix
files_changed:
  - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt
  - composeApp/src/commonTest/kotlin/ch/teamorg/navigation/InviteJoinNavigationTest.kt
  - composeApp/src/commonTest/kotlin/ch/teamorg/ui/invite/InviteViewModelTest.kt
  - shared/src/jvmTest/kotlin/ch/teamorg/repository/ClientRepositoryFlowTest.kt
