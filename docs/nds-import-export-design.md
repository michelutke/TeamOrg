# NDS (J+S / Nationale Datenbank Sport) Import & Export — Design

Status: **implemented** (V14, 2026-06-26) · Scope: backend (Ktor) + admin web

## Implementation notes (as shipped)
- Migration `V14__nds_import_export.sql`; Exposed table `NdsMembersTable` + columns on
  teams/users/events/invite_links. `users.provisional` flag added (Option A).
- Parser: `infra/nds/AnwesenheitslisteParser.kt` (Apache POI `poi-ooxml` 5.3.0), scans column A
  by label so it tolerates varying roster sizes. `infra/nds/NdsRules.kt` holds the symbol/type
  maps + DAUER allowed-sets. `infra/nds/NdsEventImporter.kt` (recurrence + attendance). Export:
  `infra/nds/NdsExportService.kt` + `NdsCsvWriter` (`;`, CRLF, UTF-8 BOM).
- Repo: `domain/repositories/NdsRepository.kt` (roster import w/ provisional users, claim/adoption
  moving attendance off the placeholder, export reads). Routes: `routes/NdsRoutes.kt`.
- **Imported events use a placeholder start time of 18:00** (the sheet has no time) and `location`
  null; pre-flight blocks export on `training_missing_location`, forcing the coach to set real
  time/location first.
- Admin UI: `NdsImportDialog.svelte` (upload→preview→confirm) on the manage-teams page; NDS
  roster + person-number entry + per-member invite + export download on the team page. Strings
  are **inline German** (Swiss-only feature) rather than the typed i18n dict.
- Tests: `server/src/test/kotlin/ch/teamorg/nds/*` (parser, rules, CSV) + `routes/NdsRoutesTest`
  (HTTP round-trip: upload→import→recurrence→attendance→claim→export, idempotency, pre-flight).
  Full server suite green; admin `npm run check` clean.

---

Original design follows.

Status: **planned** · Author: design session 2026-06-26 · Scope: backend (Ktor) + admin web (+ later app)

Bridge TeamOrg ⇄ **NDS** (the BASPO J+S "Nationale Datenbank Sport"). NDS only talks
Excel/CSV files. We:

1. **Export** a season's activities + attendances from TeamOrg → two NDS import CSVs the
   coach uploads into the NDS course (Kurs).
2. **Import** a club's roster from an NDS *Anwesenheitsliste* xlsx → create members before
   the team exists, then invite them (email or per-member magic link).
3. **Import** the activities (and optionally documented attendances) from the same
   *Anwesenheitsliste* → events, detecting recurring series.

Modeled on `docs/swissvolley-import-design.md` (same stack, same patterns). The two
features are complementary: SwissVolley feeds **games**; NDS round-trips **J+S
trainings + attendances + the official roster**. The NDS export reads TeamOrg events
regardless of source, so SwissVolley-synced matches flow into the NDS export for free.

---

## 0. Ground truth — the actual NDS files (decoded 2026-06-26)

Three real files were dissected. **The formats below are verified, not assumed.**

### 0.1 Export FROM NDS: *Anwesenheitsliste* (`.xlsx`) — our IMPORT source (flows 2 & 3)

`20260626_Anwesenheitsliste_4037090.xlsx`, single sheet, dimension `A1:DA30`. It is a
**human-readable attendance matrix**, NOT a clean data table. Layout (0-indexed cols):

| Rows | Content |
|------|---------|
| R1 | `Angebot` → **`753813`** (the NDS course/Kurs number — the linking key) |
| R2 | `Kurs` → `Damen 3. Liga pro B` (course name) |
| R3 | `Hauptsportart` → `Volleyball` (main sport) |
| R4 | `Gruppengrösse` → `24/24` |
| R5 | `Kursstatus` → `Durchführung` |
| R6 | `Wochentag` — cols 5…104 alternate `MO`,`MI` (the recurring pattern, visible directly) |
| R7 | `Datum` — **Excel serial dates** per activity column (`46139` = epoch 1899-12-30) |
| R8 | `Kalenderwoche` — ISO week per column (wraps 53→1 across the year boundary) |
| R9 | `Symbol der Tagesaktivität` — `T` (=Training) for every column here |
| R10 | `Dauer der Tagesaktivität` — `1,5` (**hours, comma-decimal** → 90 min) |
| R11 | `Trainings Athletik und Psyche` — `Normal` |
| R12 | member-table header: `Nummer \| Name \| Vorname \| Funktion / Geburtstag \| Zusätze / Alter` then per-date marker cols |
| R13 | section header `Leiter/-in(2):` |
| R14–15 | the 2 leaders: `Nummer, Name, Vorname, Funktion (text), —`, then `J` in attended columns |
| R16 | section header `Teilnehmer/-in(14):` |
| R17–30 | 14 participants: `Nummer, Name, Vorname, Geburtstag(serial), Alter`, then `J` in attended columns |

