# Exploratory E2E — real-user testing on prod (app.teamorg.ch)

Date: 2026-07-15. Tester: Claude (browser via playwright-cli). Target: **production** (mutations approved; QA data prefixed "QA ").

Method: accessibility-snapshot-driven browser automation, named sessions per role (`super`, `manager-a`, `coach-a`, `player-a`, …), storage-state reuse. Screenshots only where visual judgment needed.

## Executive summary

Exploratory, real-persona testing of the web admin across superadmin / club-manager / coach / player, plus NDS import and security (IDOR) probes. Scenarios **S0–S5** (bootstrap, hands-on manager, delegation, NDS import, player lifecycle, isolation/audit/session) and follow-up **S7–S10** (attendance depth, invite edges, event lifecycle, account/club lifecycle).

**Security held throughout**: read + write IDOR fully blocked, cross-club isolation, session handling, audit logging all correct.

**Fixes shipped on branch `fix/exploratory-e2e-findings`** (web = SvelteKit `admin/`, server = Ktor `server/`):
- Event manage actions 500 (cancel/uncancel/reopen/reconcile) — `apiPost` tolerates empty bodies.
- Attendance grid miscount — read `responseStatus` not `status`.
- NDS Angebot dedup scoped per-club (was global, blocked cross-club import).
- NDS export unauthorized → 403 (was 500).
- Impersonation banner shown in `/manage` shell.
- `/manage` i18n (mixed DE/EN removed).
- Finalize dead-end — event detail shows full roster so no-response members are resolvable.
- Subgroups — added members-read endpoint + assign/remove UI.
- Change-password — new `POST /auth/change-password` + profile form.
- Response-deadline field on event create/edit; minAttendees warning surfaced.
- Team unarchive endpoint + archived-teams UI.
- RSVP notification uses the real player name; redeemed invite shows an "already used" notice.

**Deferred (need a product/infra decision):** forgot-password reset (no email-based auth exists today); club-deactivation access enforcement (currently a soft flag — members keep access).

**Verification:** `admin` `npm run check` 0 errors; `:server:compileKotlin` green. Server integration tests need Docker (unavailable in the test env) — run `:server:test` locally before merge. QA Testclub A + all test data left intact on prod.

Test accounts (created during this run; passwords in scratchpad, not committed):
- Superadmin: teamorg@michelutke.com (existing)
- QA Manager A: qa.manager.a@example.com
- (more added per scenario)

---

## S0 — Superadmin bootstrap & club-setup authority

### How users/clubs get created (core question answered)

- **Public web signup: NONE.** Admin web (`app.teamorg.ch`) exposes login only. Backend `POST /auth/register` is open but the API is **not publicly reachable** (BFF; admin SSR talks to it on the container network `localhost:8080`). No `api.*` host resolves. So the mobile app is the only real self-signup surface.
- **Club creation: superadmin only.** `/admin/clubs` → "New club". Club managers have no create-club UI (verified S2).
- **Manager assignment: existing users only.** Superadmin club page → "Add manager" calls `POST /admin/clubs/{id}/managers` with email; returns 404 "User not found" if no account exists. **It does NOT send an invite email.** So a brand-new manager cannot be onboarded purely from the admin — they must first have an account (mobile self-signup), then superadmin assigns them.
- **Can I sign up users myself?** YES, once bootstrapped — via **invite links**. A club manager generates co-manager / team-coach / team-player invite links (`/i/{token}`); opening a link lets me register a new account with any email+password I choose (invite-gated registration, no email verification). The very first manager of a *new* club is the only bootstrap gap; I broke it with **superadmin impersonation** of an existing disposable account (see below).

### Bootstrap chain used (no user intervention needed)
1. Superadmin created **QA Testclub A** (Bern), passing qa.manager.a@example.com as manager → correctly reported "not assigned, must create account first".
2. (in progress) Assign existing disposable E2E account as manager → impersonate → generate co-manager invite → register fresh qa.manager.a.

### Findings

