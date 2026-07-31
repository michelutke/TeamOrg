# Live Production Web QA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Exception:** this plan is executed by a browser-driving operator in ONE session, not
> by parallel subagents — the journey shares a single logged-in browser session and one
> disposable production club. Run it inline.

**Goal:** Run one manual end-to-end QA pass against production web (`teamorg.ch`,
`app.teamorg.ch`) covering the 2026-07 self-serve funnel and the 2026-07-31 release, and
produce a triaged findings report.

**Architecture:** Claude drives the user's Chrome via the `claude-in-chrome` skill. A
single throwaway club is created through the real `/create` funnel, used for NDS import and
billing checks, and deleted afterwards through the super-admin console. Findings are logged
as they occur into one report file; nothing is fixed during the run.

**Tech Stack:** Chrome (claude-in-chrome MCP), production SvelteKit landing + admin, Ktor
API, Stripe test mode, NDS fixtures from `admin/e2e/fixtures/`.

Spec: `docs/superpowers/specs/2026-07-31-prod-live-qa-design.md`.

## Global Constraints

- **Production writes are confined to one disposable club** ("QA Prod 0731"). No existing
  club, team, member, or event is modified. If a step would touch real data: stop and ask.
- **Abort immediately** if Stripe on production is not in test mode (`pk_test`), or if core
  routes return 5xx (broken deploy, not a feature defect).
- **No fixing during the run.** Defects are logged only; fixes become separate branches
  after triage.
- Throwaway identity: email `utke.michel+prodqa0731@gmail.com`, club name `QA Prod 0731`.
- Test cards: success `4242 4242 4242 4242`; decline `4000 0000 0000 0002`; 3DS
  `4000 0000 0000 3155`; second card for update-card `5555 5555 5555 4444`. Any future
  expiry, any CVC, any postal code.
- Fixtures (absolute paths on this machine):
  `/Users/miggi/miggisrc/teamorg/admin/e2e/fixtures/anwesenheitsliste.xlsx`,
  `/Users/miggi/miggisrc/teamorg/admin/e2e/fixtures/leiter.xlsx`,
  `/Users/miggi/miggisrc/teamorg/admin/e2e/fixtures/teilnehmende.csv`.
- Screenshots go to
  `/private/tmp/claude-501/-Users-miggi-miggisrc-teamorg/2f44a25e-f98d-4e9c-b88b-205ee7f3f6d1/scratchpad/prod-qa-0731/`
  named `PNN-<slug>.png`.
- Report file: `docs/testing/2026-07-31-prod-live-qa.md`, on branch
  `docs/prod-live-qa-spec` (already checked out, holds the spec commit).
- **Pre-registered expected findings** — record as "known", never as new defects: SMTP 535
  contact failure; Turnstile console noise; missing `www` → apex redirect; and the NDS v2
  deferred follow-ups in `docs/session-status.md` (same-date conflict collapse, no import
  lock, re-import ignores changed wizard times, multi-word last-name split).
- Every defect entry uses this shape:

```markdown
### F<N> — <one-line title>
- **Phase:** P<N>
- **URL:** <url>
- **Repro:** 1. … 2. … 3. …
- **Expected:** …
- **Actual:** …
- **Severity:** blocker | major | minor | cosmetic
- **Origin:** regression (#PR) | pre-existing | unknown
- **Evidence:** PNN-<slug>.png
```

---

### Task 0: Session setup and abort-gate

**Files:**
- Create: `docs/testing/2026-07-31-prod-live-qa.md`
- Create: scratchpad dir `prod-qa-0731/`

**Interfaces:**
- Produces: the report skeleton every later task appends to; the confirmed
  `pk_test` / prod-health precondition all later tasks depend on.

- [ ] **Step 1: Confirm production is reachable and unchanged**

```bash
git fetch origin -q
git log --oneline origin/production..origin/main --name-only | grep -v '^docs/' | grep -v '^$' || echo "PRODUCTION UP TO DATE (docs-only delta)"
for u in https://teamorg.ch https://app.teamorg.ch/start https://server.teamorg.ch/health; do printf "%s " "$u"; curl -s -o /dev/null -w "%{http_code}\n" -L "$u"; done
```

