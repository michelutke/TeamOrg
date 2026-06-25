# Web multi-role design — coaches & players on the web

Status: DRAFT (design only, no code). Builds on the in-flight work that lets
`club_manager` users log in directly to the admin web app (today the manager UI
is reached only via super-admin *impersonation*). This doc extends that to
`coach` and `player`, giving each their app-equivalent feature set on the web.

Read alongside `docs/invite-flow-contract.md` (role model + invite scope).

## 0. Current state (what exists today)

- Single SvelteKit app under `admin/`. One login (`/admin/login`) that today
  **rejects non-super-admins** (`auth.ts login()` calls `/auth/me` and fails if
  `!isSuperAdmin`). The current task is relaxing this so `club_manager` can log
  in directly; this doc layers `coach`/`player` on top.
- Session = JWT in httpOnly cookie `admin_session`; `hooks.server.ts` resolves
  it to `locals.user` via `/auth/me` and `locals.token` for downstream API calls.
- Manager-scoped pages live under `/admin/clubs/[clubId]/teams[/[teamId]]` and
  are currently gated by an **impersonation** flag (`parent().impersonating`),
  not by a real club role. The generalization replaces that gate with a true
  role check.
- Roles come from two backend tables and one flag:
  - `UsersTable.isSuperAdmin` (seeded)
  - `ClubRolesTable(userId, clubId, "club_manager")`
  - `TeamRolesTable(userId, teamId, "coach"|"player")`
- The web can read a user's full role set from `GET /auth/me/roles`
  (`{ clubRoles: [{clubId, role}], teamRoles: [{teamId, role}, ...] }`) — this is
  the same endpoint the mobile app uses to compute `isCoach` / `isClubManager`.

## 1. Role → capability matrix (web)

Coaches/players get **only their app-equivalent features**. The web is not a new
surface for new powers — it mirrors the Compose app per role, plus the existing
super-admin/manager web tooling.

| Capability | super_admin | club_manager | coach | player |
|---|---|---|---|---|
| Platform admin: clubs CRUD, users, audit log, impersonate | ✅ | ❌ | ❌ | ❌ |
| Create club (`POST /clubs`) | ✅ | ❌ | ❌ | ❌ |
| Manage own club(s): teams CRUD, club settings | via impersonation | ✅ (own clubs) | ❌ | ❌ |
| View team roster | ✅ | ✅ | ✅ | ✅ (own teams) |
| Manage roster: change role, remove member, promote to coach | ✅ | ✅ | ❌¹ | ❌ |
| Edit member jersey / position | ✅ | ✅ | ✅ | own only² |
| Manage sub-groups | ✅ | ✅ | ✅ | ❌ |
| Create / edit / cancel / duplicate events | ✅ | ✅ | ✅ | ❌ |
| View event list & detail | ✅ | ✅ | ✅ | ✅ |
| RSVP own attendance (`PUT /events/{id}/attendance/me`) | n/a | own | own | ✅ |
| View all members' RSVP responses | ✅ | ✅ | ✅ | ✅ (read) |
| Override a member's attendance / check-in | ✅ | ✅ | ✅ | ❌ |
| Check-in roster on event day (`GET/PUT /events/{id}/check-in`) | ✅ | ✅ | ✅ | ❌ |
| View team attendance stats | ✅ | ✅ | ✅ | limited³ |
| Manage own absences/abwesenheit rules | n/a | own | own | ✅ |
| Manage another member's absences | ✅ | ✅ | ✅ | ❌ |
| Inbox / notification center (in-app list, mark read) | ✅ | ✅ | ✅ | ✅ |
| Per-team notification settings | ✅ | ✅ | ✅ | ✅ |
| Edit own profile / avatar | ✅ | ✅ | ✅ | ✅ |
| Redeem an invite (`/i/{token}` flow) | ✅ | ✅ | ✅ | ✅ |
| Push notifications (FCM/APNs) | ❌ web⁴ | ❌ web | ❌ web | ❌ web |
| QR / on-site check-in scanning | ❌ web | ❌ web | ❌ web | ❌ web |

¹ Promote-to-coach / role changes are `requireTeamRole("club_manager")` on the
  backend today — coaches cannot. Web must match.
