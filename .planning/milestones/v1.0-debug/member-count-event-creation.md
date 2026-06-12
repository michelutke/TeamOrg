---
status: awaiting_human_verify
trigger: "member-count-and-event-creation-bugs"
created: 2026-03-20T00:00:00Z
updated: 2026-03-20T00:00:00Z
---

## Current Focus

hypothesis: CONFIRMED - see root causes below
test: n/a
expecting: n/a
next_action: Apply fixes for both bugs

## Symptoms

expected:
  1. Teams list page shows correct member count per team
  2. Creating an event saves it and shows it in the events list
actual:
  1. Teams list shows 0 members (but team detail shows the member)
  2. Event creation dialog closes with no feedback and no event appears
errors: No visible errors
reproduction:
  1. As club manager, go to Teams page - see 0 members
  2. As club manager, create event for team - dialog closes, nothing happens
started: After recent fixes

## Eliminated

## Evidence

- timestamp: 2026-03-20
  checked: Server ClubRepositoryImpl.listTeams
  found: Already computes memberCount from TeamRolesTable correctly. Server test passes.
  implication: Bug1 server side is correct. Issue is client-side ViewModel caching.

- timestamp: 2026-03-20
  checked: TeamsListViewModel and AppNavigation
  found: TeamsListViewModel.init { loadTeams() } only fires once. viewModel{} composable caches the VM. Navigating back from team detail doesn't re-trigger loadTeams().
  implication: Bug1 root cause is stale ViewModel data on back-navigation.

- timestamp: 2026-03-20
  checked: EventRepositoryImpl.findEventsForUser (server)
  found: Only queries TeamRolesTable for user's teams. Club managers have ClubRolesTable entries, NOT TeamRolesTable entries. If club manager has no team roles, returns emptyList().
  implication: Bug2 root cause is findEventsForUser ignoring club_manager role.

- timestamp: 2026-03-20
  checked: CreateEditEventViewModel.save() and EventListViewModel
  found: Event IS created on server (201). onSaved() pops screen. But EventListViewModel uses init{} caching same as Bug1 — doesn't refresh on return.
  implication: Bug2 has two issues: server doesn't return events for club managers + client doesn't refresh list.

## Resolution

root_cause: |
  Bug1: TeamsListViewModel caches in init{} — navigating back from team detail doesn't refresh. Server data is correct.
  Bug2: (a) EventRepositoryImpl.findEventsForUser only checks TeamRolesTable, not ClubRolesTable. Club managers with no explicit team role see zero events. (b) EventListViewModel same caching issue as Bug1.
fix: |
  1. Server: findEventsForUser must also include teams belonging to clubs where user is club_manager
  2. Client: TeamsListViewModel and EventListViewModel need refresh triggers on back-navigation (LaunchedEffect or callback)
verification: |
  - All server tests pass (57 second run) including new regression test
  - New test: club manager sees events via GET users/me/events for managed teams
  - iOS build succeeds, shared tests pass
  - Pre-existing JVM compile errors unrelated (expect/actual platform declarations)
files_changed:
  - server/src/main/kotlin/ch/teamorg/domain/repositories/EventRepositoryImpl.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt
  - server/src/test/kotlin/ch/teamorg/routes/EventRoutesTest.kt