Expected: "PRODUCTION UP TO DATE", all three `200`. Any 5xx → abort, report the deploy is
broken.

- [ ] **Step 2: Verify Stripe is in test mode (ABORT GATE)**

Load the `claude-in-chrome` skill, open `https://app.teamorg.ch/start`, and check the page
source / network for the publishable key the backend serves.

```bash
curl -s https://app.teamorg.ch/start | grep -o 'pk_[a-z]*' | sort -u
```

Expected: `pk_test` only. If `pk_live` appears anywhere → **STOP the entire run** and tell
the user production is on live keys.

- [ ] **Step 3: Request Chrome site permissions**

Ask the user to grant the extension access to: `teamorg.ch`, `app.teamorg.ch`,
`js.stripe.com`, `hooks.stripe.com`, `challenges.cloudflare.com`. Wait for confirmation
before continuing — Stripe's Payment Element lives in a cross-origin iframe and silently
fails to render without it.

- [ ] **Step 4: Create the report skeleton**

Write `docs/testing/2026-07-31-prod-live-qa.md`:

```markdown
# Live Production Web QA — 2026-07-31

Plan: `docs/superpowers/plans/2026-07-31-prod-live-qa.md` ·
Spec: `docs/superpowers/specs/2026-07-31-prod-live-qa-design.md`

Run start: <timestamp> · Environment: production, Stripe test mode confirmed.
Throwaway identity: utke.michel+prodqa0731@gmail.com · Club "QA Prod 0731".

## Results

| Phase | Area | Result | Notes |
|---|---|---|---|
| P1 | Landing | — | |
| P2 | Contact form | — | |
| P3 | Funnel entry | — | |
| P4 | Create + card paths | — | |
| P5 | NDS Import v2 | — | |
| P6 | Billing page | — | |
| P7 | Typography / console | — | |

## Findings

_(none yet)_

## Known / pre-registered

- SMTP 535 on contact submit (landing-status.md open item)
- Cloudflare Turnstile console noise
- `www.teamorg.ch` → apex redirect missing
- NDS v2 deferred follow-ups (session-status.md)

## Cleanup

_(filled at the end)_
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-07-31-prod-live-qa.md docs/testing/2026-07-31-prod-live-qa.md
git commit -m "docs: prod live QA plan + report skeleton"
```

---

### Task 1: P1 — Landing site

**Files:**
- Modify: `docs/testing/2026-07-31-prod-live-qa.md` (P1 row + findings)

**Interfaces:**
- Consumes: Chrome permissions and report skeleton from Task 0.
- Produces: the confirmed CTA target URL used as the entry point in Task 3.

- [ ] **Step 1: Load landing in German and capture baseline**

Navigate to `https://teamorg.ch`. Screenshot `P01-landing-de.png`. Verify:
German copy by default; Graphite Cyan theme (teal accents on light, graphite sections);
two overlapping teal phone mockups in the hero; the roster "Tribüne" pattern on the contact
section and footer.

- [ ] **Step 2: Check pricing block**

