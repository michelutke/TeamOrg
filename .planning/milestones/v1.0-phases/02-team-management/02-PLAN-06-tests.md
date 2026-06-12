---
plan: "06"
wave: 4
phase: 2
title: "UI tests + E2E flows — Team Management"
depends_on: ["05"]
autonomous: true
files_modified:
  - composeApp/src/androidTest/kotlin/ch/teamorg/ui/club/ClubSetupScreenTest.kt
  - composeApp/src/androidTest/kotlin/ch/teamorg/ui/team/TeamRosterScreenTest.kt
  - composeApp/src/androidTest/kotlin/ch/teamorg/ui/invite/InviteScreenTest.kt
  - composeApp/src/androidTest/kotlin/ch/teamorg/e2e/InviteE2ETest.kt
requirements:
  - TM-04
  - TM-10
  - TM-11
  - TM-15
  - TM-17
  - TM-18
---

# Plan 06 — UI Tests + E2E: Team Management

## Compose UI Tests

### ClubSetupScreenTest
- renders all fields
- submit with empty name shows validation error
- submit success navigates to Teams tab

### TeamRosterScreenTest
- renders member list
- FAB click copies invite link (verify clipboard/snackbar)
- remove member shows confirmation dialog
- pull-to-refresh triggers reload

### InviteScreenTest
- renders invite details correctly
- join button calls redeem and navigates
- expired invite shows error state
- unauthenticated user sees "Create account first" button

## E2E Tests (MockEngine)

### InviteE2ETest
1. **Happy path - existing user:** authenticated user taps invite link → invite details shown → taps Join → lands on team roster
2. **New user via invite:** unauthenticated user taps invite link → taps "Create account" → completes registration → auto-redeemed → lands on team roster
3. **Expired invite:** user taps expired link → sees "This invite has expired" error, not a crash
4. **Idempotent join:** user who is already a member taps invite link → taps Join → no error, navigates to team normally

## must_haves
- [ ] All 4 Compose UI test files have ≥3 test cases each
- [ ] E2E new-user-via-invite flow passes without token ever touching SharedPreferences
- [ ] Expired invite E2E shows correct error state (no crash)
