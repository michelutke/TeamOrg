---
plan: "03"
wave: 2
phase: 2
title: "Club + Team CRUD API endpoints"
depends_on: ["01", "02"]
autonomous: true
files_modified:
  - server/src/main/kotlin/ch/teamorg/repository/ClubRepository.kt
  - server/src/main/kotlin/ch/teamorg/data/repository/ClubRepositoryImpl.kt
  - server/src/main/kotlin/ch/teamorg/repository/TeamRepository.kt
  - server/src/main/kotlin/ch/teamorg/data/repository/TeamRepositoryImpl.kt
  - server/src/main/kotlin/ch/teamorg/routes/ClubRoutes.kt
  - server/src/main/kotlin/ch/teamorg/routes/TeamRoutes.kt
  - server/src/main/kotlin/ch/teamorg/middleware/RoleMiddleware.kt
  - server/src/test/kotlin/ch/teamorg/routes/ClubRoutesTest.kt
  - server/src/test/kotlin/ch/teamorg/routes/TeamRoutesTest.kt
requirements:
  - TM-01
  - TM-02
  - TM-03
  - TM-05
  - TM-06
  - TM-08
  - TM-09
---

# Plan 03 — Club + Team CRUD API

## Goal
Working REST endpoints for club setup and team management. Role-gated. Integration tested.

## Endpoints

### Clubs
- `POST /clubs` — create club (ClubManager assigned to caller on creation)
- `GET /clubs/{clubId}` — get club details
- `PATCH /clubs/{clubId}` — update name/location/sport
- `POST /clubs/{clubId}/logo` — upload logo (multipart, max 2MB, jpg/png/webp)
- `GET /clubs/{clubId}/teams` — list teams in club

### Teams
- `POST /clubs/{clubId}/teams` — create team (ClubManager required)
- `GET /teams/{teamId}` — get team details + roster
- `PATCH /teams/{teamId}` — update name/description (Coach or ClubManager)
- `DELETE /teams/{teamId}` — archive team (ClubManager only; sets archived_at, data preserved)
- `GET /teams/{teamId}/members` — list members with roles, jersey numbers, positions

## Tasks

<task id="03-01" title="RoleMiddleware">
```kotlin
fun ApplicationCall.requireClubRole(clubId: UUID, role: String) { ... }
fun ApplicationCall.requireTeamRole(teamId: UUID, vararg roles: String) { ... }
```
Loads user's roles from DB for the given club/team. Throws 403 if not satisfied.
ClubManager implicitly passes any team role check within their club.
</task>

<task id="03-02" title="ClubRepository + impl">
- `createClub(userId, name, sportType, location): Club`
- `findById(clubId): Club?`
- `update(clubId, name?, location?, logoPath?): Club`
- `listTeams(clubId): List<Team>`
- `assignClubManager(userId, clubId)`
</task>

<task id="03-03" title="TeamRepository + impl">
- `createTeam(clubId, name, description?): Team`
- `findById(teamId): Team?`
- `update(teamId, name?, description?): Team`
- `archive(teamId)`
- `listMembers(teamId): List<TeamMember>` (includes role, jersey, position)
</task>

<task id="03-04" title="ClubRoutes.kt">
Implement all club endpoints with role checks and logo upload.
Logo upload: parse multipart, validate type/size, delegate to `FileStorageService`, save path to DB.
</task>

<task id="03-05" title="TeamRoutes.kt">
Implement all team endpoints with role checks.
Archive endpoint: PATCH `archived_at = NOW()` — no hard delete.
</task>

<task id="03-06" title="Integration tests">
`ClubRoutesTest`:
- create club success
- get club — returns correct data
- update club name
- upload logo — stored and accessible at /uploads/logo/{uuid}
- list teams in club

`TeamRoutesTest`:
- create team success
- get team with members
- update team name (coach)
- archive team (club manager)
- get archived team returns 404 in active listing
</task>

## must_haves
- [ ] ClubManager role assigned on club creation
- [ ] Archive sets `archived_at`, does NOT delete rows
- [ ] Logo upload validates file type and size (max 2MB)
- [ ] Role checks return 403 (not 401) for wrong role
- [ ] All 10 integration tests pass
