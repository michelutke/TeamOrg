# NDS Import v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import NDS files into existing teams with an explicit member-mapping step, event-conflict resolution (TeamOrg vs NDS), wizard-set event times, partial file subsets — on admin web AND the mobile app (coach).

**Architecture:** Stateless wizard. `parse` returns preview + match suggestions + detected series + conflicts; the client collects all decisions; one `import` call carries the full decision payload and runs in a single transaction. No server session state. Spec: `docs/superpowers/specs/2026-07-30-nds-import-existing-teams-design.md` — read it for semantics; THIS plan for exact code/files.

**Tech Stack:** Ktor/Exposed/Testcontainers (server), SvelteKit 2 + Svelte 5 runes + Tailwind 4 (admin), Compose Multiplatform Android+iOS (mobile), kotlinx.serialization DTOs in `shared`.

## Global Constraints

- German UI strings, inline (Swiss-only feature) — NOT the typed i18n dict.
- No-IDOR: every new endpoint parameter carrying an id must be role-verified (coach of team / club_manager of club). Cross-club teamId → 403.
- PERSONENNUMMER must never appear in log statements.
- Weekday convention everywhere: 0=Mon..6=Sun.
- Events use UTC-as-local convention (`ZoneOffset.UTC`, matching `EventRepositoryImpl`).
- Mapping updates NDS fields only: `nds_members.user_id`, `person_number`, `birth_date`, `funktion`. Never touch user profile or existing role rows.
- Conflict = same date + same event type (`T`↔training, `W`↔match) against non-cancelled team events. Default resolution: keep TeamOrg.
- Keep TeamOrg → no NDS event created; NDS `J`-attendances written to the EXISTING event via `insertIgnore`. Keep NDS → existing event `status=cancelled`, NDS event created with wizard time.
- `seriesTimes` required for every series/one-off importing ≥1 event; 18:00 placeholder is dead.
- Run before each commit: `./gradlew :server:test` (needs OrbStack/Docker), and for web tasks `cd admin && npm run check`, for mobile tasks `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid`.
- Never commit with Co-Authored-By or AI attribution.

## File Structure (new/modified)

- New: `server/src/main/kotlin/ch/teamorg/infra/nds/NdsMemberMatcher.kt` — pure matching function
- New: `server/src/main/kotlin/ch/teamorg/infra/nds/NdsImportPlanner.kt` — series extraction + conflict detection (pure-ish, one DB read)
- Modify: `server/src/main/kotlin/ch/teamorg/domain/models/Nds.kt` — new DTOs
- Modify: `server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt` — parse/import extensions, coach auth
- Modify: `server/src/main/kotlin/ch/teamorg/infra/nds/NdsEventImporter.kt` — decision-driven import
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/NdsRepository.kt` — `applyMapping`
- New tests: `server/src/test/kotlin/ch/teamorg/nds/NdsMemberMatcherTest.kt`, `NdsImportPlannerTest.kt`; extend `routes/NdsRoutesTest.kt`
- Web: `admin/src/lib/components/NdsImportDialog.svelte` → wizard; new `NdsMappingStep.svelte`, `NdsEventsStep.svelte`; team page entry
- Mobile: `shared/.../data/repository/NdsRepository*.kt` (client), `composeApp/.../ui/nds/NdsImportScreen.kt`, `NdsImportViewModel.kt`, `ui/util/DocumentPicker.kt` (+ `.android.kt`, `.ios.kt`)

---

### Task 1: Server — NdsMemberMatcher (pure function + unit tests)

**Files:**
- Create: `server/src/main/kotlin/ch/teamorg/infra/nds/NdsMemberMatcher.kt`
- Test: `server/src/test/kotlin/ch/teamorg/nds/NdsMemberMatcherTest.kt`

**Interfaces:**
- Produces (used by Task 2's parse endpoint):

```kotlin
data class MatchCandidateUser(          // input snapshot of a team member
    val userId: UUID,
    val firstName: String,
    val lastName: String,
    val birthDate: LocalDate?,          // from nds_members if linked, else null
    val linkedNdsIdentity: Triple<String, String, LocalDate?>? // (last,first,birth) of the nds_members row already linked to this user, or null
)
data class MemberSuggestion(
    val rowKey: String,                 // "L:<lastName>|<firstName>" or "T:<...>" — section prefix + normalized names
    val candidates: List<Candidate>,    // sorted best-first, max 5
    val preselectedUserId: UUID?,       // unique HIGH match only
    val alreadyLinkedUserId: UUID?      // locked row
) { data class Candidate(val userId: UUID, val displayName: String, val score: String /* HIGH|MEDIUM */, val birthdateMatch: Boolean) }