Scroll to pricing. Verify the price reads **2 CHF/Mitglied/Jahr** and the footnote does
**not** exclude individuals (#68 copy change). Screenshot `P01-pricing.png`.

- [ ] **Step 3: Check both CTAs point at the funnel**

Read the `href` of the hero primary CTA and the pricing-card CTA. Both must be
`https://app.teamorg.ch/start`. Nav "Demo anfragen" must still scroll to the contact
section (support path, unchanged).

- [ ] **Step 4: Language toggle, including the hover trap**

Hover the EN toggle **without clicking**, then reload the page. Expected: still German
(the toggle uses `data-sveltekit-reload` + `preload-data="off"` so hover must not set the
`lang` cookie). Then click EN. Expected: English copy, `lang` cookie = `en`, persists
across a reload. Screenshot `P01-landing-en.png`. Switch back to DE.

- [ ] **Step 5: Legal pages**

Open `/impressum` and `/datenschutz` in both languages. Verify: operator Michel Utke
(linked to michelutke.com), hosting stated as Hetzner Nürnberg (DE/EU), fonts stated as
self-hosted, address "on request" and **not** printed, no UID/Handelsregister. Screenshot
`P01-impressum.png`.

- [ ] **Step 6: Fonts and console**

Open DevTools Network, filter `font`, hard-reload. Expected: fonts served from
`teamorg.ch/fonts/…`, **zero** requests to `fonts.googleapis.com` or `fonts.gstatic.com`.
Read the console: only Turnstile-iframe noise is acceptable; any first-party error is a
finding.

- [ ] **Step 7: Mobile viewport**

Resize to 390×844. Check hero, nav, pricing cards, and footer for overflow, clipped
mockups, or unreachable CTAs. Screenshot `P01-mobile-390.png`.

- [ ] **Step 8: www redirect (expected to fail)**

```bash
curl -s -o /dev/null -w "%{http_code} -> %{redirect_url}\n" https://www.teamorg.ch
```

Expected per the open item: no redirect to the apex. Record under "Known", not as a new
finding.

- [ ] **Step 9: Record P1 and commit**

Fill the P1 row (pass/fail/blocked) and append any findings using the standard shape.

```bash
git add docs/testing/2026-07-31-prod-live-qa.md
git commit -m "docs: prod QA P1 landing results"
```

---

### Task 2: P2 — Contact form (real submit)

**Files:**
- Modify: `docs/testing/2026-07-31-prod-live-qa.md`

**Interfaces:**
- Consumes: landing session from Task 1.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Fill and submit the form**

Contact section on `https://teamorg.ch`: name `QA Prod 0731`, email
`utke.michel+prodqa0731@gmail.com`, message
`Prod QA run 2026-07-31 — automated check, please ignore.` Solve/await Turnstile. Submit.
Screenshot `P02-contact-submitted.png`.

- [ ] **Step 2: Verify the UI never hangs**

Watch the button state. Requirements from the troubleshooting log: the button must leave
"Wird gesendet" within the client timeouts (Turnstile 8s, backend 15s); on success the form
clears and Turnstile resets; on failure a readable error appears and the form stays
resubmittable. A permanently spinning button is a **blocker** finding.

- [ ] **Step 3: Classify the outcome**

- Success message shown → ask the user to confirm the mail arrived at `info@teamorg.ch`.
  If it arrives, the landing-status SMTP 535 open item is **resolved** — note that
  explicitly in the report.
- Error message shown → expected known failure (SMTP 535). Record under "Known" with the
  exact user-visible text.

- [ ] **Step 4: Record P2 and commit**

```bash
git add docs/testing/2026-07-31-prod-live-qa.md
git commit -m "docs: prod QA P2 contact form results"
```

---

### Task 3: P3 — Funnel entry and join-code oracle

**Files:**
- Modify: `docs/testing/2026-07-31-prod-live-qa.md`

**Interfaces:**
- Consumes: CTA target confirmed in Task 1.
- Produces: an anonymous browser session on `app.teamorg.ch` for Task 4. Do **not** log in
  during this task.

- [ ] **Step 1: Anonymous root redirect**

In a fresh/incognito-equivalent state (no TeamOrg session cookie), open
`https://app.teamorg.ch/`. Expected: redirect to `/start`. Screenshot `P03-start.png`.

- [ ] **Step 2: Login page back-link**

Open `/login`. Expected: a "Neu hier? Jetzt starten" link pointing to `/start` (#68).

- [ ] **Step 3: Start chooser**

On `/start`, verify three paths render: Join (code), Create, Login.

- [ ] **Step 4: Join-code error parity (security check)**

Submit on `/join`:
1. a well-formed but unknown code — e.g. `ABCDEFGH`
2. a malformed code — e.g. `zz`

Both must produce the **same** user-visible error message. A different message per case is
a code oracle → **major** finding. Screenshot both: `P03-join-unknown.png`,
`P03-join-malformed.png`.

- [ ] **Step 5: Record P3 and commit**

```bash
git add docs/testing/2026-07-31-prod-live-qa.md
git commit -m "docs: prod QA P3 funnel entry results"
```

---

### Task 4: P4 — Create wizard and Stripe card paths

**Files:**
- Modify: `docs/testing/2026-07-31-prod-live-qa.md`

**Interfaces:**
- Consumes: anonymous session from Task 3.
- Produces: `clubId`, `teamId`, owner login credentials, and the Stripe customer id — all
  three are required by Tasks 5, 6, and 8. Record them in the report as soon as they exist.

- [ ] **Step 1: Account step**

`/create` → register with `utke.michel+prodqa0731@gmail.com` and a password you record in
the report (throwaway club only; note it in the cleanup section, not as a secret elsewhere).
If the flow demands email verification, complete it via the real inbox — ask the user for
the link if the mail is not visible to you.

- [ ] **Step 2: Details step**

Select kind = **Team** (the wrapper-club model auto-creates one team). Name `QA Prod 0731`,
pick a sport, a location, billing email = the same throwaway address. Verify the pricing
note reads CHF 2 per member per year. Screenshot `P04-details.png`.

- [ ] **Step 3: Payment Element renders**

Continue to `/create/billing`. Expected: Stripe Payment Element renders (the handoff uses
the httpOnly `to_onboarding` cookie — the client secret must **not** appear in the URL;
check the address bar and record a finding if it does). Screenshot `P04-payment-element.png`.

- [ ] **Step 4: Declined card**

Enter `4000 0000 0000 0002`, future expiry, any CVC. Submit. Expected: inline decline error,
the step stays on `/create/billing` and is retryable, the club stays `pending`, no crash.
Screenshot `P04-decline.png`.

- [ ] **Step 5: 3DS card**

Retry with `4000 0000 0000 3155`. Expected: a 3DS challenge iframe appears; complete it.
Screenshot `P04-3ds.png`. Record whether this alone completes setup — if it does, note that
Step 6 was reached in an already-active state.

- [ ] **Step 6: Successful card**

Retry with `4242 4242 4242 4242`. Expected: setup confirms, the club activates, and the
browser lands in the managed club (not back on `/start`). Screenshot `P04-success.png`.

- [ ] **Step 7: Capture identifiers**

From the URL after landing, record `clubId` and (from the team page) `teamId` into the
report. Ask the user to read the Stripe **test** dashboard for: the customer id created for
`utke.michel+prodqa0731@gmail.com`, that a **SetupIntent** exists, that a **Subscription**
exists (yearly, per-seat, Jan 1 anchor), and that **no charge/invoice was paid**. A charge
at signup is a **blocker** finding.

- [ ] **Step 8: Record P4 and commit**

```bash
git add docs/testing/2026-07-31-prod-live-qa.md
git commit -m "docs: prod QA P4 create wizard + card paths results"
```

---

### Task 5: P5 — NDS Import v2

**Files:**
- Modify: `docs/testing/2026-07-31-prod-live-qa.md`

**Interfaces:**
- Consumes: `clubId`, `teamId`, and the owner session from Task 4.
- Produces: imported events inside the throwaway team — deleted with the club in Task 8.

Entry points in the web app: team page `/app/teams/{teamId}` (`NdsImportDialog`) and the
club-scoped page `/manage/{clubId}/nds`. Use the team-page entry.

- [ ] **Step 1: Open the wizard and upload all three files**

Team page → NDS import. Upload `anwesenheitsliste.xlsx`, `leiter.xlsx`, and
`teilnehmende.csv` from the fixtures path in Global Constraints. Expected: parse succeeds,
the wizard advances to the mapping step. Screenshot `P05-upload.png`.

- [ ] **Step 2: Mapping step on an empty roster**

Expected: every parsed person defaults to **create** (the team has only the owner, so
`NdsMemberMatcher` should suggest nothing). Verify the mapping table lists the AWL roster
rows merged with person-file rows (the #81 fix), that map/create/skip are all selectable,
and that switching one row to "skip" persists when moving between steps. Screenshot
`P05-mapping.png`.

- [ ] **Step 3: Events step and wizard times**

Set explicit series times. Expected: **no `18:00` placeholder** anywhere (removed in v2);
the times you set appear in the summary. Screenshot `P05-events.png`.

- [ ] **Step 4: Import and verify the result**

Confirm the import. Then open the team's event list and verify: the expected number of
events exist, dates match the Anwesenheitsliste, times match what the wizard set, event type
is correct, and J-attendance is recorded on the imported events. Screenshot
`P05-imported-events.png`. Record actual counts in the report — not "looks right".

- [ ] **Step 5: Re-import the same file → conflict resolution**

Run the wizard again with the same `anwesenheitsliste.xlsx`. Expected: the conflict step
appears listing same-date+type collisions; the bulk series default is **keep-TeamOrg**.

- [ ] **Step 6: Per-date override and RSVP-loss warning**

Override exactly one date to **keep-NDS**. Expected: an RSVP-loss warning appears **with a
count**. Confirm the import. Then verify per date: the keep-TeamOrg dates kept the original
event (with J-attendance attached to it), and the overridden date's original event was
cancelled and replaced by the NDS event at the wizard time. Screenshot
`P05-conflicts.png`, `P05-conflict-result.png`.

- [ ] **Step 7: File-subset run**

Run the wizard once more with **only** `anwesenheitsliste.xlsx`. Expected: parse succeeds
(the "AWL empfohlen" subset path), the wizard completes without demanding the person files.
Screenshot `P05-subset.png`.

- [ ] **Step 8: Record P5 and commit**

Note any behavior matching a pre-registered deferred follow-up under "Known" rather than as
a new finding.

```bash
git add docs/testing/2026-07-31-prod-live-qa.md
git commit -m "docs: prod QA P5 NDS import v2 results"
```

---

### Task 6: P6 — Billing page, update-card, convert

**Files:**
- Modify: `docs/testing/2026-07-31-prod-live-qa.md`

**Interfaces:**
- Consumes: `clubId` and the owner session from Task 4.
- Produces: a club whose `kind` must end as **team** or **club** — record which, so Task 8
  deletes the right thing.

- [ ] **Step 1: Billing page contents**

Open `/manage/{clubId}/billing` (sidebar entry "Billing"). Verify: card brand + last4 match
the `4242` card, current and projected member counts render with the basis note, the status
chip shows active, and the mode is Stripe (not "managed manually"). Screenshot
`P06-billing.png`.

- [ ] **Step 2: Update card**

Use the inline update-card with `5555 5555 5555 4444`. Expected: a fresh Payment Element,
successful confirm, and the page then shows the **new** last4 (`4444`). Screenshot
`P06-update-card.png`. Ask the user to confirm in the Stripe test dashboard that the
subscription's default payment method changed and that **no second subscription** was
created.

- [ ] **Step 3: Convert team → club**

Press convert. Expected: success, the UI now presents the club layer (teams list visible).
Screenshot `P06-convert-to-club.png`.

- [ ] **Step 4: Convert club → team**

Press convert again. The club has exactly one active team, so this must **succeed**. If it
409s, that is a finding — record the exact error. Screenshot `P06-convert-to-team.png`.

- [ ] **Step 5: Ownership guard spot-check**

Confirm the billing page is reachable only by the owner: the sidebar entry and the route
must not be exposed to a non-owner. If no second account is available in this session,
record this as "not covered" rather than passing it.

- [ ] **Step 6: Record P6 and commit**

```bash
git add docs/testing/2026-07-31-prod-live-qa.md
git commit -m "docs: prod QA P6 billing + convert results"
```

---

### Task 7: P7 — App typography and console sweep

**Files:**
- Modify: `docs/testing/2026-07-31-prod-live-qa.md`

**Interfaces:**
- Consumes: the authenticated session from Tasks 4–6.

- [ ] **Step 1: Font source check**

On `app.teamorg.ch`, DevTools Network filtered to `font`, hard-reload. Expected: Roboto Flex
and Google Sans Flex served from the app's own origin; **zero** requests to
`fonts.googleapis.com` / `fonts.gstatic.com` (the CDN link was removed in #80). Screenshot
`P07-fonts-network.png`.

- [ ] **Step 2: Rendering check**

Confirm headings and body text render in the intended faces (not a system fallback) on the
team page and the billing page. A silent fallback is exactly the #80 bug — treat any
fallback as a **major** finding.

- [ ] **Step 3: Console sweep across visited pages**

Revisit `/start`, `/login`, the team page, the NDS wizard, and `/manage/{clubId}/billing`.
Record every console error and every failed network request (4xx/5xx) that is not an
expected 401/402/404 from the checks above.

- [ ] **Step 4: Record P7 and commit**

```bash
git add docs/testing/2026-07-31-prod-live-qa.md
git commit -m "docs: prod QA P7 typography + console results"
```

---

### Task 8: Cleanup and final report

**Files:**
- Modify: `docs/testing/2026-07-31-prod-live-qa.md`

**Interfaces:**
- Consumes: `clubId`, club name, Stripe customer id, account email from Tasks 4–6.

The web app has **no owner-facing club delete**. Deletion runs through the super-admin
console: `/admin/login` → Clubs → the club → Delete (a modal requires typing the club name
exactly). This needs the super-admin account `teamorg@michelutke.com`.

- [ ] **Step 1: Ask the user to authorize deletion**

Present exactly what will be deleted: club `QA Prod 0731` (`clubId`), its auto-created team
(`teamId`), the NDS Angebot and all events imported in Task 5, and the throwaway member
rows. Wait for explicit confirmation. Ask the user to log into `/admin/login` themselves, or
to provide the super-admin credentials for this session.

- [ ] **Step 2: Delete the club**

`/admin/clubs/{clubId}` → Delete → type `QA Prod 0731` to enable the button → confirm.
Verify the club afterwards shows status `deleted` / disappears from the clubs list.
Screenshot `P08-deleted.png`. If deletion fails or is declined, record precisely what
remains instead.

- [ ] **Step 3: Hand the Stripe cleanup to the user**

List for the Stripe **test** dashboard: the customer id, its subscription, both saved cards,
and the SetupIntents. The user deletes them — Claude does not touch the Stripe dashboard.

- [ ] **Step 4: Append prior leftovers to the cleanup list**

Add, untouched, from `docs/session-status.md`: account `utke.michel+ccdebug@gmail.com`,
pending club "Claude Debug Club DELETE ME", and its test-mode Stripe customer.

- [ ] **Step 5: Finalize the report**

Complete the results table (every phase pass/fail/blocked), order findings by severity
(blocker → cosmetic), and add a short summary: what shipped works, what is broken, what
went untested and why (mobile, frozen/past-due states).

- [ ] **Step 6: Commit and open the PR (queued, not merged)**

```bash
git add docs/testing/2026-07-31-prod-live-qa.md
git commit -m "docs: prod live QA results 2026-07-31"
git push -u origin docs/prod-live-qa-spec
gh pr create --base main --title "docs: live production web QA (2026-07-31)" --body "Spec, plan, and results of the live production web QA pass: landing, self-serve funnel, NDS import v2, billing. Findings triaged by severity; fixes land in separate branches."
```

Leave the PR **open** — the bulk-merge policy merges on the user's call.

---

## Self-Review

**Spec coverage:** P1–P7 and cleanup each map to Tasks 1–8; preconditions and the abort gate
map to Task 0; the evidence/findings rules live in Global Constraints and are applied in
every task's record step. Frozen/past-due is out of scope in the spec and correspondingly
absent here; the omission is stated in Task 8 Step 5.

**Placeholders:** none — every step names the exact URL, input value, or command, and the
report format is given verbatim.

**Consistency:** `clubId` / `teamId` / Stripe customer id are produced in Task 4 and consumed
under the same names in Tasks 5, 6, and 8; the report path, branch name, screenshot
directory, and test-card numbers are fixed once in Global Constraints and referenced, not
restated with variations.
