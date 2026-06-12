---
phase: 05-notifications
plan: "05"
subsystem: ui
tags: [kotlin, compose-multiplatform, koin, notifications, inbox, ui]

dependency_graph:
  requires: [05-03, 05-04]
  provides: [NO-02, NO-08, NO-09, NO-11, inbox-ui, notification-settings-ui, reminder-picker]
  affects: [EventDetailScreen, TeamorgBottomBar, AppNavigation, Screen, UiModule]

tech-stack:
  added: []
  patterns:
    - "InboxViewModel: optimistic markRead/markAllRead with revert on failure"
    - "NotificationSettingsViewModel: 300ms debounced updateSetting via coroutine Job cancel/relaunch"
    - "formatRelativeTime: pure Kotlin ISO-8601 parse without kotlinx-datetime dependency in UI layer"
    - "ReminderPickerSheet: preset chips fill hours/minutes fields; total=0 disables CTA"
    - "EventDetailViewModel: NotificationRepository injected as 5th param; reminder loaded in loadEvent flow"

key-files:
  created:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/inbox/InboxScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/inbox/InboxViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/inbox/NotificationRow.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/inbox/NotificationSettingsScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/inbox/NotificationSettingsViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/inbox/ReminderPickerSheet.kt
  modified:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/components/TeamorgBottomBar.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/Screen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventDetailScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventDetailViewModel.kt

key-decisions:
  - "TeamRoleEntry has no teamName field — used teamId as display label in team picker (avoids extra API call)"
  - "Checkpoint:human-verify auto-approved (auto_advance=true in config)"
  - "formatRelativeTime implemented as pure Kotlin ISO-8601 parser — avoids adding kotlinx-datetime dependency to UI module"
  - "NotificationSettingsScreen + ReminderPickerSheet created in Task 1 commit (needed for AppNavigation compilation)"
  - "EventDetailBody signature extended with reminderLeadMinutes + onReminderTap — keeps state in parent composable"

metrics:
  duration: 5min
  completed: 2026-03-26
  tasks_completed: 2
  tasks_total: 2
  files_created: 6
  files_modified: 6
---

# Phase 05 Plan 05: Notification UI Summary

**Complete notification UI: InboxScreen, NotificationSettingsScreen, ReminderPickerSheet, bottom nav badge, EventDetail reminder row — all ViewModels registered in Koin**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-26T08:34:15Z
- **Completed:** 2026-03-26T08:39:18Z
- **Tasks:** 2
- **Files created:** 6
- **Files modified:** 6

## Accomplishments

- InboxScreen replaces PlaceholderScreen("Inbox") — loading/empty/error/loaded states with pull-to-refresh
- InboxViewModel: paginated load (50, 0), optimistic markRead + markAllRead with revert on failure, getDeepLinkScreen for navigation
- NotificationRow: type-mapped icon (Event/EditNote/EventBusy/Alarm/HowToVote), relative time helper, unread dot (8dp PrimaryBlue circle)
- TeamorgBottomBar: `unreadCount: Long = 0` param, BadgedBox wraps Inbox tab icon (1-99, 99+)
- Screen.NotificationSettings data object added; AppNavigation wired for both Inbox and NotificationSettings
- NotificationSettingsScreen: team FilterChip picker, Events/Reminders sections with Switch toggles, Responses (SegmentedButton) + Absences sections visible when isCoach
- NotificationSettingsViewModel: loadTeams → loadSettings chain, selectTeam, 300ms debounced updateSetting with optimistic revert
- ReminderPickerSheet: ModalBottomSheet with hours+minutes OutlinedTextFields, 5 preset FilterChips, "Set Reminder" FilledButton (disabled when total=0), "No reminder" TextButton (only when onRemove != null)
- EventDetailViewModel: NotificationRepository injected as 5th constructor param, loadReminderOverride + setReminderOverride methods, reminder loaded in loadEvent flow
- EventDetailScreen: Reminder row (Icons.Outlined.Alarm, label, trailing value + chevron) after meta section, opens ReminderPickerSheet

## Task Commits

1. **Task 1: InboxScreen + InboxViewModel + NotificationRow + navigation wiring** - `fd7d5eb`
2. **Task 2: NotificationSettings + ReminderPickerSheet + EventDetail reminder row** - `d11f6d2`

## Verification

- `./gradlew :composeApp:compileDebugKotlin` - BUILD SUCCESSFUL (17s Task 1, 6s Task 2)
- Checkpoint:human-verify auto-approved (auto_advance=true)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing] NotificationSettingsScreen + ReminderPickerSheet created in Task 1**
- **Found during:** Task 1
- **Issue:** AppNavigation.kt references NotificationSettingsScreen and NotificationSettingsViewModel; both needed to exist for Task 1 to compile
- **Fix:** Created all 3 inbox files (NotificationSettingsScreen, NotificationSettingsViewModel, ReminderPickerSheet) in Task 1 commit rather than Task 2
- **Files modified:** All 3 created in Task 1 commit `fd7d5eb`

**2. [Rule 1 - Bug] TeamRoleEntry has no teamName field**
- **Found during:** Task 2 (NotificationSettingsViewModel)
- **Issue:** Plan spec said `getMyRoles()` extracts `teamName`, but `TeamRoleEntry` only has `teamId`, `clubId`, `role`
- **Fix:** Used `teamId` as display label in TeamInfo — sufficient for MVP team selection
- **Files modified:** NotificationSettingsViewModel.kt

## Self-Check: PASSED

All key files verified present and containing required strings.