Decoding rules (all confirmed against the sample):
- **Attendance cell = `J`** (Ja/present) → empty = not present. AWK has no yes/no column,
  so each `J` becomes exactly one AWK row; empties produce nothing.
- **Two roster sections**: rows under `Leiter/-in(N):` → FUNKTION `Leiter/in`; rows under
  `Teilnehmer/-in(N):` → FUNKTION `Teilnehmer/in`. **Coaches have attendance too** (leaders
  show `J`), so they must be in the export.
- **`Nummer` is list position (1..N), NOT a national id.** Unstable across exports.
- **Leaders' `Geburtstag` column holds their function text** (`J+S-Leiter/-in (J)`), not a
  date — the column is dual-purpose ("Funktion / Geburtstag"). Participants' col = real
  birthdate serial. Parse by section.
- Excel quirks: numbers are strings with trailing `.0` (`753813.0`, `46139.0`, `18.0`);
  cells can be multi-line (`Leiter/-in Athletik…\nJ+S-Leiter/-in (J)`); umlauts present
  (`Lüthi`, `Grösse`).

> ### ⛔ The PERSONENNUMMER gap (single biggest issue — read first)
> The *Anwesenheitsliste* export **does not contain the J+S PERSONENNUMMER** (the 9-digit
> person id, e.g. `123456789`). It only has Name + Vorname + Geburtstag. **But the NDS AWK
> import requires PERSONENNUMMER as a mandatory key.** Therefore an Anwesenheitsliste import
> alone can never produce a valid AWK export. The person number must come from elsewhere:
> a separate NDS *Teilnehmer/Personen* export, or be entered per member (by the member via
> their magic link, or by the coach). See §3 — this drives the whole member-identity model.

### 0.2 Import INTO NDS, file 1: *Aktivitäten-Import* (`.csv`)

Header row is **mandatory, fixed order, never rename/reorder**:

| # | Column | Required | Notes |
|---|--------|----------|-------|
| 1 | `AKTIVITAETSTYP` | yes | `Training` \| `Trainingstag` \| `Wettkampf` |
| 2 | `DATUM` | yes | `dd.MM.yyyy` |
| 3 | `ZEIT` | **conditional** | required for `Training`; **forbidden** for `Wettkampf`/`Trainingstag` |
| 4 | `DAUER` | conditional | **minutes**, must be an allowed value for the course NG+sport (table below) |
| 5 | `ORT` | **conditional** | required for `Training`; **forbidden** for `Wettkampf`/`Trainingstag` |
| 6 | `FOKUS` | no | `Psyche`\|`Physis`; only for `Training`/`Trainingstag` |

> ⚠️ **Importing activities WIPES the course**: *"Beim Importieren von Aktivitäten gehen
> alle bereits erfassten Aktivitäten einschliesslich der Anwesenheiten im betreffenden Kurs
> verloren."* The export must be the **complete season** and the import is **destructive**.
> Order is fixed: import Aktivitäten **first** (wipes), then AWK.

### 0.3 Import INTO NDS, file 2: *Anwesenheitskontrolle (AWK)* (`.csv`)

Header mandatory/fixed-order. **One row per (person, activity) the person attended.**

| # | Column | Required | Notes |
|---|--------|----------|-------|
| 1 | `PERSONENNUMMER` | yes | 9-digit J+S id — **not in the Anwesenheitsliste** (§0.1) |
| 2 | `FUNKTION` | yes | `Teilnehmer/in` \| `Leiter/in` (only decides add-as; real role set by NDS post-import) |
| 3 | `DATUM` | yes | `dd.MM.yyyy` |
| 4 | `AKTIVITAETSTYP` | yes | `Training`\|`Wettkampf`\|`Trainingstag`\|`Lagertag` |
| 5 | `ZEIT` | conditional | required for `Training`; forbidden otherwise |
| 6 | `DAUER` | yes (cond.) | minutes (per NG table); no value allowed in a Lager |
| 7 | `ORT` | conditional | required for `Training`; forbidden otherwise |

