---
phase: 05-notifications
plan: 03
subsystem: api
tags: [kotlin, kmp, sqldelight, ktor, koin, notifications]

requires:
  - phase: 03-event-scheduling
    provides: EventCacheManager, EventRepositoryImpl patterns used as templates
  - phase: 04-attendance-tracking
    provides: AttendanceCacheManager, AttendanceRepositoryImpl patterns used as templates

provides:
  - Notification domain models (Notification, NotificationSettings, UpdateNotificationSettingsRequest, ReminderOverride, UnreadCountResponse, MarkAllReadResponse)
  - NotificationRepository interface with 8 suspend funs returning Result<T>
  - SQLDelight cached_notification table with upsert/read/markRead/markAllRead/cleanup queries
  - NotificationCacheManager for local inbox cache reads and writes
  - NotificationRepositoryImpl calling all 8 server notification endpoints with offline cache fallback
  - Koin DI wiring on Android and iOS platform modules

affects: [05-notifications-viewmodels, 05-notifications-ui, 05-notifications-push]

tech-stack:
  added: []
  patterns:
    - "NotificationCacheManager: parseIsoToEpochMillis/epochMillisToIso helpers for ISO-8601 <-> epoch millis round-trip"
    - "NotificationRepositoryImpl: relative URL paths (no base URL prefix) — base URL configured in HttpClientFactory"
    - "Offline fallback covers ConnectTimeoutException, HttpRequestTimeoutException, IOException; write ops return failure offline"

key-files:
  created:
    - shared/src/commonMain/kotlin/ch/teamorg/domain/Notification.kt
    - shared/src/commonMain/kotlin/ch/teamorg/repository/NotificationRepository.kt
    - shared/src/commonMain/sqldelight/ch/teamorg/Notification.sq
    - shared/src/commonMain/kotlin/ch/teamorg/data/NotificationCacheManager.kt
    - shared/src/commonMain/kotlin/ch/teamorg/data/repository/NotificationRepositoryImpl.kt
  modified:
    - shared/src/androidMain/kotlin/ch/teamorg/di/SharedModule.android.kt
    - shared/src/iosMain/kotlin/ch/teamorg/di/SharedModule.ios.kt

key-decisions:
  - "createdAt stored as String (ISO-8601) in domain model — avoids kotlinx-datetime Instant serialization complexity, consistent with AbwesenheitRule date fields"
  - "NotificationRepositoryImpl uses relative URLs — base URL is set in HttpClientFactory, same pattern as all other repository impls"
  - "Write ops (markRead, markAllRead, updateSettings, setReminderOverride) return failure offline — no optimistic queue for notification actions"
  - "getSettings/getReminderOverride return failure offline — no local cache for settings (not in schema)"

patterns-established:
  - "ISO string <-> epoch millis: parseIsoToEpochMillis / epochMillisToIso private helpers in cache manager"

requirements-completed: [NO-08, NO-09, NO-11]

duration: 2min
completed: 2026-03-26
---

# Phase 05 Plan 03: Shared KMP Notification Contracts Summary

**SQLDelight-backed notification inbox cache, Ktor repository impl for all 8 notification endpoints, and Koin wiring on both platforms**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-26T08:18:14Z
- **Completed:** 2026-03-26T08:20:19Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- Domain models for notifications inbox, settings, and reminder overrides in commonMain
- NotificationRepository interface with Result<T> returns covering all client-side operations
- SQLDelight schema with cached_notification table + 7 queries (upsert, paginated fetch, unread count, mark read, mark all read, cleanup, delete all)
- NotificationCacheManager bridging ISO-8601 timestamps to epoch millis for SQLDelight storage
- NotificationRepositoryImpl hitting all 8 server endpoints with offline cache fallback for read ops
- Koin registration complete on Android and iOS

## Task Commits

1. **Task 1: Domain models + repository interface + SQLDelight schema** - `174d310` (feat)
2. **Task 2: NotificationCacheManager + NotificationRepositoryImpl + Koin wiring** - `d035d93` (feat)

## Files Created/Modified

- `shared/src/commonMain/kotlin/ch/teamorg/domain/Notification.kt` - 6 serializable data classes for notification domain
- `shared/src/commonMain/kotlin/ch/teamorg/repository/NotificationRepository.kt` - Interface with 8 suspend funs
- `shared/src/commonMain/sqldelight/ch/teamorg/Notification.sq` - cached_notification table + 7 queries
- `shared/src/commonMain/kotlin/ch/teamorg/data/NotificationCacheManager.kt` - Cache read/write with ISO<->millis helpers
- `shared/src/commonMain/kotlin/ch/teamorg/data/repository/NotificationRepositoryImpl.kt` - Ktor HTTP + offline fallback
- `shared/src/androidMain/kotlin/ch/teamorg/di/SharedModule.android.kt` - Added NotificationCacheManager + NotificationRepositoryImpl
- `shared/src/iosMain/kotlin/ch/teamorg/di/SharedModule.ios.kt` - Added NotificationCacheManager + NotificationRepositoryImpl

## Decisions Made

- `createdAt` stored as String (ISO-8601) in `Notification` domain model — avoids kotlinx-datetime Instant serialization complexity; consistent with `AbwesenheitRule` date fields
- Repository uses relative URL paths (`/notifications`, `/events/{id}/reminder`) — base URL is configured in `HttpClientFactory`, matching all other repository impls
- Write operations (markRead, markAllRead, updateSettings, setReminderOverride) return `Result.failure` when offline — no mutation queue needed for notification actions
- Settings and reminder override have no local SQLDelight cache — not in schema by design (settings are small and always-fresh preferred)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Plan spec mentioned `${ApiConfig.BASE_URL}` prefix in URLs, but all existing impls use relative paths. Used relative paths to match actual codebase pattern.

## Next Phase Readiness

- NotificationRepository + cache ready for ViewModel injection
- Both platform Koin modules updated — no additional DI wiring needed
- Next: ViewModels consuming NotificationRepository (inbox screen, settings screen, unread badge)

---
*Phase: 05-notifications*
*Completed: 2026-03-26*
