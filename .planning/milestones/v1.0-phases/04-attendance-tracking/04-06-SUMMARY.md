---
phase: 04-attendance-tracking
plan: "06"
subsystem: composeApp/ui/attendance + composeApp/ui/team
tags: [kotlin, kmp, compose, ui, attendance, absences, profile, bottom-sheet, material3]
dependency_graph:
  requires: ["04-04"]
  provides: ["04-07"]
  affects: ["composeApp/ui/attendance", "composeApp/ui/team", "composeApp/di"]
tech_stack:
  added: []
  patterns:
    - "ModalBottomSheet with custom drag handle and containerColor for dark theme"
    - "animateContentSize for conditional body part grid reveal"
    - "combinedClickable for tap (edit) vs long-press (delete) on absence cards"
    - "animateFloatAsState(tween 400ms) for attendance stats bar animation"
    - "Backfill status polling: repeat(10) with delay(2000) then give up"
key_files:
  created:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/CoachOverrideSheet.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/AbsenceReasonTile.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/BodyPartGrid.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/WeekdaySelector.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/AbsenceCard.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/AddAbsenceSheet.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/attendance/AttendanceStatsBar.kt
  modified:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt
decisions:
  - "Icons.Outlined.MenuBook kept (not AutoMirrored) — deprecation warning only, consistent with existing codebase pattern"
  - "BodyPartGrid uses two Rows (not LazyVerticalGrid) — simpler for fixed 2x5 layout, avoids scrollable parent conflict"
  - "DatePickerField uses disabled OutlinedTextField with interaction source — avoids custom clickable overlay"
  - "AbsenceCard active status computed from endDate string comparison with today ISO date — no kotlinx-datetime needed"
  - "pollBackfillStatus gives up after 10 polls (20s) — avoids infinite loop if server never responds"
  - "PlayerProfileViewModel 4-param constructor matches UiModule factory(get(), get(), get(), get())"
metrics:
  duration: "~15m"
  completed_date: "2026-03-24"
  tasks_completed: 2
  files_created: 7
  files_modified: 3
---

# Phase 04 Plan 06: Coach Override Sheet + Absence Management UI Summary

**One-liner:** Coach override ModalBottomSheet with 3 status buttons + optional note; full AddAbsenceSheet with 6 reason tiles, injury body part grid, recurring/period toggle; profile screen redesigned with hero card, AttendanceStatsBar, My Absences section, FAB.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | CoachOverrideSheet + absence sub-composables | 6776f37 | CoachOverrideSheet.kt, AbsenceReasonTile.kt, BodyPartGrid.kt, WeekdaySelector.kt, AbsenceCard.kt |
| 2 | AddAbsenceSheet + Profile screen integration | e545191 | AddAbsenceSheet.kt, AttendanceStatsBar.kt, PlayerProfileScreen.kt, PlayerProfileViewModel.kt, UiModule.kt |

## What Was Built

### CoachOverrideSheet
ModalBottomSheet (bg `#13131F`) with player name header + X close button. 3 status buttons (Present/Absent/Excused) with semantic contentDescriptions "Mark as Present/Absent/Excused". Selected states: green/red/yellow per UI-SPEC. Optional note OutlinedTextField. "Save Override" CTA (48dp, `#4F8EF7`) disabled when no status selected.

### AbsenceReasonTile
72dp tappable tile with 24dp icon centered above 12sp label. Selected: `#4F8EF7` border 1.5dp + `#1C1C2E` bg. Unselected: `#2A2A40` border + `#13131F` bg.

### BodyPartGrid
2 rows × 5 columns of tappable cells (56dp height). Labels: Head/Shoulder/Chest/Back/Arm (row 1), Hip/Thigh/Knee/Shin/Foot (row 2). Selected fill `#EF4444` (injury red). Each cell has `contentDescription` matching label text.

### WeekdaySelector
Row of 7 circular toggle buttons (36dp). Labels Mo-Su. Selected: `#4F8EF7` fill, white text. Unselected: `#2A2A40` border, `#6B7280` text. Full day name contentDescriptions (Monday-Sunday).

### AbsenceCard
Card (bg `#1C1C2E`, cornerRadius 12dp). Left: type icon + label (14sp) + date range (12sp Muted). Right: Active/Ended badge. `combinedClickable` for tap=edit, longPress=delete.

### AddAbsenceSheet
Full-height ModalBottomSheet. "Add Absence"/"Edit Absence" header with X close. Scrollable content: 2x3 reason tile grid (gap 8dp) → BodyPartGrid (animateContentSize, Injury only) → SingleChoiceSegmentedButtonRow (Recurring/Period) → WeekdaySelector + end date picker OR start/end date pickers. "Save Rule" CTA (48dp, `#F97316`). Pre-fills from editingRule for edit mode.

### AttendanceStatsBar
Animated LinearProgressIndicator (8dp height, cornerRadius 4dp). Fill `#4F8EF7`, track `#1C1C2E`. Label above: "Attendance · N%". animateFloatAsState with tween(400ms).

### PlayerProfileScreen (redesigned)
- Header bar 64dp with back arrow + "Profile" 16sp SemiBold
- HeroCard (`#1C1C2E`, padding 20dp, cornerRadius 16dp, horizontal margin 20dp): avatar 72dp + name/role chip/info column + AttendanceStatsBar
- "My Absences" section header + "View All" link
- AbsenceCards (first 3) or empty state: "No absences" / "Add an absence rule..."
- Backfill pending hint surface
- FAB bottom-right (`#F97316`, 56dp, contentDescription "Add absence")
- Delete confirm AlertDialog: "Delete absence rule?" / "Delete Rule" (red) / "Keep Rule"

### PlayerProfileViewModel (extended)
Added `abwesenheitRepository: AbwesenheitRepository` + `attendanceRepository: AttendanceRepository` params. New state fields: `presencePct`, `absenceRules`, `backfillStatus`. New methods: `loadAbsences()`, `loadStats(userId)`, `createAbsence()`, `updateAbsence()`, `deleteAbsence()`, `pollBackfillStatus()` (polls every 2s up to 10 times).

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED
