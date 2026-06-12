# Phase 1: Foundation + Auth — Context

**Gathered:** 2026-03-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Scaffold the KMP monorepo, wire Material 3 theme with custom design tokens, stand up the Ktor backend with JWT auth, and ship working register/login/logout flows with full test coverage. Everything else builds on this.

</domain>

<decisions>
## Implementation Decisions

### New User Empty State
After first login/registration with no club or team yet, show a dedicated "Join or create a club" screen with two clear CTAs:
1. **Join a team** — paste/scan invite link from coach (existing flow)
2. **Create a club** — start fresh as ClubManager
3. **Share your profile link** — player copies their user ID / deep link to send to a coach, so the coach can add them without the player needing to wait for an invite (reverse invite)

The reverse invite sharing UI is on this empty state screen. The coach-side "add player by link" handling lands in Phase 2.

### API Base URL
`local.properties` + `BuildConfig` — per build variant (debug / staging / release). Same pattern as failed example. No runtime config needed for MVP.

### Auth Flow
- Email + password only (V1)
- JWT issued on login; `user_id` only in payload — roles DB-checked per request (ADR-008)
- Token stored using `multiplatform-settings` (KMP DataStore-equivalent)
- No refresh token for MVP — re-login on expiry (revisit at V2)

### Navigation Shell
- Bottom nav: Event List, Calendar, Teams, Inbox, Profile (5 tabs)
- Navigation3 for routing
- Empty placeholder screens for tabs 2–5 in Phase 1 (event list scaffold visible, populated in Phase 3)
- Auth state gates: unauthenticated → Login, authenticated + no team → Empty state, authenticated + team → Main nav

### Theme
- Material 3 components themed with custom tokens from `pencil/design.md`
- Dark mode default; light mode derived overrides
- Token values hardcoded as M3 `ColorScheme` — no dynamic color
- Typography: M3 defaults unless design.md specifies otherwise

### Testing
- Every auth flow covered by unit + instrumented tests before Phase 1 is done
- CI: GitHub Actions — runs tests on every push/PR
- No merge without green tests

### Claude's Discretion
- M3 component selection (which M3 components map to which Teamorg UI elements)
- SQLDelight schema initial structure (minimal — extended in subsequent phases)
- Koin module organization
- Gradle version catalog structure (reference failed example's `libs.versions.toml`)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Reference (failed example project)
- `gradle/libs.versions.toml` — version catalog with pinned deps; use as reference, don't copy blindly
- `composeApp/src/commonMain/kotlin/ch/teamorg/di/` — KmpViewModel pattern (expect/actual, iOS bypasses KoinViewModelFactory) — reuse this pattern
- `shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt` — Ktor client setup
- `server/` — Ktor server, Exposed tables, Flyway migrations — fresh start but use as domain reference
- `iosApp/project.yml` — XcodeGen config

### Established Patterns (from failed example, carry forward)
- `expect/actual` KmpViewModel for iOS ViewModel lifecycle compatibility
- Koin for DI across all layers
- SQLDelight for local cache (`.sq` files in `shared/src/commonMain/sqldelight/`)
- Flyway migrations in `server/src/main/resources/db/migrations/`

### Integration Points
- Phase 1 creates the auth token storage that Phase 2+ reads
- Navigation3 graph in Phase 1 is extended by every subsequent phase
- Koin modules initialized in Phase 1; each phase adds its own module
- DB user/role tables created in Phase 1 migrations

</code_context>

<specifics>
## Specific Requirements

- Reverse invite: player on empty state can copy a deep link (`teamorg://invite/player/{userId}`) to share with coach
- Nav pill bottom bar with `cornerRadius: 36`, 62px height (from design.md)
- Status bar: 62px
- Screen frame: 390×844 base (iPhone 15)
- Design tokens exact values from `pencil/design.md` — no approximations

</specifics>

<deferred>
## Deferred Ideas

- Coach-side "add player by link" handler — Phase 2 (Team Management)
- Token refresh / silent re-auth — V2
- OAuth / magic link — V2

</deferred>

---

*Phase: 01-foundation-auth*
*Context gathered: 2026-03-12*