> ⚠️ **AWK import requires the persons AND activities to already exist in the NDS course.**
> So the AWK rows must match the just-imported Aktivitäten **exactly** on
> `(DATUM, AKTIVITAETSTYP, ZEIT, DAUER, ORT)`. This is a hard **consistency invariant** on
> our export generator (§10).

### 0.4 Allowed `DAUER` values (minutes) — depends on Nutzergruppe (NG) × Aktivitätstyp × Sportart-Artikel

| NG | Art. | Training | Trainingstag | Wettkampf |
|----|------|----------|--------------|-----------|
| 1 | Abs.1 | 60,75,90 | 240,300 | — (keine Dauer nötig) |
| 2 | Abs.2 | 60,90,120,150,180,210,240,270,300 | 240,300 | 60…300 (same set) |
| 4 | Abs.1 / Abs.2 | 60,75,90 / 60,90…300 | 240,300 | keine Wettkämpfe |
| 5 | Abs.1 / Abs.2 | 45,60,75,90 / 45,60,90…300 | 240,300 | keine Wettkämpfe |

DAUER is **not free-form** — it must snap to a discrete allowed value. We know the course's
`Hauptsportart` (Volleyball) from the Anwesenheitsliste but **not its NG**. The sample's
`1,5h = 90` is valid for all NGs, but arbitrary TeamOrg event durations (e.g. 105 min) are
**not importable** and must be snapped/validated at export (§10).

---

## 1. Feature model (the three flows)

- **Flow 1 — Export (TeamOrg → NDS).** Coach has run the season in TeamOrg. They generate a
  download: **Aktivitäten.csv + AWK.csv** (zipped) covering all trainings/games + the
  coach-set attendances, and upload both into the NDS Kurs (Aktivitäten first, destructive).
- **Flow 2 — Roster import (NDS → TeamOrg).** Before the team is set up, a club manager or
  coach uploads the Anwesenheitsliste xlsx. Members are **created** (as provisional roster
  members, §4). The CM/coach then invites them — by email, or by a **per-member magic link**
  (link is bound to that roster member, so the member chooses their own email at signup and
  is auto-linked). Members add their **PERSONENNUMMER + birthdate** at claim time.
- **Flow 3 — Event import (NDS → TeamOrg).** From the same upload, import the activity
  columns as events. **Detect recurring patterns** (weekday row + regular dates) and create
  `event_series`. If the sheet already has `J` attendances, the coach chooses **keep**
  (write `attendance_records`) or **discard**.

All three hang off one link: **`teams.nds_angebot_id`** = the `Angebot` number (`753813`).
This is the NDS analogue of `team_sv_links.sv_team_id`.

---

## 2. The hard problem: member identity across the round-trip

Three identifiers exist and none is shared by all files:

| Source | Identifier available |
|--------|----------------------|
| Anwesenheitsliste (import) | Name + Vorname + Geburtstag (+ unstable list `Nummer`) |
| AWK import (export target) | PERSONENNUMMER (9-digit) |
| TeamOrg | `users.id` (UUID) once the member has an account |

**Matching strategy:**
- On roster import: identity key = `(nds_angebot_id, lastName, firstName, birthDate)`. Used
  to dedupe re-imports and to attach the right person on a re-upload.
- PERSONENNUMMER is captured later (claim/manual) and stored on the member. **Export is
  blocked for any member without a PERSONENNUMMER** (pre-flight, §10).
- Claiming (magic link / email invite) links the provisional roster member → the real
  `users.id`. Birthdate confirms the match if names differ in spelling.

---

## 3. Data model (migration **V14** — V13 is SwissVolley)

