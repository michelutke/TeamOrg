# Mobile NDS Duplicate Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a coach using the mobile app discover and merge NDS duplicate roster rows, see which accounts are import placeholders, and get actionable failure messages.

**Architecture:** Three layers, bottom-up. `shared` gains the suggestion models, a typed `LinkMemberResult` replacing the status-discarding `Result<Unit>`, and two repository methods. `TeamRosterViewModel` loads suggestions behind its existing `isCoachOrManager` gate and maps results to copy. `TeamRosterScreen` renders a banner, a merge bottom sheet, and a "Provisional" chip. The dead, uncallable `ClubMembersViewModel.linkNds` is deleted rather than migrated.

**Tech Stack:** Kotlin Multiplatform (`shared`), Compose Multiplatform (`composeApp`, shared UI for Android + iOS), Ktor client, kotlinx.serialization, Kotest matchers + `kotlin.test` + `UnconfinedTestDispatcher` for composeApp tests, `kotlin.test` + Ktor MockEngine for `shared` jvmTest.

Spec: `docs/superpowers/specs/2026-08-05-mobile-nds-duplicate-merge-design.md`

## Global Constraints

- **All new mobile copy is ENGLISH.** `composeApp` has no i18n and its existing strings are English ("Club Members", "No members found", "Add to team"). The chip reads **"Provisional"**, not "Provisorisch". Do not copy the German strings from the web admin.
- **No server changes.** Every endpoint this consumes is already implemented, tested and merged on this branch. Do not touch anything under `server/`.
- Server contract being consumed, exactly:
  - `GET /teams/{teamId}/nds/duplicate-suggestions` → `200` with a JSON array (possibly empty); coach/club_manager only, `403` otherwise.
  - `POST /teams/{teamId}/nds/members/{memberId}/link` with body `{"userId": "..."}` → `200`; `409` when the account already holds another roster row of the team OR this row is already held by a real account; `400` when the account is provisional or the userId is malformed; `404` when the member or account does not exist.
