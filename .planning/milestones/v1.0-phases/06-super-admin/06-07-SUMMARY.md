---
phase: 06-super-admin
plan: "07"
subsystem: server/test
tags: [integration-tests, admin, impersonation, audit-log, tdd]
dependency_graph:
  requires: [06-03, 06-06]
  provides: [SA-integration-tests-verified]
  affects: [server/test/routes]
tech_stack:
  added: []
  patterns: [IntegrationTestBase, withTeamorgTestApplication, testcontainers-postgres]
key_files:
  created:
    - server/src/test/kotlin/ch/teamorg/routes/AdminRoutesTest.kt
    - server/src/test/kotlin/ch/teamorg/routes/ImpersonationRoutesTest.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/routes/ImpersonationRoutes.kt
    - server/src/main/resources/db/migrations/V10__create_admin_tables.sql
decisions:
  - Used ImpersonationStatusResponse @Serializable DTO instead of mixed-type mapOf for /status endpoint
  - Changed audit_log.details + impersonation_context from JSONB to TEXT in V10 migration (Exposed uses text() column)
  - registerSuperAdmin helper inlines DB transaction to set isSuperAdmin flag (no API for this per AUTH-06)
metrics:
  duration_minutes: 4
  tasks_completed: 2
  files_created: 2
  files_modified: 2
  completed_date: "2026-04-04"
---

# Phase 06 Plan 07: Admin + Impersonation Integration Tests Summary

Integration tests for all admin API endpoints and impersonation flow, covering SA-01 through SA-12 API behaviors.

## Tasks Completed

### Task 1: Admin + Impersonation Integration Tests (TDD)

Created two integration test files totaling 15 tests that all pass.

**AdminRoutesTest.kt** (10 tests):
- `non-superadmin gets 403 on admin endpoints` — verifies requireSuperAdmin gate
- `superadmin can get dashboard stats` — DashboardStats response shape
- `superadmin can create and list clubs` — POST + GET /admin/clubs
- `superadmin can get club detail with managers array` — ClubDetail with managers
- `superadmin can update club name` — PATCH /admin/clubs/{id}
- `superadmin can deactivate and reactivate club` — status transitions
- `superadmin can delete club` — soft delete to "deleted" status
- `superadmin can manage club managers` — add + verify + remove ClubManager
- `superadmin can search users` — GET /admin/users?q= filtering
- `audit log captures admin actions` — AuditLogPage with club.create entries

**ImpersonationRoutesTest.kt** (5 tests):
- `superadmin can start impersonation` — ImpersonationResponse with token + sessionId
- `cannot impersonate another superadmin` — 400 rejection
- `impersonation status reports active when using impersonation token` — ImpersonationStatusResponse
- `can end impersonation session` — POST /admin/impersonate/end
- `normal token reports not impersonating` — impersonating=false for regular JWT

### Task 2: Human Verification of Admin Panel

Auto-approved in --auto mode. Full admin panel built in plans 06-01 through 06-06.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed JSONB vs TEXT column type mismatch in audit_log**
- **Found during:** Task 1 execution
- **Issue:** V10 migration created `details` and `impersonation_context` as `JSONB` but `AuditLogTable.kt` uses `text()` column. PostgreSQL rejected inserts with "column details is of type jsonb but expression is of type character varying"
- **Fix:** Changed both columns to `TEXT` in V10 migration (consistent with Exposed table definition, same semantics)
- **Files modified:** `server/src/main/resources/db/migrations/V10__create_admin_tables.sql`
- **Commit:** 5b2b455

**2. [Rule 1 - Bug] Fixed mixed-type map serialization in impersonation status endpoint**
- **Found during:** Task 1 execution
- **Issue:** `GET /admin/impersonate/status` returned `mapOf("impersonating" to true, "remainingSeconds" to 3600L, ...)` — mixed Boolean/String/Long types. Ktor's kotlinx.serialization content negotiation threw `IllegalStateException: Serializing collections of different element types is not yet supported`
- **Fix:** Added `ImpersonationStatusResponse @Serializable` data class; updated endpoint to use it instead of raw map
- **Files modified:** `server/src/main/kotlin/ch/teamorg/routes/ImpersonationRoutes.kt`
- **Commit:** 5b2b455

## Self-Check: PASSED

- FOUND: AdminRoutesTest.kt
- FOUND: ImpersonationRoutesTest.kt
- FOUND: commit 5b2b455
