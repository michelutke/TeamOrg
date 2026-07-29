# Self-Serve Onboarding — Web (Plan 2 of 3)

> **STATUS: ACTIVE** — backend merged to main via PR #64 with no API changes (endpoints: POST /clubs/self-serve, POST/GET /clubs/{id}/billing*, POST /clubs/{id}/convert, GET /invites/code/{shortCode}). Executing on branch `feat/self-serve-web`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Public web onboarding: first-time visitors choose "join with a code" or "create your own club/team" (with Stripe card capture), plus an owner billing page and frozen-club UX in the existing admin app.

**Architecture:** Three new public routes (`/start`, `/join`, `/create`) alongside the existing invite flow `/i/[token]`; `/join` resolves short codes to the existing invite flow. `/create` is a 3-step wizard (account → details → card) driven by SvelteKit server actions; only the card step runs client-side JS (Stripe Payment Element in setup mode). Billing management lives at `/manage/[clubId]/billing`. Frozen state surfaces as a banner in the manage layout plus 402-aware error mapping.

**Tech Stack:** SvelteKit (Svelte 5 runes), Tailwind v4 (MD3 tokens), server actions + `ApiError` wrapper (`admin/src/lib/server/api.ts`), `@stripe/stripe-js` (new dep), typed i18n dict de/en, svelte-check + Playwright.

**Spec:** `docs/superpowers/specs/2026-07-24-self-serve-onboarding-billing-design.md`

## Global Constraints

- All backend calls go through `admin/src/lib/server/api.ts` helpers with the session token; no client-side bearer tokens. The ONLY client-side network calls allowed are Stripe's own (Payment Element / `confirmSetup`).
- Stripe publishable key via `$env/dynamic/public` `PUBLIC_STRIPE_PUBLISHABLE_KEY` (document in SETUP.md; never the secret key).
- Every user-facing string added to the typed `Dict` in `admin/src/lib/i18n/index.ts` in BOTH `de` and `en`. German is the default locale — write German copy first, match existing tone (informal "du", as in existing keys).
- Follow existing conventions: `<form method="POST">` + server action + `fail(status, {error})`; Svelte 5 `$props()`/`$derived`; lucide-svelte icons; MD3 Tailwind tokens (`primary`, `surface-container-low`, `outline-variant` etc.); no new component library.
- `npm run check` (in `admin/`) must pass with 0 errors before every commit. No Co-Authored-By in commits.
- E2E tests: mutation tests gated behind `E2E_ALLOW_MUTATION=1` like existing specs; card entry cannot run headless against real Stripe — E2E covers up to the card step, card step itself gets a manual QA checklist.
- Pricing copy (exact, de): "CHF 2 pro Mitglied und Jahr, jeweils im Januar abgerechnet." (en: "CHF 2 per member per year, billed each January.")
- Kind copy must say switching later is possible: de "Du kannst später jederzeit zwischen Team und Verein wechseln." / en "You can switch between team and club anytime later."

## File Structure

- `admin/src/routes/start/+page.svelte` (+`+page.server.ts`) — public welcome: Join / Create / Login
- `admin/src/routes/join/+page.svelte` (+`+page.server.ts`) — short-code entry → redirect `/i/[token]`
- `admin/src/routes/create/+page.svelte` (+`+page.server.ts`) — wizard steps 1–2 (account, details)
- `admin/src/routes/create/billing/+page.svelte` (+`+page.server.ts`) — step 3 (Stripe card) — separate route so the Stripe JS bundle loads only here
- `admin/src/routes/(shell)/manage/[clubId]/billing/+page.svelte` (+`+page.server.ts`) — owner billing page (card meta, projected count, update card, convert)
- `admin/src/lib/components/FrozenBanner.svelte` — reusable banner
- `admin/src/lib/server/billing.ts` — thin typed wrappers for the 6 billing/self-serve endpoints
- `admin/src/lib/i18n/index.ts` — new `onboarding` + `billing` namespaces
- `admin/e2e/onboarding.spec.ts` — join + create-wizard E2E (up to card step)

