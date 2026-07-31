# Session Status — 2026-07-30 (for context resume)

Snapshot of in-flight work and recent decisions. Older feature docs: `docs/self-serve-onboarding.md`, `docs/landing-status.md`.

## Merge policy (ACTIVE, since 2026-07-30)

PRs are **queued open, not merged**. On the user's "merge window" call: merge all queued PRs into `main`, then promote `main → production` ONCE (one Coolify web redeploy + one "Release to Stores" run instead of many). Also stored in auto-memory (`teamorg-bulk-merge-policy`).

## PR queue (open, checks running, DO NOT merge until window)

| PR | Branch | Content |
|---|---|---|
| #78 | `fix/mobile-nav-issues` | Bottom nav hidden during onboarding (`showBottomBar` now excludes `CreateTeamOrClub`, `ClubSetup`, `CardSetup` in `TeamorgApp.kt`) + silent tab refresh: `loadEvents/loadTeams/loadNotifications/loadProfile` set `isLoading` only when no cached data (`EventList/TeamsList/Inbox/PlayerProfile` VMs) so the full-screen `TeamorgLoader` ("T indicator") no longer flashes on tab switches. |
| #79 | `fix/mobile-nav-avatar` | (a) FBN bounce fix: `TeamorgBottomBar` label enter/exit both `tween(250, FastOutSlowInEasing)` instead of overshooting `spring(0.6)` + unmatched exit → single clean width animation. (b) Avatar upload 400: pickers report ext `jpg` → client sent `image/jpg`, server whitelist only had `image/jpeg` → part discarded. Client (`TeamRepositoryImpl.uploadAvatar`) maps jpg/jpeg→`image/jpeg`; server (`AuthRoutes` `/auth/me/avatar`) also accepts `image/jpg`. (c) New `rememberCameraCaptureLauncher` expect/actual (`ImagePicker.kt/.android/.ios`): Android `TakePicturePreview` (no CAMERA permission declared → none needed), iOS camera source (NSCameraUsageDescription already in Info.plist from ITMS-90683 fix), returns null w/o camera; `PlayerProfileScreen` avatar tap shows Take photo / Choose from library dialog, falls back to gallery when no camera. |

Branches #78 and #79 are independent (both off `main`, no overlapping files). Verified each: Android+iOS compile, `:composeApp:testDebugUnitTest`, `:server:test` (needs OrbStack/Docker running for Testcontainers).

## Shipped earlier today (already live)

- **Landing final redesign** (PR #76 → #77 → production, live on teamorg.ch): CSS-var theme tokens light/dark (OS-pref default + Nav toggle), `TessellationPattern.svelte` (exact brand P2 lattice), hero panel card + fade-out, `RosterPattern` outline mode, ONE shared Tribüne pattern across Contact+Footer (Footer moved from `+layout` to `+page` with route guard for legal pages), open trimmed pricing (Dict 9 keys / 4 includes, de+en). Details: memory `teamorg-landing-redesign`, plan `docs/superpowers/plans/2026-07-30-landing-final-redesign.md`, SDD ledger `.superpowers/sdd/progress.md` (Plan 4).
- **Store review fixes** (build 9, uploaded green): `NSCameraUsageDescription` (ITMS-90683, stripe-ios references card-scan APIs) + Android adaptive icon scaled 0.75 to the 66dp safe zone (round masks clipped the top).
- **iOS SPM signing fix** (PR #72): manual signing scoped to app target via `update_code_signing_settings` — global xcargs broke on stripe-ios SPM targets. Memory `teamorg-release-pipeline` updated.
- **Billing confirm fix** (PR #70): hidden form posted empty `setupIntentId` (Svelte 5 bind flush async vs sync `requestSubmit()`); DOM value now set directly; gated E2E regression added.

## Figma (design source of truth)

File `iKcGJfgxUxMi2AnE9o4BAL`, section "TeamOrg Landing Page" (60876:173): final frames "Landing FINAL — Dark/Light" + variants A–F/E1–E3. Pattern board on page "Logo Concept — Graphite Cyan" (P2 Tessellation 61010:23768). Stray empty draft file `rCEmuKm34X07JTIpqkb7jD` ("TeamOrg Landing Explorations") should be trashed by the user (no delete API).

## Open loose ends

- Prod cleanup: test account `utke.michel+ccdebug@gmail.com` + pending club "Claude Debug Club DELETE ME" + its test-mode Stripe customer.
- Prod Stripe still on `pk_test` — switch to live keys before real customers pay.
- `feat/merch-badge` branch: unmerged, intentionally kept.
- 1Password commit signing intermittently locked — retry commits when user unlocks.
- Final-review residuals (accepted, landing): Turnstile `data-theme="auto"` follows OS not site toggle; `/i/[token]` `surface-variant` hover class was never defined (pre-existing no-op).
