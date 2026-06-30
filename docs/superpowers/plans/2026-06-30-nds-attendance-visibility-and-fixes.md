# NDS Attendance Visibility & UX Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make imported NDS attendance visible (server count → web/mobile UI), fix events-list scroll restoration, add a Nutzergruppe help text, and verify invite-redeem claims an NDS-imported player on both web and mobile.

**Architecture:** Server attaches a batched `presentCount` (documented `attendance_records` with status `present`) to event DTOs and returns an attendance count from NDS import. Web and mobile show an NDS-only presence indicator gated on `externalSource == 'nds'`. The web events list persists/restores scroll via SvelteKit `snapshot`. Mobile reuses existing event/attendance screens.

**Tech Stack:** Ktor + Exposed + Postgres (server), SvelteKit + Svelte 5 runes + Tailwind (admin web), Kotlin Multiplatform + Compose (mobile, `composeApp` + `shared`), kotlinx.serialization.

## Global Constraints

- Branch: `feat/nds-import-followups`. Git: **merge only, never rebase**; no AI authorship trailers; commit per logical unit.
- Web gate before push: `npm run check` clean (run in `admin/`).
- Server/shared/mobile gate before push: `./gradlew check` green.
- Present indicator shows **only** on events with `externalSource == 'nds'`.
- Security (no-IDOR): any new endpoint verifies the caller may read the target event (member of the event's team(s)) — same rule as the existing `GET /events/{id}/attendance`.
- The shared mobile `Event` model (`shared/.../domain/Event.kt`) is separate from the server's `domain.models.Event`; both must gain the new fields for mobile to deserialize them.
- DB time convention: events store start/end as UTC-as-local (see `EventRepositoryImpl`); do not change it.

---

## File Structure

**Server**
- Modify: `server/src/main/kotlin/ch/teamorg/domain/models/Event.kt` — add `presentCount: Int = 0` to the event DTO.
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/EventRepositoryImpl.kt` — batched present-count attach for list + single fetch.
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepository.kt` (+ `AttendanceRepositoryImpl.kt`) — add `presentCounts(eventIds): Map<UUID,Int>`.
- Modify: `server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt` — `NdsImportResponse.attendanceImported`.
- Modify: `server/src/main/kotlin/ch/teamorg/infra/nds/NdsEventImporter.kt` — return events + attendance counts.
- Test: `server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt`, `server/src/test/kotlin/ch/teamorg/nds/NdsTestFixtures.kt`.

**Web (`admin/`)**
- Modify: `admin/src/lib/server/events.ts` — `AppEvent.presentCount`.
- Modify: `admin/src/routes/(shell)/app/events/+page.svelte` (scroll snapshot + presence chip), `+page.server.ts` (no change needed if presentCount is on the event).
- Modify: `admin/src/routes/(shell)/app/events/[id]/+page.svelte` — presence line.
- Modify: `admin/src/lib/components/NdsImportDialog.svelte` — done-screen count, checkbox copy, NG tooltip.

**Mobile (`composeApp` + `shared`)**
- Modify: `shared/src/commonMain/kotlin/ch/teamorg/domain/Event.kt` — add `externalSource: String? = null`, `presentCount: Int = 0`.
- Modify: `composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventCard.kt` and `EventDetailScreen.kt` — NDS presence indicator.

---

## Feature #2 — Attendance import count + presence visibility

### Task 1: Server — batched present-count query

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepository.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepositoryImpl.kt`
- Test: `server/src/test/kotlin/ch/teamorg/routes/AttendanceRoutesTest.kt` (or a new `AttendancePresentCountTest.kt` if that file is absent)

**Interfaces:**
- Produces: `suspend fun presentCounts(eventIds: List<UUID>): Map<UUID, Int>` on `AttendanceRepository` — counts `attendance_records` rows with `status = present` grouped by `event_id`; events with no present rows are absent from the map.

- [ ] **Step 1: Write the failing test**

Add to the chosen test file (mirror the existing integration-test harness — `withTeamorgTestApplication`, `register`, create club/team/event, insert a present record). Minimal form:

```kotlin
@Test
fun `presentCounts groups present records by event`() = withTeamorgTestApplication {
    val repo = org.koin.java.KoinJavaComponent.getKoin().get<ch.teamorg.domain.repositories.AttendanceRepository>()
    // Seed: create event E with 2 present records + 1 declined; event F with 0.
    val (eventE, eventF, userA, userB, coach) = seedTwoEventsWithRecords() // helper added in this test file
    val counts = repo.presentCounts(listOf(eventE, eventF))
    assertEquals(2, counts[eventE])
    assertEquals(null, counts[eventF]) // no present rows → not in map
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.*resentCount*'`
Expected: FAIL — `presentCounts` unresolved reference.

- [ ] **Step 3: Implement the method**

In `AttendanceRepository.kt` interface add:

```kotlin
suspend fun presentCounts(eventIds: List<UUID>): Map<UUID, Int>
```

In `AttendanceRepositoryImpl.kt` (import `org.jetbrains.exposed.sql.count`, `ch.teamorg.db.tables.AttendanceRecordsTable`, `ch.teamorg.db.tables.RecordStatus`):

```kotlin
override suspend fun presentCounts(eventIds: List<UUID>): Map<UUID, Int> = transaction {
    if (eventIds.isEmpty()) return@transaction emptyMap()
    val cnt = AttendanceRecordsTable.eventId.count()
    AttendanceRecordsTable
        .select(AttendanceRecordsTable.eventId, cnt)
        .where { (AttendanceRecordsTable.eventId inList eventIds) and (AttendanceRecordsTable.status eq RecordStatus.present) }
        .groupBy(AttendanceRecordsTable.eventId)
        .associate { it[AttendanceRecordsTable.eventId] to it[cnt].toInt() }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.*resentCount*'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepository.kt server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepositoryImpl.kt server/src/test/kotlin/ch/teamorg/routes/
git commit -m "feat(server): batched present-count query for events"
```

---

### Task 2: Server — attach presentCount to event DTOs

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/domain/models/Event.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/EventRepositoryImpl.kt`
- Test: `server/src/test/kotlin/ch/teamorg/routes/EventRoutesTest.kt` (extend; or new `EventPresentCountTest.kt`)

**Interfaces:**
- Consumes: `AttendanceRepository.presentCounts` (Task 1).
- Produces: event JSON for `GET /users/me/events` and `GET /events/{id}` includes `presentCount: Int` (0 when none).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `event payload carries presentCount`() = withTeamorgTestApplication {
    val (token, eventId) = seedEventWithOnePresentRecord() // helper in this test file
    val ewt = createJsonClient().get("/events/$eventId") {
        header(HttpHeaders.Authorization, "Bearer $token")
    }.body<ch.teamorg.domain.models.EventWithTeams>() // server-side model
    assertEquals(1, ewt.event.presentCount)
}
```

(If the server model type name differs, use the exact type returned by `GET /events/{id}` — check `EventRoutes.kt` response type.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests '*PresentCount*'`
Expected: FAIL — `presentCount` not a member / equals 0 while expecting 1.

- [ ] **Step 3: Add the field**

In `server/.../domain/models/Event.kt` add to the event data class (near `externalSource`):

```kotlin
val presentCount: Int = 0,
```

- [ ] **Step 4: Attach in the repository**

In `EventRepositoryImpl.kt`, inject/obtain `AttendanceRepository` (follow how other repos are wired; if cross-repo calls aren't the pattern, add a private `presentCountFor(eventIds)` using the same Exposed query as Task 1 directly in this file to avoid a dependency cycle). Then in the methods backing `GET /users/me/events` and `GET /events/{id}`, after building the `Event`/list:

```kotlin
// list path: after building `events: List<Event>`
val counts = presentCountFor(events.map { UUID.fromString(it.id) })
val withCounts = events.map { it.copy(presentCount = counts[UUID.fromString(it.id)] ?: 0) }
```

```kotlin
// single path: after building `event: Event`
val n = presentCountFor(listOf(UUID.fromString(event.id)))[UUID.fromString(event.id)] ?: 0
event.copy(presentCount = n)
```

Where `presentCountFor` is the same grouped query as Task 1 (reuse `AttendanceRepository.presentCounts` if injectable; otherwise inline).

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :server:test --tests '*PresentCount*'`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/domain/models/Event.kt server/src/main/kotlin/ch/teamorg/domain/repositories/EventRepositoryImpl.kt server/src/test/kotlin/ch/teamorg/routes/
git commit -m "feat(server): include presentCount in event payloads"
```

---

### Task 3: Server — return attendanceImported from NDS import

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/infra/nds/NdsEventImporter.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt`
- Test: `server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt`

**Interfaces:**
- Produces: `NdsEventImporter.import(...)` returns `NdsImportCounts(eventsCreated: Int, attendanceImported: Int)`; `NdsImportResponse` gains `attendanceImported: Int`.

- [ ] **Step 1: Update the existing import test to assert the count**

In `NdsRoutesTest.kt`, in `import creates team members provisional users events series and attendance`, add after the existing assertions:

```kotlin
assertEquals(6, res.attendanceImported)
```

(Synthetic fixture has 6 `J` marks — see the existing `presentCount` assertion of 6.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`
Expected: FAIL — `attendanceImported` not a member of `NdsImportResponse`.

- [ ] **Step 3: Implement the count**

In `NdsEventImporter.kt`, add a result type and count inserted records:

```kotlin
data class NdsImportCounts(val eventsCreated: Int, val attendanceImported: Int)
```

Change `import(...)` to return `NdsImportCounts`. Track a counter where attendance is written:

```kotlin
var attendance = 0
// inside the attendanceMode == "keep" loop, on each successful insertIgnore:
val inserted = AttendanceRecordsTable.insertIgnore { /* …existing… */ }.insertedCount
attendance += inserted
// at the end:
NdsImportCounts(eventsCreated = created, attendanceImported = attendance)
```

(`insertIgnore { }.insertedCount` is 0 when the row already existed — correct for idempotent re-import.)

In `NdsRoutes.kt`: change the call site and response:

```kotlin
data class NdsImportResponse(
    val teamId: String,
    val membersImported: Int,
    val eventsCreated: Int,
    val attendanceImported: Int = 0
)
// …
val counts = if (request.importEvents)
    ndsEventImporter.import(teamId, parsed, request.attendanceMode, callerId)
else NdsImportCounts(0, 0)
call.respond(HttpStatusCode.OK, NdsImportResponse(
    teamId = teamId.toString(),
    membersImported = ndsRepository.listMembers(teamId).size,
    eventsCreated = counts.eventsCreated,
    attendanceImported = counts.attendanceImported
))
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/infra/nds/NdsEventImporter.kt server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt
git commit -m "feat(server): return attendanceImported count from NDS import"
```

---

### Task 4: Server — realistic regression fixture

**Files:**
- Modify: `server/src/test/kotlin/ch/teamorg/nds/NdsTestFixtures.kt`
- Test: `server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt`

**Interfaces:**
- Produces: `NdsTestFixtures.largeAnwesenheitslisteBytes(angebot: String = "…"): ByteArray` — many weeks (≥12 activity columns spanning MO/MI), ≥10 participants + 2 leaders, participant birthdates present, multi-word names, `J` marks scattered. Activity columns starting at sheet column index 5 (to mirror the real file's leading metadata columns).

- [ ] **Step 1: Write the failing end-to-end test**

```kotlin
@Test
fun `large realistic import writes the expected present record total`() = withTeamorgTestApplication {
    val mgr = register("nds_large@example.com"); promoteToSuperAdmin(mgr.userId)
    val clubId = createClub(mgr.token, "LargeClub")
    val parsed = parseFile(mgr.token, clubId, NdsTestFixtures.largeAnwesenheitslisteBytes("large-1"))
        .body<ParsedAnwesenheitsliste>()
    val expected = parsed.members.sumOf { it.attendedDates.size }
    assertTrue(expected > 20, "fixture should have many marks; got $expected")
    val res = createJsonClient().post("/clubs/$clubId/nds/import") {
        header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        contentType(ContentType.Application.Json)
        setBody(NdsImportRequest(createTeamName = "Large", nutzergruppe = "NG2", parsed = parsed, importEvents = true, attendanceMode = "keep"))
    }.body<NdsImportResponse>()
    assertEquals(expected, res.attendanceImported)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`
Expected: FAIL — `largeAnwesenheitslisteBytes` unresolved.

- [ ] **Step 3: Add the fixture builder**

In `NdsTestFixtures.kt`, copy the structure of the existing `anwesenheitslisteBytes` builder but: place metadata in column A/B; start activity columns at POI index 5; emit ≥12 date columns (alternating MO/MI weekly) with symbol `T`, duration `1,5`; add a `Leiter/-in(2):` header + 2 leaders and a `Teilnehmer/-in(N):` header + ≥10 participants with birthdates in the participant birthdate column; scatter `J` marks. (Use the existing builder's POI `XSSFWorkbook` cell-writing helpers verbatim — only the row/column counts change.)

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/src/test/kotlin/ch/teamorg/nds/NdsTestFixtures.kt server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt
git commit -m "test(server): realistic multi-week NDS import regression"
```

---

### Task 5: Web — done-screen count + checkbox copy

**Files:**
- Modify: `admin/src/lib/components/NdsImportDialog.svelte`

**Interfaces:**
- Consumes: `NdsImportResponse.attendanceImported` (Task 3). Add `attendanceImported: number` to the local `ImportResult` interface.

- [ ] **Step 1: Extend the result interface + done screen**

In the `<script>` `ImportResult` interface add:

```ts
attendanceImported: number;
```

In the `step === 'done'` block, add a list item after the events line:

```svelte
<li>{result.attendanceImported} Anwesenheiten übernommen</li>
```

- [ ] **Step 2: Clarify the checkbox label**

Replace the checkbox label text "Bereits erfasste Anwesenheiten übernehmen" with:

```svelte
Im NDS-Sheet mit «J» markierte Anwesenheiten als dokumentierte Präsenz importieren
```

and add a one-line helptext under it:

```svelte
<p class="text-[12px] text-on-surface-variant">Erscheint danach in der Anwesenheitskontrolle und als «anwesend»-Hinweis am Termin.</p>
```

- [ ] **Step 3: Verify check passes**

Run (in `admin/`): `npm run check`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add admin/src/lib/components/NdsImportDialog.svelte
git commit -m "feat(web): show imported attendance count + clearer checkbox"
```

---

### Task 6: Web — presence chip on event list + detail (NDS only)

**Files:**
- Modify: `admin/src/lib/server/events.ts`
- Modify: `admin/src/routes/(shell)/app/events/+page.svelte`
- Modify: `admin/src/routes/(shell)/app/events/[id]/+page.svelte`

**Interfaces:**
- Consumes: `event.presentCount` (Task 2), `event.externalSource` (existing).

- [ ] **Step 1: Add the type field**

In `admin/src/lib/server/events.ts`, add to `AppEvent`:

```ts
presentCount: number;
```

- [ ] **Step 2: List chip**

In `+page.svelte`, inside the event card header `<div class="flex items-center gap-2">` (next to the existing badges), add:

```svelte
{#if event.externalSource === 'nds' && event.presentCount > 0}
    <span class="rounded-full bg-success-container px-2 py-0.5 text-[10px] font-semibold text-success">
        {event.presentCount} anwesend
    </span>
{/if}
```

- [ ] **Step 3: Detail line**

In `[id]/+page.svelte`, in the header info block (after the location line), add:

```svelte
{#if data.event.externalSource === 'nds' && data.event.presentCount > 0}
    <p class="flex items-center gap-2 text-on-surface-variant">
        <ClipboardCheck size={16} /> {data.event.presentCount} dokumentierte Anwesenheiten
    </p>
{/if}
```

(`ClipboardCheck` is already imported in that file.)

- [ ] **Step 4: Verify**

Run (in `admin/`): `npm run check`
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add admin/src/lib/server/events.ts 'admin/src/routes/(shell)/app/events/+page.svelte' 'admin/src/routes/(shell)/app/events/[id]/+page.svelte'
git commit -m "feat(web): show NDS documented-presence on event list + detail"
```

---

### Task 7: Mobile — add fields to shared Event + presence indicator

**Files:**
- Modify: `shared/src/commonMain/kotlin/ch/teamorg/domain/Event.kt`
- Modify: `composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventCard.kt`
- Modify: `composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventDetailScreen.kt`
- Test: `shared/src/commonTest/kotlin/ch/teamorg/...` event deserialization test (extend existing if present)

**Interfaces:**
- Consumes: server event payload now carries `externalSource`, `presentCount` (Tasks 2 + existing server model).
- Produces: `Event.externalSource: String?`, `Event.presentCount: Int` available to mobile UI.

- [ ] **Step 1: Failing deserialization test**

Add a test asserting a JSON event with `"externalSource":"nds","presentCount":5` deserializes those fields (use the existing shared serialization test pattern; if none, add `EventSerializationTest.kt` in `shared/src/commonTest`):

```kotlin
@Test
fun `event decodes externalSource and presentCount`() {
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    val e = json.decodeFromString<Event>("""{"id":"1","title":"T","type":"training","startAt":"2026-04-27T16:00:00Z","endAt":"2026-04-27T17:30:00Z","status":"active","createdBy":"u","createdAt":"2026-04-27T16:00:00Z","updatedAt":"2026-04-27T16:00:00Z","externalSource":"nds","presentCount":5}""")
    assertEquals("nds", e.externalSource)
    assertEquals(5, e.presentCount)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:jvmTest --tests '*EventSerialization*'` (or the module's test task)
Expected: FAIL — fields unresolved / default 0.

- [ ] **Step 3: Add the fields**

In `shared/.../domain/Event.kt`, add to `data class Event`:

```kotlin
val externalSource: String? = null,
val presentCount: Int = 0,
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :shared:jvmTest --tests '*EventSerialization*'`
Expected: PASS

- [ ] **Step 5: Mobile UI indicator**

In `EventCard.kt`, near the `TypeChip`/title row, add a small chip using the existing theme:

```kotlin
if (ewt.event.externalSource == "nds" && ewt.event.presentCount > 0) {
    Text(
        text = "${ewt.event.presentCount} anwesend",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.extendedColors.success, // use existing success color accessor
        modifier = Modifier
            .clip(PillShape)
            .background(MaterialTheme.extendedColors.successContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
```

(If `extendedColors.success`/`successContainer` names differ, use the closest existing success tokens in `ui/theme`.)

In `EventDetailScreen.kt`, add an equivalent row in the info section gated on the same condition.

- [ ] **Step 6: Build the mobile module**

Run: `./gradlew :composeApp:assembleDebug` (or `:composeApp:compileCommonMainKotlinMetadata` if faster)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/ch/teamorg/domain/Event.kt shared/src/commonTest composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventCard.kt composeApp/src/commonMain/kotlin/ch/teamorg/ui/events/EventDetailScreen.kt
git commit -m "feat(mobile): show NDS documented-presence on event card + detail"
```

---

## Feature #1 — Nutzergruppe help text (web)

### Task 8: NG tooltip in import dialog

**Files:**
- Modify: `admin/src/lib/components/NdsImportDialog.svelte`

- [ ] **Step 1: Add a help disclosure**

Next to the Nutzergruppe `<select>` label, add a small toggle + collapsible text (no new dependency; use a `$state` boolean):

```svelte
<button type="button" onclick={() => (showNgHelp = !showNgHelp)} class="text-[12px] text-primary underline">Was ist das?</button>
{#if showNgHelp}
    <p class="text-[12px] text-on-surface-variant">
        Die J+S-Nutzergruppe deines Angebots bestimmt die erlaubten Trainingsdauern. Beim NDS-Export wird die Dauer dagegen geprüft und bei Bedarf auf den nächsten erlaubten Wert gerundet. Im Zweifel die in der NDS registrierte Nutzergruppe wählen.
    </p>
{/if}
```

Add `let showNgHelp = $state(false);` to the script.

- [ ] **Step 2: Verify**

Run (in `admin/`): `npm run check`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add admin/src/lib/components/NdsImportDialog.svelte
git commit -m "feat(web): explain Nutzergruppe in NDS import dialog"
```

---

## Feature #3 — Events-list scroll restoration (web)

### Task 9: Reproduce, then restore scroll on `/app/events`

**Files:**
- Modify: `admin/src/routes/(shell)/app/events/+page.svelte`
- (Optional repro) use the `playwright-cli` skill against the dev server.

**Interfaces:** none (UI-only).

- [ ] **Step 1: Reproduce the bug**

Start the web dev server and reproduce: scroll the events list, open an event, press back — observe it lands at top. (Use `playwright-cli`: navigate, `window.scrollTo(0, 800)`, click an event link, `goBack`, read `window.scrollY` — expect `0` while pre-nav was `800`.) Record the pre/post scrollY in the task notes.

- [ ] **Step 2: Add a `snapshot` export**

In `+page.svelte` `<script>`, add SvelteKit's page snapshot to capture/restore window scroll:

```ts
import { tick } from 'svelte';

export const snapshot = {
    capture: () => window.scrollY,
    restore: async (y: number) => {
        await tick();
        requestAnimationFrame(() => window.scrollTo(0, y));
    }
};
```

(`snapshot` must be a module-level `export` from the page component; Svelte 5 supports exporting it from the instance `<script>`.)

- [ ] **Step 3: Verify the fix**

Repeat the Playwright repro from Step 1. Expected: after `goBack`, `window.scrollY` ≈ the pre-nav value (allow ±2px).

- [ ] **Step 4: Verify check**

Run (in `admin/`): `npm run check`
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add 'admin/src/routes/(shell)/app/events/+page.svelte'
git commit -m "fix(web): restore events-list scroll position on back navigation"
```

---

## Feature #4 — Verify invite-redeem claims an NDS player (web + mobile)

### Task 10: Confirm + lock the mobile redeem→claim path

**Files:**
- Test (server): `server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt` (already has `member invite redeem claims the roster member and moves attendance` — backend-level; covers any client).
- Read: `composeApp/.../ui/invite/*`, `shared/.../repository/InviteRepository*`, `DeepLinkHandler.kt` to confirm the mobile redeem calls the same `POST /invites/{token}/redeem` endpoint.

**Interfaces:** none new — verification task.

- [ ] **Step 1: Confirm the mobile redeem endpoint**

Read the mobile invite redeem path and confirm it calls `POST /invites/{token}/redeem` (the same endpoint the backend test exercises). Note the exact file/function in the task notes. If mobile uses a different endpoint or skips redeem, STOP and escalate (out of plan scope).

- [ ] **Step 2: Strengthen the backend regression (NDS-member-specific)**

The existing test already asserts claim + attendance move. Add one assertion that the claimed member's row is no longer provisional and the real user holds the team role:

```kotlin
val role = transaction {
    ch.teamorg.db.tables.TeamRolesTable.selectAll()
        .where { (ch.teamorg.db.tables.TeamRolesTable.userId eq realUserId) and (ch.teamorg.db.tables.TeamRolesTable.teamId eq teamId) }
        .map { it[ch.teamorg.db.tables.TeamRolesTable.role] }.singleOrNull()
}
assertEquals("player", role)
```

- [ ] **Step 3: Run to verify**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt
git commit -m "test(server): assert redeem claims NDS member and assigns real-user role"
```

---

## Final verification

- [ ] **Server/shared/mobile:** `./gradlew check` green.
- [ ] **Web:** `cd admin && npm run check` clean.
- [ ] Manual smoke (optional): import the real `20260626_Anwesenheitsliste_4037090.xlsx` via the web dialog with the attendance checkbox ON; confirm the done screen reports a non-zero "Anwesenheiten übernommen" and an NDS event shows the "N anwesend" chip.

---

## Self-review notes (coverage vs. spec)

- #1 → Task 8. #2 → Tasks 1–7. #3 → Task 9. #4 → Task 10.
- #2 visibility on web (list + detail + done screen + checkbox) and mobile (card + detail) both covered.
- Present-count mechanism is a single batched query attached to event DTOs — no N+1 calls in the 100-event list.
- All endpoints reuse existing read-access rules; no new auth surface introduced in this plan (role-management endpoints are Plan B).
