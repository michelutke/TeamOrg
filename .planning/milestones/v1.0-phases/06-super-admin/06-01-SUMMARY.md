---
phase: 06-super-admin
plan: 01
subsystem: database
tags: [postgres, flyway, exposed, ktor, koin, audit-log, impersonation]

# Dependency graph
requires:
  - phase: 05-notifications
    provides: NotificationRepository pattern, Koin module structure
  - phase: 01-foundation-auth
    provides: UsersTable, isSuperAdmin flag, AuthMiddleware.authenticateUser

provides:
  - V10 migration: clubs.status column + audit_log table + impersonation_sessions table
  - AuditLogTable Exposed object
  - ImpersonationSessionsTable Exposed object
  - AuditLogRepository (log, query paginated, count)
  - AdminRepository (dashboard stats, user search, club CRUD, manager management)
  - requireSuperAdmin middleware (403 gate)
  - UserRepository extended with findAll, countAll, searchByNameOrEmail

affects:
  - 06-02 (admin API routes use all repositories + middleware from this plan)
  - 06-03 (SvelteKit calls admin routes)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AuditLogRepository: non-suspend fun (synchronous transaction) — matches UserRepository pattern"
    - "AdminRepository: all methods non-suspend, transaction {} wraps Exposed DSL queries"
    - "requireSuperAdmin: delegates to authenticateUser then checks isSuperAdmin field"
    - "Soft delete for clubs: status column (active/deactivated/deleted), data never hard-deleted"

key-files:
  created:
    - server/src/main/resources/db/migrations/V10__create_admin_tables.sql
    - server/src/main/kotlin/ch/teamorg/db/tables/AuditLogTable.kt
    - server/src/main/kotlin/ch/teamorg/db/tables/ImpersonationSessionsTable.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/AuditLogRepository.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/AdminRepository.kt
    - server/src/main/kotlin/ch/teamorg/middleware/AdminMiddleware.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/db/tables/ClubsTable.kt
    - server/src/main/kotlin/ch/teamorg/domain/repositories/UserRepository.kt
    - server/src/main/kotlin/ch/teamorg/plugins/Koin.kt

key-decisions:
  - "audit_log uses JSONB columns in SQL but Exposed maps them as TEXT (stored as JSON strings) — avoids exposed-json dependency"
  - "AuditLogRepository uses non-suspend funs matching UserRepository convention (not ClubRepository which is suspend)"
  - "AdminRepository.deleteClub() soft-deletes by setting status='deleted' — data preserved per SA-05 wording"
  - "SA-12 DB role immutability: documented in SQL COMMENT ON TABLE; REVOKE must be run as superuser in production"
  - "clubs.status CHECK constraint: active/deactivated/deleted — migration enforces valid values at DB level"

requirements-completed:
  - SA-09
  - SA-10
  - SA-11
  - SA-12

# Metrics
duration: 3min
completed: 2026-04-04
---

# Phase 06 Plan 01: Super Admin DB Foundation Summary

**V10 Flyway migration + AuditLogRepository + AdminRepository + requireSuperAdmin middleware providing the complete backend foundation for the SA panel**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-04T18:42:02Z
- **Completed:** 2026-04-04T18:45:23Z
- **Tasks:** 2
- **Files modified:** 9 (4 created, 3 modified, 2 new Exposed tables)

## Accomplishments

- V10 migration adds clubs.status column + creates audit_log (with immutability comment for production REVOKE) + creates impersonation_sessions table
- AuditLogRepository with paginated query, filtering by action/actor/date range, and INSERT-only log()
- AdminRepository with dashboard aggregates, paginated user search with club memberships, club CRUD (soft delete), manager management, and user detail with team+club memberships
- requireSuperAdmin middleware gate (403 for non-SA, delegates to existing authenticateUser)

## Task Commits

Each task was committed atomically:

1. **Task 1: V10 migration + Exposed table objects** - `0bde2a2` (feat)
2. **Task 2: Repositories + middleware + Koin wiring** - `57a715a` (feat)

## Files Created/Modified

- `server/src/main/resources/db/migrations/V10__create_admin_tables.sql` - clubs.status ALTER, CREATE audit_log, CREATE impersonation_sessions
- `server/src/main/kotlin/ch/teamorg/db/tables/AuditLogTable.kt` - Exposed object for audit_log
- `server/src/main/kotlin/ch/teamorg/db/tables/ImpersonationSessionsTable.kt` - Exposed object for impersonation_sessions
- `server/src/main/kotlin/ch/teamorg/db/tables/ClubsTable.kt` - added val status column
- `server/src/main/kotlin/ch/teamorg/domain/repositories/AuditLogRepository.kt` - interface + impl + AuditLogEntry + AuditLogPage data classes
- `server/src/main/kotlin/ch/teamorg/domain/repositories/AdminRepository.kt` - interface + impl + DashboardStats + UserSearchResult + ClubListItem + ClubDetail + UserDetail data classes
- `server/src/main/kotlin/ch/teamorg/middleware/AdminMiddleware.kt` - requireSuperAdmin extension function
- `server/src/main/kotlin/ch/teamorg/domain/repositories/UserRepository.kt` - added findAll, countAll, searchByNameOrEmail
- `server/src/main/kotlin/ch/teamorg/plugins/Koin.kt` - registered AuditLogRepository + AdminRepository

## Decisions Made

- JSONB columns in SQL stored as TEXT in Exposed — avoids needing exposed-json dependency, consistent with how other JSON-like fields are handled in the codebase
- Non-suspend funs throughout (matching UserRepository pattern, not ClubRepository's suspend pattern) — admin ops are batch/background, no need for coroutine context
- Soft delete (status='deleted') for clubs so historical data (teams, events, attendance) is preserved
- DB-level immutability (REVOKE UPDATE/DELETE) is documented in migration COMMENT but must be applied by superuser in production; app role enforcement via application code only in development

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All repositories and middleware ready for plan 06-02 (admin API routes)
- requireSuperAdmin gate tested by compilation; full integration tests in 06-07
- Production DB immutability requires manual REVOKE: `REVOKE UPDATE, DELETE ON audit_log FROM teamorg_app;`

---
*Phase: 06-super-admin*
*Completed: 2026-04-04*