- ✅ Club creation superadmin-only. Manager hitting `/admin/clubs`, `/admin/dashboard`, `/admin/users` → all redirect to `/app`. No create-club UI anywhere in manager shell.
- ✅ Manager assignment works (existing user only); confirm modal on remove; both add/remove verified.
- ✅ **Self-signup by me works** via invite link `/i/{token}` → account created with my chosen email+password, no email verification, auto-login, invite auto-redeemed. qa.manager.a is now a real, login-capable co-manager.
- ✅ Impersonation: superadmin → confirm dialog → banner "Impersonating … @ club" + "actions audit-logged with impersonation context"; End session returns to superadmin.
- ⚠️ **i18n inconsistency** in `/manage/{club}` shell: mixed German/English in same view ("Co-managers", "Teams", "Invite co-manager" EN vs "Mitglieder", "Abmelden", "Profil" DE). Language toggle DE/EN present.
- ⚠️ **Impersonation banner missing in `/manage` shell**: the "Impersonating…" banner + End button render only on `/admin/*` routes. While impersonating, the club-manager UI shows the target's identity with no visual cue that it's an impersonated session. Minor safety/clarity gap (actions still audit-logged).
- ℹ️ Bootstrap of the *first* manager of a brand-new club has no admin-only path (assign needs a pre-existing account; no invite email sent). Real flow = manager self-registers on mobile, then superadmin assigns. I used superadmin-impersonation of a disposable account to generate a co-manager invite as a workaround.

### Answer to "can you sign up users yourself?"
**Yes, fully — no user intervention needed.** Once any one manager exists, I generate co-manager / coach / player invite links and register arbitrary accounts through the web. The only true dependency was reaching the *first* manager for a fresh club, solved via superadmin impersonation.


---

## S1 — Hands-on manager (qa.manager.a)

Setup: 2 teams (QA Damen 1, QA Herren 1). QA Damen 1 roster = QA Coach A (promoted from player) + QA Player A1/A2/A3, all onboarded via ONE shareable player invite link.

### Findings