- Every new `@Serializable` field gets a default value — the KMP client sets `ignoreUnknownKeys = true` (`shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt:20`) and must tolerate server shape drift.
- `shared` test source sets do **not** have Kotest — use `kotlin.test` (`assertEquals`, `assertTrue`) there. `composeApp` tests DO use Kotest matchers (`shouldBe`, `shouldBeEmpty`, `shouldContainExactly`) with `kotlin.test` annotations; follow each source set's own convention.
- **Run gradle tasks strictly ONE AT A TIME, in the FOREGROUND.** Concurrent Testcontainers/Docker use wedged this machine's Docker daemon earlier in the session. Never background a gradle run; never start a second while one is in flight.
- Verification gates, all currently passing and all of which must keep passing: `./gradlew :composeApp:compileDebugKotlinAndroid`, `./gradlew :shared:compileKotlinIosSimulatorArm64`, `./gradlew :composeApp:testDebugUnitTest`, and for Task 1 `./gradlew :shared:jvmTest`.
- Never `git rebase`. No `Co-Authored-By` or AI-authorship trailer in commit messages.
- `commit.gpgsign=true` in this repo; if a 1Password/GPG prompt blocks a commit, retry as `git -c commit.gpgsign=false commit -m "..."` and note it.
- Work continues on branch `docs/nds-member-merge-spec`, which has an open PR (#85). Commit normally; the PR updates on push.

---

## File Structure

**`shared` — modified:**
- `shared/src/commonMain/kotlin/ch/teamorg/domain/NdsImport.kt` — add `MovableCounts`, `DuplicateCandidate`, `DuplicateSuggestion` beside the existing NDS wire models.
- `shared/src/commonMain/kotlin/ch/teamorg/domain/LinkMemberResult.kt` — **new file**, one sealed interface, mirroring `RedeemResult.kt` which lives beside it.
- `shared/src/commonMain/kotlin/ch/teamorg/repository/TeamRepository.kt` — one new method, one changed return type.
- `shared/src/commonMain/kotlin/ch/teamorg/data/repository/TeamRepositoryImpl.kt` — the two implementations.
- `shared/build.gradle.kts` — add the test-only MockEngine dependency to `jvmTest`.

**`shared` — created:**
- `shared/src/jvmTest/kotlin/ch/teamorg/repository/TeamRepositoryLinkResultTest.kt` — status→result mapping, via MockEngine. Separate file from `ClientRepositoryFlowTest.kt`, which boots a real Netty server + Postgres container and is far too heavy for this.

**`composeApp` — modified:**
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterViewModel.kt` — state fields, gated suggestion load, merge + error mapping.
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterScreen.kt` — banner, merge sheet, `Provisional` chip in `MemberItem`.
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/club/ClubMembersViewModel.kt` — delete the dead `linkNds`.
- `composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeTeamRepository.kt` — new method + typed result + call recording.
- `composeApp/src/commonTest/kotlin/ch/teamorg/ui/team/TeamRosterViewModelTest.kt` — the ViewModel tests.
- `composeApp/src/commonTest/kotlin/ch/teamorg/ui/events/EventListViewModelTest.kt` — inline fake signature only.
- `composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/events/EventScreenTestFakes.kt` — fake signature only.

---

### Task 1: Shared models, typed link result, and repository methods

Changing `linkNdsMember`'s return type breaks three test doubles at once, so they are part of this task — the module does not compile until all of them move together. Deleting the dead `ClubMembersViewModel.linkNds` belongs here too: it is a caller of the method whose signature is changing, and migrating it would create a second, unreachable merge path.

**Files:**
- Modify: `shared/src/commonMain/kotlin/ch/teamorg/domain/NdsImport.kt` (append)
- Create: `shared/src/commonMain/kotlin/ch/teamorg/domain/LinkMemberResult.kt`
- Modify: `shared/src/commonMain/kotlin/ch/teamorg/repository/TeamRepository.kt:23`
- Modify: `shared/src/commonMain/kotlin/ch/teamorg/data/repository/TeamRepositoryImpl.kt:263-270`
- Modify: `shared/build.gradle.kts:55-66` (jvmTest dependencies)
- Create: `shared/src/jvmTest/kotlin/ch/teamorg/repository/TeamRepositoryLinkResultTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeTeamRepository.kt:84-89`
- Modify: `composeApp/src/commonTest/kotlin/ch/teamorg/ui/events/EventListViewModelTest.kt:61`
- Modify: `composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/events/EventScreenTestFakes.kt:59`
- Modify: `composeApp/src/commonMain/kotlin/ch/teamorg/ui/club/ClubMembersViewModel.kt:142-148` (delete)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `ch.teamorg.domain.MovableCounts(attendance: Int = 0, subgroups: Int = 0, rules: Int = 0)`
  - `ch.teamorg.domain.DuplicateCandidate(userId: String, displayName: String, score: String)`
  - `ch.teamorg.domain.DuplicateSuggestion(memberId, lastName, firstName, birthDate: String?, personNumber: String?, funktion, candidates: List<DuplicateCandidate>, willMove: MovableCounts)`
  - `ch.teamorg.domain.LinkMemberResult` — `Success` / `Conflict` / `NotLinkable` / `Error(message: String)`
  - `TeamRepository.getDuplicateSuggestions(teamId: String): Result<List<DuplicateSuggestion>>`
  - `TeamRepository.linkNdsMember(teamId: String, memberId: String, userId: String): LinkMemberResult` (return type CHANGED from `Result<Unit>`)

- [ ] **Step 1: Add the MockEngine test dependency**

In `shared/build.gradle.kts`, inside the existing `val jvmTest by getting { dependencies { … } }` block (lines 55-66), add one line:

```kotlin
                implementation(libs.ktor.clientMock)
```

`ktor-clientMock` is already declared in `gradle/libs.versions.toml:85` and version-managed with the rest of Ktor; it is currently unused. This is a deliberate, test-only dependency addition so the status→result mapping can be tested without booting the real server.

- [ ] **Step 2: Write the failing test**

Create `shared/src/jvmTest/kotlin/ch/teamorg/repository/TeamRepositoryLinkResultTest.kt`:

```kotlin
package ch.teamorg.repository

import ch.teamorg.data.repository.TeamRepositoryImpl
import ch.teamorg.domain.LinkMemberResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Maps the /link endpoint's status codes onto [LinkMemberResult]. The endpoint returns 409 for two
 * distinct causes (target already holds another roster row; this row is already held by a real
 * account) — mobile collapses both, since the coach's next action is the same either way.
 */
class TeamRepositoryLinkResultTest {

    private fun repoReturning(status: HttpStatusCode, body: String = ""): TeamRepositoryImpl {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        return TeamRepositoryImpl(client)
    }

    @Test
    fun linkNdsMember_on200_returnsSuccess() = runTest {
        val result = repoReturning(HttpStatusCode.OK, "{}").linkNdsMember("t1", "m1", "u1")
        assertEquals(LinkMemberResult.Success, result)
    }

    @Test
    fun linkNdsMember_on409_returnsConflict() = runTest {
        val result = repoReturning(HttpStatusCode.Conflict, "\"already linked\"")
            .linkNdsMember("t1", "m1", "u1")
        assertEquals(LinkMemberResult.Conflict, result)
    }

    @Test
    fun linkNdsMember_on400_returnsNotLinkable() = runTest {
        val result = repoReturning(HttpStatusCode.BadRequest, "\"not linkable\"")
            .linkNdsMember("t1", "m1", "u1")
        assertEquals(LinkMemberResult.NotLinkable, result)
    }

    @Test
    fun linkNdsMember_onOtherStatus_returnsError() = runTest {
        val result = repoReturning(HttpStatusCode.NotFound, "\"nope\"").linkNdsMember("t1", "m1", "u1")
        assertTrue(result is LinkMemberResult.Error, "expected Error, got $result")
    }

    @Test
    fun getDuplicateSuggestions_parsesTheServerShape() = runTest {
        val body = """
            [{"memberId":"m1","lastName":"Müller","firstName":"Lara","birthDate":"2008-04-01",
              "personNumber":"123456789","funktion":"Teilnehmer/in",
              "candidates":[{"userId":"u1","displayName":"Lara Müller","score":"HIGH"}],
              "willMove":{"attendance":12,"subgroups":1,"rules":0}}]
        """.trimIndent()
        val result = repoReturning(HttpStatusCode.OK, body).getDuplicateSuggestions("t1")
        val suggestions = result.getOrThrow()
        assertEquals(1, suggestions.size)
        assertEquals("m1", suggestions[0].memberId)
        assertEquals("Lara", suggestions[0].firstName)
        assertEquals(12, suggestions[0].willMove.attendance)
        assertEquals("HIGH", suggestions[0].candidates.single().score)
    }

    @Test
    fun getDuplicateSuggestions_onEmptyArray_returnsEmptyList() = runTest {
        val result = repoReturning(HttpStatusCode.OK, "[]").getDuplicateSuggestions("t1")
        assertTrue(result.getOrThrow().isEmpty())
    }
}
```

If `TeamRepositoryImpl`'s constructor takes more than the `HttpClient` (check its declaration first — it is `class TeamRepositoryImpl(private val client: HttpClient)` at the top of the file), pass whatever it actually needs and note the adjustment in your report.

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests 'ch.teamorg.repository.TeamRepositoryLinkResultTest' --console=plain`

Expected: FAIL to compile — `LinkMemberResult`, `getDuplicateSuggestions` and `DuplicateSuggestion` do not exist yet.

- [ ] **Step 4: Add the domain models**

Append to `shared/src/commonMain/kotlin/ch/teamorg/domain/NdsImport.kt` (the file already has `@Serializable` models and the needed import):

```kotlin
/** What a merge would carry over from the placeholder. Mirrors the server's MovableCounts. */
@Serializable
data class MovableCounts(
    val attendance: Int = 0,
    val subgroups: Int = 0,
    val rules: Int = 0
)

@Serializable
data class DuplicateCandidate(
    val userId: String,
    val displayName: String,
    /** "HIGH" or "MEDIUM" — how confident the server's name match is. */
    val score: String
)

/** One imported roster row that is not yet backed by a real account, plus its candidate accounts. */
@Serializable
data class DuplicateSuggestion(
    val memberId: String,
    val lastName: String,
    val firstName: String,
    val birthDate: String? = null,
    val personNumber: String? = null,
    val funktion: String,
    val candidates: List<DuplicateCandidate> = emptyList(),
    val willMove: MovableCounts = MovableCounts()
)
```

- [ ] **Step 5: Add the sealed result**

Create `shared/src/commonMain/kotlin/ch/teamorg/domain/LinkMemberResult.kt`, following the shape of `RedeemResult.kt` in the same package:

```kotlin
package ch.teamorg.domain

/**
 * Outcome of linking an account to an imported NDS roster row.
 *
 * The server returns 409 for two distinct causes — the account already holds a different roster row of
 * this team, or this row is already held by a real (non-provisional) account. Both collapse to
 * [Conflict]: the coach's next action is the same either way (pick another account, or fix it on web).
 */
sealed interface LinkMemberResult {
    data object Success : LinkMemberResult
    data object Conflict : LinkMemberResult
    /** 400 — the account cannot be linked, e.g. it is itself an import placeholder. */
    data object NotLinkable : LinkMemberResult
    data class Error(val message: String) : LinkMemberResult
}
```

- [ ] **Step 6: Change the repository interface**

In `shared/src/commonMain/kotlin/ch/teamorg/repository/TeamRepository.kt`, add the imports `ch.teamorg.domain.DuplicateSuggestion` and `ch.teamorg.domain.LinkMemberResult`, then replace the `linkNdsMember` line (currently line 23) with:

```kotlin
    /** Imported roster rows of [teamId] that may be duplicates of a real account. Coach/manager only. */
    suspend fun getDuplicateSuggestions(teamId: String): Result<List<DuplicateSuggestion>>
    suspend fun linkNdsMember(teamId: String, memberId: String, userId: String): LinkMemberResult
```

- [ ] **Step 7: Implement both methods**

In `shared/src/commonMain/kotlin/ch/teamorg/data/repository/TeamRepositoryImpl.kt`, add the imports `ch.teamorg.domain.DuplicateSuggestion`, `ch.teamorg.domain.LinkMemberResult` and `io.ktor.client.request.get` (already present), then replace the existing `linkNdsMember` (lines 263-270) with:

```kotlin
    override suspend fun getDuplicateSuggestions(teamId: String): Result<List<DuplicateSuggestion>> = try {
        Result.success(client.get("/teams/$teamId/nds/duplicate-suggestions").body())
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun linkNdsMember(teamId: String, memberId: String, userId: String): LinkMemberResult = try {
        val r = client.post("/teams/$teamId/nds/members/$memberId/link") {
            contentType(ContentType.Application.Json)
            setBody(LinkNdsMemberRequest(userId))
        }
        when (r.status) {
            HttpStatusCode.OK, HttpStatusCode.Created -> LinkMemberResult.Success
            HttpStatusCode.Conflict -> LinkMemberResult.Conflict
            HttpStatusCode.BadRequest -> LinkMemberResult.NotLinkable
            else -> LinkMemberResult.Error("linkNdsMember: ${r.status}")
        }
    } catch (e: Exception) { LinkMemberResult.Error(e.message ?: "linkNdsMember failed") }
```

`LinkNdsMemberRequest` already exists in that file — reuse it, do not redeclare.

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests 'ch.teamorg.repository.TeamRepositoryLinkResultTest' --console=plain`

Expected: PASS, 6 tests.

- [ ] **Step 9: Update the three test doubles and delete the dead caller**

`composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeTeamRepository.kt` — replace the `linkNdsMemberResult` field and `linkNdsMember` override (lines 84-89) with:

```kotlin
    var linkNdsMemberResult: LinkMemberResult = LinkMemberResult.Success
    var duplicateSuggestionsResult: Result<List<DuplicateSuggestion>> = Result.success(emptyList())
    /** Every (teamId, memberId, userId) triple linkNdsMember was called with, in order. */
    val linkNdsMemberCalls = mutableListOf<Triple<String, String, String>>()

    override suspend fun addMember(teamId: String, userId: String, role: String): Result<Unit> = addMemberResult

    override suspend fun getDuplicateSuggestions(teamId: String): Result<List<DuplicateSuggestion>> =
        duplicateSuggestionsResult

    override suspend fun linkNdsMember(teamId: String, memberId: String, userId: String): LinkMemberResult {
        linkNdsMemberCalls += Triple(teamId, memberId, userId)
        return linkNdsMemberResult
    }
```

Add the imports `ch.teamorg.domain.DuplicateSuggestion` and `ch.teamorg.domain.LinkMemberResult`. If that file has a `reset()` function (check — the tests call `fakeTeamRepo.reset()`), clear `linkNdsMemberCalls` and restore both new fields to their defaults inside it.

`composeApp/src/commonTest/kotlin/ch/teamorg/ui/events/EventListViewModelTest.kt:61` — change that inline fake's override to:

```kotlin
    override suspend fun linkNdsMember(teamId: String, memberId: String, userId: String) = LinkMemberResult.Success
    override suspend fun getDuplicateSuggestions(teamId: String) = Result.success(emptyList<DuplicateSuggestion>())
```

`composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/events/EventScreenTestFakes.kt:59` — the same two overrides, same bodies.

Both files need the two new imports.

`composeApp/src/commonMain/kotlin/ch/teamorg/ui/club/ClubMembersViewModel.kt` — delete the whole `linkNds` function (lines 142-148). It has no UI caller and no test coverage; `TeamRosterViewModel` becomes the only merge path. Leave the rest of that file, including `ClubMembersScreen`, untouched. Remove the now-unused `TeamRepository` constructor parameter ONLY if nothing else in the class uses it — check first; `linkNds` may be its sole user, and an unused constructor parameter is worse than none. If you do remove it, update the two `ClubMembersViewModel(...)` construction sites (its DI registration and `ClubMembersViewModelTest`) and say so in your report.

- [ ] **Step 10: Verify everything compiles and no test regressed**

Run these one at a time, in the foreground:

```
./gradlew :shared:jvmTest --tests 'ch.teamorg.repository.TeamRepositoryLinkResultTest' --console=plain
./gradlew :composeApp:testDebugUnitTest --console=plain
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
./gradlew :shared:compileKotlinIosSimulatorArm64 --console=plain
```

Expected: all pass. The composeApp suite passed before this task, so any failure is yours.

- [ ] **Step 11: Commit**

```bash
git add shared/build.gradle.kts \
        shared/src/commonMain/kotlin/ch/teamorg/domain/NdsImport.kt \
        shared/src/commonMain/kotlin/ch/teamorg/domain/LinkMemberResult.kt \
        shared/src/commonMain/kotlin/ch/teamorg/repository/TeamRepository.kt \
        shared/src/commonMain/kotlin/ch/teamorg/data/repository/TeamRepositoryImpl.kt \
        shared/src/jvmTest/kotlin/ch/teamorg/repository/TeamRepositoryLinkResultTest.kt \
        composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeTeamRepository.kt \
        composeApp/src/commonTest/kotlin/ch/teamorg/ui/events/EventListViewModelTest.kt \
        composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/events/EventScreenTestFakes.kt \
        composeApp/src/commonMain/kotlin/ch/teamorg/ui/club/ClubMembersViewModel.kt
git commit -m "feat(shared): typed link result and duplicate-suggestion models for mobile"
```

---

### Task 2: TeamRosterViewModel — load suggestions, merge, map errors

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterViewModel.kt` (state at lines 13-29, `checkClubManagerRole` at 64-81)
- Test: `composeApp/src/commonTest/kotlin/ch/teamorg/ui/team/TeamRosterViewModelTest.kt` (append)

**Interfaces:**
- Consumes: `TeamRepository.getDuplicateSuggestions(teamId): Result<List<DuplicateSuggestion>>`, `TeamRepository.linkNdsMember(teamId, memberId, userId): LinkMemberResult`, `LinkMemberResult.{Success, Conflict, NotLinkable, Error}`, `DuplicateSuggestion`; `FakeTeamRepository.duplicateSuggestionsResult` / `.linkNdsMemberResult` / `.linkNdsMemberCalls`.
- Produces: `TeamRosterState.duplicates: List<DuplicateSuggestion>`, `.showDuplicatesSheet: Boolean`, `.mergeInProgress: Boolean`, `.mergeError: String?`, `.mergedCount: Int`; `TeamRosterViewModel.openDuplicatesSheet()`, `.closeDuplicatesSheet()`, `.clearMergeError()`, `.mergeDuplicate(teamId: String, memberId: String, userId: String)`.

- [ ] **Step 1: Write the failing tests**

Append to `composeApp/src/commonTest/kotlin/ch/teamorg/ui/team/TeamRosterViewModelTest.kt`, inside the existing class. Add the imports `ch.teamorg.domain.DuplicateCandidate`, `ch.teamorg.domain.DuplicateSuggestion`, `ch.teamorg.domain.LinkMemberResult`, `ch.teamorg.domain.MovableCounts`, and `ch.teamorg.domain.ClubRole` / `ch.teamorg.domain.TeamRole` if the roles fixture below needs them.

The ViewModel calls `getMyRoles()` inside `loadRoster`, and `isCoachOrManager` is derived from it. `FakeTeamRepository` exposes that as `var getMyRolesResult: Result<UserRoles>` (line 48), where `UserRoles(clubRoles: List<ClubRoleEntry>, teamRoles: List<TeamRoleEntry>)`, `ClubRoleEntry(clubId, role)` and `TeamRoleEntry(teamId, clubId, role)` all live in `ch.teamorg.domain`.

```kotlin
    // region — duplicate suggestions

    private val suggestionLara = DuplicateSuggestion(
        memberId = "m1",
        lastName = "Müller",
        firstName = "Lara",
        birthDate = "2008-04-01",
        personNumber = "123456789",
        funktion = "Teilnehmer/in",
        candidates = listOf(DuplicateCandidate("u9", "Lara Müller", "HIGH")),
        willMove = MovableCounts(attendance = 12, subgroups = 1, rules = 0)
    )

    @Test
    fun loadRoster_asCoach_exposesDuplicateSuggestions() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.duplicateSuggestionsResult = Result.success(listOf(suggestionLara))

        viewModel.loadRoster("t1")

        viewModel.state.value.duplicates shouldContainExactly listOf(suggestionLara)
    }

    @Test
    fun loadRoster_asPlainPlayer_doesNotFetchSuggestions() = runTest {
        givenCallerIsPlainPlayerOfTeam("t1")
        fakeTeamRepo.duplicateSuggestionsResult = Result.success(listOf(suggestionLara))

        viewModel.loadRoster("t1")

        viewModel.state.value.duplicates.shouldBeEmpty()
    }

    @Test
    fun loadRoster_whenSuggestionsFetchFails_leavesDuplicatesEmptyAndDoesNotSurfaceAnError() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.duplicateSuggestionsResult = Result.failure(RuntimeException("boom"))

        viewModel.loadRoster("t1")

        viewModel.state.value.duplicates.shouldBeEmpty()
        viewModel.state.value.error shouldBe null
    }

    @Test
    fun mergeDuplicate_onSuccess_dropsThatSuggestionAndCountsTheMerge() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.duplicateSuggestionsResult = Result.success(listOf(suggestionLara))
        viewModel.loadRoster("t1")
        fakeTeamRepo.linkNdsMemberResult = LinkMemberResult.Success
        // The post-merge refresh must not resurrect the suggestion we just resolved.
        fakeTeamRepo.duplicateSuggestionsResult = Result.success(emptyList())

        viewModel.mergeDuplicate("t1", "m1", "u9")

        val state = viewModel.state.value
        state.duplicates.shouldBeEmpty()
        state.mergedCount shouldBe 1
        state.mergeError shouldBe null
        state.mergeInProgress shouldBe false
        fakeTeamRepo.linkNdsMemberCalls shouldContainExactly listOf(Triple("t1", "m1", "u9"))
    }

    @Test
    fun mergeDuplicate_onConflict_showsTheAlreadyLinkedMessageAndKeepsTheSheetOpen() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.duplicateSuggestionsResult = Result.success(listOf(suggestionLara))
        viewModel.loadRoster("t1")
        viewModel.openDuplicatesSheet()
        fakeTeamRepo.linkNdsMemberResult = LinkMemberResult.Conflict

        viewModel.mergeDuplicate("t1", "m1", "u9")

        val state = viewModel.state.value
        state.mergeError shouldBe "This account is already linked to another member of this team."
        state.showDuplicatesSheet shouldBe true
        state.mergedCount shouldBe 0
        state.mergeInProgress shouldBe false
    }

    @Test
    fun mergeDuplicate_onNotLinkable_showsTheCannotBeLinkedMessage() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.linkNdsMemberResult = LinkMemberResult.NotLinkable

        viewModel.mergeDuplicate("t1", "m1", "u9")

        viewModel.state.value.mergeError shouldBe "This account can't be linked."
    }

    @Test
    fun mergeDuplicate_onError_showsTheGenericRetryMessage() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.linkNdsMemberResult = LinkMemberResult.Error("linkNdsMember: 404 Not Found")

        viewModel.mergeDuplicate("t1", "m1", "u9")

        // Never leak the raw status string to a coach.
        viewModel.state.value.mergeError shouldBe "Couldn't merge. Please try again."
    }

    @Test
    fun clearMergeError_removesTheMessage() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.linkNdsMemberResult = LinkMemberResult.Conflict
        viewModel.mergeDuplicate("t1", "m1", "u9")

        viewModel.clearMergeError()

        viewModel.state.value.mergeError shouldBe null
    }

    // endregion
```

Add these two helpers to the class (imports: `ch.teamorg.domain.TeamRoleEntry`, `ch.teamorg.domain.UserRoles`):

```kotlin
    private fun givenCallerIsCoachOfTeam(teamId: String) {
        fakeTeamRepo.getMyRolesResult = Result.success(
            UserRoles(teamRoles = listOf(TeamRoleEntry(teamId = teamId, clubId = "c1", role = "coach")))
        )
    }

    private fun givenCallerIsPlainPlayerOfTeam(teamId: String) {
        fakeTeamRepo.getMyRolesResult = Result.success(
            UserRoles(teamRoles = listOf(TeamRoleEntry(teamId = teamId, clubId = "c1", role = "player")))
        )
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*TeamRosterViewModelTest*' --console=plain`

Expected: FAIL to compile — `duplicates`, `mergeDuplicate`, `openDuplicatesSheet` etc. do not exist.

- [ ] **Step 3: Extend the state**

In `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterViewModel.kt`, add these fields to `TeamRosterState` (keep the existing ones and their order; append):

```kotlin
    val duplicates: List<DuplicateSuggestion> = emptyList(),
    val showDuplicatesSheet: Boolean = false,
    val mergeInProgress: Boolean = false,
    val mergeError: String? = null,
    val mergedCount: Int = 0
```

Add the imports `ch.teamorg.domain.DuplicateSuggestion` and `ch.teamorg.domain.LinkMemberResult`.

- [ ] **Step 4: Load suggestions behind the existing role gate**

`checkClubManagerRole` is where `isCoachOrManager` is resolved, so the fetch belongs there — that ordering guarantees the gate is known before the request. At the end of its `onSuccess` block, after the `_state.value = _state.value.copy(...)` that sets the roles, add:

```kotlin
                if (isClubManager || isCoach) loadDuplicateSuggestions(teamId)
```

Then add the loader. A failed fetch is deliberately silent: duplicate discovery is an enhancement, and a coach should not see a scary error because an optional extra could not load.

```kotlin
    private fun loadDuplicateSuggestions(teamId: String) {
        viewModelScope.launch {
            teamRepository.getDuplicateSuggestions(teamId).onSuccess { suggestions ->
                _state.value = _state.value.copy(duplicates = suggestions)
            }
        }
    }
```

- [ ] **Step 5: Add the sheet controls and the merge**

Add to `TeamRosterViewModel`:

```kotlin
    fun openDuplicatesSheet() {
        _state.value = _state.value.copy(showDuplicatesSheet = true, mergeError = null)
    }

    fun closeDuplicatesSheet() {
        _state.value = _state.value.copy(showDuplicatesSheet = false, mergeError = null)
    }

    fun clearMergeError() {
        _state.value = _state.value.copy(mergeError = null)
    }

    /**
     * Merges [userId]'s account into imported roster row [memberId]. Irreversible: the placeholder
     * account is deleted server-side. On failure the sheet stays open so another account can be picked.
     */
    fun mergeDuplicate(teamId: String, memberId: String, userId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(mergeInProgress = true, mergeError = null)
            when (teamRepository.linkNdsMember(teamId, memberId, userId)) {
                LinkMemberResult.Success -> {
                    _state.value = _state.value.copy(
                        duplicates = _state.value.duplicates.filterNot { it.memberId == memberId },
                        mergedCount = _state.value.mergedCount + 1,
                        mergeInProgress = false
                    )
                    loadRoster(teamId, isRefresh = true)
                }
                LinkMemberResult.Conflict -> setMergeError(
                    "This account is already linked to another member of this team."
                )
                LinkMemberResult.NotLinkable -> setMergeError("This account can't be linked.")
                is LinkMemberResult.Error -> setMergeError("Couldn't merge. Please try again.")
            }
        }
    }

    private fun setMergeError(message: String) {
        _state.value = _state.value.copy(mergeInProgress = false, mergeError = message)
    }
```

Note `mergeDuplicate` removes the merged suggestion from state directly rather than relying on the refresh, so the sheet updates even if the follow-up fetch is slow or fails.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*TeamRosterViewModelTest*' --console=plain`

Expected: PASS — the new tests plus every pre-existing test in that class.

- [ ] **Step 7: Run the whole composeApp suite**

Run: `./gradlew :composeApp:testDebugUnitTest --console=plain`

Expected: PASS. It passed before this task, so any failure is yours.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterViewModel.kt \
        composeApp/src/commonTest/kotlin/ch/teamorg/ui/team/TeamRosterViewModelTest.kt
git commit -m "feat(mobile): load duplicate suggestions and merge them from the roster"
```

---

### Task 3: TeamRosterScreen — banner, merge sheet, Provisional chip

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterScreen.kt` (`MemberItem` at 513-576; the `LazyColumn` items around 128-170; sheets near 319-489)

**Interfaces:**
- Consumes: everything Task 2 produced — `state.duplicates`, `state.showDuplicatesSheet`, `state.mergeInProgress`, `state.mergeError`, `state.mergedCount`, and `viewModel.openDuplicatesSheet()` / `closeDuplicatesSheet()` / `mergeDuplicate(teamId, memberId, userId)`; plus `DuplicateSuggestion`, `DuplicateCandidate`, `MovableCounts`, and `TeamMember.provisional`.
- Produces: nothing downstream.

No unit test: this is Compose markup wired to a ViewModel that Task 2 already covers. `composeApp` has Robolectric Compose tests under `androidUnitTest` for some screens, but the branch's whole-branch review already accepted the equivalent web UI on static review, and adding a Compose harness for one banner is not warranted. Verification is compilation of both targets plus the manual walkthrough note in Step 6.

- [ ] **Step 1: Add the Provisional chip to `MemberItem`**

In `MemberItem`, the display name is currently a bare `Text` inside the `Column` (around line 557). Replace that single `Text` with a `Row` carrying the name plus the chip, so the tag sits beside the name:

```kotlin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (member.provisional) {
                    // Import placeholder holding attendance for someone without an account yet.
                    // Only coaches and club managers ever receive these rows from the server.
                    Text(
                        text = "Provisional",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("chip_provisional_${member.userId}")
                    )
                }
            }
```

`Row`, `Arrangement`, `Alignment`, `clip`, `background`, `RoundedCornerShape`, `testTag` and `padding` are all already imported in this file (it uses them in `MemberItem` and elsewhere) — add only what the compiler actually asks for.

- [ ] **Step 2: Add the discovery banner**

In the `LazyColumn`, directly after the existing `if (state.isCoachOrManager) { item { OutlinedButton(... btn_nds_import ...) } }` block (ends around line 168), add:

```kotlin
                    if (state.duplicates.isNotEmpty()) {
                        item {
                            val n = state.duplicates.size
                            OutlinedButton(
                                onClick = { viewModel.openDuplicatesSheet() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("btn_review_duplicates"),
                                shape = PillShape
                            ) {
                                Text(
                                    if (n == 1) "1 possible duplicate — review"
                                    else "$n possible duplicates — review",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
```

`state.duplicates` is only ever non-empty for a coach or club manager (Task 2 gates the fetch), so no extra role check is needed here.

`TeamRosterScreen(teamId, viewModel, …)` receives the ViewModel directly and already calls it inline throughout (`viewModel.toggleSubGroupSheet()` at line 97, `viewModel.showEditTeamSheet()` at 104, `viewModel.promoteMember(...)` at 273). So call `viewModel.openDuplicatesSheet()` inline as shown — do not add callback parameters.

- [ ] **Step 3: Add the merge bottom sheet**

Alongside the file's other `ModalBottomSheet` usages, add:

```kotlin
    if (state.showDuplicatesSheet) {
        ModalBottomSheet(onDismissRequest = { viewModel.closeDuplicatesSheet() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Possible duplicates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "These imported members may already have an account. Merging moves their " +
                        "imported history to that account and deletes the provisional one. This can't be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (state.mergeError != null) {
                    Text(
                        state.mergeError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("txt_merge_error")
                    )
                }

                if (state.duplicates.isEmpty()) {
                    Text(
                        "Nothing left to review.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.duplicates.forEach { suggestion ->
                    DuplicateSuggestionCard(
                        suggestion = suggestion,
                        mergeInProgress = state.mergeInProgress,
                        onMerge = { userId -> viewModel.mergeDuplicate(teamId, suggestion.memberId, userId) }
                    )
                }
            }
        }
    }
```

Then add the card as a private composable at the bottom of the file, next to `MemberItem`. No candidate is pre-selected — a merge is irreversible and a single stray tap must not be able to confirm one:

```kotlin
@Composable
private fun DuplicateSuggestionCard(
    suggestion: DuplicateSuggestion,
    mergeInProgress: Boolean,
    onMerge: (String) -> Unit
) {
    var selectedUserId by remember(suggestion.memberId) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp)
            .testTag("card_duplicate_${suggestion.memberId}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "${suggestion.firstName} ${suggestion.lastName}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            listOfNotNull(
                suggestion.birthDate,
                suggestion.personNumber?.let { "No. $it" },
                suggestion.funktion
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Moves ${pluralize(suggestion.willMove.attendance, "attendance", "attendances")}, " +
                "${pluralize(suggestion.willMove.subgroups, "group", "groups")}, " +
                "${pluralize(suggestion.willMove.rules, "absence rule", "absence rules")}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        suggestion.candidates.forEach { candidate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selectedUserId = candidate.userId }
                    .padding(vertical = 6.dp)
                    .testTag("candidate_${suggestion.memberId}_${candidate.userId}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    selected = selectedUserId == candidate.userId,
                    onClick = { selectedUserId = candidate.userId }
                )
                Text(
                    if (candidate.score == "HIGH") "${candidate.displayName} (very likely)"
                    else candidate.displayName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(
            onClick = { selectedUserId?.let(onMerge) },
            enabled = selectedUserId != null && !mergeInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_merge_${suggestion.memberId}"),
            shape = PillShape
        ) {
            Text("Merge", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** English uses the plural for 0 ("0 groups"), so key on == 1, never on > 1. */
private fun pluralize(count: Int, singular: String, pluralForm: String): String =
    "$count ${if (count == 1) singular else pluralForm}"
```

Add whatever imports the compiler requires — likely `androidx.compose.material3.RadioButton`, `androidx.compose.foundation.clickable`, and `androidx.compose.runtime.{remember, mutableStateOf, getValue, setValue}`.

- [ ] **Step 4: Compile both targets**

Run these one at a time, in the foreground:

```
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
./gradlew :shared:compileKotlinIosSimulatorArm64 --console=plain
```

Expected: BUILD SUCCESSFUL for both. Pre-existing warnings in `EventListScreen.kt`, `NdsImportScreen.kt` and `TeamRosterScreen.kt:54` (deprecated `LocalClipboardManager`) are not yours — do not fix them, but do not add new ones.

- [ ] **Step 5: Re-run the composeApp suite**

Run: `./gradlew :composeApp:testDebugUnitTest --console=plain`

Expected: PASS.

- [ ] **Step 6: Record what could not be verified**

You cannot run the app. In your report, state plainly that the banner → sheet → merge flow was verified by compilation and static review only, and list what a human should tap through: the banner appears for a coach with duplicates, no candidate is pre-selected, Merge is disabled until one is picked, a 409 shows the already-linked sentence with the sheet still open, and a successful merge removes the card and refreshes the roster.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterScreen.kt
git commit -m "feat(mobile): duplicate review banner, merge sheet and provisional chip"
```

---

## Final verification

- [ ] `./gradlew :shared:jvmTest --tests 'ch.teamorg.repository.TeamRepositoryLinkResultTest'` — green
- [ ] `./gradlew :composeApp:testDebugUnitTest` — green
- [ ] `./gradlew :composeApp:compileDebugKotlinAndroid` — green
- [ ] `./gradlew :shared:compileKotlinIosSimulatorArm64` — green
- [ ] `./gradlew :server:test` — unchanged at its 4 known pre-existing failures (this plan touches no server code, so this is a sanity check, not a gate)
- [ ] Push; PR #85 picks the commits up automatically
