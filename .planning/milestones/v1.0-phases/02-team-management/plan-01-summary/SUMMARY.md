# Plan 01 Summary - DB Schema

## Accomplishments
- Created Flyway migrations V3–V6 for Phase 2:
    - `V3__create_clubs.sql`: `clubs`, `club_roles`
    - `V4__create_teams.sql`: `teams`, `team_roles`
    - `V5__create_invites.sql`: `invite_links`
    - `V6__create_subgroups.sql`: `sub_groups`, `sub_group_members`
- Implemented corresponding Exposed DSL table objects in `ch.teamorg.db.tables`:
    - `ClubsTable`, `ClubRolesTable`
    - `TeamsTable`, `TeamRolesTable`
    - `InviteLinksTable`
    - `SubGroupsTable`, `SubGroupMembersTable`

## Details
- All UUIDs use `gen_random_uuid()` as default.
- Proper foreign key constraints with `ON DELETE CASCADE` applied where logical (e.g., removing a club removes its roles and teams).
- Unique constraints and indexes added to join/lookup columns as per plan.
- Exposed DSL objects mirror the SQL schema exactly, using `CustomFunction` for PostgreSQL-specific defaults like `gen_random_uuid`.

## Next Steps
- Phase 2 Plan 02: File storage implementation for club logos and user avatars.
