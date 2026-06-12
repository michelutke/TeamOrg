---
phase: 03-event-scheduling
plan: 04
subsystem: database
tags: [sqldelight, ktor, kmp, koin, offline-cache, kotlin]

requires:
  - phase: 03-01
    provides: Event DB foundation + EventRepository interface
  - phase: 03-03
    provides: Event domain models (Event, EventWithTeams, SubGroup, etc.)
provides:
  - SQLDelight CachedEvent table schema (Event.sq) with filtered queries and upsert
  - EventCacheManager: save/read/cleanup of offline event cache (3-month window)
  - EventRepositoryImpl: Ktor HTTP calls for all 7 event endpoints + offline fallback
  - DatabaseDriverFactory expect/actual for Android + iOS
  - EventRepository + EventCacheManager registered in Koin SharedModule (both platforms)
affects: [04-attendance-tracking, 05-notifications, 06-super-admin, composeApp event UI plans]

tech-stack:
  added: [sqldelight/android-driver, sqldelight/native-driver (already in build.gradle.kts)]
  patterns:
    - SQLDelight expect/actual DatabaseDriverFactory injected via Koin
    - Cache-aside pattern: write on successful network fetch, read on network failure
    - Selective offline fallback: only ConnectTimeoutException, HttpRequestTimeoutException, IOException

key-files:
  created:
    - shared/src/commonMain/sqldelight/ch/teamorg/Event.sq
    - shared/src/commonMain/kotlin/ch/teamorg/data/DatabaseDriverFactory.kt
    - shared/src/androidMain/kotlin/ch/teamorg/data/DatabaseDriverFactory.android.kt
    - shared/src/iosMain/kotlin/ch/teamorg/data/DatabaseDriverFactory.ios.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/EventCacheManager.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/repository/EventRepositoryImpl.kt
  modified:
    - shared/src/androidMain/kotlin/ch/teamorg/di/SharedModule.android.kt
    - shared/src/iosMain/kotlin/ch/teamorg/di/SharedModule.ios.kt
    - shared/src/commonMain/kotlin/ch/teamorg/di/SharedModule.kt

key-decisions:
  - "Generated CachedEvent is in package ch.teamorg (not ch.teamorg.db) — SQLDelight 2.0.2 uses the .sq file package path"
  - "DatabaseDriverFactory uses expect/actual to provide platform-specific SqlDriver; injected into Koin as single, TeamorgDb built via createDatabase()"
  - "Offline fallback covers ConnectTimeoutException + HttpRequestTimeoutException + IOException only; ResponseException (4xx/5xx) propagates to caller"
  - "Timestamps stored as epoch millis (Long) in SQLite; converted to/from kotlinx.datetime.Instant in EventCacheManager"

patterns-established:
  - "DatabaseDriverFactory pattern: expect class in commonMain, actual in androidMain + iosMain, createDatabase() top-level factory function"
  - "Cache-aside in repository: save on success, offlineFallback() on network exception, never on server error"

requirements-completed: [ES-11, ES-13, ES-16]

duration: 25min
completed: 2026-03-19
---

# Phase 3 Plan 04: Event Repository + SQLDelight Cache Summary

**KMP EventRepositoryImpl with Ktor HTTP calls for all 7 event endpoints and SQLDelight offline read cache via cache-aside pattern**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-03-19T11:15:00Z
- **Completed:** 2026-03-19T11:40:00Z
- **Tasks:** 2
- **Files modified:** 8 (6 created, 2 modified + 1 comment removed)

## Accomplishments
- SQLDelight CachedEvent table with getUpcomingEvents, getFilteredEvents, upsertEvent, deleteOlderThan queries
- EventCacheManager with saveEvents, getOfflineEvents, getFilteredOfflineEvents, cleanup (7-day staleness purge)
- EventRepositoryImpl: all 7 API calls; getMyEvents only falls back to cache on network exceptions (not server errors)
- Expect/actual DatabaseDriverFactory wired into Koin on both Android and iOS

## Task Commits

1. **Task 1: SQLDelight event cache schema** - `a2986d4` (feat)
2. **Task 2: EventRepositoryImpl + EventCacheManager + Koin wiring** - `a395e40` (feat)

**Plan metadata:** _(docs commit follows)_

## Files Created/Modified
- `shared/src/commonMain/sqldelight/ch/teamorg/Event.sq` - CachedEvent table schema + 6 queries
- `shared/src/commonMain/kotlin/ch/teamorg/data/DatabaseDriverFactory.kt` - expect class + createDatabase() factory
- `shared/src/androidMain/kotlin/ch/teamorg/data/DatabaseDriverFactory.android.kt` - AndroidSqliteDriver actual
- `shared/src/iosMain/kotlin/ch/teamorg/data/DatabaseDriverFactory.ios.kt` - NativeSqliteDriver actual
- `shared/src/commonMain/kotlin/ch/teamorg/data/EventCacheManager.kt` - cache read/write with row mapping
- `shared/src/commonMain/kotlin/ch/teamorg/data/repository/EventRepositoryImpl.kt` - all 7 HTTP calls + offline fallback
- `shared/src/androidMain/kotlin/ch/teamorg/di/SharedModule.android.kt` - added DB + cache + EventRepository
- `shared/src/iosMain/kotlin/ch/teamorg/di/SharedModule.ios.kt` - added DB + cache + EventRepository

## Decisions Made
- SQLDelight 2.0.2 generates `CachedEvent` in package `ch.teamorg` (the .sq file's directory path), not `ch.teamorg.db`. Fixed import in EventCacheManager after first compile attempt.
- Used `singleOf(::EventCacheManager)` (no binding needed — used concretely) and `singleOf(::EventRepositoryImpl) bind EventRepository::class` matching existing Koin conventions.
- `createdAt` and `updatedAt` in cached Event rows are approximated with `cached_at` epoch millis — these fields aren't stored in the cache table to keep schema minimal.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed wrong CachedEvent package import**
- **Found during:** Task 2 (first compile run)
- **Issue:** EventCacheManager referenced `ch.teamorg.db.CachedEvent` but SQLDelight 2.0.2 generates it in `ch.teamorg`
- **Fix:** Changed type reference to `ch.teamorg.CachedEvent`
- **Files modified:** shared/src/commonMain/kotlin/ch/teamorg/data/EventCacheManager.kt
- **Verification:** `./gradlew :shared:compileDebugKotlinAndroid` passes
- **Committed in:** a395e40 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug)
**Impact on plan:** Package path correction required for generated code; no scope creep.

## Issues Encountered
- SQLDelight `generateSqlDelightInterface` task was already UP-TO-DATE before the first compile; compile errors were caught on `compileDebugKotlinAndroid` since code generation uses the cached output.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- EventRepository is ready for UI ViewModels (Plans 05-07) to inject and call
- Offline cache writes on every successful getMyEvents fetch — 3-month window via caller-provided date range
- Server 5xx errors still surface to ViewModel (not silently swallowed by cache fallback)

---
*Phase: 03-event-scheduling*
*Completed: 2026-03-19*
