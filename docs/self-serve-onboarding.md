# Self-Serve Onboarding & Billing

Feature documentation — what was built, how it fits together, how to operate it.
Shipped 2026-07 across three PRs: **#64** (backend), **#66** (web), **#67** (mobile), plus **#65** (landing refresh).
Spec: `docs/superpowers/specs/2026-07-24-self-serve-onboarding-billing-design.md` · Plans: `docs/superpowers/plans/2026-07-24-self-serve-billing-backend.md`, `...-onboarding-web.md`, `2026-07-28-self-serve-onboarding-mobile.md`.

## What it does

First-time users no longer need a super-admin. They can:
- **Join** an existing club/team via invite link or a human-friendly **8-char short code**.
- **Create** their own **team or club** self-serve, with a saved card (Stripe, no upfront charge). Billing is **CHF 2 per member per year**, auto-charged each January.
- **Convert** between team and club at any time (club→team requires exactly one active team).
- The classic path (info mail → super-admin creates the club) still exists and is exempt from auto-billing (`billingMode=manual`).

## Architecture

### Wrapper-club model
Every self-serve signup creates a **Club** row with `kind ∈ {club, team}`. A "team" is a club with one auto-created team whose club layer the UI hides. Conversion is a guarded `kind` flip; billing always attaches to the wrapper club, so conversion never touches billing.

### Billing (Stripe)
- Card captured via **SetupIntent** at creation (native PaymentSheet on mobile, Payment Element on web). No charge at signup.
- One **Stripe Subscription** per club: yearly, per-seat (CHF 2), anchored **Jan 1 Europe/Zurich**, created on billing confirm with idempotency key `sub-create-{clubId}`.
- **Billed member count** = `max(Dec 31 snapshot, median of Oct–Dec samples)` of distinct non-provisional users holding any club/team role. Anti-gaming: a randomized sampling cron (≈weekly Oct–Dec, ≈monthly otherwise) records member counts; the median defeats remove-before-Dec-31 tricks while forgiving one-off spikes.
- **Year-end job (Dec 31)** sets the subscription quantity; Stripe invoices + charges automatically on Jan 1. Deliberately no auto-retry after the idempotency marker — a logged ERROR means fix the quantity in the Stripe dashboard (documented in `BillingJobs.kt`).
- **Dunning**: Stripe Smart Retries ≈3 weeks → subscription marked *unpaid* → webhook freezes the club.

### Club states
- `clubs.status`: `pending` (created, card not yet confirmed; purged after 48h unless a subscription exists) → `active`.
- `clubs.billingStatus`: `active` → `past_due` (payment failed, retrying) → `frozen` (dunning exhausted / subscription canceled). Recovery **only** via a paid invoice (`invoice.paid` webhook) — re-entering a card alone never unfreezes.
- `clubs.billingMode`: `stripe` (self-serve) | `manual` (support-managed, all legacy clubs) | `free`.
- **Frozen enforcement**: every club/team/event/invite/subgroup/attendance **mutation** returns `402` for frozen clubs (role checks run first — no billing-state leak to non-members). Reads always work. Exempt: billing endpoints, convert, invite redeem, leaving a team, own-profile edits, invite deactivation, admin routes.

### Webhook (`POST /stripe/webhook`)
Signature-verified, idempotent (`billing_events.stripe_event_id` unique), 256 KB body cap. Transitions: `invoice.paid`→active, `invoice.payment_failed`→past_due, `customer.subscription.updated(unpaid|canceled)` / `customer.subscription.deleted`→frozen. Handled events with an unresolvable customer return **500** so Stripe retries (guards against API-version mismatch silently swallowing events). Record + status change are atomic (single transaction).

## Backend surface (server/)

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /clubs/self-serve` | any user | pending club (+team if kind=team), Stripe Customer + SetupIntent; returns `clientSecret` + `publishableKey` |
| `POST /clubs/{id}/billing/confirm` | owner | verify SetupIntent, first confirm creates subscription + activates club; later confirms only swap the card |
| `GET /clubs/{id}/billing` | owner | card meta, current + projected count, status, mode, kind |
| `POST /clubs/{id}/billing/update-card` | owner | fresh SetupIntent (+publishableKey) |
| `POST /clubs/{id}/convert` | owner | kind flip (same-kind = no-op; club→team 409 unless exactly 1 active team) |
| `POST /stripe/webhook` | Stripe signature | status transitions (above) |
| `GET /invites/code/{code}` | public | resolve 8-char short code → full invite payload incl. token |

`ownerUserId` (the creator) is the only role allowed on billing/convert; club_managers get 403. All id-bearing endpoints re-verify membership server-side.

Schema: migrations **V16** (clubs kind/owner/billingMode/billingStatus + `club_billing`, `member_count_samples`, `billing_events`; existing clubs → manual/unaffected) and **V17** (`invite_links.short_code`, generated only for reusable invites, alphabet without 0/O/1/I/L).

Jobs (`BillingJobs.kt`, hourly loop): sampling · year-end quantity · pending-club cleanup (skips clubs with a live subscription).

## Web (admin/)

- **`/start`** — public entry: Join / Create / Login (authed users redirected).
- **`/join`** — short-code entry (server-side normalization, same error for invalid/unknown → no code oracle) → existing `/i/{token}` invite flow.
- **`/create`** — wizard: account (register or login) → details (kind cards, name, sport, location, billing email, pricing note) → **`/create/billing`** Stripe Payment Element (setup mode). Handoff via user-bound httpOnly `to_onboarding` cookie (30 min, `secure` outside dev); the client secret never appears in URLs.
- **`/manage/{clubId}/billing`** — owner page: card on file, member counts + basis note, status chip, inline update-card, single-button convert; "managed manually" state for non-Stripe clubs. Sidebar entry "Billing".
- **Frozen UX**: `FrozenBanner` (error tone) / past-due (warning tokens) across the manage area; 402s map to a localized "club frozen" message.
- i18n de/en throughout (German default). E2E: `admin/e2e/onboarding.spec.ts` (mutation-gated; Stripe-dependent test behind `E2E_STRIPE=1`); verified against a live local stack.

## Mobile (composeApp/ + iosApp/)

- **EmptyState** → join-or-create chooser. The join input accepts invite links, raw tokens, and 8-char short codes.
- **CreateTeamOrClubScreen** → details wizard → **CardSetupScreen** with **native Stripe PaymentSheet** (setup mode). On completion the onboarding screens are cleared from the backstack.
- **BillingScreen** (entry: Teams → Billing): card meta, counts, status, update card, convert; friendly not-owner state. Frozen/past-due banner on Teams for all members.
- **Stripe bridge** (`ch.teamorg.payments.rememberCardSetupSheet` expect/actual):
  - Android: `stripe-android 23.13.1`, `rememberPaymentSheet` + `presentWithSetupIntent`.
  - iOS: `stripe-ios 26.0.0` via SPM (`stripe-ios-spm`, product StripePaymentSheet — the project's **first SPM package**, wired in `project.pbxproj`). Kotlin exposes `setSetupPresenter`; `StripeSetupBridge.swift` registers the presenter at launch (stripe-ios is Swift-only, invisible to Kotlin/Native cinterop).
  - 3DS redirects return via `teamorg://stripe-redirect`; iOS `onOpenURL` routes Stripe callbacks **before** the invite deep-link handler.
- The Stripe **publishable key is served by the backend** in the self-serve/update-card responses — no build-time key config in the apps.
- Mobile copy is **English** (app convention); web is German-first.

## Configuration & operations

Server env (all optional, empty fallbacks for dev): `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_ID`, `PUBLIC_STRIPE_PUBLISHABLE_KEY` (same var also feeds the admin app). Stripe dashboard setup (per mode, test AND live): CHF 2/year per-unit price; webhook endpoint with the 4 events **pinned to API version `2025-05-28.basil`** (the SDK's version — mismatch makes the server 500 events on purpose); dunning = Smart Retries then **mark subscription unpaid**. Local webhook testing: `stripe listen --forward-to localhost:8080/stripe/webhook`. Full steps in `SETUP.md`.

## Testing

- Backend: TDD throughout; testcontainers integration tests cover the full flow (self-serve→confirm, IDOR matrix, webhook transitions/idempotency/replay, frozen enforcement, job math incl. year-end idempotency + date gating, billed-count formula, short codes).
- Web: `npm run check` 0 errors; Playwright suite run against a real local stack (6 green).
- Mobile: VM test suites (wizard, card confirm, billing) + iOS sim/Android unit suites + unsigned iOS build.
- **Outstanding manual QA** (needs Stripe sandbox, real devices/browser): test-card happy/decline/3DS paths, update-card, convert, frozen banners — checklists at the end of the web spec header comment (`admin/e2e/onboarding.spec.ts`) and the mobile plan doc.

## Known issues & follow-ups (accepted at review, tracked)

1. **Android rotation/process death while PaymentSheet is open** resets navigation and drops the confirm step (pre-existing nav behavior). Recovery: Billing → update card. Follow-up: pending-club banner + saveable nav state.
2. **No web UI creates reusable invites/short codes** — codes currently mintable via mobile/API only. First fast-follow.
3. Pending club with a crash between subscription-create and activate survives cleanup but needs manual attention (logged).
4. Chunked-encoding requests bypass the webhook Content-Length cap (reverse proxy mitigates; Stripe never chunks).
5. Zero-member stripe club is billed for 1 seat (`coerceAtLeast(1)` vs spec's "zero bills zero") — accepted, CHF 2/year.
6. No rate limiting on `POST /clubs/self-serve` or the code-lookup endpoint; abandoned Stripe Customers aren't cleaned Stripe-side.
7. Session cookie `secure` flag hardcoded false (pre-existing) — flip with `!dev` alongside i18n of raw API error messages.
8. `rememberPaymentSheet` is deprecated in stripe-android 23.13.1 (builder migration is a contract change — deferred).
9. Invite lookups (token + code) don't check active/expiry on GET (redeem does) — pre-existing parity.