---

### Task 1: i18n namespaces + typed billing API wrappers

**Files:**
- Modify: `admin/src/lib/i18n/index.ts`
- Create: `admin/src/lib/server/billing.ts`

**Interfaces:**
- Produces (consumed by Tasks 2–6):

```ts
// admin/src/lib/server/billing.ts
import { apiGet, apiPost } from './api';

export type SelfServeCreate = { kind: 'club' | 'team'; name: string; sportType?: string; location?: string; billingEmail: string };
export type SelfServeCreated = { clubId: string; teamId?: string | null; setupIntentClientSecret: string };
export type BillingInfo = {
  billingEmail: string; cardBrand: string | null; cardLast4: string | null;
  cardExpMonth: number | null; cardExpYear: number | null;
  currentMemberCount: number; projectedBilledCount: number;
  billingStatus: 'active' | 'past_due' | 'frozen'; billingMode: 'stripe' | 'manual' | 'free';
};

export const createSelfServe = (token: string, body: SelfServeCreate) => apiPost<SelfServeCreated>('/clubs/self-serve', token, body);
export const confirmBilling = (token: string, clubId: string, setupIntentId: string) => apiPost<{ status: string }>(`/clubs/${clubId}/billing/confirm`, token, { setupIntentId });
export const getBilling = (token: string, clubId: string) => apiGet<BillingInfo>(`/clubs/${clubId}/billing`, token);
export const updateCard = (token: string, clubId: string) => apiPost<{ setupIntentClientSecret: string }>(`/clubs/${clubId}/billing/update-card`, token);
export const convertClub = (token: string, clubId: string, targetKind: 'club' | 'team') => apiPost<{ kind: string }>(`/clubs/${clubId}/convert`, token, { targetKind });
export const lookupInviteCode = (shortCode: string) => apiGet<{ token: string }>(`/invites/code/${encodeURIComponent(shortCode)}`, /* public: */ undefined);
```

(Adapt the `apiGet`/`apiPost` signatures to the real ones in `api.ts` — if they don't support unauthenticated calls, add an optional-token variant for `lookupInviteCode` following the file's own style.)

- i18n: add `onboarding.*` and `billing.*` keys to the `Dict` interface + `de` + `en` objects. Required keys (naming per existing convention): `onboarding.welcomeTitle`, `welcomeSubtitle`, `joinCta`, `createCta`, `loginCta`, `joinTitle`, `joinCodeLabel`, `joinCodeInvalid`, `joinSubmit`, `createAccountTitle`, `createDetailsTitle`, `kindTeam`, `kindClub`, `kindTeamHint`, `kindClubHint`, `kindSwitchNote`, `nameLabel`, `sportLabel`, `locationLabel`, `billingEmailLabel`, `pricingNote`, `cardTitle`, `cardSubmit`, `cardProcessing`, `cardError`, `done`; `billing.title`, `cardOnFile`, `noCard`, `memberCount`, `projectedCount`, `updateCard`, `convertToClub`, `convertToTeam`, `convertBlocked`, `statusActive`, `statusPastDue`, `statusFrozen`, `frozenBanner`, `frozenBannerCta`. German first, exact pricing/switch copy from Global Constraints.

- [ ] **Step 1:** Add the i18n keys (Dict + de + en). Run: `cd admin && npm run check` — Expected: 0 errors (type-safe dict compiles).
- [ ] **Step 2:** Create `billing.ts` per above, adapted to real `api.ts` signatures. Run: `npm run check` — 0 errors.
- [ ] **Step 3:** Commit: `git commit -m "feat(web): i18n + typed API wrappers for self-serve onboarding"`

---

### Task 2: /start welcome page

**Files:**
- Create: `admin/src/routes/start/+page.svelte`, `admin/src/routes/start/+page.server.ts`

