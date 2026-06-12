# Phase 6: Super Admin - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Platform operators (SuperAdmin) can manage clubs, users, and monitor the entire platform via a SvelteKit admin panel. Includes club CRUD, ClubManager management, impersonation, platform dashboard, user search, and immutable audit log viewer.

</domain>

<decisions>
## Implementation Decisions

### Auth & Session Strategy
- **D-01:** Same JWT auth — SvelteKit calls Ktor `/auth/login`, stores JWT in httpOnly cookie server-side
- **D-02:** Dedicated admin login page at `/admin/login` — only accepts SuperAdmin credentials
- **D-03:** All API calls via SvelteKit server-side load functions — JWT never hits browser. Token stored in SvelteKit server session/cookie, forwarded to Ktor in load functions and form actions

### Admin Panel Styling
- **D-04:** Tailwind CSS for styling
- **D-05:** Same design tokens as mobile app — dark background `#090912`, primary `#4F8EF7`, accent `#F97316`
- **D-06:** No component library (no shadcn-svelte) — Tailwind utility classes directly

### Impersonation Design
- **D-07:** Scoped impersonation JWT — Ktor issues short-lived token with `{actorId: superadmin, targetId: clubmanager, exp: 1h}`. Both original and impersonation tokens stored server-side
- **D-08:** Top banner with countdown timer during impersonation — "Impersonating [Name] — 42:13 remaining — [End Session]". Auto-ends at expiry
- **D-09:** Full ClubManager actions while impersonating — can create teams, invite, manage. All actions audit-logged with impersonation context (actor + target)
- **D-10:** Impersonation happens within admin panel only — SA sees ClubManager's data in admin layout. No mobile-web equivalent needed

### Audit Log
- **D-11:** All role-based actions platform-wide logged — SA actions, coach overrides, ClubManager actions, everything
- **D-12:** Filterable paginated table — columns: timestamp, actor, action, target, details. Filters: action type, actor, date range
- **D-13:** DB role restriction for immutability — app role gets INSERT + SELECT only on `audit_log`. No UPDATE/DELETE grants. 2-year minimum retention (SA-12)

### Claude's Discretion
- SvelteKit project structure and routing conventions
- Dashboard widget layout and specific aggregation queries
- Pagination approach for audit log and user search
- V10 migration schema for `audit_log` and `impersonation_sessions` tables

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Server Auth
- `server/src/main/kotlin/ch/teamorg/middleware/AuthMiddleware.kt` — Existing JWT auth + `UserPrincipal(userId, isSuperAdmin)`
- `server/src/main/kotlin/ch/teamorg/db/tables/UsersTable.kt` — `is_super_admin` boolean column
- `server/src/main/kotlin/ch/teamorg/domain/models/User.kt` — User domain model with `isSuperAdmin`

### Existing Routes (patterns to follow)
- `server/src/main/kotlin/ch/teamorg/routes/ClubRoutes.kt` — Club CRUD routes (extend for SA endpoints)
- `server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt` — Login/register/logout (reference for admin login)

### Database Migrations
- `server/src/main/resources/db/migrations/` — V1–V9 existing; V10 needed for audit_log + impersonation

### Design Tokens
- `pencil/design.md` — Design token values for admin panel theming

### Requirements
- `.planning/REQUIREMENTS.md` §SA — SA-01 through SA-12 acceptance criteria

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `UserPrincipal.isSuperAdmin` — already in auth middleware, can gate SA routes immediately
- `ClubRoutes.kt` / `TeamRoutes.kt` — existing CRUD patterns to extend with SA-specific endpoints
- `UserRepository` — `findById()` already loads `isSuperAdmin`; needs `findAll()`, `searchByNameOrEmail()` for SA-10

### Established Patterns
- Ktor route pattern: `authenticate("jwt") { route("/path") { get { call.authenticateUser(repo) { user -> ... } } } }`
- Flyway migrations: sequential V{N}__description.sql
- Exposed DSL for table definitions
- All timestamps stored UTC

### Integration Points
- New `admin/` directory at monorepo root — SvelteKit project
- New Ktor routes: `/admin/*` endpoints gated by `isSuperAdmin` check
- V10 migration: `audit_log` table + `impersonation_sessions` table
- Existing tables queried for dashboard aggregates: `users`, `clubs`, `teams`, `events`

</code_context>

<specifics>
## Specific Ideas

- Impersonation banner must be impossible to miss — persistent top bar with live countdown
- Audit log covers ALL platform actions, not just SA — comprehensive compliance view
- Admin panel is server-rendered (SvelteKit load functions) — JWT never exposed to browser

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 06-super-admin*
*Context gathered: 2026-04-04*
