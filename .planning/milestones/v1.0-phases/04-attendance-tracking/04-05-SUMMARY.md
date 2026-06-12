---
phase: 04-attendance-tracking
plan: "05"
subsystem: composeApp/ui
tags: [kotlin, kmp, compose, attendance, rsvp, ui, viewmodel]
dependency_graph:
  requires: ["04-04"]
  provides: ["04-06", "04-07"]
  affects: ["composeApp/ui/attendance", "composeApp/ui/events", "composeApp/di"]
tech_stack:
  added: []
  patterns:
    - "optimistic RSVP update: state updates before HTTP, reverts on failure"
    - "compact/full composable variant via boolean param (32dp vs 48dp)"
    - "grouped list by status: filter+render pattern, empty groups hidden"
key_files:
  created:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/AttendanceRsvpButtons.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/BegrundungSheet.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/ResponseDeadlineLabel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/MemberResponseList.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/MemberResponseRow.kt
  modified:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventDetailScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventDetailViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventListScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventListViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt
decisions:
  - "EventListItem compact RSVP buttons are read-only (onSelect = {}) — user taps card to navigate to detail where they can actually respond"
  - "Auto-declined status detected from myResponse == 'declined-auto' in EventListState, fetched alongside attendance counts"
  - "loadAttendanceCounts fetches per-event serially (not batched) — acceptable for MVP list size"
  - "submitResponse reverts optimistic update on failure (sets myResponse = null, not previous value) — simple safe fallback"
metrics:
  duration: "~15m"
  completed_date: "2026-03-24"
  tasks_completed: 2
  files_created: 5
  files_modified: 5
---

# Phase 04 Plan 05: Player-facing RSVP UI Summary

**One-liner:** RSVP button row (compact+full), Begrundung sheet with mandatory reason, member response list grouped by status, all wired to ViewModel with optimistic updates and real attendance data.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | RSVP buttons, BegrundungSheet, ResponseDeadlineLabel | ede2d6e | AttendanceRsvpButtons.kt, BegrundungSheet.kt, ResponseDeadlineLabel.kt |
| 2 | MemberResponseList, MemberResponseRow, EventDetail/List integration | 518c7d6 | MemberResponseList.kt, MemberResponseRow.kt, EventDetailScreen.kt, EventDetailViewModel.kt, EventListScreen.kt, EventListViewModel.kt, UiModule.kt |

## What Was Built

### AttendanceRsvpButtons
Row of 3 equal-weight buttons (Going/Maybe/Can't Go). Two variants: compact 32dp with icon+count for list cards, full 48dp with text label for detail screen. Colors per UI-SPEC RSVP states. Disabled state (alpha 0.5) when `deadlinePassed = true`. contentDescription on each for accessibility.

### BegrundungSheet
ModalBottomSheet triggered when player taps "Maybe". OutlinedTextField with mandatory reason (minLines 3). "Confirm Maybe" CTA disabled when blank — enforces AT-01. Matches UI-SPEC S5: bg #13131F, border #2A2A40 unfocused / #4F8EF7 focused.

### ResponseDeadlineLabel
Inline 12sp label: "Respond by [date] at HH:mm" in #9090B0 when deadline in future; "Response closed" in #6B7280 when deadline passed. Hidden when deadline is null.

### MemberResponseList
Groups CheckInEntry list into CONFIRMED/MAYBE/DECLINED/NO RESPONSE sections with colored section headers (12sp SemiBold, letterSpacing 1.5sp). Empty groups hidden. Empty state: "No responses yet" + body text.

### MemberResponseRow
56dp row with 40dp avatar circle (initials fallback), name + reason column, optional coach override buttons (3x 28dp circles). Auto-declined badge inline when `abwesenheitRuleId != null`. Coach override pencil indicator when `record.setBy != userId`.

### EventDetailScreen modifications
Replaced placeholder ("Attendance tracking coming in Phase 4") with real attendance section: AttendanceRsvpButtons + ResponseDeadlineLabel + Divider + MemberResponseList. BegrundungSheet state managed in screen composable — visible when Maybe tapped, calls submitResponse("unsure", reason) on confirm.

### EventDetailViewModel modifications
Extended EventDetailState with: myResponse, confirmedCount, maybeCount, declinedCount, responseDeadline, deadlinePassed, checkInEntries. Added constructor param AttendanceRepository. Added: loadAttendance(), submitResponse() (optimistic), submitOverride().

### EventListScreen modifications
EventListItem expanded from Row to Column layout. Compact AttendanceRsvpButtons added below event info row. Auto-declined cards: alpha(0.6f) + "Auto-declined" badge top-right.

### EventListViewModel modifications
Added AttendanceRepository constructor param. Added EventAttendanceCounts data class. State includes attendanceCounts: Map<String, EventAttendanceCounts>. loadAttendanceCounts() fetches getEventAttendance per visible event after loadEvents() completes.

### UiModule.kt
EventListViewModel and EventDetailViewModel factories updated to include extra `get()` for AttendanceRepository (4 params each).

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

### Notes

- Plan 04-06 (CoachOverrideSheet + absence sub-composables) was already committed to branch before 04-05 execution. MemberResponseRow coach buttons tap inline (no CoachOverrideSheet) — that sheet is wired in plan 04-06.
- PlayerProfileScreen.kt has pre-existing compilation warnings (deprecated ArrowBack icon, LocalClipboardManager) — out of scope for this plan.

## Self-Check: PASSED

All 5 created files verified on disk. Task commits ede2d6e and 518c7d6 confirmed in git log. Build passes: `./gradlew :composeApp:compileDebugKotlinAndroid` — BUILD SUCCESSFUL.
