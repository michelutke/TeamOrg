# Self-Serve Onboarding + Billing — Design

Date: 2026-07-24
Status: Approved (chunks A+B reviewed in session)

## Goal

First-time users can onboard without a super-admin: join an existing club/team via invite link/code, or create their own team or club. Creation requires a saved card; billing is CHF 2 per member per year, charged each January for the previous year. Team and club are convertible in both directions. The existing "contact info mail → super-admin sets up club" path stays as the primary route for full clubs and is exempt from auto-billing.

## Decisions (locked)

| Topic | Decision |
|---|---|
| Standalone team modeling | Wrapper club always: every self-serve signup creates a Club row; `kind = team \| club`. "Team" = club with `kind=team` + one auto-created team; UI hides the club layer. |
| Payment rail | Stripe only, card/TWINT. No self-serve QR-bill; QR-bill = contact support → super-admin sets `billingMode=manual`. |
| Card capture | Native Stripe PaymentSheet (setup mode) in the Compose Multiplatform app via expect/actual; Stripe Elements on web. Card saved at creation, no upfront charge. |
| Charge mechanics | Stripe Subscription per club: yearly cycle anchored Jan 1, per-seat price CHF 2. Cron sets seat quantity before renewal; Stripe auto-invoices and charges the saved card. |
| Billed member count | `max(Dec 31 snapshot, median of Oct 1–Dec 31 samples)` of distinct active users holding any ClubRole or TeamRole in the club. Provisional users (`provisional=true`) excluded. |
| Anti-gaming sampling | Randomized sampling cron: ~monthly Jan–Sep, ~weekly Oct–Dec, random day/hour. Stored per club. |
| Payment failure | Stripe Smart Retries + dunning emails (~3 weeks). Unpaid 30 days after invoice → club `billingStatus=frozen` (read-only). Auto-reactivates on payment. Data never deleted. |
| Conversion | Owner-only. team→club: flip `kind`. club→team: only if exactly 1 active team. Billing unaffected (subscription is on the wrapper club in both cases). |
| Platforms | Join + create flows on both mobile app and admin/web. |

## Data model changes (Exposed, `server/src/main/kotlin/ch/teamorg/db/tables/`)

### ClubsTable (extend)
- `kind` enum `club | team`, default `club`
- `ownerUserId` FK → users, nullable (null = super-admin-managed legacy club)
- `billingMode` enum `stripe | manual | free`, default `manual`
- `billingStatus` enum `active | past_due | frozen`, default `active`
- `status` gains `pending` value for clubs awaiting successful card setup

### ClubBillingTable (new)
`clubId` (FK, unique), `stripeCustomerId`, `stripeSubscriptionId`, `cardBrand`, `cardLast4`, `cardExpMonth`, `cardExpYear`, `billingEmail`, `createdAt`, `updatedAt`. Card fields are display-only metadata; card data never touches our servers.

### MemberCountSamplesTable (new)
`clubId`, `sampledAt`, `memberCount`. Written by the sampling cron.

### BillingEventsTable (new)
`id`, `clubId`, `stripeEventId` (unique — idempotency), `type`, `payload` (jsonb), `processedAt`. Audit trail + webhook dedup.

### Migration
Existing clubs: `kind=club`, `billingMode=manual`, `billingStatus=active`, `ownerUserId=null`. No behavior change for them.

## Backend

### New endpoints
- `POST /clubs/self-serve` (authed, any user): input `{kind, name, sportType?, location?, billingEmail}`. Creates club in `status=pending`, Stripe Customer, returns SetupIntent client secret (+ auto-creates the single team when `kind=team`, creator as coach). Creator becomes `ownerUserId` + `club_manager`.
- `POST /clubs/{id}/billing/confirm` (owner): called after SetupIntent succeeds client-side; verifies with Stripe, creates the Subscription (anchor Jan 1, quantity = current count), activates club.
- `POST /clubs/{id}/convert` (owner): guarded kind flip as above.
- `GET /clubs/{id}/billing` (owner): card metadata, next invoice estimate, billed-count basis.
- `POST /clubs/{id}/billing/update-card` (owner): new SetupIntent for card replacement.
- `POST /stripe/webhook` (public, signature-verified): `invoice.paid`, `invoice.payment_failed`, `customer.subscription.updated` → update `billingStatus`; final dunning failure → `frozen`.

All id-bearing endpoints verify caller role/membership (no-IDOR rule).

### Scheduled jobs
- **Sampling cron**: randomized cadence as decided; writes MemberCountSamples for `billingMode=stripe` clubs.
- **Year-end cron (Dec 31)**: compute billed count per stripe club, update subscription quantity before Jan 1 renewal.
- **Cleanup cron**: delete `pending` clubs older than 48h (abandoned signups).

### Enforcement
Existing role middleware gains a club-status check: `billingStatus=frozen` blocks mutating endpoints for that club (reads still work).

### Invite short codes
Add human-friendly short code (6–8 chars, unambiguous alphabet) alongside existing invite tokens for reusable invites, enterable manually in the app.

## Client flows

### Onboarding (mobile + web)
1. Welcome: **Join a team** / **Create your own** / Login.
2. Join: code or deep link → `GET /invites/{token}` preview → register (existing) → redeem (existing).
3. Create: register → choose Team vs Club (copy: "you can switch later") → name/sport/location → billing screen ("CHF 2 per member per year, billed each January") → PaymentSheet (app) / Elements (web) → confirm → done.

### Mobile specifics
- expect/actual Stripe wrapper: Android PaymentSheet (Compose), iOS PaymentSheet (UIKit presented from CMP).
- Universal/app links so invite URLs open the app.

## Risks & mitigations
- **Apple review (non-IAP payment for digital service)**: position as real-world services (guideline 3.1.3(e)) — app organizes physical sports club activity; same posture as comparable club-management apps. Fallback if rejected: move payment step to external browser link-out.
- **Webhook replay/dup**: `stripeEventId` unique constraint.
- **Abandoned signups**: `pending` + cleanup cron.
- **Billing disputes**: invoice line shows count basis (snapshot vs median); samples retained as evidence.
- **VAT**: not modeled; assumed small-business exempt. Confirm with accountant before launch.

## Testing
- Unit: billed-count math (Dec 31 vs median, zero members, provisional exclusion, manual/free skip), conversion guards.
- Integration: Stripe test clocks — year-end renewal, failed payment → frozen → recovery; webhook idempotency.
- E2E (Playwright, `admin/`): web onboarding join + create paths; frozen-club read-only behavior.

## Out of scope
- Self-serve QR-bill invoicing (support/manual path instead)
- VAT handling
- Plan tiers / pricing changes
- OAuth or email verification changes
