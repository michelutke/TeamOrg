# Live Production Web QA — 2026-07-31

Plan: `docs/superpowers/plans/2026-07-31-prod-live-qa.md` ·
Spec: `docs/superpowers/specs/2026-07-31-prod-live-qa-design.md`

Run: 2026-07-31, 13:15–14:15 CEST. Environment: production, Stripe **test mode** confirmed
(`pk_test` on `/start` and `/create`; consent copy reads "TeamOrg sandbox").
`origin/production` == `origin/main` apart from doc-only commits — no deployment pending.

Throwaway identity: `utke.michel+prodqa0731@gmail.com` ("QA Bot").
Club **QA Prod 0731** `5b97b6a5-8aeb-4428-af40-fe9215540fd5`,
team `693bc58a-5b2a-438d-8bc3-55ae2578a40b`.

## Results

| Phase | Area | Result | Notes |
|---|---|---|---|
| P1 | Landing | pass (3 findings) | CTAs, fonts, i18n, legal all correct; layout + policy nits |
| P2 | Contact form | pass, delivery unconfirmed | Success state shown, no hang; mailbox check pending |
| P3 | Funnel entry | pass | Redirect, back-link, no code oracle |
| P4 | Create + card paths | pass (3DS not run) | Decline then success; club active, no charge |
| P5 | NDS Import v2 | **fail** (1 major) | Import works; wizard times land 2 h late |
| P6 | Billing + convert | pass; update-card pending | Card, counts, status, both conversions OK |
| P7 | Typography / console | pass | Self-hosted fonts, no Google CDN, clean console |

## Findings

