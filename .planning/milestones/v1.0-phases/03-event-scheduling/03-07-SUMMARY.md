---
phase: 03-event-scheduling
plan: "07"
subsystem: ui
tags: [kizitonwose, calendar, compose-multiplatform, kmp, ios, android, koin]

requires:
  - phase: 03-event-scheduling/03-04
    provides: EventRepository interface and EventRepositoryImpl (Ktor)
  - phase: 03-event-scheduling/03-05
    provides: EventListScreen and EventListViewModel patterns

provides:
  - CalendarViewModel with eventsByDate map (multi-day spanning)
  - CalendarScreen with HorizontalCalendar month view (coloured event dots)
  - CalendarScreen with WeekCalendar week view (time-block grid)
  - Month/Week segmented control toggle
  - Day tap shows selected day's events inline
  - CalendarViewModel registered in Koin UiModule
  - Calendar tab wired in AppNavigation (replaced PlaceholderScreen)

affects:
  - Phase 4 attendance tracking (CalendarScreen has isCoach FAB slot)
  - Any future calendar enhancement work

tech-stack:
  added:
    - "com.kizitonwose.calendar:compose-multiplatform:2.7.0 (downgraded from 2.10.0)"
  patterns:
    - "kizitonwose HorizontalCalendar with dayContent lambda for month view"
    - "kizitonwose WeekCalendar with dayContent lambda for week view"
    - "YearMonth arithmetic via custom extension functions (pre-kotlinx.datetime.YearMonth era)"
    - "Multi-day event spanning via date iteration in ViewModel (not UI)"

key-files:
  created:
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/calendar/CalendarScreen.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/ui/calendar/CalendarViewModel.kt
  modified:
    - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt
    - composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt
    - composeApp/build.gradle.kts
    - gradle/libs.versions.toml

key-decisions:
  - "kizitonwose downgraded 2.10.0 → 2.7.0: v2.10.0 references kotlinx.datetime.YearMonth (0.7.x only) causing unlinked iOS symbols; v2.7.0 uses library's own YearMonth type, iOS links cleanly"
  - "Multi-day event spanning in CalendarViewModel (not UI layer): iterates startDate..endDate inclusive, adds event to each date in byDate map"
  - "DayOfWeek.MONDAY via daysOfWeek(firstDayOfWeek=DayOfWeek.MONDAY) utility from kizitonwose"
  - "YearMonth arithmetic as private extension functions (minusMonths/plusMonths) since kotlinx-datetime 0.6.1 lacks YearMonth.plus(months) API"

patterns-established:
  - "Calendar month/week state held in CalendarState with eventsByDate: Map<LocalDate, List<EventWithTeams>>"
  - "kizitonwose dayContent lambda receives CalendarDay/WeekDay with .date: LocalDate for lookup"

requirements-completed: [ES-14, ES-02]

duration: 17min
completed: "2026-03-19"
---

# Phase 3 Plan 7: Calendar Screen Summary

**kizitonwose 2.7.0 HorizontalCalendar + WeekCalendar with coloured event dots, multi-day spanning, and week time-block grid — iOS and Android compatible**

## Performance

- **Duration:** 17 min
- **Started:** 2026-03-19T11:35:23Z
- **Completed:** 2026-03-19T11:52:22Z
- **Tasks:** 2 executed + 1 auto-approved checkpoint
- **Files modified:** 6

## Accomplishments
- CalendarViewModel builds eventsByDate map with multi-day event spanning (iterates startDate to endDate inclusive)
- CalendarScreen month view: kizitonwose HorizontalCalendar with coloured dots (training=blue, match=green, other=purple, cancelled=grey)
- CalendarScreen week view: kizitonwose WeekCalendar with time-block grid, coloured left border, alpha=0.4 + strikethrough for cancelled
- Month/Week segmented control toggle; FAB for coach only; Calendar tab replaces placeholder in navigation

## Task Commits

Each task was committed atomically:

1. **Task 1: Add kizitonwose dependency + validate iOS linkage** - `c29ab7c` (chore)
2. **Task 2: CalendarScreen + CalendarViewModel + navigation wiring** - `30d92dd` (feat)
3. **Task 3: Visual verification checkpoint** - auto-approved (auto_advance=true)

**Plan metadata:** (docs commit below)

## Files Created/Modified
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/calendar/CalendarViewModel.kt` - CalendarState, CalendarViewMode, CalendarViewModel with eventsByDate and multi-day spanning
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/calendar/CalendarScreen.kt` - Full calendar screen: month + week views, DayCell with dots, WeekDayColumn with time blocks
- `composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt` - Calendar route wired (replaces PlaceholderScreen)
- `composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt` - CalendarViewModel Koin factory registered
- `composeApp/build.gradle.kts` - kizitonwose dependency added to commonMain
- `gradle/libs.versions.toml` - kizitonwose version set to 2.7.0

## Decisions Made
- **Downgraded kizitonwose 2.10.0 → 2.7.0:** v2.10.0 uses `kotlinx.datetime.YearMonth` (introduced in 0.7.x). Project pins `kotlinx-datetime:0.6.1` (K/N IR crash on 0.7.x). iOS linkage with 2.10.0 showed `unlinked class symbol 'kotlinx.datetime/YearMonth'` warnings — would crash at runtime on iOS. v2.7.0 uses its own `com.kizitonwose.calendar.core.YearMonth` type, iOS links clean.
- **Multi-day spanning in ViewModel:** event appears on every date from startDate to endDate inclusive. Built as `Map<LocalDate, MutableList<EventWithTeams>>` in ViewModel before passing to composables.
- **YearMonth extension functions:** `minusMonths`/`plusMonths` as private Kotlin extensions since `kotlinx-datetime 0.6.1` has no `YearMonth` type; kizitonwose's own `YearMonth` has no built-in month arithmetic.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Downgraded kizitonwose 2.10.0 → 2.7.0 to fix iOS linkage**
- **Found during:** Task 1 (Add kizitonwose dependency + validate iOS linkage)
- **Issue:** kizitonwose 2.10.0 references `kotlinx.datetime.YearMonth` (0.7.x API). Project pins `kotlinx-datetime:0.6.1`. iOS linkage compiled successfully but produced `unlinked class symbol` warnings — any call to `rememberCalendarState` would crash on iOS at runtime.
- **Fix:** Downgraded `kizitonwose-calendar = "2.10.0"` → `"2.7.0"` in libs.versions.toml. v2.7.0 uses the library's own `com.kizitonwose.calendar.core.YearMonth` type with 0.6.x kotlinx-datetime. iOS links with zero warnings.
- **Files modified:** gradle/libs.versions.toml
- **Verification:** `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL, no unlinked symbol warnings
- **Committed in:** c29ab7c (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug fix on iOS linkage)
**Impact on plan:** Required downgrade from specified 2.10.0. Functionality is identical — HorizontalCalendar, WeekCalendar, rememberCalendarState, rememberWeekCalendarState all present in 2.7.0.

## Issues Encountered
- kizitonwose 2.10.0 task name `compileKotlinAndroid` ambiguous (multiple variants) — used `compileDebugKotlinAndroid` instead. Pre-existing issue unrelated to this plan.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Calendar tab fully functional on both Android and iOS
- CalendarScreen has `if (state.isCoach)` FAB slot ready for Phase 4 (currently `isCoach` defaults false; wired to UserPreferences in future iteration)
- EventsByDate map and multi-day spanning logic established for potential Phase 4 attendance overlay

---
*Phase: 03-event-scheduling*
*Completed: 2026-03-19*
