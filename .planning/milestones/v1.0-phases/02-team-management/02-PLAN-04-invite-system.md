---
plan: "04"
wave: 2
phase: 2
title: "Invite system — generate, send, redeem"
depends_on: ["01"]
autonomous: true
files_modified:
  - server/src/main/kotlin/ch/teamorg/repository/InviteRepository.kt
  - server/src/main/kotlin/ch/teamorg/data/repository/InviteRepositoryImpl.kt
  - server/src/main/kotlin/ch/teamorg/routes/InviteRoutes.kt
  - server/src/test/kotlin/ch/teamorg/routes/InviteRoutesTest.kt
requirements:
  - TM-04
  - TM-10
  - TM-17
  - TM-18
---

# Plan 04 — Invite System: Generate, Send, Redeem

## Goal
Coaches/ClubManagers can generate invite links (and optionally email invites). Any user (including unregistered) can redeem an invite link. Redemption is idempotent.

## Endpoints

- `POST /teams/{teamId}/invites` — create invite link (Coach/ClubManager required)
  - Body: `{ role: "player"|"coach", email?: string }`
  - Returns: `{ token, inviteUrl, expiresAt }`
- `GET /invites/{token}` — get invite details (public — no auth required)
  - Returns: `{ teamName, clubName, role, invitedBy, expiresAt, alreadyRedeemed }`
- `POST /invites/{token}/redeem` — redeem invite (auth required)
  - If already member of team with same role: 200 OK (idempotent)
  - If expired: 410 Gone
  - If already redeemed by someone else: 409 Conflict
  - Success: creates `team_roles` row, marks invite as redeemed

## Tasks

<task id="04-01" title="InviteRepository + impl">
- `create(teamId, createdByUserId, role, email?): InviteLink`
- `findByToken(token): InviteLink?`
- `redeem(token, userId): InviteLink` — updates redeemed_at + redeemed_by_user_id, creates team_roles row in transaction
- `listByTeam(teamId): List<InviteLink>`
</task>

<task id="04-02" title="InviteRoutes.kt">
Generate: validate caller has coach/club_manager role, create invite, return deep link URL.
Deep link format: `teamorg://invite/team/{token}`
Fallback web URL (for SMS/email): `https://teamorg.app/join/{token}` (handled by app's deep link config)

Get invite details: public endpoint, no auth gate.

Redeem: requires authenticated user. Wrapped in DB transaction (create team_roles + mark redeemed atomically).
</task>

<task id="04-03" title="Integration tests">
`InviteRoutesTest`:
- create invite link (coach creates for player)
- create invite link (club_manager creates for coach)
- get invite details — unauthenticated access works
- redeem invite — user joins team
- redeem invite — expired token returns 410
- redeem invite — already redeemed by another user returns 409
- redeem invite — already member returns 200 (idempotent)
- redeem invite — unauthenticated returns 401
</task>

## Notes
- Email delivery NOT in Phase 2 (deferred) — invite link is returned in response for manual sharing
- Token is the UUID cast to text from the invite's `token` column — already unique by DB constraint

## must_haves
- [ ] Redemption is atomic (team_roles + invite_links update in single transaction)
- [ ] GET /invites/{token} requires NO auth
- [ ] POST /invites/{token}/redeem requires auth
- [ ] Expired invites return 410 (not 404)
- [ ] Idempotent redemption returns 200 (not error)
- [ ] All 8 test cases pass