```sql
-- Link a TeamOrg team to its NDS course (Kurs). 1:1 is enough (a team = one J+S Kurs/season).
ALTER TABLE teams
  ADD COLUMN nds_angebot_id   TEXT NULL,   -- the 'Angebot' number, e.g. '753813'
  ADD COLUMN nds_kurs_name    TEXT NULL,
  ADD COLUMN nds_hauptsportart TEXT NULL,
  ADD COLUMN nds_nutzergruppe TEXT NULL;   -- NG 1/2/4/5 — needed to validate DAUER (see §0.4)
CREATE UNIQUE INDEX uq_teams_nds_angebot ON teams(nds_angebot_id) WHERE nds_angebot_id IS NOT NULL;

-- Per-member NDS identity. ONE row per (team, person). Holds the data the Anwesenheitsliste
-- carries plus the PERSONENNUMMER captured later. user_id links to a real account once claimed.
CREATE TABLE nds_members (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  team_id         UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  user_id         UUID NULL REFERENCES users(id) ON DELETE SET NULL,  -- set on claim
  last_name       TEXT NOT NULL,
  first_name      TEXT NOT NULL,
  birth_date      DATE NULL,
  person_number   TEXT NULL,        -- J+S PERSONENNUMMER (9 digits); needed for export
  funktion        TEXT NOT NULL,    -- 'Teilnehmer/in' | 'Leiter/in'
  source          TEXT NOT NULL DEFAULT 'nds_import', -- 'nds_import' | 'manual'
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (team_id, last_name, first_name, birth_date)
);
CREATE INDEX idx_nds_members_team ON nds_members(team_id);
CREATE INDEX idx_nds_members_user ON nds_members(user_id);

-- Tie a per-member invite link to the roster row it claims (extends existing invite_links).
ALTER TABLE invite_links ADD COLUMN nds_member_id UUID NULL REFERENCES nds_members(id) ON DELETE CASCADE;

-- Mark events that came from / map to an NDS activity (idempotent re-import + export filter).
ALTER TABLE events
  ADD COLUMN nds_symbol TEXT NULL;   -- raw 'T'/'W'/… as imported; informational
-- (events already carry external_source from V13; we reuse external_source='nds' to tag imports.)
```

### 3.1 The attendance tension — **provisional users vs. roster-only** (KEY DECISION)

`attendance_records.user_id` and `attendance_responses.user_id` are **NOT NULL → users(id)**.
So an imported member with no account **cannot hold attendance rows** as-is. Flow 3 wants to
import historical `J` attendances for not-yet-registered members. Two options:

- **Option A — provisional `users` rows (RECOMMENDED).** On roster import create a real
  `users` row per member with an unusable password and a synthetic unique email
  (`nds-{angebotId}-{ndsMemberId}@import.teamorg.local`), exactly like the V13 `VolleyManager`
  system user. `team_roles.user_id` + `nds_members.user_id` point at it. Attendance works
  immediately. Claiming = the invite redeem **adopts** the provisional user: set the real
  email + password on that same row (no merge needed) → all attendance/history carries over
  seamlessly. KISS, reuses an established pattern, zero changes to attendance tables.
  - Cost: synthetic emails in `users` (filter them out of admin/user lists by the
    `@import.teamorg.local` domain or a `provisional BOOLEAN` flag — add that flag).
- **Option B — roster-only, no user.** Keep imported members purely in `nds_members`
  (`team_roles.user_id` stays NULL, already supported). Then attendance can't reference them
  → would require `attendance_records.nds_member_id` (nullable user_id) — a schema change to
  hot tables + every attendance query. Bigger blast radius. Only worth it if provisional
  user rows are unacceptable.

**Recommendation: Option A**, plus `ALTER TABLE users ADD COLUMN provisional BOOLEAN NOT NULL
DEFAULT FALSE` so provisional accounts are filterable and cannot log in. Resolve before
Phase 2.

---

## 4. Server components (Ktor / Exposed / Flyway / Koin)

- **`infra/nds/AnwesenheitslisteParser.kt`** — parse the xlsx into a typed
  `ParsedAnwesenheitsliste { angebotId, kursName, hauptsportart, gruppengroesse, activities:
  List<ParsedActivity{date, weekday, kw, symbol, durationMin, fokus}>, members:
  List<ParsedMember{section, nummer, lastName, firstName, birthDate?, attendedDates: Set<LocalDate>}> }`.
  - **xlsx parsing needs a library** — `server/build.gradle.kts` has **no Apache POI**. Either
    (a) add `org.apache.poi:poi-ooxml` (server-side, robust), or (b) parse client-side in the
    admin web with SheetJS and POST normalized JSON. **Recommend (a)** to keep one trusted
    code path and avoid shipping a 1 MB JS lib; flag the dep add.
  - Convert: serial→`LocalDate` (epoch 1899-12-30, mind the 1900 leap bug), `1,5`→90 min,
    strip `.0`, map section→funktion, `J`→attended.
