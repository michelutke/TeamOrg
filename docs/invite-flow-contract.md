# Invite-flow contract (FROZEN)

Single source of truth for the email-invite feature. Backend, app, and web MUST
match this exactly. Do not deviate field names or status codes.

## URLs
- Canonical invite URL (in emails, web, App Links): `https://teamorg.ch/i/{token}`
- In-app custom scheme (still supported, unchanged): `teamorg://invite/team/{token}`
- Host is the apex `teamorg.ch` (never `www`). Base configurable via server env
  `INVITE_BASE_URL` (default `https://teamorg.ch`).

## Roles & scope
- `club_manager` → club scope → `ClubRolesTable(userId, clubId, "club_manager")`
- `coach`, `player` → team scope → `TeamRolesTable(userId, teamId, role)`
- **Club-scoped invites can ONLY carry role `club_manager`** (coaches/players are
  inherently team-scoped; `team_roles.team_id` is NOT NULL).

## DB migration — `server/src/main/resources/db/migrations/V11__invite_scope_and_reusable.sql`
```sql
ALTER TABLE invite_links ALTER COLUMN team_id DROP NOT NULL;
ALTER TABLE invite_links ADD COLUMN club_id UUID REFERENCES clubs(id) ON DELETE CASCADE;
ALTER TABLE invite_links ADD COLUMN reusable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE invite_links ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_invites_club ON invite_links(club_id);
```
Mirror in the Exposed `InviteLinksTable` object: `teamId` nullable, add
`clubId` (nullable FK to clubs, onDelete CASCADE), `reusable` bool default false,
`active` bool default true. `InviteLink` domain model: `teamId: String?`, add
`clubId: String?`, `reusable: Boolean`, `active: Boolean`.

## Endpoints

### POST /teams/{teamId}/invites  (auth; requireTeamRole(teamId, "coach","club_manager"))
Request: `{ "role": "player"|"coach", "email": String?, "reusable": false, "expiresInDays": Int? }`
- `reusable=true` requires `role="player"` AND `email` null/omitted (→ 400 otherwise).
- Personal invite = `email` present → `reusable` must be false.
- Default expiry: personal 7 days, reusable 30 days; `expiresInDays` overrides both.
Response 201: `{ "token", "inviteUrl", "expiresAt" }`  (`inviteUrl = {INVITE_BASE_URL}/i/{token}`)
Side effect: if `email` present, send invite email (NON-FATAL: still return 201 if mail fails).

### POST /clubs/{clubId}/invites  (auth; requireClubRole(clubId, "club_manager"))
Request: `{ "role": "club_manager", "email": String }`  (email required; personal only; no reusable)
Response 201: same `{ "token", "inviteUrl", "expiresAt" }`. Sends email (non-fatal).

### GET /invites/{token}  (public)
Response 200 `InviteDetails`:
```json
{
  "token": "string",
  "scope": "team",            // "team" | "club"
  "teamName": "string | null", // null for club-scoped
  "clubName": "string",
  "role": "player",            // player | coach | club_manager
  "invitedBy": "string",       // inviter display name
  "invitedEmail": "string | null", // null for reusable/shared links
  "reusable": false,
  "expiresAt": "ISO-8601",
  "alreadyRedeemed": false     // always false for reusable
}
```
404 if token unknown.

### PATCH /invites/{token}  (auth; team→requireTeamRole(coach,club_manager); club→requireClubRole(club_manager))
Request: `{ "active": false }`   Response 200 `{ "ok": true }`.

### POST /invites/{token}/redeem  (auth)
Order of checks, each returns a JSON error body:
1. not found → 404 `{ "error": "not_found" }`
2. `!active` → 410 `{ "error": "inactive" }`
3. expired (now > expiresAt) → 410 `{ "error": "expired" }`
4. personal (`invitedEmail != null`): compare `invitedEmail.lowercase().trim()` to the
   caller's email (`userRepository.findById(userId).email`, normalized). Mismatch →
   403 `{ "error": "email_mismatch", "invitedEmail": "<email>" }`
5. already a member of the target role → 200 `{ "ok": true }` (idempotent)
6. otherwise insert the role row:
   - scope=club → `ClubRolesTable(userId, clubId, "club_manager")`
   - scope=team → `TeamRolesTable(userId, teamId, role)`
   - personal: also set `redeemedAt` + `redeemedByUserId`.
   - reusable: do NOT touch `redeemed*`. Wrap the insert; on unique-index violation
     `(userId, teamId, role)` treat as already-member → 200.
   Response 200 `{ "ok": true }`.
