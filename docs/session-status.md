# Session Status — 2026-07-31 (post bulk-merge release)

Snapshot of the latest development window. Older feature docs: `docs/self-serve-onboarding.md`,
`docs/landing-status.md`, `docs/nds-import-export-design.md`.

## Merge policy (ACTIVE)

PRs are **queued open, not merged**. On the user's "merge window" call: merge all queued PRs
into `main`, then promote `main → production` ONCE (one Coolify web redeploy + one "Release to
Stores" run). Also in auto-memory (`teamorg-bulk-merge-policy`).

## Released 2026-07-31 (merge window: PRs #78–#81 → main → production)

Pipelines: main merged clean (one trivial `InboxViewModel` conflict #78↔#80, resolved),
production promoted once, **Release to Stores green** (Android → Play internal, iOS →
TestFlight), web live (teamorg.ch 200, app.teamorg.ch serving self-hosted fonts).

### PR #78 — mobile nav/onboarding
- Bottom nav hidden during onboarding (`showBottomBar` excludes `CreateTeamOrClub`,
  `ClubSetup`, `CardSetup`).
- Silent tab refresh: `EventList/TeamsList/Inbox/PlayerProfile` VMs set `isLoading` only when
  no cached data — no more full-screen "T" loader between main tabs.

### PR #79 — bottom-nav animation, avatar, camera
- `TeamorgBottomBar` label enter/exit both `tween(250, FastOutSlowInEasing)` (bounce fix).
- Avatar upload 400: `image/jpg` vs `image/jpeg` MIME fixed client (`TeamRepositoryImpl`) and
  server (`AuthRoutes` whitelist).
- `rememberCameraCaptureLauncher` expect/actual (Android `TakePicturePreview`, iOS camera
  source); profile avatar tap offers Take photo / Choose from library.

### PR #80 — event form, repeats, inbox, design fonts
- Event form: ambiguous Starts/Ends chips (hidden nested tap targets) split into four explicit
  chips: Start date / Start time / End date / End time (`CreateEditEventScreen`).
- Repeats sheet preselects the start date's weekday (`isoDayNumber - 1`, 0=Mon..6=Sun). Also
  fixes weekly patterns confirmed without a weekday producing zero occurrences.
- Inbox stale-list fix: badge polled live but the cached `InboxViewModel` never refreshed →
  "badge 2 / list empty". Now refreshes on screen entry (silent with cached data), delete-all
  reverts on failure, pull-to-refresh state lives in the VM (stuck-spinner fix).
- **Typography = design**: mobile app bundles Roboto Flex variable TTF via compose resources
  (`teamorgTypography()`, weights 400/500/700/800 via wght axis — works on iOS since CMP
  1.8.2); admin web self-hosts Roboto Flex + Google Sans Flex (Google Fonts CDN link removed;
  `font-display` previously silently fell back). Landing was already correct.

### PR #81 — NDS Import v2 (server + admin web + mobile)
Spec `docs/superpowers/specs/2026-07-30-nds-import-existing-teams-design.md` · plan
`docs/superpowers/plans/2026-07-30-nds-import-v2.md` · SDD ledger `.superpowers/sdd/progress.md`.
- Import into **existing teams** with member mapping (map/create/skip; server-side suggestions
  via `NdsMemberMatcher`: exact/umlaut/Levenshtein + birthdate boost; mapping sets NDS fields
  only, adds team role when missing).
- **Conflict resolution** vs existing TeamOrg events (same date+type, bulk per series with
  per-date overrides, default keep-TeamOrg = J-attendance onto the existing event; keep-NDS
  cancels — or detaches when the event is shared with another team — and imports with the
  wizard time incl. RSVP-loss warning with count).
- Wizard-set series times (18:00 placeholder removed), any file subset (AWL „empfohlen"),
  coach authorization, migration **V18** (Angebot unique per club).
- Mobile: 4-step Compose wizard (`NdsImportScreen/ViewModel`), document pickers
  (`DocumentPicker` expect/actual), shared DTOs/repo (`NdsImport.kt`,
  `NdsImportRepository`), entry on the team roster screen.
- Web: `NdsImportDialog` → 4-step wizard (`NdsMappingStep`, `NdsEventsStep`), pure logic in
  `admin/src/lib/nds-import-wizard.ts` (vitest 18), new proxies (team-scoped parse/import,
  manage import, team-members), team-page entry; **vitest introduced to admin**.
- Tests: 30+ new server route tests (permission matrix, rollback, idempotency, two clubs same
  Angebot), planner/matcher unit tests, Playwright E2E `admin/e2e/nds-import.spec.ts`
  (mutation-gated, local stack only, per-run Angebot rewrite for repeatability, needs
  `E2E_CLUB_ID`), 17 mobile VM tests.
- 7 real bugs found by adversarial reviews and fixed pre-merge, incl. a client/server
  `effectiveKeep` divergence on partially-conflicting series and a cross-club Angebot 500.

## Deferred follow-ups (from the NDS v2 final review — none merge-blocking)

- Date-keyed conflict resolutions collapse two conflicts on the same date (key by date+type).
- No import lock: two concurrent imports into one team can duplicate events.
- Re-import ignores changed wizard times for existing NDS events (UX).
- Matching uses `displayName` first-space split — wrong for multi-word last names (suggestions
  only; fix when users get real first/last columns).
- Matcher `take(5)` cap + funktion heuristic untested; no import-route coach-auth test (parse
  has one; code verified).
- Android document picker silently no-ops on unreadable files (add a toast).
- `NdsTimePickerDialog` duplicates `EventTimePickerDialog` (extract shared).
- Detach-instead-of-cancel leaves the detached team's attendance rows on the shared event.
- Spec follow-up idea: unlink/re-link an Angebot from a team (no endpoint today).

## Manual QA still recommended

- Real Anwesenheitsliste round-trip on staging incl. re-import.
- Mobile: file pick + full NDS wizard on physical Android and iOS.
- Roboto Flex rendering on a physical iOS device (wght axis via skiko — compile-verified only).

## Open loose ends (carried over)

- Prod cleanup: test account `utke.michel+ccdebug@gmail.com` + pending club "Claude Debug Club
  DELETE ME" + its test-mode Stripe customer.
- Prod Stripe still on `pk_test` — switch to live keys before real customers pay.
- `feat/merch-badge` branch: unmerged, intentionally kept.
- Local dev DB contains throwaway NDS-E2E data (`e2e.nds.*@example.com`, "E2E NDS Club").
- 1Password commit signing intermittently locked — retry commits when unlocked.