- **`infra/nds/NdsCsvWriter.kt`** — emit the two CSVs. Decisions: **delimiter `;`** (Swiss/DE
  Excel default), **encoding UTF-8 with BOM** (so umlauts survive in Excel), line ending
  `\r\n`, `DATUM` `dd.MM.yyyy`, `ZEIT` `HH:mm`, `DAUER` integer minutes. Exact header strings,
  exact column order, conditional fields blank where forbidden. (Confirm `;` vs `,` with a
  real NDS import test — §14.)
- **`domain/repositories/NdsRepository`** — CRUD `nds_members`, team NDS link, import txn.
- **`infra/nds/NdsExportService.kt`** — build Aktivitäten + AWK from TeamOrg events +
  `attendance_records`, run the pre-flight validation, zip the two files.
- **`infra/nds/RecurrenceDetector.kt`** — group activities → `event_series` candidates (§7).

No polling job (unlike SwissVolley) — import/export are user-triggered.

---

## 5. API endpoints (all guarded; no-IDOR per `docs/swissvolley` §8 conventions)

| Method | Path | Role | Action |
|--------|------|------|--------|
| `POST` | `/clubs/{clubId}/nds/parse` | club_manager(clubId) | Upload xlsx → return parsed preview JSON (no writes) |
| `POST` | `/clubs/{clubId}/nds/import` | club_manager(clubId) | Body: parsed payload + choices `{teamId?, createTeam?, importEvents:bool, attendanceMode:'keep'\|'discard', recurrence:[…]}` → create team?/members/events/attendances |
| `GET`  | `/teams/{teamId}/nds/members` | coach\|club_manager(teamId) | Roster with claim status + missing-person-number flags |
| `PATCH`| `/teams/{teamId}/nds/members/{id}` | coach\|club_manager(teamId) | Set `person_number`, fix name/birthdate |
| `POST` | `/teams/{teamId}/nds/members/{id}/invite` | coach\|club_manager(teamId) | Create per-member invite link (binds `nds_member_id`) or send email |
| `GET`  | `/teams/{teamId}/nds/export/preflight` | coach\|club_manager(teamId) | Validation report (missing person numbers, bad durations, trainings missing time/location, date-range issues) |
| `GET`  | `/teams/{teamId}/nds/export` | coach\|club_manager(teamId) | Returns ZIP of Aktivitäten.csv + AWK.csv (only if preflight clean, else 409 with report) |

Claim linking rides the **existing** `POST /invites/{token}/redeem` (invite-flow-contract.md):
when the redeemed link has `nds_member_id`, after inserting the team role, set
`nds_members.user_id = caller` and (Option A) adopt the provisional user. The member then
fills `person_number`/`birth_date` via `PATCH …/members/{id}` (self-allowed for own row).

---

## 6. Flow 2 — roster import logic

1. `POST /nds/parse` → preview (no writes). UI shows course meta + members + activities.
2. Manager picks/creates the target team; `teams.nds_angebot_id` etc. set from the sheet.
3. For each parsed member, upsert `nds_members` on `(team_id, last_name, first_name,
   birth_date)`. Re-uploads update, never duplicate.
4. (Option A) create a provisional `users` row + `team_roles` row (`coach` for `Leiter/in`,
   `player` for `Teilnehmer/in`), `user_id` NOT changed if already claimed.
5. Manager invites: per-member magic link (`nds_member_id` bound) or email. On redeem the
   provisional user is adopted and `nds_members.user_id` set.

Idempotency: same sheet twice → same members, no dupes (unique key). Person numbers and
claimed links are preserved across re-imports (match by identity key, update only blanks).

---

## 7. Flow 3 — event import + recurrence detection

- Each activity column → one event candidate: `start_at` from `Datum`(+`ZEIT` if we have it —
  the sheet has **none**, so trainings import **without a time/location** and the coach must
  fill them before export), `end_at = start + DAUER`, `type` from symbol map
  (`T→training`, `W→match`, others → §14), `nds_symbol` raw, `external_source='nds'`.
