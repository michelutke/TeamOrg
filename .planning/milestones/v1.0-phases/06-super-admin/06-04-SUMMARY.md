---
phase: 06-super-admin
plan: 04
subsystem: ui
tags: [sveltekit, admin-panel, tailwind, svelte5, lucide-svelte]

# Dependency graph
requires:
  - phase: 06-03
    provides: AdminRoutes with /admin/stats, /admin/clubs CRUD, /admin/clubs/{id}/managers endpoints

provides:
  - admin/src/lib/server/api.ts: server-side API helper (apiGet, apiPost, apiPatch, apiDelete) forwarding JWT to Ktor
  - admin/src/routes/admin/dashboard/+page.server.ts: load function fetching /admin/stats
  - admin/src/routes/admin/dashboard/+page.svelte: dashboard with 4 stat widgets + recent sign-ups table
  - admin/src/routes/admin/clubs/+page.server.ts: clubs list load + create action
  - admin/src/routes/admin/clubs/+page.svelte: clubs data table with status badges, create form, pagination, empty state
  - admin/src/routes/admin/clubs/[clubId]/+page.server.ts: club detail load + edit/deactivate/reactivate/delete/addManager/removeManager actions
  - admin/src/routes/admin/clubs/[clubId]/+page.svelte: full club management UI with confirmation modals

affects:
  - 06-05 (users page will follow same api.ts pattern)
  - 06-06 (audit log page will follow same pattern)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "All API calls via server-side load functions and form actions — JWT never exposed to browser"
    - "apiGet/apiPost/apiPatch/apiDelete helpers in $lib/server/api.ts — single place for Authorization header"
    - "Svelte 5 $state/$derived/$props for reactive UI — no legacy stores in page components"
    - "Type-to-confirm pattern for permanent delete: $derived(deleteConfirmInput === data.club.name)"
    - "Confirmation modals as inline #if blocks with $state booleans — no separate modal component"

key-files:
  created:
    - admin/src/lib/server/api.ts
    - admin/src/routes/admin/dashboard/+page.server.ts
    - admin/src/routes/admin/clubs/+page.server.ts
    - admin/src/routes/admin/clubs/+page.svelte
    - admin/src/routes/admin/clubs/[clubId]/+page.server.ts
    - admin/src/routes/admin/clubs/[clubId]/+page.svelte
  modified:
    - admin/src/routes/admin/dashboard/+page.svelte

key-decisions:
  - "API helper in $lib/server/api.ts has generic return type <T> — callers define their own response interfaces inline for simplicity"
  - "Confirmation modals implemented as inline #if blocks with $state booleans — no shared modal component (YAGNI, only 4 modals total)"
  - "removeManagerTarget state holds {id, name} object — enables 'Are you sure you want to remove [Name]' copy without extra data fetch"

requirements-completed:
  - SA-01
  - SA-02
  - SA-03
  - SA-04
  - SA-05
  - SA-06
  - SA-07
  - SA-09

# Metrics
duration: 12min
completed: 2026-04-04
---

# Phase 06 Plan 04: Dashboard + Club Management Pages Summary

**SvelteKit dashboard with 4 stat widgets and recent sign-ups table, plus full club lifecycle management (create, edit, deactivate/reactivate, delete with type-to-confirm) and ClubManager invite/remove — all server-side via Ktor API helper**

## Performance

- **Duration:** 12 min
- **Started:** 2026-04-04T19:00:00Z
- **Completed:** 2026-04-04T19:12:00Z
- **Tasks:** 2
- **Files modified:** 7 (6 created, 1 modified)

## Accomplishments

- `$lib/server/api.ts` with apiGet/apiPost/apiPatch/apiDelete helpers — JWT forwarded server-side, never hits browser
- Dashboard page: 4 stat cards (Building2/Users/Calendar/UserPlus icons, #4F8EF7 color, 28px display numbers) + recent sign-ups table with formatDate helper
- Clubs list: data table with 7 columns, status badges (green/yellow/red with rgba backgrounds), clickable rows, Create Club modal form, empty state, pagination
- Club detail: info card with inline edit form, status actions (deactivate/reactivate/delete), ClubManagers table with Invite/Remove, 4 confirmation modals (deactivate, reactivate, delete type-to-confirm, remove manager)
- `npm run build` passes — SSR bundle builds successfully

## Task Commits

Each task was committed atomically:

1. **Task 1: API helper + Dashboard page** - `3c0b57d` (feat)
2. **Task 2: Clubs list + club detail pages** - `8439063` (feat)

## Files Created/Modified

- `admin/src/lib/server/api.ts` — apiGet, apiPost, apiPatch, apiDelete with Authorization Bearer header
- `admin/src/routes/admin/dashboard/+page.server.ts` — load function calling apiGet('/admin/stats', token)
- `admin/src/routes/admin/dashboard/+page.svelte` — 4 stat widgets + recent sign-ups table (replaces placeholder)
- `admin/src/routes/admin/clubs/+page.server.ts` — load (paginated clubs) + create form action
- `admin/src/routes/admin/clubs/+page.svelte` — full clubs table, status badges, empty state, pagination, create form
- `admin/src/routes/admin/clubs/[clubId]/+page.server.ts` — load + 6 form actions (edit, deactivate, reactivate, delete, addManager, removeManager)
- `admin/src/routes/admin/clubs/[clubId]/+page.svelte` — club info card, edit form, status action buttons, ClubManagers table, 4 confirmation modals

## Decisions Made

- API helper generic `<T>` return — callers define their own response interfaces inline rather than a shared types file (YAGNI; only 2 pages needed at this stage)
- Confirmation modals as inline `#if` blocks — simpler than a shared modal component given only 4 modals exist in this plan
- `removeManagerTarget` stores `{id, name}` object — enables exact copywriting ("remove [Name] as ClubManager of [Club]") without extra API calls

## Deviations from Plan

None — plan executed exactly as written. The logout page.server.ts already existed from 06-02 with the correct implementation; no changes needed.

## Issues Encountered

- Worktree was behind `gsd/phase-06-super-admin` branch — merged before starting to get admin directory and prior work. Fast-forward merge, no conflicts.

## Self-Check: PASSED

- `admin/src/lib/server/api.ts` exists: FOUND
- `admin/src/routes/admin/dashboard/+page.server.ts` exists: FOUND
- `admin/src/routes/admin/clubs/+page.svelte` exists: FOUND
- `admin/src/routes/admin/clubs/[clubId]/+page.svelte` exists: FOUND
- Commit `3c0b57d` exists: FOUND
- Commit `8439063` exists: FOUND
- Build: PASSED (npm run build succeeds)

## Next Phase Readiness

- API helper pattern established — 06-05 (users) and 06-06 (audit log) follow same pattern
- Dashboard and clubs management complete; users page (06-05) and audit log (06-06) can proceed in parallel

---
*Phase: 06-super-admin*
*Completed: 2026-04-04*