² A player can edit their own jersey/position only if the app allows it; mirror
  `state.isOwnProfile` from `PlayerProfileScreen`.
³ Players see the same response list coaches see (read-only) — Compose passes
  `isCoach=false` to `MemberResponseList`, which hides override affordances.
⁴ See §5 — push is app-only by design.

## 2. Route / IA structure

### 2.1 One app, one login, role-branched landing

Keep the single unified login. After successful auth, branch the redirect by the
user's highest-privilege role (fetched once from `/auth/me` + `/auth/me/roles`):

1. `isSuperAdmin` → `/admin/dashboard` (unchanged).
2. else has any `club_manager` club role → `/app` home scoped to their club(s).
3. else has any `coach` team role → `/app` home (coach view).
4. else has any `player` team role → `/app` home (player view).
5. else (no roles) → a friendly "you have no teams yet / redeem an invite" page.

A user can hold several roles (player on team A, coach on team B, manager of a
club). The home is **role-aware per team/club**, not a single global mode — same
philosophy as the Compose app, which computes `isCoach` per-screen from
`/auth/me/roles` rather than a single account-level role.

### 2.2 Two route trees

- `/admin/*` — **super-admin only** (and the manager-via-impersonation views that
  already exist). Untouched in spirit; keep its layout guard.
- `/app/*` — **new** member surface for club_manager / coach / player when they
  log in *as themselves* (not impersonated). This is where mobile parity lives.

Recommended `/app` IA (mirrors Compose bottom bar: Events, Teams, Inbox, Profile):

```
/app                                  → role-aware home / team picker
/app/events                           → event list (across user's teams)
/app/events/[eventId]                 → event detail (RSVP, responses, check-in*)
/app/events/[eventId]/edit            → coach/manager only
/app/events/new                       → coach/manager only
/app/teams                            → teams the user belongs to
/app/teams/[teamId]                   → roster
/app/teams/[teamId]/members/[userId]  → player profile (jersey/position/absences)
/app/teams/[teamId]/checkin/[eventId] → coach/manager check-in screen
/app/inbox                            → notification center
/app/inbox/settings                   → per-team notification settings
/app/profile                          → own profile + avatar
/i/[token]                            → invite redeem (already specced; shared)
```

### 2.3 Reuse the manager club-scoping pattern

The existing `/admin/clubs/[clubId]/teams/...` tree already demonstrates the
right pattern: a `[clubId]` layout that loads club context once
(`+layout.server.ts`) and child pages that fetch team/member/event data with
`locals.token`. **Reuse it** rather than inventing a parallel structure:

- For club managers logging in directly, the same `/admin/clubs/[clubId]/...`
  pages can serve them — just replace the `impersonating` gate in those
  `+page.server.ts` loads with a real `requireClubManager(clubId)` check
  (see §3). This is the cheapest path and is the current task's deliverable.
- For coaches/players, mirror the **shape** (a `[teamId]` layout that loads team
  context once, children fetch scoped resources) under `/app/teams/[teamId]`.
- Keep one `+layout.svelte` shell per tree (admin shell vs app shell) instead of
  toggling nav inside a single mega-layout — the current admin layout already
  branches nav by `isImpersonating`; that conditional grows unmanageable with 4
  roles. A dedicated `/app/+layout.svelte` with the mobile-style bottom/side nav
  is cleaner.

### 2.4 Shared vs role-specific routes

- **Shared routes, role-conditional content:** event list/detail, roster,
  inbox, profile — same route for all roles, with coach/manager affordances
  (edit, override, check-in) rendered conditionally AND guarded server-side.
- **Role-specific routes:** `/app/events/new`, `/app/events/[id]/edit`,
  `/app/teams/[id]/checkin/*` — coach/manager only; player hitting them gets a
  403 from the load guard, not just a hidden button.

## 3. RBAC enforcement model

**Principle: the web must never rely on UI hiding alone.** Every privileged
action is enforced in two places — the SvelteKit `load`/action guard (so a
player can't deep-link into an edit page) and the backend route (the real
authority). UI hiding is purely cosmetic.

### 3.1 Server-side guards (SvelteKit)

Add small reusable guards in `admin/src/lib/server/` (e.g. `guards.ts`):

