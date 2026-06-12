# Plan 03 Summary - Club + Team CRUD API

## Accomplishments
- **Domain Models:** Created `Club`, `Team`, and `TeamMember` data classes.
- **Repositories:**
    - `ClubRepository` + `ClubRepositoryImpl`: Handles club creation (with automatic `club_manager` role assignment), retrieval, updates, and team listing.
    - `TeamRepository` + `TeamRepositoryImpl`: Handles team creation, retrieval, updates, archiving (soft-delete), and member listing. Implements implicit `club_manager` permission for team operations.
- **Middleware:**
    - `RoleMiddleware`: Provides `requireClubRole` and `requireTeamRole` helpers for route-level authorization.
- **Routes:**
    - `ClubRoutes`: Implements endpoints for club CRUD and logo upload. Logo upload includes validation for file type (jpg/png/webp) and size (max 2MB).
    - `TeamRoutes`: Implements endpoints for team CRUD and archiving.
- **Dependency Injection:** Registered new repositories in `Koin.kt`.
- **Routing Configuration:** Plugged new routes into `Routing.kt`.
- **Integration Tests:**
    - `ClubRoutesTest`: 5 cases covering creation, retrieval, updates, logo upload, and team listing.
    - `TeamRoutesTest`: 5 cases covering creation, retrieval, updates, archiving, and member listing.

## Key Technical Decisions
- **Implicit Permissions:** ClubManagers automatically pass any team role check within their club.
- **Soft Delete:** Teams are archived by setting `archived_at` instead of being deleted from the database.
- **File Storage:** Logo uploads use `FileStorageService` and are stored in the `logo/` directory.

## Known Limitations / Notes
- Integration tests were written but could not be executed due to the absence of a Java runtime in the current environment.
- The `TeamRoutes` logic for member listing returns an empty list currently, as the member-joining logic (invites) is part of a subsequent plan.
- Commit was performed after implementing the core logic and tests.