### F1 — NDS import writes wizard times as UTC instead of local time
- **Phase:** P5 · **Severity:** major · **Origin:** regression (#81)
- **URL:** `/manage/{clubId}/teams` → NDS-Import wizard
- **Repro:** 1. Run the NDS wizard for a team. 2. Set the MO series to 18:00–19:30 and the
  MI series to 19:00–20:30. 3. Import. 4. Open the created events.
- **Expected:** events at 18:00 / 19:00 Europe/Zurich.
- **Actual:** events show 20:00 / 21:00 — exactly the UTC+2 offset later. The stored value is
  `2026-08-03T18:00:00Z`, i.e. the wizard's local time was persisted as UTC.
- **Control (isolates it to NDS):** a manually created event set to 18:00 stores
  `2026-08-03T16:00:00Z` and renders 18:00 correctly. Same browser, timezone Europe/Zurich.
- **Impact:** every NDS-imported event is off by the local UTC offset — 2 h in summer, 1 h in
  winter. J+S attendance times are wrong as exported.
- **Evidence:** `P05-imported-events.png`, `__data.json` timestamps.

### F2 — Import preview and result counts are wrong on re-import
- **Phase:** P5 · **Severity:** minor · **Origin:** regression (#81)
- **URL:** NDS-Import wizard, confirm step
- **Repro:** 1. Import a file. 2. Run the wizard again with the same file and team.
- **Expected:** the preview reflects what will happen (nothing new).
- **Actual:** the preview claims "8 Events neu / 0 Konflikte"; after import the result box
  says "3 Mitglieder, 0 Termine, 0 Anwesenheiten" although no member was added either.
  The underlying behaviour is correct — no duplicate events are created — only the counts lie.
- **Impact:** a coach cannot tell from the preview whether a re-import is safe.

### F3 — Privacy policy names the wrong email processor
- **Phase:** P1 · **Severity:** minor (legal accuracy) · **Origin:** pre-existing
- **URL:** `/datenschutz` (both languages)
- **Actual:** §3 lists "E-Mail-Versand (Proton Mail / Proton AG, Schweiz)" while §4 speaks of
  "Proton und Infomaniak". Contact mail actually goes out over Infomaniak SMTP
  (`mail.infomaniak.com`, mailbox `info@teamorg.ch`).
- **Impact:** revDSG disclosure names a processor that is not used.

### F4 — Landing scrolls horizontally at narrow desktop widths
- **Phase:** P1 · **Severity:** minor · **Origin:** unknown
- **URL:** `https://teamorg.ch/`
- **Repro:** load at a 864 px viewport; scroll right.
- **Expected:** no horizontal scrolling.
- **Actual:** `documentElement.scrollWidth` 928 vs `clientWidth` 864 — 64 px of horizontal
  scroll. A DOM sweep found no unclipped overflowing element (the roster-pattern SVG sits
  inside `overflow-hidden`), so the culprit needs DevTools to pin down.

### F5 — Logo collides with the first nav link
- **Phase:** P1 · **Severity:** cosmetic · **Origin:** unknown
- **URL:** `https://teamorg.ch/`
- **Actual:** at ~864 px the header renders "TeamOrgFunktionen" with no gap between the
  wordmark and the first nav item.
- **Evidence:** `P01-landing-de.png`.

### F6 — Club layer stays visible for a club of kind "team"
- **Phase:** P4/P6 · **Severity:** minor (spec mismatch) · **Origin:** unknown
- **URL:** `/manage/{clubId}`
- **Expected:** per `docs/self-serve-onboarding.md`, a "team" is a wrapper club **whose club
  layer the UI hides**.
- **Actual:** with kind = Team the sidebar shows the "VEREIN" group with Club / Teams /
  Abrechnung, and the club overview offers Co-Manager and Mitglieder management.
- **Note:** may be intended for the owner view; worth confirming against the spec's intent.

### F7 — Join code field clears itself after an error
- **Phase:** P3 · **Severity:** cosmetic · **Origin:** unknown
- **URL:** `/join`
- **Actual:** after "Dieser Code ist ungültig oder abgelaufen." the entered code is wiped, so
  a typo means retyping all 8 characters.

## Verified working

- **Landing:** DE default; `?lang=en` switches and persists; **hovering** the toggle does not
  flip the cookie (the `data-sveltekit-reload` fix holds); Graphite Cyan theme with teal
  mockups and the Tribüne pattern; pricing "2 CHF / Mitglied / Jahr" with a footnote that no
  longer excludes individuals; hero CTA and pricing CTA both → `https://app.teamorg.ch/start`;
  "Demo anfragen" → `#kontakt`; Impressum/Datenschutz complete and bilingual (no postal
  address, Hetzner Nürnberg named); zero Google-font requests.
- **Contact:** Turnstile solved, submit returned "Danke für deine Anfrage!" in ~2 s, no stuck
  button. Backend accepted the request.
- **Funnel:** anonymous `/` → `/start`; `/login` carries the "Neu hier? Jetzt starten" link;
  unknown and malformed join codes return the identical error (no code oracle).
- **Create + billing:** declined card shows an inline error and stays retryable; `4242`
  succeeds; the client secret never appears in the URL; the club activates and lands in
  `/manage/{clubId}`; card stored as visa ••••4242 12/2034; status chip "Aktiv".
- **NDS import (first run):** 3 files parsed; mapping merged AWL and person rows and defaulted
  every row to "Neuen Nutzer erstellen"; no `18:00` placeholder — the time fields start empty;
  8 events created (4× MO, 4× MI) with the correct dates, type and per-series location;
  6 J-attendances imported (Anna Trainer and Lara Müller show as Zusagen).
- **NDS re-import:** subset run with only `anwesenheitsliste.xlsx` parses (AWL "empfohlen"
  path); already-linked people are marked "bereits verknüpft"; Anna Trainer is suggested
  correctly; **no duplicate events are created**.
- **NDS conflicts:** a non-NDS event on the same date+type is detected ("1 Terminkonflikt(e)"),
  the series default is keep-TeamOrg, per-date override lists the conflicting event by name and
  time, choosing NDS warns "Dieser bestehende Termin wird storniert." and the existing event is
  indeed cancelled after import.
- **Billing/convert:** counts render with the basis note; convert team → club and club → team
  both succeed; no console errors.
- **Typography:** Roboto Flex (body) and Google Sans Flex (headings) self-hosted on both
  `teamorg.ch` and `app.teamorg.ch`; no CDN fallback.

## Known / pre-registered (not counted as new findings)

- **SMTP 535** — status unresolved: the form reported success, so delivery must be confirmed in
  the `info@teamorg.ch` mailbox before the open item can be closed.
- **Cloudflare Turnstile console noise** — none observed this run; remaining console output was
  a MetaMask extension, not first-party.
- **`www.teamorg.ch` → apex redirect** — still missing.
- **Re-import ignores changed wizard times** — confirmed: a re-import with 17:00 left the
  existing events at their original time.

## Not covered

- **Mobile apps** — out of scope by agreement (separate device QA).
- **Frozen / past-due banners** — out of scope; requires forcing Stripe subscription state.
- **3DS card (`4000 0000 0000 3155`)** — not run; the decline → success sequence completed the
  setup first.
- **RSVP-loss warning count** — the conflict path was exercised with an event that had zero
  RSVPs, so the "with count" variant of the warning never rendered.
- **Landing at ≤390 px** — Chrome refused to size the window below ~864 CSS px; needs a manual
  DevTools device-emulation pass.
- **Billing owner-guard** — only one account existed in the session; a non-owner was never
  tested against `/manage/{clubId}/billing`.
- **Update-card** — pending (second test card entered manually by the user).

## Cleanup

Pending. Requires the super-admin console (`/admin/login` → Clubs → QA Prod 0731 → Delete;
the modal requires typing the club name). The web app has no owner-facing club delete.

To delete:
- Club **QA Prod 0731** `5b97b6a5-8aeb-4428-af40-fe9215540fd5` (kind: team) with its team
  `693bc58a-5b2a-438d-8bc3-55ae2578a40b`, 8 NDS events, 1 cancelled probe event
  ("Manual Conflict Probe"), the NDS Angebot and 3 provisional members
  (Lara Müller, Tim Meier, Anna Trainer).
- Account `utke.michel+prodqa0731@gmail.com` ("QA Bot").

For the user, in the Stripe **test** dashboard: the customer for
`utke.michel+prodqa0731@gmail.com`, its subscription, saved cards and SetupIntents.

Carried over from `docs/session-status.md`, untouched by this run:
- Account `utke.michel+ccdebug@gmail.com`
- Pending club "Claude Debug Club DELETE ME" and its test-mode Stripe customer
