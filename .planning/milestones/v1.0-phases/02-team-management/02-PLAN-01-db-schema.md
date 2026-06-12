---
plan: "01"
wave: 1
phase: 2
title: "DB schema — clubs, teams, roles, invites, sub-groups"
depends_on: []
autonomous: true
files_modified:
  - server/src/main/resources/db/migrations/V3__create_clubs.sql
  - server/src/main/resources/db/migrations/V4__create_teams.sql
  - server/src/main/resources/db/migrations/V5__create_roles.sql
  - server/src/main/resources/db/migrations/V6__create_invites.sql
  - server/src/main/resources/db/migrations/V7__create_subgroups.sql
  - server/src/main/kotlin/ch/teamorg/db/tables/ClubsTable.kt
  - server/src/main/kotlin/ch/teamorg/db/tables/TeamsTable.kt
  - server/src/main/kotlin/ch/teamorg/db/tables/RolesTable.kt
  - server/src/main/kotlin/ch/teamorg/db/tables/InviteLinksTable.kt
  - server/src/main/kotlin/ch/teamorg/db/tables/SubGroupsTable.kt
requirements:
  - TM-01
  - TM-02
  - TM-04
  - TM-10
  - TM-14
  - TM-15
---

# Plan 01 — DB Schema: Clubs, Teams, Roles, Invites, Sub-groups

## Goal
All Phase 2 tables created via Flyway migrations with Exposed DSL objects. No business logic here — just schema.

## Tasks

<task id="01-01" title="V3__create_clubs.sql">
```sql
CREATE TABLE clubs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  sport_type TEXT NOT NULL DEFAULT 'volleyball',
  location TEXT,
  logo_path TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE club_roles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK (role IN ('club_manager')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(user_id, club_id, role)
);
CREATE INDEX idx_club_roles_user ON club_roles(user_id);
CREATE INDEX idx_club_roles_club ON club_roles(club_id);
```
</task>

<task id="01-02" title="V4__create_teams.sql">
```sql
CREATE TABLE teams (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  description TEXT,
  archived_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE team_roles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK (role IN ('coach', 'player')),
  jersey_number INT,
  position TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(user_id, team_id, role)
);
CREATE INDEX idx_teams_club ON teams(club_id);
CREATE INDEX idx_team_roles_user ON team_roles(user_id);
CREATE INDEX idx_team_roles_team ON team_roles(team_id);
```
</task>

<task id="01-03" title="V5__create_invites.sql">
```sql
CREATE TABLE invite_links (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  token TEXT UNIQUE NOT NULL DEFAULT gen_random_uuid()::text,
  team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  invited_by_user_id UUID NOT NULL REFERENCES users(id),
  invited_email TEXT,
  role TEXT NOT NULL CHECK (role IN ('coach', 'player')) DEFAULT 'player',
  expires_at TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '7 days',
  redeemed_at TIMESTAMPTZ,
  redeemed_by_user_id UUID REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_invites_token ON invite_links(token);
CREATE INDEX idx_invites_team ON invite_links(team_id);
```
</task>

<task id="01-04" title="V6__create_subgroups.sql">
```sql
CREATE TABLE sub_groups (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(team_id, name)
);
CREATE TABLE sub_group_members (
  sub_group_id UUID NOT NULL REFERENCES sub_groups(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (sub_group_id, user_id)
);
```
</task>

<task id="01-05" title="Exposed DSL table objects">
Create `ClubsTable`, `ClubRolesTable`, `TeamsTable`, `TeamRolesTable`, `InviteLinksTable`, `SubGroupsTable`, `SubGroupMembersTable` — each as Exposed `object : Table(...)` with all columns mapped.
</task>

## must_haves
- [ ] All migrations create their tables without errors against H2
- [ ] Foreign key constraints correct
- [ ] Indexes on all join/lookup columns
- [ ] Exposed table objects match migration schema exactly