**Interfaces:**
- Consumes: i18n `onboarding.*` (Task 1).
- Produces: public route `/start` with three actions: link to `/join`, link to `/create`, link to `/login`. If already authenticated (session cookie valid), `+page.server.ts` redirects to `/app`.

- [ ] **Step 1:** `+page.server.ts` load: resolve session (same helper `/login` uses — see `admin/src/routes/login/+page.server.ts`); if logged in → `redirect(303, '/app')`; else return `{}`.
- [ ] **Step 2:** `+page.svelte`: centered card layout (match `/login` styling), app logo, `welcomeTitle`/`welcomeSubtitle`, two primary option cards (Join: `joinCta` + short description; Create: `createCta` + `pricingNote`), subdued `loginCta` link. Lucide icons (`Users`, `PlusCircle`). MD3 tokens only.
- [ ] **Step 3:** Manual verify: `npm run dev`, open `/start` logged out (shows page) and logged in (redirects). `npm run check` 0 errors.
- [ ] **Step 4:** Commit: `feat(web): public /start onboarding entry page`

---

### Task 3: /join short-code entry

**Files:**
- Create: `admin/src/routes/join/+page.svelte`, `admin/src/routes/join/+page.server.ts`

**Interfaces:**
- Consumes: `lookupInviteCode` (Task 1), existing `/i/[token]` flow (unchanged — it already handles register/redeem/mismatch states).
- Produces: form with one code input (8 chars, auto-uppercase, spaces/dashes stripped); action calls `GET /invites/code/{code}`; on success `redirect(303, '/i/' + token)`; on 404 `fail(404, { error: m.onboarding.joinCodeInvalid })`. Also accepts `?code=XXXX` query param to prefill (deep-link support).

- [ ] **Step 1:** Server action `lookup`: normalize input (`code.trim().replaceAll(/[\s-]/g, '').toUpperCase()`), validate 8 chars before calling API (client-side friendly error otherwise), call `lookupInviteCode`, catch `ApiError` 404 → `fail`. Redirect on success.
- [ ] **Step 2:** Page: single input styled like login inputs, monospace, `maxlength="8"`, uppercase transform on input; submit `joinSubmit`; error rendering per existing `fail` pattern; back-link to `/start`.
- [ ] **Step 3:** Manual verify with a real reusable invite code from a dev-server club. `npm run check` 0 errors.
- [ ] **Step 4:** Commit: `feat(web): join-with-code page resolving to invite flow`

---

### Task 4: /create wizard — account + details steps

**Files:**
- Create: `admin/src/routes/create/+page.svelte`, `admin/src/routes/create/+page.server.ts`

**Interfaces:**
- Consumes: `register()`/`login()` from `admin/src/lib/server/auth.ts` (same as `/i/[token]` uses), `createSelfServe` (Task 1).
- Produces: two-step wizard on one route (step in `$page.url.searchParams` or form-carried state):
  - Step "account": if session exists → skip to details. Else registration form (email, password ≥8, displayName) using `register()` (auto-login sets cookie), or "already have an account" → `/login?redirect=/create`.
  - Step "details": kind picker (two cards: `kindTeam`/`kindClub` with hints + `kindSwitchNote`), name, sport (default volleyball; reuse the sport options source used elsewhere if one exists, else free text), location (optional), billingEmail (prefilled with account email), `pricingNote` displayed prominently. Action calls `createSelfServe` → on success `redirect(303, '/create/billing?clubId=...&secret=...')` — NO: never put the client secret in the URL. Instead: store `{clubId, teamId, setupIntentClientSecret}` in a short-lived httpOnly cookie `to_onboarding` (JSON, maxAge 1800, path='/create') and redirect to `/create/billing`.
- Validation mirrors backend: kind ∈ {club,team}, name non-blank, billingEmail contains @ — friendly `fail()` messages before hitting the API; surface `ApiError` messages otherwise.