- **Recurrence detection** (`RecurrenceDetector`): group activities by `(weekday, time?,
  symbol, duration)`; if ≥3 occur at a regular weekly (or n-weekly) interval → emit one
  `event_series` (`pattern_type='weekly'`, `weekdays=[…]`, template fields) and mark members'
  events as that series. Tolerate gaps (skipped holiday weeks) and treat pattern-breakers
  (one-off games, duration changes) as standalone events with `series_override`. The sample
  is a clean MO+MI weekly series → two weekly series.
- **Attendances** (`attendanceMode`): `keep` → for each `J` write an `attendance_records`
  row (`status='present'`, `set_by` = importing user) for the member's (provisional) user;
  `discard` → import events only. Empty cells write nothing (NDS has no explicit "absent").
- Idempotency: dedupe events on `(team via event_teams, date, nds_symbol, time)`; re-import
  updates rather than duplicates.

---

## 8. Flow 1 — export logic (TeamOrg → NDS)

Source data: the team's `events` (training/match/other) in the chosen season window +
`attendance_records` (coach-authoritative `present`). Build two files:

**Aktivitäten.csv** — one row per event:
- `AKTIVITAETSTYP`: `training→Training`, `match→Wettkampf`, `other→` (needs mapping — §14).
- `DATUM` = `start_at` date; `ZEIT` = `start_at` time **only for Training** (blank for
  Wettkampf/Trainingstag — forbidden there); `DAUER` = round(minutes) **snapped to the NG
  allowed set**; `ORT` = `location` **only for Training**; `FOKUS` blank (or future field).

**AWK.csv** — one row per (member, event) where `attendance_records.status='present'`:
- `PERSONENNUMMER` = `nds_members.person_number`; `FUNKTION` from `funktion`; `DATUM`,
  `AKTIVITAETSTYP`, `ZEIT`, `DAUER`, `ORT` **copied byte-for-byte from the matching
  Aktivitäten row** (the consistency invariant — AWK must match an existing activity).

**Pre-flight validation (blocks export, returns a report):**
1. Every member with ≥1 present record has a `person_number` (else list them).
2. Every Training has a `ZEIT` and `ORT` (NDS rejects trainings without them).
3. Every `DAUER` ∈ allowed set for the team's NG + Hauptsportart (§0.4); offer to snap.
4. All events fall inside one NDS course window / consistent `Angebot`.
5. `nds_angebot_id` and `nds_nutzergruppe` are set on the team.

The download dialog must warn: **"Importing Aktivitäten into NDS deletes all existing
activities and attendances in that course. Import Aktivitäten first, then AWK."**

---

## 9. UX touch points (admin web — SvelteKit 2 / Svelte 5 runes, Tailwind 4)

- **Manager**: on `/manage/[clubId]` an "NDS Import" card → upload Anwesenheitsliste →
  preview (course meta, roster, detected series, attendance toggle) → confirm. After import,
  a roster table with claim status + a "fehlende Personennummer" badge per member and
  per-member "Einladen" (link/email).
- **Coach**: on the team page, "NDS Export" → pre-flight report (blocking issues listed) →
  when clean, "Download (Aktivitäten + AWK)" + the destructive-import warning. Editing a
  member's `person_number` and an event's time/location to clear pre-flight issues.
- **Member (app/web)**: at invite claim, optional "J+S-Personennummer" + birthdate fields
  that populate their `nds_members` row.

German UI strings throughout (this is a Swiss J+S feature).

---

## 10. Pitfalls & edge cases (the bulletproofing checklist)

1. **PERSONENNUMMER absent from the import** (§0.1) — export blocked until captured. #1 risk.
2. **Aktivitäten import is destructive** — full-season export only; document the order.
3. **AWK↔Aktivitäten consistency invariant** — identical `(DATUM,TYP,ZEIT,DAUER,ORT)` or AWK
   rows fail to match in NDS. Generate AWK strictly from the emitted Aktivitäten rows.
4. **DAUER is a discrete enum per NG** — snap/validate; never emit free-form minutes.
5. **Conditional ZEIT/ORT** — required for Training, *forbidden* for Wettkampf/Trainingstag.
   Emitting them on a game is a rejection.
