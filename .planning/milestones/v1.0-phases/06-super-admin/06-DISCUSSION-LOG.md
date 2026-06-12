# Phase 6: Super Admin - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 06-super-admin
**Areas discussed:** Auth & session strategy, Admin panel styling, Impersonation design, Audit log viewer

---

## Auth & Session Strategy

### Q1: How should the admin panel authenticate with the Ktor backend?

| Option | Description | Selected |
|--------|-------------|----------|
| Same JWT | SvelteKit calls Ktor login endpoint, stores JWT in httpOnly cookie. Reuses existing auth infra. | ✓ |
| Separate session system | SvelteKit manages its own session (lucia-auth or built-in). Ktor validates session token via new endpoint. | |
| API key + JWT hybrid | Long-lived API key for server-server, JWT for user-facing session. | |

**User's choice:** Same JWT
**Notes:** Reuses existing auth infrastructure. No new auth system needed.

### Q2: Should the admin panel have its own login page?

| Option | Description | Selected |
|--------|-------------|----------|
| Dedicated admin login | Separate /admin/login page, admin-branded. Only accepts SuperAdmin credentials. | ✓ |
| Shared login endpoint | Same form as mobile, checks isSuperAdmin after login. | |

**User's choice:** Dedicated admin login
**Notes:** None

### Q3: How should the admin panel call the Ktor API?

| Option | Description | Selected |
|--------|-------------|----------|
| Server-side (SvelteKit load) | SvelteKit server routes fetch from Ktor, forward data to pages. JWT stored server-side. Token never hits browser. | ✓ |
| Client-side (browser fetch) | Browser calls Ktor directly. JWT exposed to browser JS. | |
| Mix of both | Server-side for page loads, client-side for interactive actions. | |

**User's choice:** Server-side (SvelteKit load)
**Notes:** Most secure — JWT never exposed to browser.

---

## Admin Panel Styling

### Q1: What CSS/styling approach?

| Option | Description | Selected |
|--------|-------------|----------|
| Tailwind CSS | Utility-first, fast to build admin UIs. Most common for admin panels. | ✓ |
| Plain CSS / Svelte scoped | No framework, Svelte's built-in scoped styles. | |
| shadcn-svelte + Tailwind | Pre-built accessible components on top of Tailwind. | |

**User's choice:** Tailwind CSS
**Notes:** None

### Q2: Should the admin panel use the same design tokens as mobile?

| Option | Description | Selected |
|--------|-------------|----------|
| Same tokens | Dark #090912, primary #4F8EF7, accent #F97316. Consistent brand. | ✓ |
| Admin-specific look | Clean neutral admin aesthetic, visually distinct. | |
| You decide | Claude picks | |

**User's choice:** Same tokens
**Notes:** Consistent brand across mobile and admin.

---

## Impersonation Design

### Q1: How should impersonation work technically?

| Option | Description | Selected |
|--------|-------------|----------|
| Scoped impersonation token | Ktor issues short-lived JWT with actorId/targetId/1h exp. Admin stores both tokens. | ✓ |
| Header-based switching | X-Impersonate-As header with normal JWT. Server checks isSuperAdmin. | |
| Session-based impersonation | Server stores impersonation state in session table. Start/stop endpoints. | |

**User's choice:** Scoped impersonation token
**Notes:** None

### Q2: What should the SuperAdmin see during impersonation?

| Option | Description | Selected |
|--------|-------------|----------|
| Top banner with timer | Persistent banner with countdown timer and End Session button. Auto-ends at expiry. | ✓ |
| Sidebar indicator | Status in sidebar. Less intrusive but easier to miss. | |
| Modal on every action | Confirmation modal before each action. Very safe but tedious. | |

**User's choice:** Top banner with timer
**Notes:** None

### Q3: What can a SuperAdmin do while impersonating?

| Option | Description | Selected |
|--------|-------------|----------|
| Read-only view | See what ClubManager sees, cannot make changes. | |
| Full ClubManager actions | Everything the ClubManager can do, all audit-logged as impersonated. | ✓ |
| You decide | Claude picks based on SA-08 | |

**User's choice:** Full ClubManager actions
**Notes:** All actions audit-logged with impersonation context.

### Q4: Where does impersonation happen?

| Option | Description | Selected |
|--------|-------------|----------|
| Admin panel only | ClubManager's data shown within admin panel UI. No mobile-web equivalent. | ✓ |
| Redirect to mobile web | Opens web view of mobile app. Requires web client (out of scope). | |

**User's choice:** Admin panel only
**Notes:** No web client needed (ADR-001 unresolved).

---

## Audit Log Viewer

### Q1: What actions should be recorded?

| Option | Description | Selected |
|--------|-------------|----------|
| SA actions only | Only SuperAdmin panel actions. | |
| SA + coach overrides | SA actions plus coach attendance overrides (AT-16). | |
| Everything | All role-based actions across the platform. | ✓ |

**User's choice:** Everything
**Notes:** Comprehensive platform-wide audit.

### Q2: How should the audit log be displayed?

| Option | Description | Selected |
|--------|-------------|----------|
| Filterable table | Paginated table with timestamp, actor, action, target, details. Filters by type, actor, date range. | ✓ |
| Timeline view | Chronological feed with cards. More visual but harder to scan. | |
| You decide | Claude picks | |

**User's choice:** Filterable table
**Notes:** None

### Q3: How should audit_log table be protected?

| Option | Description | Selected |
|--------|-------------|----------|
| DB role restriction | App role gets INSERT + SELECT only. No UPDATE/DELETE grants. | ✓ |
| DB triggers | BEFORE UPDATE/DELETE triggers that RAISE EXCEPTION. | |
| Both | Role restriction + triggers. | |

**User's choice:** DB role restriction
**Notes:** Matches PROJECT.md principle.

---

## Claude's Discretion

- SvelteKit project structure and routing conventions
- Dashboard widget layout and specific aggregation queries
- Pagination approach for audit log and user search
- V10 migration schema details

## Deferred Ideas

None — discussion stayed within phase scope