- [ ] **Step 1:** Implement `+page.server.ts` (load resolves session → which step; actions `register` and `create` per above, cookie handling for the billing handoff).
- [ ] **Step 2:** Implement `+page.svelte`: step indicator (1 Konto → 2 Details → 3 Zahlung), account form, details form with kind cards (selected state via `border-primary` etc.).
- [ ] **Step 3:** Manual verify against dev backend: fresh browser → `/create` → register → details (team) → lands on `/create/billing` with cookie set; club exists as `pending` in DB. `npm run check` 0 errors.
- [ ] **Step 4:** Commit: `feat(web): create-club wizard account and details steps`

---

### Task 5: /create/billing — Stripe Payment Element (setup mode)

**Files:**
- Modify: `admin/package.json` (add `@stripe/stripe-js`)
- Create: `admin/src/routes/create/billing/+page.svelte`, `admin/src/routes/create/billing/+page.server.ts`
- Modify: `SETUP.md` (PUBLIC_STRIPE_PUBLISHABLE_KEY for admin app), `admin/.env.example` if present

**Interfaces:**
- Consumes: `to_onboarding` cookie (Task 4), `confirmBilling` (Task 1), backend confirm endpoint.
- Produces the card step:
  - `+page.server.ts` load: read+validate `to_onboarding` cookie (missing → redirect `/create`); return `{clubId, clientSecret, publishableKey: env.PUBLIC_STRIPE_PUBLISHABLE_KEY}`.
  - `+page.svelte` (client): `loadStripe(publishableKey)` → `stripe.elements({clientSecret})` → mount Payment Element → on submit `stripe.confirmSetup({elements, redirect: 'if_required'})`. If Stripe returns a redirect-based method, set `return_url` to `/create/billing` (absolute) — on return, `setup_intent` + `setup_intent_client_secret` arrive as query params; handle both paths.
  - After `confirmSetup` succeeds (or on return-redirect with `setup_intent`), POST the SetupIntent id to the `confirm` server action → `confirmBilling(token, clubId, setupIntentId)` → clear `to_onboarding` cookie → `redirect(303, '/manage/' + clubId)` (owner is club_manager, manage guard passes). On backend 402 → `fail(402, {error: m.onboarding.cardError})` and let the user retry (fresh Element state).
  - Show `pricingNote` again above the Element ("no charge now — first invoice each January" nuance in copy).

- [ ] **Step 1:** `npm i @stripe/stripe-js` in `admin/`; wire `PUBLIC_STRIPE_PUBLISHABLE_KEY` via `$env/dynamic/public`; SETUP.md note.
- [ ] **Step 2:** Implement server load + `confirm` action per above.
- [ ] **Step 3:** Implement the client page (Svelte 5: `onMount` for Stripe init, `$state` for submitting/error).
- [ ] **Step 4:** Manual verify with Stripe TEST keys end-to-end: card `4242 4242 4242 4242` → lands on `/manage/{clubId}`; club `active` in DB, subscription exists in Stripe test dashboard. Also verify decline card `4000 0000 0000 0002` shows `cardError` and allows retry. `npm run check` 0 errors.
- [ ] **Step 5:** Commit: `feat(web): stripe card setup step for self-serve creation`

---

### Task 6: Billing settings page + convert

**Files:**
- Create: `admin/src/routes/(shell)/manage/[clubId]/billing/+page.svelte`, `+page.server.ts`
- Modify: the manage sidebar/nav (where existing manage sections are declared) to add a "Billing" entry — visible only when the load says the caller is the owner (see below)