Drop the old "redeemedByUserId != userId → 409" guard; member-check + email-match replace it.

## Email (German only)
- MailService: new `ch.teamorg.mail.MailService` interface + impl wrapping simple-java-mail,
  reusing the SAME SMTP env as ContactRoutes (`SMTP_HOST/PORT/USER/PASS`, `CONTACT_FROM`).
  Refactor ContactRoutes to use it. Koin singleton.
- From: `CONTACT_FROM` (info@teamorg.ch). Reply-to: inviter email if available.
- Subject (team): `Einladung ins Team {teamName} bei teamorg`
- Subject (club): `Einladung als Club-Manager bei {clubName}`
- Body: greeting, who invited, role label, the `https://teamorg.ch/i/{token}` link,
  expiry date. Plain text + minimal HTML. Role labels DE: player→`Spieler`,
  coach→`Trainer`, club_manager→`Club-Manager`.
- Sending is best-effort: try/catch, log on failure, never fail invite creation.

## Club creation gating
- `POST /clubs`: add `if (!user.isSuperAdmin) → 403`. (Super-admins are seeded in DB.)
- App: hide the "Set up your club" button for non-super-admins (read `isSuperAdmin`
  from `/auth/me`).

## App (composeApp + shared)
- `shared/.../domain/Club.kt` `InviteDetails`: add `scope`, `invitedEmail: String?`,
  `reusable: Boolean`; make `teamName: String?`. (ignoreUnknownKeys=true → additive-safe.)
- Invite repo: map HTTP status → typed result: 403 email_mismatch (parse `invitedEmail`),
  410 expired/inactive, 404 not_found, 200 ok, "already member"/409 → success.
- InviteViewModel: on email_mismatch show DE message
  `Diese Einladung ist für {invitedEmail}. Du bist als {currentEmail} angemeldet.` + offer
  logout. Current email via `/auth/me` (`AuthUser.email`).
- InviteScreen: render team invite (teamName) vs club invite (teamName null → show clubName
  + "Club-Manager"). Reusable → no email shown.
- Register: when invite is personal, prefill + LOCK email = `invitedEmail`. Carry it via a
  `DeepLinkHandler.pendingInviteEmail: String?` (mirrors `pendingToken`); RegisterViewModel
  reads it, disables the email field when set.
- EmptyState parser: accept `https://teamorg.ch/i/{token}` (`substringAfterLast("/i/")`),
  keep `teamorg://invite/team/` and raw-token fallbacks.
- AndroidManifest: ADD a SEPARATE `<intent-filter android:autoVerify="true">` for
  `https` host `teamorg.ch` pathPrefix `/i/`. Keep the existing `teamorg://` filter separate.
- MainActivity.inviteToken(): also accept `https` + host `teamorg.ch` + path `/i/{token}`.
- MainViewController.handleDeepLink: also accept `https://teamorg.ch/i/{token}`.

## Web (landing)
- New route `src/routes/i/[token]/+page.server.ts` SSR-fetches `${API_URL}/invites/{token}`
  (mirror the contact action's `env.API_URL` pattern). 200 → render; 404/410 → friendly
  "ungültig/abgelaufen" page. Reuse i18n DE/EN.
- `+page.svelte`: show clubName/teamName/role/invitedBy/expiry. Buttons:
  - "App öffnen" → `teamorg://invite/team/{token}`
  - Android download → `PUBLIC_ANDROID_DOWNLOAD_URL` (Updraft; optional public env,
    hide button if unset)
  - iOS: text "iOS-App folgt bald" (no store yet)
- `src/hooks.server.ts` (NEW): intercept `GET /.well-known/assetlinks.json` → return the
  JSON below with `content-type: application/json` (do NOT use a route dir — SvelteKit
  ignores dot-dirs):
```json
[{ "relation": ["delegate_permission/common.handle_all_urls"],
   "target": { "namespace": "android_app", "package_name": "ch.teamorg",
     "sha256_cert_fingerprints": ["F2:39:C1:56:12:78:F0:6E:A9:2A:CC:25:8F:6F:1C:62:54:55:A2:B1:78:E3:2F:32:73:34:72:19:1D:E8:CA:CD"] } }]
```
- iOS Universal Links / apple-app-site-association: DEFERRED (no Apple account yet).
