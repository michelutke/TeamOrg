---
status: awaiting_human_verify
trigger: "join-button-noop-for-authenticated-user"
created: 2026-03-20T00:00:00Z
updated: 2026-03-20T00:00:00Z
---

## Current Focus

hypothesis: CONFIRMED — onJoinSuccess in committed code calls onAuthSuccess() without removing Invite from backStack first. TeamorgApp LaunchedEffect guard `backStack.none { it is Screen.Invite }` prevents navigation when Invite is still in backStack.
test: Committed code: `onJoinSuccess = { onAuthSuccess() }` — Invite stays in backStack, guard skips navigation
expecting: Adding `backStack.removeAll { it is Screen.Invite }` before `onAuthSuccess()` fixes it
next_action: Fix committed code, add tests, hide bottom bar on Invite screen

## Symptoms

expected: Clicking Join redeems invite, navigates to Events screen (user already has teams)
actual: Nothing happens on Join click
errors: None visible
reproduction: Login as club manager with existing team -> open invite link from another manager -> see invite details -> click Join -> nothing
started: After recent invite/join flow fixes

## Eliminated

## Evidence

- timestamp: 2026-03-20T00:01:00Z
  checked: AppNavigation.kt onJoinSuccess callback (committed vs uncommitted)
  found: Committed code is `onJoinSuccess = { onAuthSuccess() }` — missing backStack.removeAll
  implication: Invite screen stays in backStack after redeem

- timestamp: 2026-03-20T00:02:00Z
  checked: TeamorgApp.kt LaunchedEffect guard condition
  found: `else if (backStack.none { it is Screen.Invite })` — skips navigation if Invite in backStack
  implication: After checkAuthState completes with Authenticated, the guard sees Invite still present, does nothing

- timestamp: 2026-03-20T00:03:00Z
  checked: Full redeem chain: InviteScreen button -> ViewModel -> Repository -> Server -> back
  found: HTTP POST works fine, isRedeemed=true is set, onJoinSuccess is called. Bug is purely in navigation after onJoinSuccess.
  implication: Fix is to remove Invite from backStack before calling onAuthSuccess()

- timestamp: 2026-03-20T00:04:00Z
  checked: showBottomBar excludes list in TeamorgApp
  found: Screen.Invite not in exclusion list — bottom bar shows on invite screen (minor UX issue)
  implication: Should hide bottom bar on Invite screen

## Resolution

root_cause: onJoinSuccess callback only called onAuthSuccess() without removing Invite screen from backStack first. TeamorgApp LaunchedEffect has guard `backStack.none { it is Screen.Invite }` that prevents navigation when Invite is present, causing the "nothing happens" behavior.
fix: Add backStack.removeAll { it is Screen.Invite } before onAuthSuccess() in onJoinSuccess callback. Also hide bottom bar on Invite screen.
verification: All tests pass — 7 new tests added (2 navigation, 2 ViewModel, 1 InviteScreen UI, 1 server integration), all existing tests still pass
files_changed:
  - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/TeamorgApp.kt
  - composeApp/src/commonTest/kotlin/ch/teamorg/navigation/InviteJoinNavigationTest.kt
  - composeApp/src/commonTest/kotlin/ch/teamorg/ui/invite/InviteViewModelTest.kt
  - composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/invite/InviteScreenTest.kt
  - server/src/test/kotlin/ch/teamorg/flows/CrossTeamInviteTest.kt