- `requireUser(locals)` — already implicit via `/admin/+layout.server.ts`;
  generalize to `/app/+layout.server.ts`.
- `loadRoles(locals)` — fetch `/auth/me/roles` once in the `/app` layout load,
  return it as layout data so child loads can derive `isCoach(teamId)` /
  `isClubManager(clubId)` without re-fetching.
- `requireTeamRole(roles, teamId, ...allowed)` — throw `redirect`/`error(403)`
  in a page `load` if the user lacks the role for that team.
- `requireClubManager(roles, clubId)` — for the manager pages, replacing the
  `impersonating` flag check.

These mirror, on the web tier, the backend `requireTeamRole`/`requireClubRole`
in `RoleMiddleware.kt`. They are a UX nicety (clean 403 page, no broken
half-loaded screen) — **not** the security boundary.

### 3.2 Backend endpoints by role — and the gaps

The backend is the security boundary. Audit of current guards:

| Endpoint | Current guard | Needed for web | Action |
|---|---|---|---|
| `GET /teams/{id}/members`, `/teams/{id}` | none beyond auth | coach/player read | verify membership check; likely OK |
| `PATCH /teams/{id}/members/{u}/role` | `requireTeamRole(club_manager)` | manager | OK |
| `DELETE /teams/{id}/members/{u}`, `/leave` | `requireTeamRole(club_manager)` / self | manager / self | OK |
| `PATCH /teams/{id}/members/{u}/profile` | `requireTeamRole(coach, club_manager)` | coach/manager | OK |
| sub-groups (all writes) | `requireTeamRole(coach, club_manager)` | coach/manager | OK |
| `POST /events`, `PATCH /events/{id}`, cancel/uncancel/duplicate | **none** (only `authenticate("jwt")`) | coach/manager only | **GAP — add team-role check on the event's team(s)** |
| `GET /events/{id}`, `/teams/{id}/events`, `/users/me/events` | auth only | all members | verify membership scoping; add if missing |
| `PUT /events/{id}/attendance/me`, `GET .../attendance` | auth only | self / member read | verify membership; add if missing |
| `GET/PUT /events/{id}/check-in[/{u}]` | coach/manager but **global, not team-scoped** | coach/manager of *that event's team* | **GAP — scope the role check to the event's team** |
| `*/abwesenheit` (own) | self (`/users/me/...`) | self | OK |
| manage another member's absences | (verify) | coach/manager of team | confirm guard exists |
| `GET /notifications*`, settings | self / membership | all | OK |
| `POST /clubs` | `isSuperAdmin` (per invite-flow contract) | super-admin | OK |
| invite create/redeem | per invite-flow-contract | per role | OK (frozen) |

**Two concrete backend work items before player/coach web ships:**

1. **Event mutations are unauthorized at the role level.** `POST /events` and
   `PATCH /events/{id}` (and cancel/uncancel/duplicate) only require a valid JWT
   — any authenticated user, including a player, can create or edit events for
   any team. Add `requireTeamRole(teamId, "coach", "club_manager")` for each
   target team. This is exploitable from the app today too; the web just makes
   it more visible.
2. **Check-in role check is not team-scoped.** `CheckInRoutes` checks "is this
   user a coach/manager of *any* team," not of the event's team. A coach of team
   A can check-in roster for team B's event. Tighten to the event's team(s).

(These two are pre-existing backend gaps surfaced by widening the client base —
worth fixing regardless of the web work.)

## 4. Component reuse strategy

The web is SvelteKit; the app is Compose — no literal component sharing. Reuse is
within the web tier:

- **One presentational component, role-prop-driven**, mirroring how Compose
  passes `isCoach`/`isCoachOrManager` into `MemberResponseList`,
  `EventDetailScreen`, `PlayerProfileScreen`, `SubGroupSheet`. Build Svelte
  equivalents that take a `canManage: boolean` prop and hide override/edit
  affordances when false. Same component renders for coach and player.
- **Reuse the existing manager UI components** built for the impersonated
  `/admin/clubs/[clubId]/teams/...` pages (roster table, member row, invite
  dialog, team edit form). Lift the reusable pieces into
  `admin/src/lib/components/` so both the `/admin` manager pages and the new
  `/app` coach pages consume them. The roster + invite UI is the highest-value
  shared surface (coach and manager both manage rosters/invites per the contract:
  `POST /teams/{id}/invites` is `requireTeamRole("coach","club_manager")`).