object NdsMemberMatcher {
    fun rowKey(funktion: String, lastName: String, firstName: String): String
    fun suggest(rows: List<NdsMemberInput>, teamUsers: List<MatchCandidateUser>): List<MemberSuggestion>
}
```

**Algorithm (implement exactly):**
- `normalize(s)`: lowercase, trim, replace ä→ae ö→oe ü→ue é/è/ê→e à/â→a ß→ss, collapse whitespace.
- Per row × candidate: exact `normalize(last)+normalize(first)` equality → HIGH. Else Levenshtein ≤ 2 on one name with the other exact → MEDIUM. If both birthdates non-null and equal: MEDIUM→HIGH; store `birthdateMatch=true`.
- Candidate whose `linkedNdsIdentity` equals the row's `(lastName, firstName, birthDate)` (normalized names) → `alreadyLinkedUserId`, no other candidates needed.
- `preselectedUserId`: set only when exactly ONE candidate has HIGH.
- Levenshtein: implement a plain two-row DP, no dependency.

**Steps:**
- [ ] Write failing tests in `NdsMemberMatcherTest.kt` (plain JUnit, no DB): exact match → HIGH+preselect; umlaut variant ("Lüthi" vs "Luethi") → HIGH; one-char typo → MEDIUM, no preselect; typo + equal birthdate → HIGH+preselect+birthdateMatch; two users identical name → both HIGH, preselect null; linked identity → alreadyLinkedUserId set, preselect null; rowKey stable + section-prefixed.
- [ ] Run `./gradlew :server:test --tests '*NdsMemberMatcherTest*'` → FAIL (class missing).
- [ ] Implement `NdsMemberMatcher.kt` per algorithm.
- [ ] Run same command → PASS.
- [ ] Commit `feat(server): NDS member matching with suggestions`.

### Task 2: Server — parse endpoint v2 (file subsets, suggestions, series, conflicts)

**Files:**
- Create: `server/src/main/kotlin/ch/teamorg/infra/nds/NdsImportPlanner.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/domain/models/Nds.kt`, `server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt`
- Test: `server/src/test/kotlin/ch/teamorg/nds/NdsImportPlannerTest.kt`, extend `server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt`

**Interfaces (add to Nds.kt, all `@Serializable`, UUIDs as String in DTOs):**

```kotlin
data class NdsSeries(val seriesKey: String, val weekday: Int?, val symbol: String, val durationMin: Int?, val dates: List<LocalDate>, val count: Int)
// seriesKey = "<weekday0to6orX>-<SYMBOL>-<durationMinOr0>", one-offs get key "single-<date>-<SYMBOL>"
data class NdsConflictDate(val date: LocalDate, val existingEventId: String, val existingEventTitle: String, val existingEventStart: String /* ISO instant */)
data class NdsConflictGroup(val seriesKey: String, val dates: List<NdsConflictDate>)
data class NdsParseResponse(
    val anwesenheitsliste: ParsedAnwesenheitsliste?,   // null when slot absent
    val persons: List<NdsMemberInput>,                 // union of teilnehmende+leiter files
    val memberSuggestions: List<MemberSuggestionDto>,  // string-UUID mirror of MemberSuggestion
    val series: List<NdsSeries>,
    val conflicts: List<NdsConflictGroup>,
    val linkedTeamId: String?, val linkedTeamName: String?
)
```

**NdsImportPlanner** (class, injected via Koin like NdsEventImporter):
- `fun series(activities: List<ParsedActivity>): List<NdsSeries>` — same grouping as NdsEventImporter (weekday+symbol+duration, ≥3 → series; rest one-offs), extracted so parse and import agree. Refactor NdsEventImporter to call this (delete its inline grouping).
- `fun conflicts(teamId: UUID, series: List<NdsSeries>): List<NdsConflictGroup>` — one query: non-cancelled events of team (`EventsTable.status neq cancelled` via `EventTeamsTable`) where event date ∈ activity dates AND event type == `NdsRules.aktivitaetstypToEventType(symbolToAktivitaetstyp(symbol))`. **Exclude events with `externalSource='nds'`** (those are re-import updates, not conflicts). Group results under the owning seriesKey.

**Route changes (`POST /clubs/{clubId}/nds/parse`):**
- Multipart parts by NAME: `anwesenheitsliste`, `teilnehmende`, `leiter`; optional form field `teamId`. Any subset; none → 400 "Keine Datei hochgeladen". (Keep the legacy single-file behavior working: an unnamed file part = anwesenheitsliste.)
- Auth: club_manager(clubId) OR (teamId present AND coach(teamId) via `requireTeamRole` AND team belongs to clubId — verify club ownership, else 403).
- Suggestions computed only when a target team is known (explicit teamId or linked team): rows = AWL members mapped to `NdsMemberInput` + persons union (dedupe by rowKey, AWL wins on birthdate); teamUsers = team roster joined with `nds_members` links (write repo helper `listTeamUsersForMatching(teamId): List<MatchCandidateUser>` in NdsRepository).
- Deprecate `POST /clubs/{id}/nds/parse-roster`: keep route delegating into the same handler (backward compat), mark with comment.

**Steps:**
- [ ] Write failing `NdsImportPlannerTest.kt`: series extraction matches importer grouping (MO+MI sample → 2 series), one-offs get single keys; conflict query respects date+type, excludes cancelled and nds-source events (Testcontainers, seed via existing test helpers in NdsRoutesTest).
- [ ] Write failing route tests in NdsRoutesTest: parse with only teilnehmende CSV → 200, persons filled, no series; parse with no files → 400; coach of team parses with teamId → 200; coach of OTHER team → 403; cross-club teamId → 403; parse with teamId returns suggestions incl. preselect for an exact-name roster member.
- [ ] Run → FAIL.
- [ ] Implement planner + DTOs + route changes; refactor NdsEventImporter grouping to use `NdsImportPlanner.series`.
- [ ] `./gradlew :server:test` → PASS (full suite, regressions included).
- [ ] Commit `feat(server): NDS parse v2 — file subsets, match suggestions, series + conflicts`.

### Task 3: Server — import endpoint v2 (mappings, seriesTimes, conflictResolutions)

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt` (NdsImportRequest + handler), `server/src/main/kotlin/ch/teamorg/infra/nds/NdsEventImporter.kt`, `server/src/main/kotlin/ch/teamorg/domain/repositories/NdsRepository.kt`
- Test: extend `server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt`

