# Session Status: 2026-11-04 to 2026-11-05 (Sentry, password reset, store readiness)

Snapshot of the latest development window. The previous window (store compliance: transport
encryption and account deletion, PRs #85 to #88) is in git history at `871cb46`; its open items
are still valid unless listed as done below. Infrastructure steps owed to a human:
`docs/security-runbook.md`. Store questionnaire answers: `docs/store-data-safety.md`.

## Merge policy (ACTIVE)

PRs are **queued open, not merged**. On the user's "merge window" call: merge all queued PRs
into `main`, then promote `main` to `production` ONCE (one Coolify web redeploy and one "Release
to Stores" run). Also in auto-memory (`teamorg-bulk-merge-policy`).

## Released this window: `production` `59f4add` to `bc4072a`

All merged into `main`, one promote (#104). **Release to Stores run 42042636115 (run number 15)
green**: Play internal track and TestFlight, build 15.

| PR | What |
|---|---|
| #96 | Password reset (server, web, mobile): `POST /auth/forgot-password`, `POST /auth/reset-password`, migration `V20__password_reset.sql`, sessions revoked via `token_version` |
| #97 | Testability groundwork: test tags, `e2e/seed/seed.sh`, `POST /e2e/seed/club` (only with `E2E_SEED_ENABLED=true`), iOS simulator E2E build, logout button on own profile, iOS push prompt moved after login |
| #98 | Mobile crash reporting with Sentry (Android and iOS) |
| #99 | ViewModel, offline queue and cache tests; three bugs fixed (notification settings debounce, RSVP failure restore, duplicate queue flush) |
| #101 | Sentry error reporting for server and admin; admin SvelteKit 2.56.1 to 2.70.3 (one high and several moderate advisories); transitive `nanoid`, `postcss` and `glob` fixes |
| #102 | Fix server test compile on `main` (#96 and #97 together broke `PasswordResetTest`) |
| #103 | Landing: same SvelteKit 2.70.3 and `nanoid` security bump |
| #104 | Promote `main` to `production` (replaces #95, closed unmerged) |

PR descriptions hold the full change and test lists.

## Sentry setup

Org `teamorg-46`, **EU region** (`https://de.sentry.io`), team `teamorg`. One project per app:

| Project | App | DSN configured in |
|---|---|---|
| `teamorg-server` | Ktor server | Coolify env `SENTRY_DSN` |
| `teamorg-admin` | SvelteKit web app (server and browser side) | Coolify env `PUBLIC_SENTRY_DSN` |
| `teamorg-mobile` | Android and iOS | GitHub `production` environment secret `SENTRY_DSN` |

Landing has no Sentry (static marketing site, deliberately skipped).

`teamorg-mobile` was created as `teamorg-android` and renamed; the DSN is unchanged because it is
keyed on the project id. Android and iOS share it since the release workflow passes one secret to
both jobs; filter by OS inside Sentry. The separate `teamorg-ios` project is unused and should be
removed (Settings, General, Remove Project).

### How each app reports

- **Server:** `server/.../plugins/Sentry.kt` initializes first in `Application.module`. Blank
  `SENTRY_DSN` disables it, so local dev and tests send nothing. `SENTRY_ENVIRONMENT` defaults to
  `production`, `SENTRY_RELEASE` is optional. Logback `SentryAppender` sends `ERROR` logs as
  events and `INFO` and above as breadcrumbs. Unhandled exceptions in StatusPages carry the
  `errorId` tag, which matches the id returned to the client, so a user report can be found in
  Sentry by that id.
- **Admin:** `@sentry/sveltekit` 11.10.0. `src/instrumentation.server.ts` (server),
  `src/hooks.client.ts` (browser), `sentryHandle` and `handleErrorWithSentry` in the hooks. Traces
  sample at 20% (100% when `PUBLIC_SENTRY_ENVIRONMENT=development`). The CSP `connect-src` allows
  `https://*.ingest.de.sentry.io`. One `PUBLIC_` variable serves both sides because SvelteKit
  exposes only `PUBLIC_` variables to the browser and Coolify shares env vars across the compose
  file. Source maps upload during the Docker build only when the build arg `SENTRY_AUTH_TOKEN` is
  set.
- **Mobile:** Sentry Kotlin Multiplatform 0.29.0 and sentry-cocoa 9.30.2 (SPM). DSN baked in at
  build time (Android `BuildConfig`, iOS `Info.plist` via `Config.xcconfig`, injected by
  fastlane) and validated (must be https and contain `@`). Release store builds only; debug,
  local and Updraft builds have an empty DSN and never start Sentry.

### Privacy

`sendDefaultPii` off everywhere. No session replay, screenshots or view hierarchy. Admin sends no
user info, cookies or request bodies. Mobile sends the user id only (no email or name) and drops
HTTP breadcrumbs because request paths carry invite tokens. Store answers in
`docs/store-data-safety.md`.

### Verified

A test event from each app (server, admin, Android, iOS) arrived in its project, then the four
test issues were resolved. The production store builds (run 15) are the first with a DSN.

## CI change

Dependency Review (`.github/workflows/test.yml`) now fails on **moderate and above** instead of
low. Two low SvelteKit advisories (GHSA-27mw-82g8-phjj, GHSA-pfhg-q3qv-9q6r) are only fixed in
SvelteKit 3. Restore the default when admin and landing move to SvelteKit 3.

## Open items

- **Store questionnaires:** add crash data to Play Data Safety and Apple App Privacy (answers in
  `docs/store-data-safety.md`), on top of the items carried from the previous window.
- **Sentry "Prevent Storing of IP Addresses"** is not yet enabled on the admin and mobile
  projects.
- **Delete `teamorg-ios`** in Sentry (unused).
- **Symbolication:** no dSYM (iOS) or R8 mapping (Android) upload yet, so release stack traces may
  show addresses or obfuscated names. Needs a GitHub secret `SENTRY_AUTH_TOKEN` and an upload step
  in the store lanes.
- **Password reset in production:** check that the SMTP env vars are set on the server (same
  `contact.*` config as invite mails) and walk the flow once on web and mobile.
- **SvelteKit 3 migration** for admin and landing, then restore the Dependency Review threshold.
- Draft #100 untouched.

## Gotchas learned this window

1. **Two PRs green alone, red together.** #97 added a parameter before the trailing lambda of
   `withTeamorgTestApplication`; #96's new test passed the block positionally. Each PR's CI was
   green, `main` broke after both merged (fixed in #102). When merging a batch, rerun CI on
   `main` after each merge or merge `main` into the remaining PRs first.
2. **Local Testcontainers on OrbStack** needs:
   `JAVA_TOOL_OPTIONS="-Dapi.version=1.44" DOCKER_HOST=unix:///Users/miggi/.orbstack/run/docker.sock TESTCONTAINERS_DOCKER_CLIENT_STRATEGY=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy TESTCONTAINERS_RYUK_DISABLED=true`.
3. **Robolectric dependency downloads flake in CI** (`MavenArtifactFetcher`). Rerun the failed job
   before debugging.
4. **Dependency Review checks transitive packages too.** Fixing #101 took four rounds (SvelteKit,
   `nanoid`, `postcss`, then `glob` and `minimatch` under `@sentry/bundler-plugins`, solved with
   an npm `overrides` entry). Run `npm audit` locally before pushing a dependency change.
5. A Docker build `ARG` does nothing unless `docker-compose.yml` passes it under `build.args`.
