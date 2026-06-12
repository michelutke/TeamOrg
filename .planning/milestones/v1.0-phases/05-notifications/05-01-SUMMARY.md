---
phase: 05-notifications
plan: "01"
subsystem: server-notifications-foundation
tags: [notifications, push, onesignal, exposed, flyway, repository, koin]
dependency_graph:
  requires: []
  provides: [V9-migration, NotificationTables, PushService, NotificationRepository]
  affects: [05-02, 05-03, 05-04, 05-05, 05-06]
tech_stack:
  added: [ktor-client-cio, ktor-client-content-negotiation]
  patterns: [exposed-transaction, insertIgnore-dedup, koin-single]
key_files:
  created:
    - server/src/main/resources/db/migrations/V9__create_notifications.sql
    - server/src/main/kotlin/ch/teamorg/db/tables/NotificationTables.kt
    - server/src/main/kotlin/ch/teamorg/domain/models/NotificationModels.kt
    - server/src/main/kotlin/ch/teamorg/infra/PushService.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/NotificationRepository.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/plugins/Koin.kt
    - server/build.gradle.kts
    - gradle/libs.versions.toml
decisions:
  - "Used timestamp() (java.time.Instant) not timestampWithTimeZone() — timestampWithTimeZone not in exposed-java-time 0.54.0 jar; timestamp() maps correctly to TIMESTAMPTZ via PostgreSQL JDBC"
  - "Used insertIgnore for dedup in createNotification/createBatch — maps to INSERT ... ON CONFLICT DO NOTHING via Exposed, returns insertedCount to detect duplicates"
  - "Added ktor-client-cio + ktor-client-content-negotiation to server production deps — PushService needs HTTP client; CIO engine is JVM-native, no extra deps"
metrics:
  duration_minutes: 3
  tasks_completed: 2
  files_created: 5
  files_modified: 3
  completed_date: "2026-03-26"
---

# Phase 5 Plan 1: Notification Foundation Summary

**One-liner:** V9 Flyway migration + 4 Exposed table objects + PushService (OneSignal REST) + NotificationRepository (dedup, settings, membership) registered in Koin.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | V9 migration + Exposed tables + DTOs | e673926 | V9__create_notifications.sql, NotificationTables.kt, NotificationModels.kt |
| 2 | PushService + NotificationRepository + Koin | cf1cb7d | PushService.kt, NotificationRepository.kt, Koin.kt |

## What Was Built

### V9 Migration
4 tables: `notifications` (with dedup unique index on user_id+idempotency_key), `notification_settings` (per user+team), `event_reminder_overrides` (per user+event), `notification_reminders` (scheduler queue).

### NotificationTables.kt
4 Exposed table objects in `ch.teamorg.db.tables` matching V9 schema. Uses `timestamp()` (java.time.Instant) consistent with existing tables.

### NotificationModels.kt
`NotificationResponse`, `NotificationSettingsResponse`, `UpdateNotificationSettingsRequest` (all-nullable for partial update), `ReminderOverrideRequest`.

### PushService
Interface + `PushServiceImpl` wrapping OneSignal REST API. Fire-and-forget: errors logged, not thrown. Early return on empty userIds.

### NotificationRepository
Full interface + impl with:
- `createNotification` / `createBatch`: `insertIgnore` for ON CONFLICT DO NOTHING dedup
- `isUserEligible`: checks `notification_settings` toggles by type string → column mapping; defaults true if no row
- `isDuplicate`: checks idempotency_key existence
- `getTeamMemberIds`: queries `team_roles WHERE user_id IS NOT NULL` (removed-member guard)
- `upsertReminderOverride`: select-then-insert-or-update pattern
- `deleteOldNotifications`: timestamp cutoff deletion for 90-day retention

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] timestampWithTimeZone() not available in exposed-java-time 0.54.0**
- **Found during:** Task 1 — jar inspection confirmed function absent
- **Fix:** Used `timestamp()` (java.time.Instant) throughout NotificationTables.kt — consistent with all existing table files; PostgreSQL JDBC handles TIMESTAMPTZ mapping transparently
- **Files modified:** NotificationTables.kt

**2. [Rule 3 - Blocking] No Ktor client deps in server production build**
- **Found during:** Task 2 — PushService needs HttpClient; server build.gradle.kts only had client deps in testImplementation
- **Fix:** Added `ktor-clientCio` to libs.versions.toml and added `ktor.clientCio` + `ktor.clientContentNegotiation` to server production deps
- **Files modified:** gradle/libs.versions.toml, server/build.gradle.kts

## Self-Check: PASSED

Files created:
- [x] server/src/main/resources/db/migrations/V9__create_notifications.sql
- [x] server/src/main/kotlin/ch/teamorg/db/tables/NotificationTables.kt
- [x] server/src/main/kotlin/ch/teamorg/domain/models/NotificationModels.kt
- [x] server/src/main/kotlin/ch/teamorg/infra/PushService.kt
- [x] server/src/main/kotlin/ch/teamorg/domain/repositories/NotificationRepository.kt

Commits verified: e673926, cf1cb7d
Compilation: BUILD SUCCESSFUL (server:compileKotlin)
