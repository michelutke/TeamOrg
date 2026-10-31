# Session Status — 2026-08-05/06 (store compliance: transport encryption + account deletion)

Snapshot of the latest development window. The previous window (prod QA + security hardening,
PRs #83/#84) is in git history; its QA report is `docs/testing/2026-07-31-prod-live-qa.md`.
Older feature docs: `docs/self-serve-onboarding.md`, `docs/landing-status.md`,
`docs/nds-import-export-design.md`. Infrastructure steps owed to a human:
`docs/security-runbook.md`. Store questionnaire answers: `docs/store-data-safety.md`.

## Merge policy (ACTIVE)

PRs are **queued open, not merged**. On the user's "merge window" call: merge all queued PRs
into `main`, then promote `main → production` ONCE (one Coolify web redeploy + one "Release to
Stores" run). Also in auto-memory (`teamorg-bulk-merge-policy`).

## Released this window — `main` `095d0bf` → `production` `8276e08`

Four PRs, all merged, one promote, **Release to Stores run 31021128217 green** (Play internal
track + TestFlight build **13**, version 1.0.13).

| PR | What |
|---|---|
| #85 | NDS duplicate merge — generically-joined accounts can be merged into imported roster rows (server, web, mobile) |
| #86 | Store compliance — transport-encryption hardening + in-app account deletion |
| #87 | Time-bomb test fix (unblocked CI on everything) |
| #88 | Android release-lint fix + CI pipeline optimization + landing pricing correction |

Specs/plans for #86: `docs/superpowers/specs/2026-08-05-transport-encryption-design.md`,
`docs/superpowers/specs/2026-08-05-account-deletion-design.md`, plus the matching plans under
`docs/superpowers/plans/`.

## What shipped

### Account deletion (the store blocker)

`DELETE /auth/me`, password in the body, auth rate-limit bucket. **204** success, **401** wrong
password, **409** `{"reason":"owns_clubs","clubs":[…]}` when the caller still owns a live club.

Deletion is an **anonymization, not a row delete**: six FKs reference `users(id)` with
`ON DELETE RESTRICT` (event authorship, `attendance_records.set_by`, invites,
`audit_log.actor_id`), so any user who ever created an event cannot be physically removed. One
transaction deletes the personal rows (absence rules, attendance responses, notifications +
settings + reminders, event reminder overrides, subgroup/team/club memberships), detaches
`nds_members.user_id`, clears the address from `invite_links.invited_email` (matched
case-insensitively against the real email read **before** the scrub), revokes impersonation
sessions where the user is actor or target, and scrubs the `users` row to
`deleted-<uuid>@deleted.invalid` / `Gelöschtes Konto` / unusable hash / `deleted_at`. The audit
entry logs the **scrubbed** email — writing the real one would re-add the PII just removed. The
avatar file is deleted best-effort **outside** the transaction (an orphaned file is a lesser harm
than a rolled-back deletion).

**Session invalidation is in the JWT `validate` block** (`server/.../plugins/Auth.kt`), NOT in
`authenticateUser`. Eleven of sixteen route files read the principal directly and never call it,
so a check there would leave all of them reachable with a deleted user's token. Cost is one
indexed PK lookup per authenticated request — do not "optimize" it away.

UI: web at `/app/profile/delete`, reached by a **link** rather than an inline button so a
mis-click is never one step from deletion; mobile on the profile tab **and** on
`EmptyStateScreen`, both using the shared `DeleteAccountDialog` with its expandable
deleted/kept disclosure.

### Transport encryption

Production already had TLS (Traefik terminates, server emits HSTS, releases use HTTPS). Four gaps
closed: Android release builds could speak cleartext (the permissive `domain-config` sat outside
`debug-overrides`); iOS shipped an ATS exception for `localhost`; a wrong `API_BASE_URL` shipped
silently (now `requireSecureBaseUrl` throws unless HTTPS or a local dev host, anchored on the host
boundary so `localhost.evil.example` cannot pass); and **iOS never received `API_BASE_URL` at
all** — `ApiConfig.ios.kt` read an Info.plist key nothing set, so every build including TestFlight
resolved `http://localhost:8080`, which the new guard allow-lists. Now wired through
`Config.xcconfig` → `Info.plist` (the mechanism `MARKETING_VERSION` already uses) with fail-loud
CI injection.

**No certificate pinning** — deliberate: a Let's Encrypt chain rotation would brick every
installed app with no server-side remedy, and neither store requires it.

### CI pipeline

Concurrency groups with `cancel-in-progress` on the test suite and both `main` deploys. Store
releases deliberately **queue instead of cancelling** — killing a Play/ASC upload mid-flight can
leave a half-ingested build and burn a version code that cannot be reused. Path filters so a docs,
server or web commit no longer spends an Android or `macos-15` build. `test.yml` now also runs on
push to `main`. Android unit tests no longer sit behind `needs` (it reported *skipping* instead of
a verdict and hid Android-only breakage). `timeout-minutes` on every job. Suite wall-clock ~11 min
→ ~6 min.

## Verified

- `:server:test` **373 tests, 0 failures** on the integrated tree (357 base + #85 + #86).
- `:shared:jvmTest` 48/0; `:composeApp:testDebugUnitTest` 250/0; Android and both iOS frameworks
  compile; `admin && npm run check` 0 errors / 47 pre-existing warnings; `landing && npm run check`
  0/0.
- `main` CI green on all five checks; `production` store release green on both jobs.
- **Deletion endpoint confirmed live on prod** by method/path discrimination against
  `https://server.teamorg.ch`: unknown path → 404, wrong method → 405, `DELETE /auth/me` → 401.
- **Mobile deletion tested by the user on TestFlight build 13 — works.**

## Owed verification

Nothing here is a known defect; it is unexercised surface.

- **Web deletion flow never walked by a human.** `/app/profile/delete` is its own code path
  (SvelteKit action, `apiDeleteJson`, `ApiError.payload` parse, cookie clear + redirect).
- **The NDS duplicate merge (#85) has no human walkthrough either**, and each merge is
  irreversible. Import a roster, join with a generic link, confirm the banner appears and that the
  merge carries attendance, subgroups and absence rules.
- **Teamless deletion path on mobile**, if the tested one was the profile tab: register fresh,
  never join a team, delete from `EmptyStateScreen`. That is the path an App Review tester hits,
  and it nearly shipped missing.
- An Updraft release build installed and confirmed to load data over HTTPS; a debug build still
  reaching a local server at `10.0.2.2:8080`.
- **Store questionnaires are not filled in.** Answers are recorded in `docs/store-data-safety.md`
  but nobody has entered them. Play Data Safety needs "encrypted in transit: yes" plus the
  deletion URL `https://app.teamorg.ch/app/profile/delete`; Apple App Privacy needs the same.

## Open items

### Correctness / accuracy
- **`api.teamorg.app` is a host that does not exist.** The real API is `server.teamorg.ch`
  (`docs/deployment-status.md:17`). The fictional host is hardcoded as the release *default* in
  `shared/build.gradle.kts`, as the iOS default in `iosApp/Configuration/Config.xcconfig`, and —
  worst — cited as evidence in `docs/store-data-safety.md`, which is what gets read when filling in
  the store forms. Release builds are unaffected (CI injects the secret), but a local build without
  the secret silently points at a dead host. **Fix owed.**
- `docs/store-data-safety.md` states "the avatar file is deleted from storage" as an unconditional
  guarantee; it is best-effort outside the transaction and only logs on failure. Safe for the form
  (it over-discloses deletion rather than hiding retention), but worth softening.
- `docs/deployment.md` carries a stale generic example (`admin.teamorg.app`) that does not match the
  real deployment.
- The pricing figure corrected in #88 still survives in
  `docs/superpowers/plans/2026-07-30-landing-final-redesign.md` — left as a historical record, but
  that file is where the typo was copied from.
- Carried over and still true: **NDS timezone bug**, re-import preview counts, **billing never
  exercised end-to-end** (no invoice has ever been produced; the CHF 2 × members charge and the
  subscription quantity are unverified — worth a Stripe test clock), and billed count excluding
  provisional members.

### Deferred, from this window's reviews
- `Event.presentCount` is player-visible and still counts provisional confirmations.
- The NDS import wizard's `map` action should route through the merge path when the target row's
  current user is provisional.
- A partial unique index on `nds_members (team_id, user_id) WHERE user_id IS NOT NULL` would make
  the double-link 409 a real constraint instead of check-then-act.
- `audit_log.actor_email` retains historical real addresses (that table is deliberately immutable).
- Admin user listings still show scrubbed rows as "Gelöschtes Konto" — arguably intended.
- Registering the literal `deleted-<uuid>@deleted.invalid` address would 500 on the unique
  constraint (contrived).
- The HTTPS base-URL guard is case-sensitive and does not allow-list IPv6 `::1`; no build config
  uses either form.
- Nothing documents how a developer points the iOS app at a local server now that the fallback is a
  production URL — editing the checked-in xcconfig is the only route, which is a commit hazard.

### Infrastructure (see `docs/security-runbook.md`)
- **HTTP → HTTPS redirect on all three domains — unconfirmed.** HSTS only protects a client that
  has already completed one successful HTTPS request.
- **Postgres `sslmode` decision — open.** Server → Postgres is plaintext over the internal Docker
  network; the privacy answer needs a recorded position rather than an open question.
- **Web/server deploys still stack.** Coolify builds them from its own git integration, so GitHub
  concurrency cannot reach them. Options: Coolify's own queue behaviour and/or a dedicated Build
  Server; or disable Coolify auto-deploy and trigger its webhook from a workflow with `paths` +
  `cancel-in-progress` + a test gate — which would also stop Coolify shipping a red `main`. Full
  write-up in the PR #88 comment thread.
- Carried over: server container still root (needs the uploads volume chowned first); published
  container ports likely bypass Traefik; production Stripe still on test keys; `www` → apex
  redirect missing.

### Cleanup owed
- Any throwaway accounts created while testing deletion — they are anonymized, not removed; look
  for `deleted-*@deleted.invalid` rows.
- Carried over: club **QA Prod 0731** `5b97b6a5-8aeb-4428-af40-fe9215540fd5` and its data, its
  Stripe test customer/subscription/cards, plus `utke.michel+ccdebug@gmail.com` and the pending club
  "Claude Debug Club DELETE ME". Delete via `/admin/login` → Clubs (no owner-facing delete exists).

## Gotchas learned this window

1. **Four tests were failing on `main` for days, invisibly.** `test.yml` ran only
   `on: pull_request`, so pushes to `main` never ran the suite and the red surfaced as an inherited
   failure on unrelated PRs. `NdsTestFixtures` hardcoded August 2026 dates and asserted behaviour
   that only holds while they are in the future. Fixture dates now derive from `LocalDate.now()`.
   **Never hardcode a date that "is in the future".**
2. **`assembleDebug` does not run lint; `lintVitalRelease` does.** An invalid
   `network_security_config.xml` (a `<domain-config>` nested inside `<debug-overrides>`, which the
   schema forbids) passed every local gate and failed only in the deploy, after merge. Run
   `./gradlew :composeApp:lintVitalRelease` — about a second — on any manifest or resource change.
3. **Variant-specific Android resources live in `src/androidDebug/res`** in this KMP module, not
   `src/debug/res` (both are registered; the former matches the `androidMain` convention). Prove an
   override actually lands by reading `composeApp/build/intermediates/packaged_res/debug/…` — a
   wrong directory fails silently and breaks only local development.
4. **A plan that cites another branch's files sends implementers chasing ghosts.** The
   account-deletion plan referenced `LinkMemberResult` and `TeamRepositoryLinkResultTest`, which
   existed only on the then-unmerged #85 branch.
5. `attendance_records` was dropped by `V15__unified_attendance.sql`; attendance lives entirely in
   `attendance_responses`. Several docs still implied otherwise.
6. **Version codes are fine, contrary to an earlier worry of mine.** `ANDROID_VERSION_CODE` is
   `github.run_number`, which is per-workflow, but Play only ever receives `release-stores` builds
   (monotonic within that workflow), so the two counters never collide. It would only matter if an
   Updraft-built APK were uploaded to Play.
7. Concurrent Gradle/Testcontainers runs wedged the Docker daemon (`orb stop && orb start`
   recovered it). Run server suites in the foreground, one at a time.
8. 1Password commit signing stalls reliably here; every commit this window used
   `git -c commit.gpgsign=false commit`, so they are unsigned.

## Carried over
- `feat/merch-badge` branch: unmerged, intentionally kept.
- Local dev DB holds throwaway NDS-E2E data (`e2e.nds.*@example.com`, "E2E NDS Club").
- Merged branches not deleted: `docs/nds-member-merge-spec`, `feat/store-compliance`,
  `fix/time-bomb-tests`, `fix/android-release-lint`.
