# PROJECT.md — Teamorg

## What We're Building

Cross-platform team appointment and attendance management app for sports clubs.
Targeted at Swiss clubs running **Jugend und Sport (J+S)** programs requiring Anwesenheitskontrolle.
Think Spielerplus.de — built with Kotlin Multiplatform + Compose Multiplatform.

## Why

The first attempt failed: a bad SDD plugin wrote code without tests. Nothing was verifiable.
This rebuild prioritises **clean, tested, working flows** — every phase ships a working product.

## Monorepo Structure

```
/
├── shared/         — KMP shared domain, repositories, business logic, SQLDelight cache
├── composeApp/     — Compose Multiplatform UI (commonMain + androidMain + iosMain)
├── androidApp/     — Android thin shell (MainActivity, Koin init, push wiring only)
├── iosApp/         — iOS entry point (Swift → ComposeUIViewController)
├── server/         — Ktor JVM backend + Flyway migrations (fresh start, failed example as reference)
├── admin/          — SvelteKit SuperAdmin panel
└── .planning/      — GSD planning artifacts
```

Note: `failed example project/` will be deleted once GSD planning is complete.

## Tech Stack

### Mobile
| Concern | Choice |
|---|---|
| Language | Kotlin 2.3.10 |
| Multiplatform | Kotlin Multiplatform (KMP) |
| UI | Compose Multiplatform 1.10.1 |
| UI library | Material 3 — themed with custom design tokens |
| DI | Koin 4.1.0 |
| Navigation | Navigation3 1.0.0-alpha06 |
| Local cache | SQLDelight 2.0.2 |
| Networking | Ktor client |
| Dates | kotlinx-datetime 0.6.0 (0.7.x causes K/N IR crash — pinned) |
| Images | Coil3 3.4.0 |
| Build | AGP 8.13.2, Gradle 8.13 |

### Backend
| Concern | Choice |
|---|---|
| Framework | Ktor 3.1.0 (JVM) |
| DI | Koin (`koin-ktor`) 4.1.0 |
| ORM | Jetbrains Exposed (DSL) |
| DB | PostgreSQL + HikariCP + Flyway |
| Push | OneSignal (behind `PushService` abstraction) |
| Email | Simple Java Mail |
| Serialization | kotlinx-serialization |

### Web Admin
| Concern | Choice |
|---|---|
| Framework | SvelteKit |
| Purpose | SuperAdmin panel only (main app web TBD — ADR-001 unresolved) |

## Design System

- **Design file:** `pencil/teamorg.pen`
- **Tokens documented:** `pencil/design.md`
- **Approach:** Material 3 components, fully themed with custom tokens
  - Dark mode default: `background #090912`, `primary #4F8EF7`, `accent #F97316`
  - Light mode: derived overrides (primary/accent unchanged)
  - Component library: 29 components (Buttons, Chips, Avatars, Badges, Forms, Misc)

## Core Features

1. **Team management** — create clubs/teams, invite members, assign roles (coach/manager/player)
2. **Event scheduling** — create/edit/cancel events, recurring series, subgroups
3. **Attendance tracking** — confirm/decline/no-response, J+S compliance reports
4. **Notifications** — push (OneSignal), in-app inbox, settings
5. **Super-admin panel** — club oversight, impersonation, audit log (SvelteKit)

## Architecture Principles (from ADRs + anti-patterns)

- Business logic lives in `shared/` — never in Composables
- Roles checked from DB per request — never embedded in JWT
- Offline-first: SQLDelight cache + mutation queue; server authoritative on conflict (409)
- Stats aggregated client-side — no server stats endpoint (ADR-007)
- Push provider abstracted behind `PushService` — never call OneSignal directly from logic
- Background jobs are Ktor coroutines — never block request path
- All user feedback via Snackbar system (top of screen, severity-coloured)
- Audit log immutable at DB level (no UPDATE/DELETE on `audit_log` for app role)

## UX Standards

- Snackbar system: top of screen, severity-coloured (green/orange/red), swipe-up to dismiss
- No technical error details ever shown to users
- Offline support: show schedules + submit attendance without connectivity
- Mobile frame: 390×844, bottom nav pill with cornerRadius 36

## Quality Contract

Every phase must:
- Ship a working product (no broken states between phases)
- Include automated tests for all new flows
- Pass tests before moving to the next phase

## Non-Goals (V1)

- Main app web client (ADR-001 unresolved)
- External job queue (in-process Ktor coroutines sufficient for MVP)
- Server-side stats aggregation (client-side per ADR-007)

## Key Decisions

| Decision | Rationale | Status |
|---|---|---|
| Fresh backend start | Failed example had significant tech debt | Decided |
| Failed example as reference only | Don't copy-paste — use for domain knowledge | Decided |
| M3 + custom tokens | M3 structure, Teamorg visual identity | Decided |
| Phase-by-phase, always working | Prevent repeat of non-functional first attempt | Decided |
| Navigation3 | Latest KMP navigation | Decided |
| ADR-001 (web client) | Deferred — SvelteKit admin only for now | Open |

## Requirements

### Validated (v1.0)
- [x] Auth (email/password, JWT, roles from DB) — v1.0
- [x] Team management (clubs, teams, invite, roles) — v1.0
- [x] Event scheduling (create, edit, cancel, recurring, subgroups) — v1.0
- [x] Attendance tracking (confirm/decline, J+S reports, offline) — v1.0
- [x] Push notifications + in-app inbox — v1.0
- [x] Super-admin panel (clubs, users, audit log, impersonation) — v1.0

### Active
(None — v1.0 shipped. Next milestone TBD via `/gsd:new-milestone`)

### Out of Scope
- Main app web client — ADR-001 unresolved
- OAuth / magic link — V2
- External job queue — V2 if scale demands

## Current State

v1.0 MVP shipped 2026-04-07. All 62 requirements satisfied across 9 phases (53 plans).
Codebase: ~28k Kotlin, ~2.7k Svelte/TS, 10 Flyway migrations (V1–V10), SQLDelight offline cache.
Apps build and run on Android + iOS. Ktor backend serves mobile + SvelteKit admin panel.

---
*Last updated: 2026-04-07 after v1.0 milestone completion*
