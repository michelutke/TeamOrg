# NDS Import Follow-ups — Design

**Date:** 2026-06-30
**Branch:** `feat/nds-import-followups`
**Scope:** server (Ktor) + web admin (SvelteKit `admin/`) + mobile (`composeApp` KMP, shared via `shared/`)

Follow-up work after the NDS (J+S Nationale Datenbank Sport) import/export feature
(PR #39). Five issues raised during real-world use. This spec is the agreed design;
implementation is sequenced in one branch with separate commits per logical unit.

---

## Background / investigation findings

Investigated against the real export `20260626_Anwesenheitsliste_4037090.xlsx`
(Angebot 753813, "Damen 3. Liga pro B", 16 members, 100 activity columns).

- **Attendance import is NOT broken.** Running the full web import path
  (parse → import) on the real file writes **92 present records** and links all
  16 members to provisional users. Parser, importer, serialization round-trip,
  and SvelteKit proxy all preserve `attendedDates`. The user's "empty" result was
  most likely an import run on an earlier build (attendance import landed in the
  same NDS branch). Conclusion: #2 is an **observability** gap, not a logic bug.
- **"Nutzergruppe"** is a J+S concept (NG 1/2/4/5), not TeamOrg-specific. It is the
  J+S offer category; TeamOrg stores it per team (`teams.nds_nutzergruppe`) and uses
  it only to validate/snap activity duration (DAUER) at export
  (`NdsExportService.preflight`, `NdsRules.allowedDurations`).
- **Per-member email invite already exists** (team detail → NDS section → "Einladen"
  → `POST /teams/{teamId}/nds/members/{id}/invite`). Redeem binds the invite to the
  roster row (`invite_links.nds_member_id`) and `claimMember()` migrates attendance
  + role from the provisional user to the real account. Covered by the test
  `member invite redeem claims the roster member and moves attendance`.
- **Events page scroll**: the `(shell)` layout scrolls the **window** (no inner
  overflow container). SvelteKit native scroll restoration should apply; it does not,
  most likely because `load` re-runs on back-nav and content reflows after restore.
- **Mobile** (`composeApp`) has event list/detail, attendance/check-in, RSVP, and
  invite-redeem (deep link `/i/{token}`) screens. Mobile users see events/attendance
  and redeem invites today.

---

## #1 Nutzergruppe help text (web only)

**Goal:** explain the NG dropdown in the import dialog.

- Add a "Was ist das?" tooltip/popover next to the Nutzergruppe `<select>` in
  `admin/src/lib/components/NdsImportDialog.svelte`.
- Copy (DE): NG = J+S-Nutzergruppe deines Angebots; bestimmt die erlaubten
  Trainingsdauern bei der J+S-Meldung. Beim Export wird die Dauer dagegen geprüft
  und ggf. auf den nächsten erlaubten Wert gerundet. Im Zweifel: die in der NDS
  registrierte Nutzergruppe wählen.
- No backend change.

---

## #2 Attendance visibility (server + web + mobile)

**Goal:** make imported documented presence visible and unambiguous. Doubles as the
diagnostic for the "still empty" report — a fresh import will show the count.

### Server
- `NdsImportResponse` gains `attendanceImported: Int`.
- `NdsEventImporter.import()` returns the number of `attendance_records` rows it
  wrote (in addition to events created) — adjust signature/return to carry both,
  or add a small result type `{ eventsCreated, attendanceImported }`.
- `NdsRoutes` `/clubs/{clubId}/nds/import` populates the new field.

### Web (`admin/`)
- **Done screen** (`NdsImportDialog.svelte`): add line "X Anwesenheiten übernommen".
- **Checkbox**: relabel + helptext so the effect is obvious, e.g.
  "Im NDS-Sheet mit «J» markierte Anwesenheiten als dokumentierte Präsenz importieren".
- **Event list** (`/app/events/+page.svelte`) and **event detail**
  (`/app/events/[id]/+page.svelte`): show a documented-presence indicator
  ("N anwesend") sourced from `attendance_records` (present), **only on events with
  `external_source = 'nds'`**. Full per-member presence stays on the check-in page.
  - Requires the events/event-detail loaders to surface present counts (and the
    `externalSource` flag, already present on the list).

### Mobile (`composeApp`)
- Same NDS-only presence indicator on `EventCard` / `EventDetailScreen`
  (`composeApp/src/commonMain/.../ui/events/`), gated on `external_source = 'nds'`.
- Source the count via the existing attendance repository in `shared/`; add a
  present-count accessor if not already available.

### Tests
- New realistic fixture (multi-week, many members, ~100 activity columns,
  participant birthdates, multi-word names) in `NdsTestFixtures` or a dedicated
  fixture, plus an integration test asserting the present-record count end-to-end
  through `/nds/import` (mirrors the manual probe that found 92 records).

**Fallback:** if a fresh real import still reports 0 after this ships, that is a
reproducible signal — open a focused bug hunt then.

---

## #3 Events-list scroll restoration (web only)

**Goal:** returning from an event detail keeps the previous scroll position.

- **Diagnose first** with a Playwright repro on `/app/events` (scroll → open detail
  → back) to confirm the cause (load re-run + reflow vs. something disabling
  restoration).
- **Fix:** persist and restore the scroll position for `/app/events` — prefer
  SvelteKit's `snapshot` export; fallback `sessionStorage` + `afterNavigate`,
  applying scroll after the list data has rendered.
- Mobile uses native scrolling — out of scope.

---

## #4 Attach an account to an imported player (verify; no new build)

**Goal:** confirm + guarantee the existing flow, per explicit user requirement:
"if I get invited and accept, I may be added to a user that was previously imported
via NDS."

- The per-member email invite (`/teams/{teamId}/nds/members/{id}/invite`) already
  binds to the roster row and `claimMember()` adopts the provisional user on redeem.
- **Verify both redeem paths claim the NDS member:**
  - Web redeem (existing test covers backend).
  - **Mobile redeem** (InviteScreen / deep link) — confirm it hits the same redeem
    endpoint so the NDS member is claimed and attendance migrates. Add a test/check
    if missing.
- Improve discoverability by surfacing this in the #5 management screens.

---

## #5 Club-manager role management (server + new web screen + mobile)

**Goal:** let a club manager assign/repair roles and connect accounts easily.

### New web screen: `/manage/{clubId}/members`
- Lists **all club users**, **server-sorted**, **paginated / lazy-loaded** (no
  minimum typing to see results; an optional filter box on top narrows the list).
- Per user: their team roles; controls to add to a team, change/remove a role, and
  link to an imported NDS player.

### Equivalent mobile management screen (`composeApp`)
- New club-management screen with the same capabilities, reachable from the mobile
  nav for users who are club managers.
- New `shared/` repository methods backing it (list club users paginated, add role,
  change role, remove role, link member).

### Backend endpoints (new unless noted)

| Capability | Endpoint | Notes |
|---|---|---|
| List club users (sorted, paginated) | `GET /clubs/{clubId}/users?limit=&offset=` | server-sorted; returns user + their team roles |
| Add existing club user to a team | `POST /teams/{teamId}/members { userId, role }` | role ∈ {player, coach} |
| Change a member's team role | `PATCH /teams/{teamId}/members/{userId} { role }` | |
| Remove a member's team role | `DELETE /teams/{teamId}/members/{userId}` | |
| Link existing account → imported player | `POST /teams/{teamId}/nds/members/{id}/link { userId }` | calls `claimMember(memberId, userId)` |
| Invite brand-new user by email | `POST /teams/{teamId}/invites { role, email }` | **exists** — UI only |

### Security (OWASP / no-IDOR — see memory `security-no-idor`)
- Every new endpoint verifies the caller is `club_manager` of the relevant club
  (via `requireClubRole` / `requireTeamRole(..., "club_manager")`), and that the
  target team / NDS member / user belongs to that club. The `GET /clubs/{clubId}/users`
  endpoint must only return users within that club.

---

## Sequencing (one branch, separate commits)

1. **#2** server + web + mobile presence visibility + regression test.
2. **#3** web scroll restoration (diagnose → fix → verify).
3. **#1** web Nutzergruppe help text.
4. **#5** server endpoints → web `/manage/{clubId}/members` screen → `shared` repo
   methods + mobile management screen.
5. **#4** verification (web + mobile redeem → claim of NDS member) folded into #5
   testing.

## Quality gates
- Web: `npm run check` clean (TypeScript + Svelte) before push.
- Server/shared/mobile: `./gradlew check` green.
- Git: merge only (never rebase); no AI authorship trailers; commit per logical unit.

## Out of scope
- Reworking the NDS parser/importer (proven correct).
- Changing the attendance data model.
- Unrelated refactoring.
