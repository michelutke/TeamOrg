---
phase: 06-super-admin
plan: 06
subsystem: ui
tags: [sveltekit, impersonation, tailwind, svelte5, countdown-timer]

# Dependency graph
requires:
  - phase: 06-04
    provides: admin layout shell, clubs/[clubId] page with ClubManagers section, api.ts helper
  - phase: 06-03
    provides: POST /admin/impersonate/start and POST /admin/impersonate/end Ktor routes

provides:
  - admin/src/lib/server/impersonation.ts: startImpersonation, endImpersonation, getImpersonationState helpers
  - admin/src/hooks.server.ts: impersonation expiry check + locals.impersonation population
  - admin/src/app.d.ts: Locals.impersonation type declaration
  - admin/src/routes/admin/+layout.server.ts: impersonation state passed to layout data
  - admin/src/routes/admin/+layout.svelte: fixed 44px orange banner with MM:SS countdown + End Session
  - admin/src/routes/admin/impersonate/start/+page.server.ts: form action calling startImpersonation
  - admin/src/routes/admin/impersonate/end/+page.server.ts: form action calling endImpersonation
  - admin/src/routes/admin/clubs/[clubId]/+page.svelte: Impersonate button + confirmation modal per ClubManager

affects:
  - 06-07 (audit log page uses same layout with impersonation banner)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Svelte 5 $effect for reactive countdown initialization from server data"
    - "onMount/onDestroy for setInterval countdown timer lifecycle"
    - "httpOnly cookie pair: admin_impersonation (metadata) + admin_session_original (original SA token)"
    - "Token swap pattern: startImpersonation replaces admin_session with impersonation token, endImpersonation restores original"
    - "invalidateAll() on countdown expiry — triggers SvelteKit server reload to clean up via hooks.server.ts"

key-files:
  created:
    - admin/src/lib/server/impersonation.ts
    - admin/src/routes/admin/impersonate/start/+page.server.ts
    - admin/src/routes/admin/impersonate/end/+page.server.ts
  modified:
    - admin/src/hooks.server.ts
    - admin/src/app.d.ts
    - admin/src/routes/admin/+layout.server.ts
    - admin/src/routes/admin/+layout.svelte
    - admin/src/routes/admin/clubs/[clubId]/+page.svelte

key-decisions:
  - "Svelte 5 $effect used instead of $: reactive statement for countdown init — consistent with runes codebase convention"
  - "Sidebar top offset and height adjusted via inline style string interpolation when impersonation active — avoids extra CSS classes"
  - "impersonateTarget state stores {id, name} — enables modal copy 'Impersonate [Name]?' without extra fetch"

requirements-completed:
  - SA-08

# Metrics
duration: 5min
completed: 2026-04-04
---

# Phase 06 Plan 06: Impersonation UI Flow Summary

**Server-side impersonation session management with httpOnly cookie token swap, persistent orange 44px banner with MM:SS countdown on all admin pages, and Impersonate button + confirmation modal on club detail page**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-04T19:00:32Z
- **Completed:** 2026-04-04T19:05:32Z
- **Tasks:** 2
- **Files modified:** 8 (3 created, 5 modified)

## Accomplishments

- `admin/src/lib/server/impersonation.ts`: `startImpersonation` (calls Ktor, swaps admin_session cookie, stores original token + metadata in httpOnly cookies), `endImpersonation` (calls Ktor /admin/impersonate/end, restores SA token, clears impersonation cookies), `getImpersonationState` (reads metadata cookie, checks expiry timestamp)
- `admin/src/hooks.server.ts`: checks impersonation expiry on every request, auto-ends expired sessions and re-fetches session with restored original token, populates `locals.impersonation`
- `admin/src/app.d.ts`: `Locals.impersonation` type with `{ active, targetName, targetEmail, sessionId, expiresAt }`
- `admin/src/routes/admin/+layout.server.ts`: returns `impersonation` in load data
- `admin/src/routes/admin/+layout.svelte`: fixed 44px `#F97316` banner above sidebar+main, `role="alert"`, "Impersonating [Name] — MM:SS remaining" text, "End Session" white-outline button, `setInterval` countdown with `invalidateAll()` on expiry, layout adjusts padding-top and sidebar top offset when active
- `admin/src/routes/admin/impersonate/start/+page.server.ts`: form action → `startImpersonation` → redirect to /admin/dashboard
- `admin/src/routes/admin/impersonate/end/+page.server.ts`: form action → `endImpersonation` → redirect to /admin/dashboard
- `admin/src/routes/admin/clubs/[clubId]/+page.svelte`: Impersonate button (accent border/text) per ClubManager row, confirmation modal "Impersonate [Name]? You will act as ClubManager for 1 hour. All actions are audit-logged." with orange Confirm + Cancel
- `npm run build` succeeds

## Task Commits

1. **Task 1: Server-side impersonation helpers + layout integration** - `158a889` (feat)
2. **Task 2: Impersonation banner in layout + start trigger in club detail** - `01e5ac6` (feat)

## Files Created/Modified

- `admin/src/lib/server/impersonation.ts` — server-side impersonation session management
- `admin/src/hooks.server.ts` — auto-expiry check + locals.impersonation population
- `admin/src/app.d.ts` — Locals.impersonation type
- `admin/src/routes/admin/+layout.server.ts` — passes impersonation to layout data
- `admin/src/routes/admin/+layout.svelte` — impersonation banner with countdown
- `admin/src/routes/admin/impersonate/start/+page.server.ts` — start form action
- `admin/src/routes/admin/impersonate/end/+page.server.ts` — end form action
- `admin/src/routes/admin/clubs/[clubId]/+page.svelte` — Impersonate button + modal

## Decisions Made

- Svelte 5 `$effect` for countdown initialization (consistent with existing runes pattern in codebase)
- Sidebar height/top adjusted via inline style interpolation rather than conditional CSS classes — simpler for a one-off layout shift
- `impersonateTarget` stores `{id, name}` to avoid extra API call for modal copy

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

- `admin/src/lib/server/impersonation.ts` exists: FOUND
- `admin/src/routes/admin/impersonate/end/+page.server.ts` exists: FOUND
- `admin/src/routes/admin/impersonate/start/+page.server.ts` exists: FOUND
- Commit `158a889` exists: FOUND
- Commit `01e5ac6` exists: FOUND
- Build: PASSED (npm run build succeeds)

---
*Phase: 06-super-admin*
*Completed: 2026-04-04*
