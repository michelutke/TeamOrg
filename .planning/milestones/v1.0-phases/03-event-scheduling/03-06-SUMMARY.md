---
phase: 03-event-scheduling
plan: "06"
subsystem: ui-events
tags: [compose, viewmodel, forms, recurring-events, bottom-sheets, navigation, koin]
dependency_graph:
  requires: [03-03, 03-04, 03-05]
  provides: [create-edit-form-ui, recurring-pattern-sheet, scope-sheet, create-edit-navigation]
  affects: [AppNavigation, UiModule, EventDetailScreen]
tech_stack:
  added: []
  patterns: [ModalBottomSheet, DatePickerDialog, TimePicker, SingleChoiceSegmentedButtonRow, FilterChip]
key_files:
  created:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/CreateEditEventViewModel.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/CreateEditEventScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/RecurringPatternSheet.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/RecurringScopeSheet.kt
  modified:
    - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt
decisions:
  - "Local val capture for nullable RecurringPatternState required for Kotlin smart cast across module boundary"
  - "detailRefreshTrigger incremented in both EditEvent onSaved and EventDetail onCancel to cover all write paths"
  - "isDuplicate flag passed into loadForEdit rather than separate ViewModel instance to avoid race condition with async fetch"
metrics:
  duration_seconds: 293
  completed_date: "2026-03-19"
  tasks_completed: 4
  files_modified: 6
---

# Phase 3 Plan 6: Create/Edit Event UI Summary

Event create/edit form (S4), recurring pattern sheet (S5), scope sheet (S6), and navigation wiring with full recurring support via ModalBottomSheet and Material 3 date/time pickers.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | CreateEditEventViewModel + form state | 7e25992 | CreateEditEventViewModel.kt |
| 2 | CreateEditEventScreen composable | 12ff84a | CreateEditEventScreen.kt |
| 3 | RecurringPatternSheet + RecurringScopeSheet | a2980a2 | RecurringPatternSheet.kt, RecurringScopeSheet.kt |
| 4 | Navigation wiring + Koin registration | 55da62d | AppNavigation.kt, UiModule.kt |

## What Was Built

**CreateEditEventViewModel** manages all form state for create/edit/duplicate flows:
- `CreateEditEventState` covers all fields including recurring pattern, sub-groups, min attendees
- `save()` converts local date/time to `Instant` via `LocalDateTime(...).toInstant(TimeZone.currentSystemDefault())`
- `loadForEdit()` / `loadForDuplicate()` pre-fill from existing event; `isDuplicate=true` clears series info and sets `isEditMode=false`
- `cancelEvent(scope)` delegates to `eventRepository.cancelEvent`
- `validate()` checks: non-blank title, end after start, at least one team
- `loadSubgroupsForSelectedTeams()` fetches sub-groups from all selected teams (ES-05)

**CreateEditEventScreen** renders 6 sections per UI-SPEC S4:
- Section 1: title OutlinedTextField, type SingleChoiceSegmentedButtonRow (Training/Match/Other with type colours), team multi-select FilterChip group
- Section 2: start/end date rows (DatePickerDialog), time rows (TimePicker in AlertDialog), timezone label inline, meetup time toggle
- Section 3: location field
- Section 4: Audience / sub-group targeting — visible only when teams selected
- Section 5: min attendees Switch + NumberField, description multiline
- Section 6: recurring Switch — auto-opens RecurringPatternSheet when no pattern set; shows summary chip when pattern exists
- Sticky CTA bar: "Create Event" / "Save Changes" + "Cancel", disabled during save
- Inline validation errors for title, end time, team selection

**RecurringPatternSheet** (S5): Daily/Weekly/Custom radio group, Mon-Sun FilterChip weekday grid, custom interval number field, end date Switch + DatePickerDialog

**RecurringScopeSheet** (S6): "Edit recurring event" or "Cancel recurring event" header (parameterized), three radio options (this_only / this_and_future / all), Continue + Cancel buttons

**Navigation**: CreateEvent and EditEvent routes replaced from PlaceholderScreen; EditEvent uses `LaunchedEffect(screen.eventId)` to load form; `detailRefreshTrigger++` on both EditEvent onSaved and EventDetail onCancel; Koin factory registered

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Missing kotlinx-datetime imports in RecurringPatternSheet**
- **Found during:** Compile check after Task 3
- **Issue:** `toLocalDateTime` unresolved reference — fully qualified path used but extension function not imported
- **Fix:** Added `Clock`, `TimeZone`, `toLocalDateTime` imports; used short names
- **Files modified:** RecurringPatternSheet.kt
- **Commit:** 0119adc

**2. [Rule 1 - Bug] Kotlin smart cast failure for nullable RecurringPatternState**
- **Found during:** Compile check after Task 2
- **Issue:** `state.recurringPattern` is a cross-module public API property; Kotlin cannot smart-cast after null check
- **Fix:** Captured as local val before if-check
- **Files modified:** CreateEditEventScreen.kt
- **Commit:** 0119adc

## Self-Check: PASSED

All created files verified to exist. All commits verified in git log.
