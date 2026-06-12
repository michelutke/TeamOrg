# Milestones

## v1.0 MVP (Shipped: 2026-04-07)

**Timeline:** 34 days (2026-03-02 → 2026-04-04)
**Scope:** 9 phases, 53 plans, 62 requirements — all satisfied
**Codebase:** ~28k Kotlin, ~2.7k Svelte/TS, ~1.7k SQL/SQLDelight | 444 commits, 449 files

**Key accomplishments:**

1. KMP monorepo (shared/composeApp/androidApp/iosApp/server/admin) with Ktor backend, JWT auth, role-per-request from DB
2. Club + team management: invites (email + link, 7-day expiry), roles (coach/player/manager), sub-groups, player profiles
3. Event scheduling with recurring series (daily/weekly/custom), server-side materialisation, edit scopes (this/future/all), calendar views (month + week)
4. Attendance tracking: RSVP with Begrundung, Abwesenheit (weekly + period), coach overrides, offline mutation queue, deadline enforcement (server 409)
5. Push notifications (OneSignal) + in-app inbox, per-user settings, reminder scheduler with coach pre-event summaries
6. SvelteKit super-admin panel: club CRUD, impersonation (1h limit, audit-logged, countdown banner), user search, immutable audit log

**Tech stack:** Kotlin 2.3.10, Compose Multiplatform 1.10.1, Ktor 3.1.0, Exposed, PostgreSQL + Flyway (V1–V10), SQLDelight 2.0.2, Navigation3, Koin 4.1.0, SvelteKit, OneSignal

---
