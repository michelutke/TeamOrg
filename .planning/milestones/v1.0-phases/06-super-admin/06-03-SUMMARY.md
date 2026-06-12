---
phase: 06-super-admin
plan: 03
subsystem: server-api
tags: [ktor, admin-api, impersonation, jwt, audit-log, club-management]

# Dependency graph
requires:
  - phase: 06-01
    provides: AdminRepository, AuditLogRepository, AdminMiddleware, ImpersonationSessionsTable

provides:
  - AdminRoutes.kt: all admin club + user + audit endpoints under /admin
  - ImpersonationRoutes.kt: impersonation start/end/status under /admin/impersonate
  - createClub() added to AdminRepository interface + impl

affects:
  - 06-04 (SvelteKit admin panel calls these routes)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Admin routes use requireSuperAdmin middleware gate on every endpoint"
    - "All mutations audit-logged with actorId, actorEmail, action, targetType, targetId, details"
    - "Impersonation JWT uses withClaim(impersonator_id) + withClaim(impersonation_session_id) + subject = targetUserId"
    - "POST /end extracts impersonator from JWT claims — not requiring SuperAdmin re-auth"
    - "updateClub() returns Unit — route re-fetches via findClubWithManagers() for response"

key-files:
  created:
    - server/src/main/kotlin/ch/teamorg/routes/AdminRoutes.kt
    - server/src/main/kotlin/ch/teamorg/routes/ImpersonationRoutes.kt
  modified:
    - server/src/main/kotlin/ch/teamorg/domain/repositories/AdminRepository.kt
    - server/src/main/kotlin/ch/teamorg/plugins/Routing.kt

key-decisions:
  - "createClub() added to AdminRepository (not using ClubRepository.create which assigns creator club_manager role) — SA creates clubs without self-assigning"
  - "PATCH /admin/clubs/{clubId} re-fetches via findClubWithManagers after update (AdminRepository.updateClub returns Unit)"
  - "POST /admin/impersonate/end uses JWT claims for impersonatorId — not requireSuperAdmin (token is impersonation token, not SA token)"

requirements-completed:
  - SA-01
  - SA-02
  - SA-04
  - SA-05
  - SA-06
  - SA-07
  - SA-08

# Metrics
duration: 6min
completed: 2026-04-04
---

# Phase 06 Plan 03: Admin API Routes Summary

**Ktor admin API routes for club CRUD, ClubManager management, user search, audit log query, and scoped impersonation JWT — all endpoints gated by requireSuperAdmin and audit-logged**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-04T18:45:00Z
- **Completed:** 2026-04-04T18:51:39Z
- **Tasks:** 2
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments

- AdminRoutes.kt with 13 endpoints: club list/detail/create/update/deactivate/reactivate/delete, manager add/remove, user search/detail, audit-log query, dashboard stats
- ImpersonationRoutes.kt: POST /start issues scoped 1h JWT (subject=targetUser, claims: impersonator_id + impersonation_session_id), POST /end deactivates session row, GET /status returns remainingSeconds
- All mutations (8 endpoints) call auditLogRepository.log() with actor, action, target, details
- createClub() added to AdminRepository interface + impl — SA club creation doesn't assign creator role (unlike ClubRepository.create)

## Task Commits

Each task was committed atomically:

1. **Task 1: Admin club management routes** - `28ae8c2` (feat)
2. **Task 2: Impersonation routes with scoped JWT** - `b39a92c` (feat)

## Files Created/Modified

- `server/src/main/kotlin/ch/teamorg/routes/AdminRoutes.kt` — 13 endpoints, 3 request DTOs
- `server/src/main/kotlin/ch/teamorg/routes/ImpersonationRoutes.kt` — start/end/status, 3 response DTOs
- `server/src/main/kotlin/ch/teamorg/domain/repositories/AdminRepository.kt` — added createClub() to interface + impl
- `server/src/main/kotlin/ch/teamorg/plugins/Routing.kt` — registered adminRoutes() + impersonationRoutes()

## Decisions Made

- createClub() placed in AdminRepository (not ClubRepository) — ClubRepository.create() auto-assigns creator as club_manager which is wrong for SA-initiated club creation
- PATCH /clubs/{clubId} re-fetches via findClubWithManagers() since AdminRepository.updateClub() returns Unit (matches existing non-suspend pattern)
- POST /impersonate/end extracts impersonator identity from JWT claims, not from requireSuperAdmin — the caller is using an impersonation token (subject=target), not their SA token

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing functionality] Added createClub() to AdminRepository**
- **Found during:** Task 1
- **Issue:** Plan's POST /admin/clubs called `adminRepository.createClub()` but method didn't exist in AdminRepository interface or impl
- **Fix:** Added `createClub(name, sportType, location): ClubListItem` to interface and impl; impl does direct ClubsTable INSERT without assigning club_manager role
- **Files modified:** `server/src/main/kotlin/ch/teamorg/domain/repositories/AdminRepository.kt`
- **Commit:** `28ae8c2`

**2. [Rule 1 - Bug] updateClub() return type mismatch**
- **Found during:** Task 1
- **Issue:** Plan's PATCH route expected `updateClub()` to return a Club but AdminRepository.updateClub() returns Unit
- **Fix:** Route calls updateClub() then re-fetches via findClubWithManagers() for the response body; no interface change needed
- **Files modified:** `server/src/main/kotlin/ch/teamorg/routes/AdminRoutes.kt`
- **Commit:** `28ae8c2`

## Self-Check: PASSED

- `AdminRoutes.kt` exists: FOUND
- `ImpersonationRoutes.kt` exists: FOUND
- Commit `28ae8c2` exists: FOUND
- Commit `b39a92c` exists: FOUND
- Server compiles: BUILD SUCCESSFUL (12s, warnings pre-existing only)
