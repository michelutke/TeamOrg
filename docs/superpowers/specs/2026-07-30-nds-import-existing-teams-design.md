# NDS Import v2 — Existing Teams, Member Mapping, Conflict Resolution, Mobile — Design

Status: **approved design** · 2026-07-30 · Scope: server (Ktor) + admin web (SvelteKit) + mobile app (Compose Multiplatform)

Builds on `docs/nds-import-export-design.md` (V14/V15, implemented). That doc is ground truth
for file formats, parser, data model (`nds_members`, provisional users), and export. This design
changes the **import** side only.

## Goals

1. Import into **existing teams** with an explicit **member-mapping step**: per parsed
   Teilnehmer/Leiter row the manager/coach decides — map to an existing user, create a new
   (provisional) user, or skip. Auto-suggested matches, pre-selected when confident.
2. Anwesenheitsliste import into a team with existing events: **conflict resolution** — per
   overlapping event (same date + type) choose keep TeamOrg or keep NDS, bulk per series with
   per-date overrides.
3. **Event times in the wizard**: per detected series the importer sets start time (+ optional
   location); end time prefilled from Dauer, editable. Replaces the 18:00 placeholder.
4. **Partial imports**: any subset of the 3 files (Teilnehmende CSV, Leiter XLSX,
   Anwesenheitsliste XLSX); ≥1 required; AWL marked „empfohlen".
5. **Mobile**: the full wizard also in the Compose app for team coaches. Same API.

Non-goals: export changes, parser changes, NDS file-format changes, claim-flow changes.

## Decisions (from design session)

| Topic | Decision |
|---|---|
| Mapping options per row | map to existing member (suggested, pre-selected when confident) / create new / skip |
| Mapping update scope | **NDS fields only**: set `nds_members.user_id`, overwrite person number, birthdate, Funktion. TeamOrg profile (name, email) and existing role untouched. Mapped user not on team → added with role from Funktion (Leiter/in→coach, Teilnehmer/in→player) |
| Conflict definition | same **date + type** (NDS `T`↔training, `W`↔match); NDS side has no reliable time |
| Keep semantics | **Keep TeamOrg**: existing event survives (time/location/RSVPs intact), NDS column not imported as event, its `J`-attendances written onto the existing event (`insertIgnore` — real RSVPs never overwritten). **Keep NDS**: existing event set `status=cancelled`, NDS event imported with the series time |
| Conflict granularity | bulk choice per series group, expandable per-date overrides; default **keep TeamOrg** |
| Event times | set in wizard per detected series: start time (+ optional location); end = start + Dauer, editable |
| File combos | any subset, ≥1 file; AWL „empfohlen". Person-files-only import needs no Angebot link (targets chosen team directly) |
| Entry points | club manage page (club_manager, team picker/create) + team page (coach of that team, teamId fixed) + mobile team screen (coach) |
| Architecture | **stateless wizard**: `parse` returns preview + suggestions + conflicts; client collects all decisions; single `import` call, one transaction. No server session state |
| Rollout | one spec/plan; server + web first, mobile tasks in the same plan, one release |

## Wizard flow (identical semantics web + mobile, German strings)

Steps: **Dateien → Mitglieder-Zuordnung → Events & Konflikte → Bestätigen**

1. **Dateien** — 3 upload slots (Teilnehmende `.csv`, Leiter/innen `.xlsx`, Anwesenheitsliste
   `.xlsx`), each optional, ≥1 required; AWL slot badged „empfohlen". Target team: fixed from
   team-page/mobile entry; on the manage page a team dropdown or „Neues Team" (unchanged
   create path). AWL present → Angebot link/one-team-per-Angebot check as today (409 names
   linked team, runs before team creation).
2. **Mitglieder-Zuordnung** — table of parsed rows (Funktion, Name, Geburtsdatum,
   Personennummer falls vorhanden). Per row a select: suggested existing member(s), all other
   team members, „Neuen Nutzer erstellen (provisorisch)", „Überspringen". Pre-selection only
   for high-confidence unique matches. Rows already linked (`nds_members.user_id` set from a
   prior import/claim) render locked with „bereits verknüpft".