- **Shared server helpers**: the `api.ts` (`apiGet/Post/Patch/Delete`) and the
  new `guards.ts` are used by every role's loads. Keep all role logic on the
  server (loads/actions), keep components dumb.
- **Layout shells**: `/admin/+layout.svelte` (admin chrome) and a new
  `/app/+layout.svelte` (mobile-parity nav: Events / Teams / Inbox / Profile).
  Do not overload one layout with 4-role conditionals.

## 5. Mobile features: port to web vs keep app-only

| Feature | Web? | Rationale |
|---|---|---|
| Event list/detail, create/edit, cancel/duplicate | Port | Pure data + forms; high value for coaches on a laptop |
| RSVP / attendance responses | Port | Simple state toggle; players check from anywhere |
| Roster view + manage (role, jersey, position, sub-groups) | Port | Already exists for managers; extend to coaches |
| Abwesenheit / absence rules | Port | Form-driven; useful on a bigger screen |
| Inbox / notification *center* (list, mark read) | Port | Just a list backed by `/notifications`; works fine in a tab |
| Per-team notification settings | Port | Config form |
| Profile + avatar | Port | Standard |
| Invite create / redeem | Port | Already specced in invite-flow-contract |
| **Push notifications (FCM/APNs delivery)** | App-only | Web push is a separate channel (service worker + VAPID), not the FCM/APNs path the backend already drives. The inbox center already shows the same notifications server-side; live push on web is a net-new subsystem with low payoff for a coach/admin sitting at a desk. Defer. |
| **QR / on-site check-in scanning** | App-only | Needs a camera; the day-of-event use case is inherently mobile. Web keeps the *manual* check-in screen (`PUT /events/{id}/check-in/{userId}`) — coaches mark roster from a list — but not QR scan. |
| Deep links / app-link routing | App-only | Native concern; web's equivalent is normal URLs. |

Guiding rule: port everything that is **data + forms**; leave **device-bound**
features (push delivery, camera/QR) to the app. The web's job is parity for
desk/laptop workflows, not to replace the phone for on-field moments.

## 6. Migration / rollout phasing

Sequence by (a) backend readiness and (b) value-per-effort.

**Phase 0 — current task (prereq):** club_manager direct login. Relax
`auth.ts login()` to admit non-super-admins; replace the `impersonating` gate in
`/admin/clubs/[clubId]/...` loads with `requireClubManager(clubId)`. No new
backend work. Validates the multi-role login + guard pattern.

**Phase 1 — backend RBAC hardening (blocks everything below):** add team-role
guards to event mutations (§3.2 item 1) and team-scope the check-in guard (§3.2
item 2). Ship with tests (`EventRoutesTest`, `CheckInRoutes` test). This is the
security gate for letting players/coaches authenticate broadly.

**Phase 2 — coach web first.** Coaches get the most laptop value (planning
events, managing rosters, check-in lists). Build `/app` shell + `/app/events*`
(incl. new/edit), `/app/teams/[teamId]` roster, `/app/teams/[teamId]/checkin`.
Reuse manager roster/invite components from Phase 0. Coaches also get the shared
read surfaces (inbox, profile).

**Phase 3 — player web.** Mostly read + self-service: event list/detail, RSVP,
own absences, own profile, inbox. Almost entirely the *same* components from
Phase 2 with `canManage=false`. Low incremental effort once Phase 2 lands.

**Phase 4 — shared polish.** Inbox center, notification settings, profile/avatar
for all roles; "no teams yet → redeem invite" empty state; cross-role home/team
picker for multi-role users.

Rationale for coach-before-player: coaches are the desk-workflow audience and
exercise the harder write paths first (forcing the RBAC + reusable-component work
to be solid), after which player is a thin read-only delta.

## 7. Open questions / decisions

- Same SvelteKit app (`/app` tree) or a separate deploy? (recommend same app)
- Keep cookie name `admin_session` for member sessions or rename (e.g.
  `to_session`)? Branding: is "admin" in URLs acceptable for players?
- Multi-role users: explicit team/club switcher, or auto-merge everything into
  one home? (Compose merges via `/auth/me/roles`.)
