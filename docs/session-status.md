# Session Status — 2026-07-31 (second window: prod QA + security hardening)

Snapshot of the latest development window. Older feature docs: `docs/self-serve-onboarding.md`,
`docs/landing-status.md`, `docs/nds-import-export-design.md`. Infrastructure steps owed to a
human: `docs/security-runbook.md`. QA detail: `docs/testing/2026-07-31-prod-live-qa.md`.

## Merge policy (ACTIVE)

PRs are **queued open, not merged**. On the user's "merge window" call: merge all queued PRs
into `main`, then promote `main → production` ONCE (one Coolify web redeploy + one "Release to
Stores" run). Also in auto-memory (`teamorg-bulk-merge-policy`).

## Released 2026-07-31, second window (PRs #83, #84 → main → production `b8ec450`)

Both PRs merged, `main` promoted once, **Release to Stores run 30638103994 green** (iOS →
TestFlight, Android → Play internal). Web verified live afterwards: all three domains 200,
`/start` renders, security headers present on the wire (HSTS, nosniff, frame-deny, referrer
policy on all three; CSP on both web apps; CORP on the API). The server booting at all
confirms the production `JWT_SECRET` satisfies the new ≥32-character guard.

**Deploy cost an outage:** all three domains were unreachable for ~12 minutes while the
containers rebuilt simultaneously (the server image runs a full Gradle build). Port 443 kept
accepting connections throughout — only the upstreams were missing. Consider staggering
redeploys next time.

## PR #83 — live production web QA

Chrome-driven manual pass over landing, the self-serve funnel, NDS Import v2 and billing.
Full report with repro steps in `docs/testing/2026-07-31-prod-live-qa.md`.

### Confirmed working
Landing i18n incl. the hover-does-not-flip-the-cookie fix, Graphite Cyan theme, pricing copy,
both CTAs → `/start`, bilingual legal pages, self-hosted fonts (zero Google requests).
Anonymous `/` → `/start`; join codes give an identical error for unknown and malformed input
(no oracle). Create wizard: declined card retryable, `4242` succeeds, client secret never in
the URL, club activates with no charge. NDS import: 8 events with correct dates/type/location,
6 J-attendances, mapping defaults, "bereits verknüpft" recognition, AWL-only subset parse, no
duplicate events on re-import, conflict detection with per-date override and cancellation.
Billing: card metadata, counts, update-card, and both conversions.

### Findings
1. **NDS import writes wizard times as UTC (major, open).** Wizard 18:00 stores
   `2026-08-03T18:00:00Z` and renders 20:00 in Europe/Zurich — off by the local offset (2 h
   summer, 1 h winter). Isolated with a control: a manually created 18:00 event stores
   `16:00Z` and renders correctly, so this is NDS-specific, not display. Every imported event
   and its J+S attendance time is wrong. **Not yet fixed.**
2. Re-import preview counts lie: claims "8 Events neu / 0 Konflikte" when nothing is created,
   and the result box reports "3 Mitglieder" when none were added. Behaviour is correct
   (idempotent); only the numbers mislead.
3. Privacy policy names Proton Mail as the email processor; delivery actually runs over
   Infomaniak SMTP.
4. Landing scrolls horizontally 64 px at an 864 px viewport (culprit not isolated).
5. Logo wordmark collides with the first nav link at that width.
6. Club layer stays visible for a club of `kind=team`, though the spec says the UI hides it —
   may be intentional for the owner view.
7. `/join` clears the code field after an error.

### Resolved by this run
The **SMTP 535** open item in `docs/landing-status.md` is closed: a real submission was
delivered to `info@teamorg.ch`.

### Not covered
Mobile apps; frozen/past-due banners; the 3DS card (the decline → success sequence completed
setup first); the RSVP-loss warning's count variant (the conflict event had zero RSVPs);
landing below 390 px (Chrome refused to size the window under ~864 CSS px); the billing
owner-guard (only one account existed).

## PR #84 — security hardening (server, web, infra, mobile)

### Server
- Unhandled exceptions returned the exception type, message and 2000 chars of stack trace to
  the client. Now a correlation id; details stay in the log.
- **Impersonation revocation was cosmetic**: `/admin/impersonate/end` flipped `is_active` but
  nothing ever read it, so the token kept working for its remaining hour and a leaked one
  could never be killed. Sessions are now verified per authenticated request.
- Login skipped bcrypt entirely for unknown addresses, making it an account-enumeration
  oracle by timing. Now always constant-cost against a dummy hash.
- Avatar **and club-logo** uploads trusted the client `Content-Type` and buffered the body
  unbounded. Both now validate magic bytes (`storage/ImageValidation.kt`) and cap the read.
- Rate limits on login/register/change-password (20/min) and invite short codes (20/min).
- Startup refuses the placeholder or a sub-32-character JWT secret
  (`ALLOW_WEAK_JWT_SECRET=true` for local dev, documented in `SETUP.md`).
- Contact endpoint: length caps, CRLF header-injection guard on the fields that reach
  `Reply-To`, constant-time shared-secret comparison.
- Global security headers; uploads served with a sandboxing CSP and private caching.

### Web
- **Session and impersonation cookies were written `secure: false` in production** — the code
  even carried the comment "true in production". The session JWT could travel over plain
  HTTP. Now `secure: !dev`.
- CSP declared through SvelteKit's `csp` config on both apps, so hydration script hashes stay
  correct; Stripe allowlisted on admin, Turnstile on landing, API origin appended at runtime.

### Infra
- `admin` and `landing` containers run as the image's unprivileged `node` user;
  `no-new-privileges` on all three services.
- The **server container deliberately stays root**: it writes to the pre-existing `uploads`
  volume, and switching users without chowning it first breaks avatar and logo uploads. Steps
  in the runbook.

### Mobile
- Android: `allowBackup=false` plus `data_extraction_rules.xml` excluding `teamorg_prefs.xml`,
  so the session JWT is no longer copied out by cloud backup or device-to-device transfer.
- iOS: session storage moved from `NSUserDefaults` (a plain plist in the app container) to the
  Keychain, with first-launch migration so no one is logged out.

### Adversarial review (Fable) — rejected the first pass, all findings fixed
1. `XForwardedHeaders` used the default `useFirstProxy()`, trusting the client-supplied first
   `X-Forwarded-For` entry. Rotating a fake header per request would have given a fresh
   limiter key every time — unlimited login attempts plus unbounded key-map growth. Now
   `useLastProxy()`.
2. The auth limiter wrapped the whole `/auth` subtree including `/auth/me` and
   `/auth/me/roles`, which the admin app calls server-to-server on every page load from one
   container IP. All users would have shared a 10/min bucket (~5 page loads/min) and appeared
   logged out. Rescoped to the three credential endpoints at 20/min.
3. The contact limiter capped the entire internet at 5 submissions per 10 minutes, same root
   cause. Removed — Turnstile, honeypot and shared secret already cover abuse.
4. The club-logo upload was still unvalidated while a comment claimed otherwise. Fixed for
   real.
5. Over-engineering cut: CSP and Permissions-Policy on a JSON API (only produced duplicate
   headers on `/uploads`), a redundant `X-Forwarded-Proto` re-check, and the duplicate
   `nosniff`.

Fable also confirmed as safe: the JWT guard against the test suites, the Ktor 2 → 3
`/uploads` rewrite, the impersonation check (no per-request DB cost for normal tokens, no
nested transaction), and `readRemaining` as correct Ktor 3 API.

### Tests
`:server:test` green (353, two new: login is limited while `/auth/me` is not; HTML disguised
as `image/png` is rejected). `:shared:jvmTest` green. Both web apps type-check and build; iOS
and Android targets compile. CSP verified in a real browser against local production builds —
no violations, hydration intact, Turnstile rendering and passing.

**Note on tooling:** the integration suites need a container runtime. Docker Desktop is not
installed; **OrbStack** is (`docker context` → `orbstack`). With it stopped, ~288 server tests
and 2 shared tests fail with `NoClassDefFoundError` — environmental, not real failures.

## Open items

### Correctness
- **NDS timezone bug (above) — unfixed and user-visible.**
- Re-import preview counts (finding 2).
- **Billing has never been exercised end-to-end.** Signup only captures a card via SetupIntent;
  no invoice has ever been produced in either mode, and the CHF 2 × members charge, the
  subscription quantity and the billed-count formula are all unverified. Worth advancing a
  Stripe test clock against a test-mode subscription before real customers arrive.
- Billed count excludes provisional members: a club whose roster came from an NDS import and
  who never sign in bills as 1 member. Spec-conformant, but confirm it is intended.

### Infrastructure (see `docs/security-runbook.md`)
- Server container still root (needs the uploads volume chowned first).
- Published container ports likely bypass Traefik — verify before changing.
- Production Stripe still on test keys; live webhook endpoint must be pinned to API version
  `2025-05-28.basil` (confirmed as stripe-java 29.2.0's pin).
- `www` → apex redirect still missing.

### Cleanup owed
- Club **QA Prod 0731** `5b97b6a5-8aeb-4428-af40-fe9215540fd5` (team
  `693bc58a-5b2a-438d-8bc3-55ae2578a40b`), its 8 NDS events, one cancelled probe event, the
  Angebot and 3 provisional members — delete via `/admin/login` → Clubs (no owner-facing
  delete exists). Account `utke.michel+prodqa0731@gmail.com`.
- Its Stripe **test** customer, subscription, two saved cards and SetupIntents.
- Carried over: `utke.michel+ccdebug@gmail.com` and pending club "Claude Debug Club DELETE ME".

### Carried over
- `feat/merch-badge` branch: unmerged, intentionally kept.
- Local dev DB holds throwaway NDS-E2E data (`e2e.nds.*@example.com`, "E2E NDS Club").
- 1Password commit signing intermittently locks — retry commits when it does.