**Interfaces (request additions, `@Serializable`):**

```kotlin
data class NdsMapping(val rowKey: String, val action: String /* map|create|skip */, val userId: String? = null)
data class NdsSeriesTime(val seriesKey: String, val startTime: String /* HH:mm */, val endTime: String, val location: String? = null)
data class NdsConflictResolution(val seriesKey: String, val keep: String /* teamorg|nds */, val overrides: List<NdsConflictOverride> = emptyList())
data class NdsConflictOverride(val date: LocalDate, val keep: String)
// NdsImportRequest gains: mappings, seriesTimes, conflictResolutions (all default emptyList()); parsed becomes nullable (persons-only import)
```

**Semantics (one `transaction {}` wrapping members + events; wire so a thrown exception rolls everything back — move the route's import work into a single suspend repo/service call):**
- Validation before writes (400 with German message): unknown rowKey; `map` without userId or userId not member of the CLUB; two mappings to same userId; unresolved conflict (conflict group from a fresh `planner.conflicts` run not covered by a resolution); missing seriesTime for a series importing ≥1 event; invalid HH:mm; endTime ≤ startTime.
- `NdsRepository.applyMapping(teamId, rowKey→NdsMemberInput, userId)`: upsert `nds_members` on identity key with `user_id=userId`, overwrite person_number/birth_date/funktion; if user has no `team_roles` row for team → insert role from funktion (Leiter/in→coach else player). 409 if the user is already linked to a DIFFERENT nds_members row of this team. Do NOT create provisional users on `map`. `create` → existing `upsertOne`+`ensureUserAndRole` path. `skip` → nothing.
- `NdsEventImporter.import` new signature: `import(teamId, parsed, attendanceMode, createdBy, seriesTimes: Map<String, NdsSeriesTime>, resolutions: Map<LocalDate, String /* keep decision per conflict date */>)`.
  - Effective keep per date = override if present else group keep; dates with keep=teamorg → excluded from event creation; their attendance target = the existing (conflicting) event id (extend the `dateToEventFull` map with them).
  - keep=nds dates → `EventsTable.update` existing conflicting event `status = EventStatus.cancelled` (suppress notifications: direct update, not the route that notifies) before inserting the NDS event.
  - Start/end from the series' NdsSeriesTime (`act.date.atTime(LocalTime.parse(startTime))`), location from it; series templates use the same times. Remove `PLACEHOLDER_START`.
- Auth like parse (coach of target team OR club_manager).

**Steps:**
- [ ] Write failing route tests: map action links user + adds role + overwrites NDS fields + leaves user email/name untouched; map to user already on team keeps existing role row; skip writes nothing; double-map → 400; map foreign-club user → 400; conflict keep-teamorg → no new event, J-attendance row lands on existing event, pre-existing RSVP survives (`insertIgnore`); keep-nds → old event cancelled, new event has wizard time; per-date override wins over group; missing seriesTime → 400; persons-only import (parsed=null) creates/updates members, zero events; injected failure mid-import (e.g. invalid second mapping) → NO partial rows (rollback assert); re-import preserves mapping.
- [ ] Run → FAIL.
- [ ] Implement.
- [ ] `./gradlew :server:test` → PASS full suite (export pre-flight, claim, one-team-per-Angebot regressions green).
- [ ] Commit `feat(server): NDS import v2 — mappings, wizard times, conflict resolution`.

### Task 4: Admin web — 4-step wizard

**Files:**
- Modify: `admin/src/lib/components/NdsImportDialog.svelte` (becomes wizard shell)
- Create: `admin/src/lib/components/NdsMappingStep.svelte`, `admin/src/lib/components/NdsEventsStep.svelte`
- Modify: team page `admin/src/routes/(shell)/app/teams/[teamId]/+page.svelte` (add „NDS Import" button beside export, dialog with fixed teamId), manage page keeps existing entry
- Modify: `admin/src/routes/(shell)/manage/[clubId]/nds/parse/+server.ts` proxy if request shape changes (check how upload is proxied; team-scoped calls may need a sibling proxy under app/teams)
- Test: `admin/src/lib/components/*.test.ts` (Vitest — check existing setup; if none exists, add minimal vitest config per repo conventions)

**Wizard (Svelte 5 runes, inline German):**
1. Dateien — three labeled slots (Teilnehmende `.csv`, Leiter/innen `.xlsx`, Anwesenheitsliste `.xlsx` badge „empfohlen"), ≥1 to continue; team select on manage entry (existing teams of club + „Neues Team" name field), fixed on team entry. Submit → parse → store `NdsParseResponse`.
2. Mitglieder-Zuordnung — table: Funktion | Name | Geburtsdatum | Personennummer | Zuordnung. Zuordnung select per row: preselected candidate / other candidates ("Vorschlag" group) / all team members / „Neuen Nutzer erstellen" / „Überspringen". `alreadyLinkedUserId` rows render locked text „bereits verknüpft". State: `Map<rowKey, {action, userId?}>` default: preselected→map, else create.
3. Events & Konflikte (skip when no AWL) — per series card: „MO · Training · 90 min · 18 Termine", time inputs (start; end prefilled start+Dauer, editable), Ort input. Conflict groups: radio „TeamOrg behalten" (default) / „NDS übernehmen", expandable date list with per-date radio override; keep-NDS warning shows existing event title/time. Gate: continue disabled until all times set for importing series.
4. Bestätigen — counts summary; „Importieren" posts assembled `NdsImportRequest` v2; success → existing post-import view (roster table refresh).

**Steps:**
- [ ] Write failing Vitest tests: mapping default state derivation from suggestions (preselect→map, none→create, locked row excluded); events step gating (missing time blocks, fully-keep-teamorg series needs no time); conflict override state assembly into request payload.
- [ ] Run `cd admin && npx vitest run` → FAIL.
- [ ] Implement wizard + steps + team-page entry.
- [ ] Vitest → PASS; `npm run check` → 0 errors.
- [ ] Commit `feat(admin): NDS import wizard — mapping, conflicts, times`.

### Task 5: Admin web — Playwright E2E

**Files:**
- Create: `admin/e2e/nds-import.spec.ts` (follow existing E2E conventions in `admin/e2e/`), fixtures under `admin/e2e/fixtures/` (generate minimal xlsx/csv fixtures in a setup script with a tiny xlsx writer already used in tests, or check `server/src/test/resources` for existing sample files and copy)
- Test: itself

**Scenarios (against local dev stack per existing E2E setup):** full wizard into existing team (map one member, create one, skip one; set times; resolve one conflict each way; assert roster + events); members-only import (Teilnehmende CSV only); assert badge counts on Bestätigen.

**Steps:**
- [ ] Check `admin/e2e/` conventions + how server fixtures are seeded; write spec + fixtures.
- [ ] Run per repo E2E command → PASS (if the suite requires the prod-readonly env, mark local-only per existing conventions).
- [ ] Commit `test(admin): NDS import wizard E2E`.

### Task 6: Mobile — shared DTOs + repository + document picker

**Files:**
- Create: `shared/src/commonMain/kotlin/ch/teamorg/domain/NdsImport.kt` (kotlinx-serializable mirrors: NdsParseResponse, NdsImportRequest v2 + nested DTOs — dates as ISO strings)
- Create: `shared/src/commonMain/kotlin/ch/teamorg/repository/NdsImportRepository.kt` + `shared/src/commonMain/kotlin/ch/teamorg/data/repository/NdsImportRepositoryImpl.kt` — `suspend fun parse(clubId, teamId, files: List<NdsFilePart{slot, fileName, bytes}>): Result<NdsParseResponse>` (Ktor `submitFormWithBinaryData`, part names = slot names, mime by extension: csv→text/csv, xlsx→application/vnd.openxmlformats-officedocument.spreadsheetml.sheet); `suspend fun import(clubId, request): Result<NdsImportResponse>`; register in DI (mirror TeamRepository pattern incl. Koin module).
- Create: `composeApp/src/commonMain/kotlin/ch/teamorg/ui/util/DocumentPicker.kt`:
```kotlin
/** Opens a document picker for the given extensions; returns bytes + filename, or null result when cancelled. */
@Composable
expect fun rememberDocumentPickerLauncher(extensions: List<String>, onResult: (bytes: ByteArray, fileName: String) -> Unit): () -> Unit
```
- Create: `composeApp/src/androidMain/.../DocumentPicker.android.kt` — `ActivityResultContracts.OpenDocument()` with mime types (csv: `text/*` + `text/comma-separated-values`, xlsx mime above), read via contentResolver.
- Create: `composeApp/src/iosMain/.../DocumentPicker.ios.kt` — `UIDocumentPickerViewController(forOpeningContentTypes:)` with `UTType` from extensions, delegate reads NSData→ByteArray (mirror ImagePicker.ios delegate pattern).

**Steps:**
- [ ] Implement DTOs + repo + DI + picker expect/actual.
- [ ] `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64` → PASS.
- [ ] Commit `feat(mobile): NDS import data layer + document picker`.

### Task 7: Mobile — wizard screens + ViewModel

**Files:**
- Create: `composeApp/src/commonMain/kotlin/ch/teamorg/ui/nds/NdsImportViewModel.kt`, `NdsImportScreen.kt` (+ step composables in same file or `NdsImportSteps.kt`)
- Modify: navigation (`Screen` sealed class + `AppNavigation.kt`) — `Screen.NdsImport(teamId, clubId)`; entry button on team screen near existing NDS UI (find via grep "NDS" in composeApp)
- Modify: `composeApp/.../di/UiModule.kt` — factory
- Test: `composeApp/src/commonTest/kotlin/ch/teamorg/ui/nds/NdsImportViewModelTest.kt` (follow existing VM test setup; fake repository)

**ViewModel state machine:** `data class NdsImportState(step: Int, files: Map<String, PickedFile>, parse: NdsParseResponse?, mappings: Map<String, MappingChoice>, seriesTimes: Map<String, SeriesTimeInput>, resolutions: Map<String, ResolutionChoice>, isLoading, error, done)`. Derivations identical to web: mapping defaults from suggestions; step-3 gating (all importing series have valid times, all conflicts resolved — default teamorg counts as resolved); payload assembly mirrors web. Steps as horizontal pager or simple `when(step)`; German strings; series time via existing `EventTimePickerDialog` pattern (copy the M3 TimePicker dialog usage from CreateEditEventScreen); conflict groups as expandable cards, radio rows.

**Steps:**
- [ ] Write failing VM tests: default mapping derivation; gating logic (missing time blocks, keep-teamorg-only series exempt); payload assembly (map/create/skip + overrides); parse error surfaces + retry keeps files.
- [ ] `./gradlew :composeApp:testDebugUnitTest` → FAIL.
- [ ] Implement VM + screens + navigation + DI.
- [ ] `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64` → PASS.
- [ ] Commit `feat(mobile): NDS import wizard for coaches`.

### Task 8: Quality pass (controller does this, not a subagent implementer)

- [ ] PII gate: `grep -rn "personNumber\|person_number\|PERSONENNUMMER" server/src/main/kotlin --include='*.kt' | grep -i "log"` → empty.
- [ ] Full gates: `./gradlew :server:test :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64` + `cd admin && npm run check` + Vitest + E2E.
- [ ] Adversarial verifier subagent on the whole branch (correctness, transaction/rollback, no-IDOR on every new parameter, conflict-resolution edge cases).
- [ ] Fix findings; re-verify.
- [ ] Update `docs/nds-import-export-design.md` with a short "v2 (2026-07-30)" section pointing to the spec; commit.

## Unresolved questions

None — spec approved 2026-07-30.