- OK to fix the two backend RBAC gaps (event mutations, check-in scoping) as part
  of this, or land them as a separate security PR first?
- Players editing own jersey/position on web — allowed? (app gates on
  `isOwnProfile`; confirm parity.)
- Web push: confirm deferred (app-only) — or is there a stakeholder need?
- i18n: app + landing are DE-first; should `/app` be DE-only, or DE/EN like the
  landing/invite pages?
- Does `GET /teams/{id}/events` / `/events/{id}` already scope to membership, or
  do we need a membership guard there too before exposing to players?

## 8. Confirmed decisions (2026-06-24)

- **Phase 0 (manager login) + Phase 1 (backend RBAC hardening): DONE** and merged to
  `main` (PRs #31, #32). Membership scoping for `/teams/{id}/events`, `/events/{id}`,
  check-in, attendance, etc. is in place — the security gate is cleared.
- **Multi-role users:** auto-merge into one role-aware home (per team/club), like the
  Compose app. No explicit global mode switcher.
- **Language:** `/app` is **DE/EN** (like landing/invite), not DE-only.
- **Players may edit their own jersey/position** on web (mirror `isOwnProfile`).
- **Rename — no "admin" for members:** member surface lives at `/app` (not `/admin`);
  use a separate session cookie (not `admin_session`).
- **Web push deferred:** push notifications stay app-only (FCM/APNs); web shows the
  in-app inbox/notification center only.

## 9. Implementation plan (Phase 2 → 4)

Grounded in the **actual** current `admin/` tree (which has evolved past §0):

- Shared login at `/admin/login` → cookie `admin_session` (httpOnly). `hooks.server.ts`
  resolves it to `locals.user` + `locals.token`.
- `getSession()` (`auth.ts`) only parses **club_manager** roles into
  `managedClubIds`; `teamRoles` from `/auth/me/roles` is dropped (`teamRoles: unknown[]`).
- Manager work surface already lives at route group `(manager)` → `/manage/[clubId]/...`
  (super-admins redirected to `/admin/dashboard`). So "admin" only leaks into the
  **login/logout** URLs for managers; the manage tree is already neutral.
- `guards.ts` has only `assertClubAccess`. No `src/lib/components/`. **No i18n** (no
  Paraglide/inlang/svelte-i18n).
- Backend RBAC (Phase 1) **confirmed done**: `POST /events`,
  `PATCH/cancel/uncancel/duplicate /events/{id}` are guarded by
  `requireTeamRole`/`requireEventAccess("coach","club_manager")`; check-in is
  event-team-scoped; `/auth/me/roles` returns `teamRoles:[{teamId,clubId,role}]`.

### 9.1 Phase 2a — `/app` foundations (shared by all member roles)

These land first; every screen depends on them.

1. **Session: parse team roles.** Extend `auth.ts`: add
   `teamRoles: {teamId,clubId,role}[]` to `UserInfo`; populate in `getSession()` and
   `login()`. Update `app.d.ts` `Locals.user`. Now `locals.user` knows every team
   role, not just managed clubs.
2. **Relax the login gate.** Remove the `isAllowed = isSuperAdmin || managedClubIds>0`
   rejection in `auth.ts login()`. Admit any authenticated user; zero-role users still
   get a session and land on the empty state (§Phase 4). Keep credential failure intact.
3. **Neutral login + cookie rename.** Add member-neutral `/login` + `/logout`
   (recommend moving `/admin/login` logic there; `/admin/login` can 302 → `/login`).
   Rename cookie `admin_session` → `to_session` in `auth.ts`, `impersonation.ts`
   (`admin_session_original`), `hooks.server.ts`. Read-accept both names for one
   release to avoid logging everyone out (grace shim), then drop.
4. **Role-branched landing** (`/+page.server.ts` or `/login` post-action redirect):
   `isSuperAdmin` → `/admin/dashboard`; else any role → `/app`; computed from
   `locals.user`.
5. **`/app` route group + shell.** New `(app)` group:
   - `(app)/app/+layout.server.ts` — `requireUser`; expose `roles` (club+team) as
     layout data so children derive access without re-fetching.
   - `(app)/app/+layout.svelte` — M3 Expressive shell from the Figma design
     (sidebar/bottom nav: **Start / Termine / Teams / Inbox / Profil**, round TO badge,
     Abmelden). DE/EN aware.
6. **Web guards** (`guards.ts`): add
   `isCoach(roles, teamId)`, `canManageTeam(roles, teamId)` (coach|club_manager),
   `isClubManager(roles, clubId)`, and throwing
   `requireTeamRole(locals, teamId, ...allowed)` / `requireClubManager(locals, clubId)`
   for page `load`/actions. Mirror backend `RoleMiddleware`; UI-only — backend is the
   boundary.
7. **i18n.** Add **Paraglide JS** (inlang) — SvelteKit-native, type-safe, build-time.
   DE default + EN; locale from `to_locale` cookie / `Accept-Language`. Wrap `/app`
   strings only (admin tree stays as-is). No prior lib in repo (§10).
8. **Team appearance (backend).** Add `TeamsTable.appearance` (`shape`, `color`),
   accept on `PATCH /teams/{id}`, expose on `GET /teams/{id}`. Backs the
   Team-Einstellungen picker and per-team shape/color in the `/app` shell + app.
9. **Extract shared components** → `src/lib/components/`. Lift the currently-inline
   manager pieces (roster table, member row, invite dialog, event form, RSVP/status
   chips) out of `(manager)/manage/[clubId]/...` into prop-driven Svelte components
   keyed on a single `canManage: boolean` (mirrors Compose `isCoach`). Both `(manager)`
   and `(app)` consume them. Highest-value shared surface: roster + invite.

### 9.2 Phase 2b — coach screens (`/app`)

Each `+page.server.ts` `load` guards with `requireTeamRole(...,"coach","club_manager")`;
mutations re-checked server-side (backend already enforces).

| Figma screen | Route | Guard |
|---|---|---|
| Home (Coach) | `/app` | any role; coach affordances if `canManageTeam` |
| Termine (Coach) | `/app/events` (+ `?team=`) | member of ≥1 team |
| Event Detail (Coach) | `/app/events/[id]` | member of event's team |
| — create/edit | `/app/events/new`, `/app/events/[id]/edit` | coach/manager of team |
| Roster (Coach) | `/app/teams`, `/app/teams/[teamId]` | member; manage if coach/mgr |
| (member profile) | `/app/teams/[teamId]/members/[userId]` | member read; edit if coach/mgr |
| Check-in (Coach) | `/app/teams/[teamId]/checkin/[eventId]` | coach/manager of team |

### 9.3 Phase 3 — player screens

Same routes/components with `canManage=false`. Net-new player paths: self RSVP
(`PUT /events/{id}/attendance/me`), own absences, own profile. Response lists render
read-only (no override). **Backend gap to fix first:**
`PATCH /teams/{id}/members/{u}/profile` is `requireTeamRole("coach","club_manager")`,
so a player **cannot** edit own jersey/position — contradicts the §8 decision. Add a
self-edit branch (`userId == caller` → allow jersey/position only) or a dedicated
`/users/me/...` route, with a test. Blocks player profile edit.

### 9.4 Phase 4 — shared polish

- **Empty state** (`/app` with zero roles): "no teams yet → redeem invite" (Figma
  Empty State screen).
- **Multi-role home / team picker**: auto-merged home; per-team chips switch context
  (no global mode). Figma Home already shows merged teams.
- **Inbox + settings** (`/app/inbox`, `/app/inbox/settings`), **Profil + avatar**
  (`/app/profile`) — shared, all roles.
- **Team-Einstellungen (shape + color picker)** — Figma screen. UI built here; backing
  field `TeamsTable.appearance` lands in 2a (§9.1.8), so this phase is just the picker UI
  wired to `PATCH /teams/{id}`.

### 9.5 Testing

- **Vitest**: `guards.ts` unit tests (role matrix → allow/deny).
- **Playwright**: per-role deep-link guard tests — player hitting
  `/app/events/new` / `/app/teams/[id]/checkin/*` gets 403, not a hidden button.
- Backend Phase-1 guards already covered; add a test for the player self-profile fix.

### 9.6 Sequencing

2a (foundations) → 2b (coach) → 3 (player, incl. self-profile backend fix) → 4 (polish).
2a is the critical path; 3 is a thin delta on 2b once components are `canManage`-driven.

## 9.7 Implementation status (2026-06-25, branch `feat/web-app-foundations`)

- **2a foundations — DONE**: team roles in session, `to_session` cookie (dual-read),
  neutral `/login`, role-branched landing, web guards, M3 `/app` shell, DE/EN i18n,
  backend `teams.appearance`.
- **2b coach — DONE**: teams list + roster, events list (merged + filter chips) +
  detail + RSVP, event create/edit forms, check-in screen. All load-guarded.
- **3 player — DONE (core)**: backend self-profile `PUT /users/me/teams/{id}/profile`
  + member profile page (self/coach edit). Players already had read + RSVP via the
  shared guarded routes.
- **4 polish — PARTIAL**: inbox (list + mark read) + profile (info + language) done.
- **Still open**: own absences (Abwesenheit) UI; per-team notification settings
  (`/app/inbox/settings`); avatar upload; Team-Einstellungen shape/color picker UI
  (backend ready); event cancel/duplicate UI (backend ready); extracting shared
  components out of the `/manage` tree (2a item 9 — deferred, no blocker).

## 10. Resolved decisions (2026-06-25)

- **Login URL:** neutral `/login` for **all** roles. `/admin/login` 302 → `/login`.
- **Cookie:** rename `admin_session` → `to_session` with a **dual-read grace period**
  (read both names for one release, write only `to_session`, then drop the shim) — no
  forced re-login.
- **i18n:** **dependency-free typed dictionary** mirroring `landing/src/lib/i18n`
  (typed `Dict`, `de`/`en` consts, `lang` cookie resolved server-side, `?lang=` toggle).
  Chosen over Paraglide: the landing site already ships this exact pattern — reusing it
  keeps both web apps consistent, adds zero deps, and is SSR-correct + type-checked.
  DE default, EN second. Cookie name `lang` (matches landing), not `to_locale`.
- **Player self jersey/position:** **new `/users/me/teams/{teamId}/profile`** (PUT) —
  self-scoped, jersey/position only. Do NOT widen the coach/manager
  `/teams/{id}/members/{u}/profile` guard. App reads the same field.
- **Team shape/color:** add `TeamsTable.appearance` (`shape`, `color`) backend field
  **now** — `PATCH /teams/{id}` accepts it, app + web read it. Pull into Phase 2a
  foundations so the Figma Team-Einstellungen screen has a real backing.
- **Coach `/app/events`:** **merged across all the user's teams**; the top filter chips
  narrow by team. No per-team default.

## 11. Domain + web invite redeem (2026-06-25)

- **Single signed-in app on its own subdomain `app.teamorg.ch`** (the `admin/`
  codebase: players → super-admins). Role-branch happens **inside** one origin via
  `landingPathFor()` (super-admin → `/admin/*`, everyone else → `/app`). No
  cross-domain redirect — the host-only session cookie would not survive a hop to a
  different host (and `admin.teamorg.michelutke.com` is a different registrable domain
  entirely). `teamorg.ch` = marketing only; cookie stays host-only on `app.teamorg.ch`.
  Set `APP_URL=https://app.teamorg.ch` on the landing service; retire the old admin host.
- **Web invite redeem is additive, canonical URL unchanged.** Emails + the
  `teamorg://` deep-link + `INVITE_BASE_URL` keep pointing at `teamorg.ch/i/{token}`
  (no https App Link exists today — the app uses the custom scheme). The landing invite
  page gains a **"Join on the web"** button → `app.teamorg.ch/i/{token}`, which does the
  actual redeem against the existing auth-gated `POST /invites/{token}/redeem`.
- **New invitees:** registration is **invite-only** (no public `/register`) and lives
  inside the redeem flow. For **personal** invites the invited email is **prefilled +
  locked** (avoids the email-mismatch 403); reusable invites get an open field. Register
  → auto-login → redeem → `/app`.
- **Hardening:** `redirectTo` is validated (`safeRedirect`, no open-redirect); redeem
  page is `noindex`; redeem is idempotent server-side (`ALREADY_MEMBER`→200);
  404/410/403/email-taken all handled; SvelteKit same-origin CSRF check kept.