**Interfaces:**
- Consumes: `getBilling`, `updateCard`, `convertClub` (Task 1); Stripe Element pattern (Task 5) for the update-card flow.
- Produces `/manage/[clubId]/billing`:
  - Load: call `getBilling`; backend 403 (not owner) → SvelteKit `error(403)`; render card-on-file (brand/last4/exp or `noCard`), `memberCount`, `projectedCount` with a one-line explanation of the count basis, status chip (`StatusChip.svelte` if it fits; else MD3 badge), and — when `billingMode !== 'stripe'` — a "managed manually" note instead of card UI.
  - `updateCard` action → returns fresh clientSecret → same Payment Element flow inline (progressive: section swaps to card form) → confirm action reuses `confirmBilling`.
  - Convert section: current kind fetched from the club detail the manage layout already loads (it exposes `kind` since the backend change); one button (`convertToClub` when kind=team, `convertToTeam` when kind=club); backend 409 → `fail` with `convertBlocked`. After team→club conversion, note that multi-team UI is now available.
  - Nav entry: owner check = `getBilling` succeeding; simplest correct approach: the billing page handles 403 itself, and the nav entry shows for club_managers with a lock state — DECISION for implementer: only show nav entry when `club.ownerUserId === session.userId` if the club DTO exposes ownerUserId to the web; check the actual club payload first; if absent, show entry and rely on the 403 page.
- [ ] **Step 1:** Server load + actions. **Step 2:** Page UI. **Step 3:** Manual verify as owner (full page), as non-owner club_manager (403), convert both directions incl. 409 with 2 teams. `npm run check` 0 errors. **Step 4:** Commit: `feat(web): club billing settings and conversion page`

---

### Task 7: Frozen-club UX

**Files:**
- Create: `admin/src/lib/components/FrozenBanner.svelte`
- Modify: `admin/src/routes/(shell)/manage/[clubId]/+layout.server.ts` + layout svelte (expose `billingStatus`, render banner)
- Modify: the shared error-mapping spot(s) where `ApiError` → user message happens (find via grep for existing 403 mappings) to map 402 → `billing.frozenBanner` message

**Interfaces:**
- Produces: red/error-container banner at the top of all `/manage/[clubId]/*` pages when `billingStatus === 'frozen'`: `frozenBanner` text + `frozenBannerCta` linking to `/manage/[clubId]/billing`. Mutating actions that hit backend 402 show the same message inline instead of a generic error. `past_due` shows a softer amber variant (same component, `variant` prop) WITHOUT blocking copy.

- [ ] **Step 1:** Component (props: `variant: 'frozen' | 'past_due'`, `clubId`). **Step 2:** Layout wiring (club detail already carries `billingStatus` after backend change — verify, else extend the layout's club fetch). **Step 3:** 402 mapping in the error helper(s). **Step 4:** Manual verify by setting a dev club's `billing_status='frozen'` in DB: banner shows, team-create fails with friendly message, billing page reachable. `npm run check` 0 errors. **Step 5:** Commit: `feat(web): frozen and past-due billing banners + 402 handling`

---

### Task 8: E2E + final gate

**Files:**
- Create: `admin/e2e/onboarding.spec.ts`

**Interfaces:** mirrors existing spec conventions (`E2E_BASE_URL`, `E2E_ALLOW_MUTATION` gate, explicit empty `storageState` for anonymous contexts — see scenarios.spec.ts precedent).

- [ ] **Step 1:** Tests (mutation-gated where they create data): `/start` renders both CTAs anonymous; `/join` with garbage code shows error; full join flow with a freshly created reusable invite (existing seeded flows from scenarios.spec.ts as template); `/create` wizard through account+details against dev backend → asserts redirect to `/create/billing` and that the Payment Element iframe mounts (do NOT attempt card entry headless).
- [ ] **Step 2:** Manual QA checklist appended to the spec file header comment: test-card happy path, decline path, 3DS card (`4000 0027 6000 3184`), update-card, convert, frozen banner.
- [ ] **Step 3:** Run `npm run check` (0 errors) + `npx playwright test e2e/onboarding.spec.ts` against local dev stack. Commit: `test(web): onboarding e2e + manual QA checklist`

---

## Deferred / Plan 3 handoff

- Mobile (CMP) onboarding + native PaymentSheet — Plan 3, written after this plan ships.
- Landing page (`landing/`) "Get started" links pointing at `app.teamorg.ch/start` — one-liner, fold into Task 2 if trivial or defer.
- Public `/register` (non-invite, non-create) remains intentionally absent — every path either joins via invite/code or creates a club.