3. **Events & Konflikte** (only when AWL present) — detected series
   („MO · Training · 90 min · 18 Termine") with start-time input, editable end time
   (prefilled + Dauer), optional Ort. Below: conflict groups with bulk keep-choice
   (default „TeamOrg behalten") and expandable per-date overrides. One-off activities render
   as single rows with the same controls.
4. **Bestätigen** — summary: x zugeordnet / y neu / z übersprungen; n Events neu /
   m Konflikte (k TeamOrg, l NDS); Anwesenheiten ja/nein. Then import.

## API

### `POST /clubs/{clubId}/nds/parse` (extended)
- Multipart: any subset of `teilnehmende`, `leiter`, `anwesenheitsliste`; optional `teamId`.
- Auth: club_manager(clubId) **or coach(teamId)** (teamId then required and must belong to clubId).
- Response additions:
  - `memberSuggestions[]` per parsed row: `{rowKey, candidates: [{userId, displayName, score, birthdateMatch}], preselectedUserId?, alreadyLinkedUserId?}`
  - `series[]`: `{seriesKey, weekday, type, durationMin, dates[], count}` (recurrence detection moved to parse so the wizard can show it)
  - `conflicts[]` (AWL + teamId only): `{seriesKey, groups: [{date, existingEventId, existingEventTitle, existingEventStart}]}` matched on same date + type against non-cancelled team events
- Still no writes.

### `POST /clubs/{clubId}/nds/import` (extended)
- Auth: as parse.
- Request additions:
  - `mappings[]`: `{rowKey, action: 'map'|'create'|'skip', userId?}` — required when member rows present; server rejects unknown rowKeys, `map` without userId, userId not in club
  - `seriesTimes[]`: `{seriesKey, startTime 'HH:mm', endTime 'HH:mm', location?}` — required for every series/one-off that imports ≥1 event (a series fully resolved keep-TeamOrg needs none); no more 18:00 placeholder
  - `conflictResolutions[]`: `{seriesKey, keep: 'teamorg'|'nds', overrides: [{date, keep}]}` — required covering every conflict returned by parse; unresolved conflict → 400
- Semantics (one transaction, rollback on any failure):
  - `map`: set `nds_members.user_id`, overwrite person_number/birth_date/funktion on the nds row; add `team_roles` row if user not on team (role from Funktion); **no provisional user created**; user profile untouched
  - `create`: today's provisional-user path unchanged
  - `skip`: no row written
  - events: keep-TeamOrg → no NDS event; `J`-attendances → `attendance_responses` on the existing event (`insertIgnore`, attended→confirmed, auto-finalize past); keep-NDS → existing event `status=cancelled`, NDS event created with series time/location, attendance as today
  - idempotency: re-import matches events by date+symbol as today; mappings/claims preserved; cancelled events are not conflict candidates

### Permission changes
Both endpoints: coach of the target team allowed (was club_manager only). No-IDOR: teamId
membership verified against clubId; foreign team → 403. Memory rule `security-no-idor` applies
to every new parameter.

## Matching algorithm (server, pure function — unit-testable)

Normalize names (case, umlauts ä→ae etc., trim). Scoring per candidate (team roster +
already-linked nds_members):
- exact normalized `lastName+firstName` → HIGH
- Levenshtein ≤ 2 on either name (other exact) → MEDIUM
- birthdate equal (both known) → +boost (MEDIUM→HIGH)
- `nds_members.user_id` already links this user for this identity key → LOCKED
Preselect only a **unique** HIGH candidate. MEDIUM candidates listed, nothing preselected.
Ambiguous HIGH (two users same name) → nothing preselected, both listed.

## Web UI (admin)

- `NdsImportDialog.svelte` → grows into a 4-step wizard (extract step components:
  `NdsMappingStep.svelte`, `NdsEventsStep.svelte`); reachable from manage page (as today) and
  team page next to the NDS export card.
- Inline German strings (Swiss-only feature, established pattern).

## Mobile (Compose Multiplatform, coach)

- Entry: team screen NDS section → `NdsImportScreen` (4 steps, one `NdsImportViewModel`
  holding step state + decision payload).
- File picking: new `rememberDocumentPickerLauncher(mimeTypes)` expect/actual
  (Android `OpenDocument`, iOS `UIDocumentPickerViewController`), pattern like `ImagePicker`.
- DTOs in `shared` mirroring the API; repository method in shared data layer.
- Series time via existing time-picker dialog; conflict groups as expandable cards.
- German strings.

## Testing (full suite — quality gates)

**Server (JUnit + Testcontainers, `server/src/test/kotlin/ch/teamorg/nds/`)**
- Unit: matching (exact / umlaut-normalized / Levenshtein / birthdate boost / ambiguous →
  no preselect / already-linked → locked), conflict detection (date+type, grouping,
  cancelled events excluded), series-time application (end = start + Dauer, override).
- Route/integration (real Postgres): parse with all 7 file-subset combos + 400 on none;
  mapping map/create/skip incl. role-add, NDS-field overwrite, profile untouched, invalid
  mapping rejections; conflict keep-teamorg (event survives, J-attendance lands, existing
  RSVP not overwritten), keep-nds (event cancelled, new event with chosen time), per-date
  override; permission matrix (coach own team 200, foreign team 403, cross-club 403);
  idempotent re-import preserves mappings/claims; transaction rollback on injected
  mid-import failure (no partial members/events).
- Regression: existing export pre-flight, claim/adoption, one-team-per-Angebot tests stay green.

**Web**
- Vitest component tests: mapping table (preselect, action switching, locked rows), conflict
  bulk+override state, step gating (can't proceed with unresolved conflicts/missing times).
- Playwright E2E (fixtures = real sample NDS files): full wizard into existing team,
  members-only import, both conflict directions.
- `npm run check` zero errors.

**Mobile**
- ViewModel unit tests: step state machine, payload assembly, error/retry.
- Compile gates: Android + iosSimulatorArm64.

**Quality gates (per task + final)**
- Full server suite + web check + mobile unit tests green before every commit.
- Final adversarial verifier pass: correctness, transaction/concurrency, no-IDOR on all new
  endpoint parameters.
- PII gate: PERSONENNUMMER never logged (grep for it in log statements); birthdates only in
  role-guarded responses.
- Manual QA checklist: real Anwesenheitsliste round-trip on staging incl. re-import; mobile
  file pick + full wizard on physical Android and iOS.

## Edge cases

1. AWL absent → steps 3 skipped entirely; no Angebot link touched.
2. Person file rows not in AWL (and vice versa) → union by identity key, as today.
3. Mapped user already has a different `nds_members` row on this team → 409 with names
   (one user ↔ one NDS identity per team).
4. Two parsed rows mapped to the same user → 400 (client prevents, server validates).
5. Keep-NDS on an event with existing RSVPs → warn in UI (count shown); cancellation keeps
   rows for history, notifications suppressed for imported cancellations.
6. Series time makes events overlap other non-conflicted team events → not blocked (coach's
   call), no new conflict pass after time entry (conflicts are date+type only).
7. Re-import after v2: previously imported events (18:00 placeholder) match by date+symbol
   and get updated times only if the importer sets them; no silent time overwrite.
8. Provisional users created by v1 imports appear as mapping candidates (they are team
   members) — mapping to them is a no-op link refresh, not an error.

## Phasing (implementation plan order)

1. Server: matching function + parse extensions (suggestions, series, conflicts) + tests.
2. Server: import extensions (mappings, seriesTimes, conflictResolutions, coach auth) + tests.
3. Web: wizard rework (4 steps, mapping table, conflicts, times) + component tests + E2E.
4. Mobile: shared DTOs/repo + document picker expect/actual + wizard screens + VM tests.
5. Quality pass: verifier, PII gate, staging round-trip, store-track QA.
