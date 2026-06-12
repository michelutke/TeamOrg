---
phase: 2
name: team-management
status: planning
---

# Phase 2 Context — Team Management

## Locked Decisions

### File Storage
- **Approach:** Filesystem on the server (`/uploads/` directory, served as static)
- **Abstraction:** `FileStorageService` interface with `LocalFileStorageService` as the concrete impl
- **Path:** `server/uploads/{type}/{uuid}.{ext}` (type = avatar, logo)
- **Reason:** Simple for MVP; interface allows swapping to S3/R2/Cloudflare without touching call sites
- **Static route:** `GET /uploads/{type}/{filename}` — Ktor static files plugin

### Invite Token Flow
- **Approach:** Token passed through navigation state → redeemed immediately post-auth
- **Flow:**
  1. User taps invite link: `teamorg://invite/team/{inviteToken}`
  2. App checks auth state
     - If authenticated: immediately call `POST /invites/{token}/redeem` → navigate to team
     - If unauthenticated: navigate to Register (or Login) with token in nav backstack args
  3. After successful register/login: auto-call `POST /invites/{token}/redeem`
  4. Land on team screen
- **Why not store locally:** token in nav state never touches disk → zero data-loss risk; if user exits, they just tap the link again
- **Server-side:** invite_links table stores `{ token, team_id, created_by_user_id, email (nullable for email invites), expires_at, redeemed_at, redeemed_by_user_id }`
- **Expiry:** 7 days default, configurable
- **Idempotent:** re-redeeming an already-accepted invite returns 200 (no error)

### Role System (Phase 2 additions to DB)
- New tables: `clubs`, `teams`, `club_roles` (user→club→role), `team_roles` (user→team→role), `invite_links`
- Roles: `club_manager`, `coach`, `player` — stored as strings
- A user can hold multiple roles (e.g. ClubManager is also a Coach on a team)
- Role checks: middleware loads roles from DB on each request (no JWT claims for roles)

### Sub-groups
- `sub_groups` table: `{ id, team_id, name }`
- `sub_group_members` join table: `{ sub_group_id, user_id }`
- Used in Phase 3 for event targeting

## Key Architecture Notes
- All club/team creation endpoints: ClubManager role required
- All team management endpoints: Coach role (or ClubManager) required
- Invite redemption endpoint: public (needs valid token only, no auth gate pre-redemption)
- Player profile: stored in `team_members` extended fields (jersey_number, position)

## Phase 2 Branch
`feat/phase-2-team-management`
