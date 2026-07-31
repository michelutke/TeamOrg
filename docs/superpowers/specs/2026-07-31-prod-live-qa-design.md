# Live Production QA — Web (2026-07-31)

Design for a single manual QA pass against production web (`teamorg.ch`,
`app.teamorg.ch`), driven by Claude in Chrome, covering the functionality shipped in the
2026-07-31 release window and the self-serve funnel from 2026-07.

## Goal

Confirm on real production that the recently shipped **web** features work end to end for a
new user, and produce a triaged findings list. Fixing is out of scope for this pass.

## Scope

In scope:

- Landing site `teamorg.ch` (PR #65 visual refresh, #68 funnel CTAs, legal pages, contact
  endpoint).
- Self-serve funnel on `app.teamorg.ch` (#64 backend, #66 web, #68 wiring): `/start`,
  `/join`, `/create` wizard, Stripe Payment Element, billing page, convert.
- NDS Import v2 on web (#81): mapping, wizard times, conflict resolution, file subsets.
- Self-hosted typography on landing and app (#80 web half).

Out of scope:

- Mobile apps (TestFlight / Play internal) — separate device QA.
- Frozen / past-due banner states — require forcing Stripe subscription state on
  production; deliberately deferred.
- Any code fix arising from findings.

## Environment and preconditions

- `origin/production` equals `origin/main` except doc-only commits; no deployment is
  pending. Verified live before the run: `teamorg.ch` 200, `app.teamorg.ch/start` 200,
  `/login` 200, `server.teamorg.ch/health` 200.
- Production Stripe runs on **test keys** (`pk_test`). Test cards therefore create no real
  charges. If production has since been switched to live keys, the run is aborted.
- Chrome extension site permissions required: `teamorg.ch`, `app.teamorg.ch`,
  `js.stripe.com`, `hooks.stripe.com`, `challenges.cloudflare.com`.
- Throwaway identity: `utke.michel+prodqa0731@gmail.com`, club name "QA Prod 0731".
- NDS fixtures from the repo: `admin/e2e/fixtures/anwesenheitsliste.xlsx`,
  `leiter.xlsx`, `teilnehmende.csv`.

## Write policy

Production writes are allowed, confined to one disposable club created through `/create`.
No existing club, team, or member is modified. If a step would touch real data, the run
stops and asks.

## Journey

Executed as one continuous user journey, in this order.

### P1 — Landing

- German is the default; `?lang=en` switches and persists via the `lang` cookie; hovering
  the toggle does not flip the cookie.
- Graphite Cyan theme; roster "Tribüne" pattern on contact and footer; teal phone mockups
  render.
- Pricing reads "2 CHF/Mitglied/Jahr"; the footnote no longer excludes individuals.
- Hero CTA and pricing CTA both link to `https://app.teamorg.ch/start`.
- `impressum` and `datenschutz` render in both languages, name Michel Utke, state hosting
  in Hetzner Nürnberg, and publish no postal address.
- Network panel shows no request to `fonts.googleapis.com` or `fonts.gstatic.com`.
- Console is clean apart from Cloudflare Turnstile noise.
- Layout holds at a 390 px viewport.
- `www.teamorg.ch` redirects to the apex (known open item; expected to fail).

### P2 — Contact form

Submit the form with Turnstile for real. Expected known failure: SMTP `535` auth error.
Regardless of delivery, the UI must not hang on "Wird gesendet", must reset Turnstile, and
must clear the form on success. Delivery to `info@teamorg.ch` is confirmed by the user in
the mailbox.

### P3 — Funnel entry

- Anonymous `app.teamorg.ch/` redirects to `/start`.
- `/login` shows the "Neu hier? Jetzt starten" link back to `/start`.
- `/start` renders the join / create / login chooser.
- `/join` with an unknown 8-character code and with a malformed code returns the **same**
  error message (no code oracle).

### P4 — Create and card paths

`/create`: register the throwaway account, complete the details step (kind = **team**,
name, sport, location, billing email), continue to `/create/billing` with the Stripe
Payment Element. Card variants are retried in place on the same pending club:

1. `4000 0000 0000 0002` — declined; inline error; the step stays retryable.
2. `4000 0000 0000 3155` — 3DS challenge appears and completes.
3. `4242 4242 4242 4242` — succeeds.

Then verify the club becomes `active`, the user lands in the managed club, and Stripe shows
a SetupIntent with **no charge**.

### P5 — NDS Import v2

In the new team:

1. Upload all three fixtures. Mapping step: a fresh club yields "create" for every person;
   the suggestion UI still renders correctly.
2. Events step: set series times through the wizard; no `18:00` placeholder remains.
3. Import and verify created events — dates, times, type, and J-attendance.
4. **Re-import the same file.** The conflict step must appear. Keep the keep-TeamOrg
   default for the series, override one date to keep-NDS, confirm the RSVP-loss warning
   shows a count, and verify the outcome matches both choices.
5. Run one **file-subset** import (`anwesenheitsliste.xlsx` only) to exercise the "AWL
   empfohlen" path.

### P6 — Billing page

`/manage/{clubId}/billing`: card brand and last4, member count with its basis note, status
chip, inline update-card with a second test card (`5555 5555 5555 4444`), then convert
team → club and club → team (the second conversion has exactly one active team and must
succeed).

### P7 — Typography and console on the app

Roboto Flex and Google Sans Flex are self-hosted; no Google CDN font request occurs on
`app.teamorg.ch`; no console errors on the pages visited.

## Evidence and findings

- One screenshot per phase in the session scratchpad (`prod-qa-0731/PNN-*.png`); console
  and network errors captured as they occur.
- Nothing is fixed during the run. Each defect is logged with phase, URL, repro steps,
  expected versus actual, severity (blocker / major / minor / cosmetic), and whether it is
  a regression from PRs #78–#82 or pre-existing.
- Pre-registered expected findings, excluded from the defect list: SMTP 535, Turnstile
  console noise, missing `www` → apex redirect, and the NDS v2 deferred follow-ups already
  recorded in `docs/session-status.md` (same-date conflict collapse, missing import lock,
  re-import ignoring changed wizard times, multi-word last-name splitting).
- Output: `docs/testing/2026-07-31-prod-live-qa.md` — a per-phase result table
  (pass / fail / blocked), the findings list, and screenshot references. Committed on a
  `docs/prod-live-qa` branch; the PR is queued open per the bulk-merge policy.
- Fixes go to separate follow-up branches after the user triages the findings.

## Cleanup

- The disposable club is deleted through the app UI if a delete or leave path exists. If
  none exists, that is reported plainly rather than worked around.
- Imported events and NDS data are removed with the club; whatever cannot be removed is
  listed explicitly (club id, team id, Angebot, account email).
- The user deletes the Stripe test customer and its SetupIntents; Claude supplies the
  customer id and email.
- Earlier leftovers (`utke.michel+ccdebug@gmail.com`, club "Claude Debug Club DELETE ME"
  and its test-mode Stripe customer) are appended to the cleanup list, untouched unless the
  user asks.

## Abort conditions

- Production turns out to run live Stripe keys.
- A step would modify a real club, team, or member.
- Production returns 5xx on core routes, indicating a broken deployment rather than a
  feature defect.