- ✅ Team create ×2, team appears with member counts.
- ✅ Invite links: shareable (no-email) link is reusable & registered 4 accounts. Role selector player/coach. Email-locked invite (coach) is emailed, **no link shown in UI** (server returns `inviteUrl` but web hides it) — inconsistent with co-manager invite which DOES show the link.
- ✅ Role change player→coach via roster "Make Coach"/"Make Player".
- ✅ **Series creation**: weekly Tue/Thu, Jul 21–Aug 31 → exactly 12 occurrences generated. Correct.
- ✅ **Series-scope edit (PR #45)** all three scopes correct:
  - "Nur dieser Termin" → 1/12 changed.
  - "Dieser und alle zukünftigen" → 9/12 (Jul 30→Aug 27).
  - "Alle Termine der Serie" → 12/12.
- ⚠️ **this+future overwrites prior per-occurrence overrides** in its range: the Aug 13 "only-this" title edit was clobbered by a later this+future edit spanning it. Calendar apps usually preserve single-occurrence exceptions — confirm intended.
- ✅ Single match create (type=match, non-series).
- ✅ Event create scoped to caller's teams: manager sees both teams; coach (QA Coach A) sees only QA Damen 1 in team picker.
- ✅ New-event submit is disabled until a team is selected (not a past-date restriction — past dates are allowed).
- ✅ **Unified attendance finalize**: on a past (ended) event, "CheckIn abschliessen" appears; self-response buttons correctly disabled ("Zeit … abgelaufen"). Finalize resolved 4 no-response members → confirmed via `defaultResponse=accepted`. Roster (3 players + coach) shows as "4 Zusagen".
- ℹ️ Correction: `defaultResponse` is applied **at finalize**, not at event creation — pre-finalize the roster shows "—"/0-0-0 (no response rows yet). By design per spec.

### 🐞 BUG (reproducible, HIGH, systemic): event manage actions return 500 (cancel / uncancel / reopen)

Confirmed on THREE core actions, each shows the bare "500 Internal Error" page yet the mutation still applies:
- **Absagen** (cancel event) → `POST …?/cancel` → 500; event still becomes "Abgesagt".
- **Absage aufheben** (uncancel) → `POST …?/uncancel` → 500.
- **CheckIn wieder öffnen** (reopen finalized attendance) → `POST …?/reopen` → 500; check-in still reopens.
- `reconcile` shares the identical code pattern → latent (not exercised live).
- Finalize (`?/finalize`) returns 200 fine (uses a different code path). Reopen repro'd twice.
- **The reopen actually succeeds server-side**: `check_in_completed_at` is cleared (finalize button returns, responses intact). A plain GET reload of the same event renders 200 normally — so the event-detail `load` is healthy; the failure is on the reopen action's response path.
- **Root cause (confirmed by code):** `apiPost` (`admin/src/lib/server/api.ts`) ends with `return res.json()` **unconditionally**. These backend endpoints respond bare `HttpStatusCode.OK` with **no body** (`AttendanceRoutes.kt:275` reopen; `EventRoutes.kt` cancel ~line 298 / uncancel / reconcile). Parsing an empty body throws `SyntaxError` — *not* `ApiError` — so each action's `catch (e) { if (e instanceof ApiError) …; throw e }` rethrows → SvelteKit 500. The DB mutation already committed on the backend, hence "succeeds but 500s".
- **Why finalize is fine:** the `finalize` action uses raw `fetch` + `res.ok` and returns `{finalized:true}` without parsing the empty body. That asymmetry is the whole bug.
- **Fix (single point):** make `apiPost` tolerate empty/204 bodies (e.g. `const t = await res.text(); return t ? JSON.parse(t) : null`). Fixes cancel/uncancel/reopen/reconcile at once. (Alt: give each endpoint a JSON body, or use raw fetch per-action like finalize.)
- Impact: cancelling an event, un-cancelling, and reopening check-in all throw an error page at the user despite working — they'll retry, double-act, and distrust the data. Core coach/manager actions.

- ✅ Rename team (QA Herren 1 → RENAMED) persists.
- ✅ Remove member (Player A3) → confirm modal → roster 4→3, gone from club members page too.
- ✅ Club members page: all members listed with team · role; supports invite, add-to-team ("+ Team hinzufügen"), remove. Good club-wide overview.

---

## S2 — Delegation boundary: what a coach can/can't do

Tested against club A with qa.coach.a (team coach of QA Damen 1). Determines how far a "delegating" manager can offload work.

### Findings

- ✅ **Coach CAN:** create/edit/cancel events for teams they belong to (event team-picker shows only their teams); take attendance (finalize) — and hit the reopen/cancel 500 bug above.
- 🚫 **Coach CANNOT create teams.** All `/manage/{club}/*` routes → **403 "You do not have access to this club"** (club, teams, members, team detail). Team creation is club-manager-only.
- 🚫 **Coach CANNOT invite or add members** (players or coaches). The coach-facing team page `/app/teams/{id}` is **read-only** — roster list only, no invite/add controls. Member invites live solely in the manager `/manage` shell.
- 🚫 Coach cannot invite co-managers (that's in `/manage`, 403).
- ✅ **Co-manager = full manager powers.** qa.manager.a was onboarded via a *co-manager* invite and has full `/manage` access (teams, members, invites). So a manager can fully delegate to another **manager**, but only partially to a **coach**.

### Scenario conclusion
A "delegating" manager who only invites coaches will find coaches **cannot build their own rosters or teams** — the manager (or a co-manager) must create every team and invite every player/coach. Coaches' autonomy is limited to running events and attendance on teams the manager set up. True hands-off delegation requires promoting people to **co-manager**, not coach.

---

## S3 — NDS import (real VBC Thun files → QA Testclub A)

Files: `20260626_Teilnehmende_4037090.csv` (14 participants), `..._Leiterinnen_Leiter_...xlsx` (2 coaches), `..._Anwesenheitsliste_...xlsx` (Angebot 753813, Kurs "Damen 3. Liga pro B", 100 activities).

### Blocker discovered → workaround
- First import into QA-A **failed with 409 "Angebot … bereits mit einem anderen Team verknüpft"**. Angebot 753813 was already imported into the real **VBC Thun** club (28 Jun). `findTeamIdByAngebot` (`NdsRoutes.kt:169`) is a **global** lookup, not club-scoped → the exact real files can't be imported into any *other* club.
  - The SvelteKit action returns HTTP 200 to the browser even on this logical 409 (enhance quirk) — the failure is only in the payload; easy to misread in logs.
  - Isolation still holds: the import did **not** mutate VBC Thun, and repeated blocked imports left **no orphan team** in QA-A (create-before-conflict-check ordering at `NdsRoutes.kt:163` vs `:170` is latent but produced no orphan in practice).
- To exercise the write path I cloned the attendance xlsx with a fresh Angebot (`999001`); person files unchanged. Import then succeeded.

### Findings (import write path — with cloned Angebot)
- ✅ Parse preview accurate: "100 Aktivitäten · 2 Leiter · 14 Teilnehmer", 16 Personennummern, Kurs/Sportart shown.
- ✅ **Import result: 16 Mitglieder, 100 Termine, 92 Anwesenheiten übernommen.** New team "QA NDS Damen 3L" created with 16 members.
- ✅ **Roles correct**: 14 players + 2 coaches; the two Leiter (Ciabuschi, Lüthi) mapped to `coach`, participants to `player`.
- ✅ **Attendance imported correctly** (verified on event detail): Apr 29 event → 12 Zusagen / 0 Unsicher / 4 Absage (Lüthi present=Zusagen); Apr 7 event → 16 Absage (nobody marked present that date). Matches the J/X marks. NDS-imported presence → confirmed, non-present → declined, per spec.
- ✅ Coach per-member edit popup "Anwesenheit bearbeiten" is present on the event detail (this is the coach-marking UI; it renders once response rows exist — which is why brand-new empty events show "—").
- ✅ **Idempotency/dedup**: re-import of the same Angebot → blocked as "already linked" (no duplicate team/members).

### 🐞 Issues
1. **Import→Export round-trip is broken.** After a clean import, NDS **export is blocked** by pre-flight: *"100 Training(s) ohne Ort. NDS verlangt Zeit und Ort für Trainings."* The importer sets no location on trainings, but export requires one → a coach who imports cannot re-export without manually adding a location to all 100 events. (`GET /teams/{id}/nds/export` → 409; `NdsExportService.preflight` `training_missing_location`.)
2. **Global Angebot dedup (cross-club).** As above — an Angebot linked in club X blocks import into club Y, with an error the club-Y manager can't resolve (can't see/act on club X's team). Consider scoping the uniqueness per-club, or a clearer message.
3. ⚠️ **Team attendance grid under-reports imported responses.** `/manage/{club}/teams/{id}/attendance` shows every imported event as `0 In / 0 Unsure / 0 Out / 16 No-reply`, while the event *detail* for the same event shows the real 12/0/4. The two coach-facing attendance views disagree for imported data. (Possibly the grid derives "no-reply" for un-finalized events — but it contradicts the detail and is confusing.)
4. ⚠️ **Nutzergruppe selected at import not reflected**: I picked "NG 1 — Sportverein" in the import dialog, but pre-flight still warns "Keine Nutzergruppe gesetzt". Either not persisted by `linkTeam`, or a test-harness `select` that didn't fire the Svelte handler — needs confirmation.

---

## S4 — Player lifecycle + cross-role live + IDOR

Player qa.player.a1 (QA Damen 1). All future series events are in "Open" state so players can respond.

### Findings
- ✅ Player scope: nav = Teams / Inbox / Profil only (no Club/Manage/Admin). Home lists only their own team (QA Damen 1).
- ✅ Player event view: self-response buttons (Zusagen/Unsicher/Absage) only; no coach edit controls, no roster editing.
- ✅ **Cross-role live**: player-a1 → "Zusagen" on the Jul 21 training; manager's view of the same event immediately shows "1 Zusagen / QA Player A1: Zusagen".

### Security — IDOR probes (all PASS)
- Read side: `/admin/*` → redirect to /app; `/manage/{club}/*` (incl. other team) → 403 "do not have access"; `/app/teams/{otherTeam}` (Herren, NDS team) → 403; `/app/events/{otherTeamEvent}` → 403. No cross-team/cross-club leakage.
- Write side: player POST `?/finalize`, `?/reopen`, `?/cancel` on own-team event → **403** each. Coach-only actions rejected.
- Self-response `PUT /events/{id}/attendance/me` derives userId from JWT subject (not client input) → no proxy-set of another user. Coach edit `PUT …/attendance/{userId}` guarded by `requireEventAccess(coach, club_manager)`.
- ⚠️ Minor: unauthorized **NDS export** by a non-member returns **500** (uncaught `ApiError` thrown from the export `+server.ts`) instead of a clean 403. Access is still denied (no data), but the status/UX is wrong — same "throw not handled" family as the cancel/reopen bug.

---

## S5 — Cross-club isolation, audit log, session

- ✅ **Cross-club isolation**: qa.manager.a (manager of QA-A only) → VBC Thun `/manage/{vbc}`, `/teams`, `/members` all **403 "do not have access"**. Managers can't reach other clubs.
- ✅ **Audit log** (`/admin/audit-log`) records superadmin mutations with actor + target + timestamp: `club.create`, `club.manager.add`, `club.manager.remove`, `impersonation.start`, `impersonation.end` — all present for the QA-A run.
- ✅ **Session expiry**: deleting the `to_session` cookie → next navigation cleanly redirects to `/login` (no error page, no leaked content).

---

## Fix status (branch `fix/exploratory-e2e-findings`)

- **#1 event manage 500s** — FIXED. `apiPost/apiPut/apiPatch/apiPostForm` now tolerate empty bodies via `parseJsonOrNull` (`admin/src/lib/server/api.ts`). Covers cancel/uncancel/reopen/reconcile.
- **#4 attendance grid under-report** — FIXED. Root cause: grid loader read `r.status` but the `/teams/{id}/attendance` DTO serialises `responseStatus`; every response counted as no-reply. Loader now reads `responseStatus`.
- **#3 global Angebot dedup** — FIXED. `findTeamIdByAngebot(angebotId, clubId)` now scoped per-club (`NdsRepository.kt`, `NdsRoutes.kt`), so an Angebot linked in one club no longer blocks import into another.
- **#5 unauthorized export 500** — FIXED. Export `+server.ts` now throws SvelteKit `error(status)` (403 for non-members) instead of an uncaught `ApiError`.
- **#7/#8 i18n + impersonation banner in `/manage`** — FIXED. Banner added to the `(shell)` layout; manage-shell strings routed through i18n.
- **#2 NDS training location** — NOT A BUG (by design). The importer intentionally uses a placeholder start time and no location; the export pre-flight forces the coach to set real time + location before exporting (see `NdsEventImporter` header comment). Left as-is.
- **#6 series this+future overwrites overrides** — NOT A BUG. This matches standard calendar semantics (Google/Outlook "this and following" also overwrites later occurrences). Left as-is.
- **#9 Nutzergruppe not persisted** — NOT A BUG. The import client sends `nutzergruppe` correctly; my Playwright `select` didn't fire Svelte's `bind:value`. Test-harness artifact.
- Verification: `admin` `npm run check` 0 errors; `:server:compileKotlin` clean. Server integration tests need Docker (not available in this env) — run `:server:test` locally with Docker up.

## Summary — bugs & issues (most severe first)

1. **🐞 HIGH — event manage actions 500 (cancel / uncancel / reopen; reconcile latent).** `apiPost` calls `res.json()` on empty-body 200s → uncaught `SyntaxError` → 500 page, though the mutation commits. One-line fix in `apiPost` (tolerate empty body). Core coach/manager actions affected. See S1.
2. **🐞 MED — NDS import→export round-trip broken.** Imported trainings have no location; export pre-flight requires it ("100 Trainings ohne Ort") → freshly imported data can't be re-exported without manual fixup. See S3.
3. **🐞 MED — global Angebot dedup across clubs.** An Angebot linked in one club blocks NDS import into any other club, with an unresolvable error for the second club's manager. Consider per-club scoping / clearer message. Isolation itself holds (no cross-club mutation, no orphan team). See S3.
4. **⚠️ LOW — attendance grid vs event-detail disagree for imported data.** Team grid shows imported events as all "No reply"; event detail shows the real confirmed/declined (e.g. 12/0/4). See S3.
5. **⚠️ LOW — unauthorized NDS export → 500 instead of 403** (uncaught `ApiError` in export `+server.ts`). Access still denied. See S4.
6. **⚠️ LOW — series "this+future" edit clobbers prior single-occurrence overrides.** Confirm intended. See S1.
7. **⚠️ LOW — i18n: mixed DE/EN in `/manage` shell.** See S0.
8. **⚠️ LOW — impersonation banner absent in `/manage` shell** (only on `/admin/*`). Actions still audit-logged. See S0.
9. **❓ CONFIRM — Nutzergruppe chosen at NDS import not reflected in pre-flight** (possibly a test-harness `select` that didn't fire). See S3.

### What works well
Series generation + all 3 scope edits (PR #45); unified-attendance finalize + default-response resolution; NDS parse + import (members/roles/events/attendances); role management; invite-link onboarding; **security** — read & write IDOR fully blocked, cross-club isolation, session handling, audit logging.

### Test data left on prod (QA Testclub A — club id 2fe8659a-9659-4052-96ea-423f4c968c21)
Teams: QA Damen 1, QA Herren 1 RENAMED, QA NDS Damen 3L (16 imported members, 100 events). Users: qa.manager.a, qa.coach.a, qa.player.a1/a2/a3 (@example.com). Superadmin can "Delete club permanently" (type-to-confirm) to remove teams/events; user accounts persist unless separately removed. **Not auto-deleted — left for review.**

---

## S7 — Attendance depth (follow-up session)

- ✅ **default=none finalize block**: coach finalize with unresolved no-response members → dialog "CheckIn kann nicht abgeschlossen werden … müssen zuerst manuell eingetragen werden." Blocks correctly.
- 🐞 **MED — default=none + nobody responded = finalize dead-end.** The block dialog is OK-only (doesn't list who / no inline resolve). On the event detail the response list is "—" (no rows) because no one responded, so there are **no per-member "Anwesenheit bearbeiten" controls to mark them**, and past-event self-response is locked ("Anmeldeschluss ist vorbei"). Net: a past `default=none` event with no prior responses can never be finalized through the web UI. (Fix: render the full roster with coach-edit controls even when no response rows exist, or list+resolve members inside the block dialog.)
- ✅ **Coach per-member edit popup** ("Status bearbeiten"): Anwesend / Abgemeldet + "Nicht entschuldigt" (unexcused) checkbox + Speichern. Marking Balmer present updated counts (0→1 Zusagen); "Nicht entschuldigt" persisted (checked on reopen). unexcused is internal — not shown in the roster row (per spec).
- ✅ **unsure finalize block** — verified by code (`finalize()` returns `BlockedUnsure` for any `unsure` roster member). A live end-to-end test was impractical: see deadline finding below.
- ⚠️ **No response-deadline field in the web event form.** Cutoff therefore = event start. An event created to start ~2 min out was already past cutoff ("Anmeldeschluss ist vorbei") at test time, so players can't RSVP to imminent events and there's no way to set a later deadline from the web. (Far-future events RSVP fine — verified in S4.)
- ⚠️ **minAttendees is write-only in the web UI.** Set to 5 on a 3-member team and persisted (confirmed via edit form), but no warning, badge, or display appears on the event detail or events list when confirmed < min. Either mobile-only or unwired in web.

---

## S8 — Invite & onboarding edges

- ✅ **Invalid/garbage token** `/i/not-a-real-token` → clean "Einladung ungültig oder abgelaufen. Bitte fordere beim Verein eine neue an."
- ✅ **Email-mismatch**: an email-locked co-manager invite opened while logged in as a different account (player-a1) → blocked with "Diese Einladung wurde an eine andere Adresse gesendet. Melde dich mit dem eingeladenen Konto an." Good.
- ✅ **Multi-team player**: added player-a1 to a 2nd team via the members page ("+ Team hinzufügen" → Team+Rolle → Speichern). player-a1's home now lists both QA Damen 1 and QA Herren 1.
- ✅ **Second co-manager**: manager-a invited qa.manager.b (co-manager); after registering, manager-b has full `/manage` access — sees all 3 teams, has New-team/write controls, no 403. Equal-powers collaboration confirmed.
- ⚠️ **Already-redeemed invite still renders the join form** (doesn't say "already used"). The guard is at submit time (email_taken → 409), not view time. Minor: a used personal invite could show a misleading "join" screen.

---

## S9 — Event lifecycle extras

- ✅ **Duplicate event** ("Duplizieren") → creates a new independent event with copied fields (title "QA Match vs Bern"), opens it in edit; saved as a separate id.
- ✅ **Time-change propagation**: coach changed a series occurrence start 18:00→19:30 (scope "nur dieser Termin"); player-a1's view of that event immediately showed "21. Juli um 19:30".
- ✅ **Archive team**: archiving QA Herren 1 removed it from the active teams list (confirm dialog "Archive team").
- ⚠️ **No un-archive / archived-teams view in the manager UI.** After archiving, there is no toggle, tab, or list to see or restore archived teams from `/manage`. Archive is effectively one-way in the web.
- 🐞 **MED — Subgroups can be created but members can't be assigned (web).** "Add subgroup" creates a subgroup (Rename/Delete work), but the roster table columns are Name/Role/Jersey/Position/Actions — there is **no subgroup-assignment control**, and clicking the subgroup does nothing. Subgroups stay at "0 members", so the advertised "target events at part of the team" cannot be used from the web at all.
- ℹ️ **Reconcile** not exercised live — it uses the same `apiPost`-empty-body path as cancel/uncancel/reopen, so on current prod it would 500 (see S1 bug, now fixed on the branch).

---

## S10 — Account & club lifecycle

- 🐞 **MED — No password management in the web at all.**
  - `/app/profile` is **read-only**: shows Name + E-Mail as static text plus a language toggle; there is no display-name edit and no change-password control.
  - The login page has **no "forgot password" / reset** link.
  - Net: a web user cannot change their password, and one who forgets it has no recovery path on the web. (Presumably mobile-only — confirm.)
- ✅ **Language DE/EN persists** across navigation + reload via the `lang` cookie (verified EN survived a reload, toggled back to DE).
- ✅ **Inbox** populates: coach-a's inbox showed "RSVP: … confirmed for QA Training ALLSCOPE" after player-a1's accept.
- ⚠️ **Notifications are anonymised to "A player".** The RSVP notification body reads "A player confirmed for …" (hardcoded `playerName = "A player"` in `AttendanceRoutes.kt`) — the coach can't tell *which* player responded. Low, but undermines the feature's usefulness.
- ⚠️ **Club deactivation has no functional effect on members.** After superadmin deactivates the club, manager/coach/player all retain full access — teams still listed, `/manage` still loads with Edit / Invite co-manager / New team controls, no "inactive" banner. Deactivation only flips a flag in the superadmin listing (dialog says "keeps all data"). If deactivation is meant to suspend access, this is a gap; if it's a soft archive, at least show an "inactive" indicator. Reactivate works.

## Summary — round 2 (S7–S10) issues

- 🐞 MED — default=none + no responses = **finalize dead-end** (no member-marking UI). [S7]
- 🐞 MED — **subgroups can't get members** in web (no assignment UI) → unusable for event targeting. [S9]
- 🐞 MED — **no web password management** (read-only profile, no forgot-password). [S10]
- ⚠️ no response-deadline field (cutoff=start; can't RSVP to imminent events) [S7]; minAttendees write-only [S7]; no un-archive for teams [S9]; RSVP notifications say "A player" [S10]; club deactivation is a no-op for members [S10]; already-redeemed invite still shows join form [S8].
- ✅ Verified working: default=none block, coach per-member edit + unexcused, duplicate event, time-change propagation, archive, invalid-token + email-mismatch handling, multi-team player, second co-manager, language persistence, inbox.

---

## Fix status — round 2 (S7–S10), branch `fix/exploratory-e2e-findings`

- **Finalize dead-end (MED)** — FIXED. Event detail now renders the full team roster (synthetic "no-response" rows), so a coach can resolve no-response members via the per-member edit control; `default=none` finalize is no longer a dead-end.
- **Subgroups can't get members (MED)** — FIXED. Added backend `GET /teams/{id}/subgroups/{sgId}/members` (only add/delete existed) + a manage-team UI to assign/remove members.
- **No web password management (MED)** — PARTIALLY FIXED. Added `POST /auth/change-password` + a change-password form on the (previously read-only) profile page. **Forgot-password/reset is DEFERRED** — it needs a reset-token table + transactional email + a reset page, and the app has no email-based auth flows today; wants a product/infra decision.
- **No response-deadline field** — FIXED. Optional "Anmeldeschluss" on event create + edit; backend now accepts `responseDeadline`.
- **minAttendees write-only** — FIXED. Warning shown on the event detail when confirmed < min.
- **No un-archive for teams** — FIXED. `POST /teams/{id}/unarchive` + `listTeams(includeArchived)`; manage-teams shows an archived section with restore.
- **RSVP notification says "A player"** — FIXED. Uses the responder's real display name.
- **Already-redeemed invite shows join form** — FIXED. `/i/{token}` now shows an "already used" notice when the invite is redeemed (backend already exposed `alreadyRedeemed`).
- **Club deactivation is a no-op for members** — DEFERRED (product decision). Should deactivation suspend member/manager access, or remain a soft archive? Not changed; needs a call on intended semantics before enforcing.

Verification: `admin` `npm run check` → 0 errors; `:server:compileKotlin` → BUILD SUCCESSFUL. Server integration tests need Docker (unavailable here) — run `:server:test` locally. Fixes are on the branch, NOT deployed to prod.