6. **Excel serial dates** — epoch 1899-12-30, 1900 leap-year bug; birthdates are serials too.
7. **Comma decimals / `.0` strings / multi-line cells / umlauts** in the xlsx — robust parse.
8. **CSV encoding** — UTF-8 **BOM** + `;` delimiter for Swiss Excel, else umlauts/columns break.
9. **Leaders' dual-purpose Geburtstag column** — parse by section, not by column alone.
10. **`Nummer` is unstable** — never use as an identity key; use name+birthdate.
11. **Coaches are exported too** (Leiter rows have attendance) — include them, FUNKTION=Leiter.
12. **Provisional-user pollution** (Option A) — synthetic emails must be hidden from user
    lists and unable to log in; claim must adopt cleanly (no duplicate accounts).
13. **Name-spelling drift** between NDS and self-signup — birthdate is the disambiguator;
    magic-link binding avoids matching entirely.
14. **Re-import idempotency** — members and events must dedupe (unique keys), preserving
    captured person numbers and claims.
15. **Recurrence false positives/negatives** — require ≥3 regular occurrences; gaps tolerated;
    breakers become standalone events.
16. **Trainings imported from NDS lack time + location** — the sheet has none; the coach must
    fill before any export passes pre-flight.
17. **Timezone** — store events in the team's local tz; NDS dates/times are local (Europe/Zurich).
18. **PII** — birthdate + J+S person number are sensitive personal data (Swiss DSG); store,
    log, and export-handle accordingly; never log person numbers.
19. **Symbol legend incomplete** — only `T` seen; need full `W`/`TT`/`L` mapping (§14).
20. **Large sheets** — full season ×40 members is fine, but stream/limit upload size and
    validate the file is actually an Anwesenheitsliste (check R1=`Angebot`) before parsing.

---

## 11. Phasing

1. **V14 migration + `nds_members` + team NDS columns + provisional-user decision (Option A)**
   + Exposed table objects. Seed nothing.
2. **Roster import (flow 2)**: POI dep + `AnwesenheitslisteParser` + `/nds/parse` + `/nds/import`
   (members only) + manager preview/confirm UI + per-member invite link binding + claim adoption.
3. **Event import (flow 3)**: activity→event creation + `RecurrenceDetector` + attendance
   keep/discard + idempotent re-import.
4. **Export (flow 1)**: `NdsCsvWriter` + `NdsExportService` + pre-flight validation endpoint +
   download UI + destructive-import warning. End-to-end test against a real NDS course.
5. **Polish**: member self-service person-number capture in the app, DAUER auto-snap UI,
   symbol-map completeness, super-admin filtering of provisional users.

---

## 12. Resolved decisions & remaining open questions

### Resolved (design session 2026-06-26)
- **PERSONENNUMMER source → MANUAL for now.** Captured via member self-entry at invite claim
  (`PATCH …/members/{id}`, own row) and/or coach manual entry in the roster table. A separate
  NDS *Teilnehmer/Personen* export may exist and is worth checking later to auto-fill — but
  not a Phase-1 dependency. Export stays blocked per-member until the number is present.
- **Member model → Option A (provisional users).** Create real `users` rows (synthetic
  `@import.teamorg.local` email + `provisional BOOLEAN` flag, unusable password) per import;
  invite-claim adopts the row. No changes to the attendance tables.
- **Symbol legend → assume standard J+S** until a real games export proves otherwise:
  `T→Training`, `W→Wettkampf`, `TT→Trainingstag`, `L→Lagertag`. Verify in the Phase-4 import test.

### Still open
1. **NDS CSV delimiter + encoding** — confirm `;` + UTF-8 BOM by a real test import (vs `,`).
2. **Does NDS import accept `.xlsx`** or strictly `.csv`? (PDFs say CSV.)
3. **Course NG** — where does the team's Nutzergruppe (1/2/4/5) come from? Assume
   manager-entered on the team's NDS settings until proven available in an export. Needed for
   DAUER validation.
4. **Team ↔ Kurs cardinality** — assume 1 team = 1 NDS Kurs/season (single `nds_angebot_id`
   column). Revisit with a link table if a team must map to several Angebote.
5. **`other` event type → which AKTIVITAETSTYP** on export (skip, or map to Trainingstag)?
6. **Season window for export** — explicit date range picker, or infer from `nds_angebot_id`
   events?
