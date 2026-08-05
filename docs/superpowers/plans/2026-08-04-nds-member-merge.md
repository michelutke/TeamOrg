# NDS Member Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a coach merge a generically-joined account into an already-imported NDS roster member, with proactive duplicate suggestions, and hide provisional placeholder accounts from ordinary team members.

**Architecture:** Server-side, all merge work funnels through the existing `NdsRepository.claimMember` (extended to carry subgroup memberships and absence rules) and the existing `POST /teams/{teamId}/nds/members/{id}/link` endpoint (hardened with three guards). A new read-only `GET /teams/{teamId}/nds/duplicate-suggestions` endpoint reuses `NdsMemberMatcher` to surface unresolved roster rows. Separately, `users.provisional` becomes role-gated in the team-members and attendance-response responses so placeholders are visible only to coaches and club managers.

**Tech Stack:** Kotlin / Ktor / Exposed / Postgres (server), Flyway migrations (none needed here), kotlin.test + `IntegrationTestBase` (server tests), SvelteKit 2 + Svelte 5 runes + Tailwind (admin web), Kotlin Multiplatform shared models.

Spec: `docs/superpowers/specs/2026-08-04-nds-member-merge-design.md`

## Global Constraints

- No database migration is required. Every column this plan reads already exists (`users.provisional` from `V14__nds_import_export.sql`).
- `attendance_records` is a dead table — no code reads or writes it. Do NOT add moves for it.
- All user-facing strings are **German** (the admin UI and NDS error messages are German throughout).
- Never use `git rebase`; use `git merge`. Never add `Co-Authored-By` or any AI-authorship trailer to commits.
- Run `npm run check` in `admin/` before pushing any web change. Never push without a clean check.
- Server tests: `./gradlew :server:test`. A single test: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`.
- Exposed idioms used in this codebase: `selectAll().where { }`, `select(col).where { }`, `update({ pred }) { it[col] = v }`, `deleteWhere { Op.build { … } }`, `insertIgnore { }`. Follow them exactly.
- `TeamMember` exists **twice** and both copies must stay field-identical: `server/src/main/kotlin/ch/teamorg/domain/models/ClubTeam.kt:39` and `shared/src/commonMain/kotlin/ch/teamorg/domain/Club.kt:37`. New fields need a default value so older mobile builds keep deserializing (the KMP client already sets `ignoreUnknownKeys = true` in `shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt:20`).
- Commit after each task.

---

## File Structure

**Server — modified:**
- `server/src/main/kotlin/ch/teamorg/domain/repositories/NdsRepository.kt` — `claimMember` gains two moves; `moveAttendance` generalized to `moveRows`; new `moveSubGroupMemberships`; `listTeamUsersForMatching` filters provisional; new `listUnresolvedMembers`.
- `server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt` — `/link` guards; new `GET /teams/{teamId}/nds/duplicate-suggestions`.
- `server/src/main/kotlin/ch/teamorg/domain/models/Nds.kt` — new `DuplicateSuggestion` wire model.
- `server/src/main/kotlin/ch/teamorg/domain/models/ClubTeam.kt` — `TeamMember.provisional`.
- `server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepository.kt` + `TeamRepositoryImpl.kt` — `listMembers(teamId, includeProvisional)`.
- `server/src/main/kotlin/ch/teamorg/routes/TeamRoutes.kt` — role-gated `includeProvisional`.
- `server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepository.kt` + `AttendanceRepositoryImpl.kt` — `includeProvisional` on `getEventAttendance` / `getTeamAttendance`.
- `server/src/main/kotlin/ch/teamorg/routes/AttendanceRoutes.kt` — pass the flag from the caller's role.

**Shared — modified:**
- `shared/src/commonMain/kotlin/ch/teamorg/domain/Club.kt` — `TeamMember.provisional`.

**Web — modified:**
- `admin/src/routes/(shell)/app/teams/[teamId]/+page.server.ts` — load suggestions, surface merge errors.
- `admin/src/routes/(shell)/app/teams/[teamId]/+page.svelte` — duplicate banner + merge preview + "Provisorisch" tag.

**Tests — modified:**
- `server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt` — merge carry-over, `/link` guards, suggestions.
- `server/src/test/kotlin/ch/teamorg/routes/TeamRoutesTest.kt` — member visibility.
- `server/src/test/kotlin/ch/teamorg/routes/AttendanceRoutesTest.kt` — response filtering.

**Docs — modified:**
- `docs/invite-flow-contract.md` — generic invites need a post-hoc merge.

---

### Task 1: `claimMember` carries subgroup memberships and absence rules

Today `claimMember` (`NdsRepository.kt:336`) moves only `attendance_responses`. Deleting the provisional placeholder then CASCADEs `sub_group_members` and `abwesenheit_rules`, silently losing them — and losing an absence rule also NULLs `attendance_responses.abwesenheit_rule_id` on the rows that were just moved.

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/NdsRepository.kt:336-382`
- Test: `server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt` (append a new `@Test`)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `NdsRepository.claimMember(memberId: UUID, realUserId: UUID)` — signature unchanged, behaviour extended. Private helpers `moveRows(table: Table, keyCol: Column<UUID>, userCol: Column<UUID>, from: UUID, to: UUID)` and `moveSubGroupMemberships(teamId: UUID, from: UUID, to: UUID)` in `NdsRepositoryImpl`.

- [ ] **Step 1: Write the failing test**

Append to `NdsRoutesTest.kt`, inside `class NdsRoutesTest`:

```kotlin
    @Test
    fun `link carries subgroup memberships and absence rules to the real account`() = withTeamorgTestApplication {
        val mgr = register("nds_merge_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "MergeClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        val lara = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>().single { it.lastName == "Müller" }
        val provisionalUserId = lara.userId!!

        // The generic-join scenario: Lara registers herself and is added to the team directly,
        // so she is a club member with her own account, beside her placeholder.
        val laraUser = register("lara_generic@example.com")
        val realUserId = UUID.fromString(laraUser.userId)
        createJsonClient().post("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId = laraUser.userId, role = "player"))
        }

        // Subgroup A: placeholder only -> must be repointed.
        // Subgroup B: placeholder AND real user -> placeholder row must be dropped, not clash on PK.
        val (groupA, groupB) = transaction {
            val a = UUID.randomUUID()
            val b = UUID.randomUUID()
            SubGroupsTable.insert { it[id] = a; it[SubGroupsTable.teamId] = teamId; it[name] = "Gruppe A" }
            SubGroupsTable.insert { it[id] = b; it[SubGroupsTable.teamId] = teamId; it[name] = "Gruppe B" }
            SubGroupMembersTable.insert { it[subGroupId] = a; it[userId] = provisionalUserId }
            SubGroupMembersTable.insert { it[subGroupId] = b; it[userId] = provisionalUserId }
            SubGroupMembersTable.insert { it[subGroupId] = b; it[userId] = realUserId }
            a to b
        }

        // An absence rule on the placeholder, plus a response pointing at it.
        val ruleId = transaction {
            val rid = UUID.randomUUID()
            AbwesenheitRulesTable.insert {
                it[id] = rid
                it[userId] = provisionalUserId
                it[presetType] = PresetType.injury
                it[label] = "Knie"
                it[ruleType] = RuleType.period
                it[startDate] = java.time.LocalDate.of(2026, 1, 1)
                it[endDate] = java.time.LocalDate.of(2026, 12, 31)
            }
            val anyEventId = AttendanceResponsesTable.select(AttendanceResponsesTable.eventId)
                .where { AttendanceResponsesTable.userId eq provisionalUserId }
                .map { it[AttendanceResponsesTable.eventId] }
                .first()
            AttendanceResponsesTable.update({
                (AttendanceResponsesTable.userId eq provisionalUserId) and
                    (AttendanceResponsesTable.eventId eq anyEventId)
            }) { it[abwesenheitRuleId] = rid }
            rid
        }

        val link = createJsonClient().post("/teams/$teamId/nds/members/${lara.id}/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = laraUser.userId))
        }
        assertEquals(HttpStatusCode.OK, link.status)

        transaction {
            // Real user is now in both subgroups; nothing left on the placeholder.
            val realGroups = SubGroupMembersTable.select(SubGroupMembersTable.subGroupId)
                .where { SubGroupMembersTable.userId eq realUserId }
                .map { it[SubGroupMembersTable.subGroupId] }
                .toSet()
            assertEquals(setOf(groupA, groupB), realGroups)
            assertEquals(
                0,
                SubGroupMembersTable.selectAll()
                    .where { SubGroupMembersTable.userId eq provisionalUserId }.count().toInt()
            )

            // The absence rule moved rather than being CASCADE-deleted, and the moved response
            // still references it.
            assertEquals(
                realUserId,
                AbwesenheitRulesTable.select(AbwesenheitRulesTable.userId)
                    .where { AbwesenheitRulesTable.id eq ruleId }
                    .map { it[AbwesenheitRulesTable.userId] }
                    .single()
            )
            assertEquals(
                1,
                AttendanceResponsesTable.selectAll()
                    .where {
                        (AttendanceResponsesTable.userId eq realUserId) and
                            (AttendanceResponsesTable.abwesenheitRuleId eq ruleId)
                    }.count().toInt()
            )
        }
    }
```

Add the imports this test needs at the top of `NdsRoutesTest.kt` (keep the existing alphabetical-ish grouping):

```kotlin
import ch.teamorg.db.tables.AbwesenheitRulesTable
import ch.teamorg.db.tables.PresetType
import ch.teamorg.db.tables.RuleType
import ch.teamorg.db.tables.SubGroupMembersTable
import ch.teamorg.db.tables.SubGroupsTable
```

`AddMemberRequest` and `NdsMemberLinkRequest` are already resolvable — both live in `ch.teamorg.routes`, the test's own package.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest.link carries subgroup memberships and absence rules to the real account'`

Expected: FAIL. The subgroup assertion fails first — the real user is only in `groupB`, because the placeholder's `groupA` row was CASCADE-deleted with the placeholder account rather than repointed.

- [ ] **Step 3: Generalize the row-mover**

In `NdsRepositoryImpl` (`NdsRepository.kt`), rename `moveAttendance` to `moveRows` — the body is unchanged, only the parameter names generalize:

```kotlin
    /**
     * Repoint [table] rows from [from] to [to]. Rows whose [keyCol] the target already holds are
     * deleted instead of updated, so the move cannot clash on a (key, user) primary key.
     */
    private fun moveRows(
        table: Table,
        keyCol: Column<UUID>,
        userCol: Column<UUID>,
        from: UUID,
        to: UUID
    ) {
        val targetKeys = table.select(keyCol).where { userCol eq to }.map { it[keyCol] }.toSet()
        // Delete source rows that would collide with an existing target row, then repoint the rest.
        if (targetKeys.isNotEmpty()) {
            table.deleteWhere { Op.build { (userCol eq from) and (keyCol inList targetKeys) } }
        }
        table.update({ userCol eq from }) { it[userCol] = to }
    }
```

- [ ] **Step 4: Add the subgroup mover**

Add below `moveRows` in `NdsRepositoryImpl`. `sub_group_members` has no team column, so the move is scoped through `sub_groups.team_id` — defensive, since a placeholder cannot belong to another team's subgroup.

```kotlin
    /** Move the placeholder's memberships in THIS team's subgroups to the real account. */
    private fun moveSubGroupMemberships(teamId: UUID, from: UUID, to: UUID) {
        val teamSubGroupIds = SubGroupsTable.select(SubGroupsTable.id)
            .where { SubGroupsTable.teamId eq teamId }
            .map { it[SubGroupsTable.id] }
        if (teamSubGroupIds.isEmpty()) return

        val alreadyHeld = SubGroupMembersTable.select(SubGroupMembersTable.subGroupId)
            .where {
                (SubGroupMembersTable.userId eq to) and
                    (SubGroupMembersTable.subGroupId inList teamSubGroupIds)
            }
            .map { it[SubGroupMembersTable.subGroupId] }

        if (alreadyHeld.isNotEmpty()) {
            SubGroupMembersTable.deleteWhere {
                Op.build { (userId eq from) and (subGroupId inList alreadyHeld) }
            }
        }
        SubGroupMembersTable.update({
            (SubGroupMembersTable.userId eq from) and
                (SubGroupMembersTable.subGroupId inList teamSubGroupIds)
        }) {
            it[userId] = to
        }
    }
```

- [ ] **Step 5: Call both movers from `claimMember`**

In `claimMember`, replace the single `moveAttendance(...)` call (`NdsRepository.kt:350`) with:

```kotlin
        // Move attendance from the provisional placeholder to the real user, skipping events where
        // the real user already has a row (avoids PK clash on (event_id, user_id)).
        moveRows(AttendanceResponsesTable, AttendanceResponsesTable.eventId, AttendanceResponsesTable.userId, provisionalUserId, realUserId)

        // Subgroup memberships and absence rules would otherwise be CASCADE-deleted with the
        // placeholder. Rules must move BEFORE the delete, or attendance_responses.abwesenheit_rule_id
        // on the rows just moved silently goes NULL.
        moveSubGroupMemberships(teamId, provisionalUserId, realUserId)
        AbwesenheitRulesTable.update({ AbwesenheitRulesTable.userId eq provisionalUserId }) {
            it[userId] = realUserId
        }
```

Add the imports at the top of `NdsRepository.kt`:

```kotlin
import ch.teamorg.db.tables.AbwesenheitRulesTable
import ch.teamorg.db.tables.SubGroupMembersTable
import ch.teamorg.db.tables.SubGroupsTable
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest.link carries subgroup memberships and absence rules to the real account'`

Expected: PASS.

- [ ] **Step 7: Run the full NDS suite for regressions**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`

Expected: PASS — in particular `member invite redeem claims the roster member and moves attendance`, which exercises the renamed `moveRows` through the invite path.

- [ ] **Step 8: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/domain/repositories/NdsRepository.kt \
        server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt
git commit -m "fix(nds): carry subgroups and absence rules through a member claim"
```

---

### Task 2: Harden the `/link` merge endpoint

`POST /teams/{teamId}/nds/members/{id}/link` (`NdsRoutes.kt:533`) currently accepts any existing user id. It will happily link one account to two roster rows of the same team, link a provisional placeholder to another placeholder's row, or link a user from a different club. The import path already forbids the first and third (`NdsRoutes.kt:322`, `NdsRepository.kt:118`).

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt:533-550`
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/NdsRepository.kt` (two new query methods)
- Test: `server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt`

**Interfaces:**
- Consumes: `claimMember` from Task 1 (unchanged signature).
- Produces:
  - `NdsRepository.findMemberIdByUser(teamId: UUID, userId: UUID): UUID?` — the roster row of `teamId` currently backed by `userId`, or null.
  - `NdsRepository.isProvisionalUser(userId: UUID): Boolean` — already exists as a private helper at `NdsRepository.kt:437`; promote it to the interface with this exact name and signature.

- [ ] **Step 1: Write the failing tests**

Append to `NdsRoutesTest.kt`:

```kotlin
    @Test
    fun `link rejects a user already linked to another roster row of the team`() = withTeamorgTestApplication {
        val mgr = register("nds_dup_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "DupClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        val members = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>()
        val first = members.first { it.funktion == "Teilnehmer/in" }
        val second = members.filter { it.funktion == "Teilnehmer/in" }[1]

        val user = register("dup_target@example.com")
        createJsonClient().post("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId = user.userId, role = "player"))
        }

        suspend fun link(memberId: UUID) = createJsonClient().post("/teams/$teamId/nds/members/$memberId/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = user.userId))
        }

        assertEquals(HttpStatusCode.OK, link(first.id).status)
        assertEquals(HttpStatusCode.Conflict, link(second.id).status)
    }

    @Test
    fun `link rejects a provisional target and a user outside the club`() = withTeamorgTestApplication {
        val mgr = register("nds_guard_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "GuardClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        val members = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>()
        val target = members.first { it.lastName == "Müller" }
        val otherPlaceholderUserId = members.first { it.id != target.id }.userId!!

        // Provisional placeholder as the link target -> 400.
        val provisional = createJsonClient().post("/teams/$teamId/nds/members/${target.id}/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = otherPlaceholderUserId.toString()))
        }
        assertEquals(HttpStatusCode.BadRequest, provisional.status)

        // A real account that is not a member of this club -> 400.
        val outsider = register("outsider@example.com")
        val outside = createJsonClient().post("/teams/$teamId/nds/members/${target.id}/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = outsider.userId))
        }
        assertEquals(HttpStatusCode.BadRequest, outside.status)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest.link rejects*'`

Expected: FAIL — both currently return `200 OK`.

- [ ] **Step 3: Expose the two repository queries**

In `NdsRepository.kt`, add to the `interface NdsRepository` (next to `claimMember`, around line 212):

```kotlin
    /** The roster row of [teamId] currently backed by [userId], or null if none. */
    suspend fun findMemberIdByUser(teamId: UUID, userId: UUID): UUID?
    /** True when [userId] is an import placeholder account (users.provisional). */
    suspend fun isProvisionalUser(userId: UUID): Boolean
```

Add to `NdsRepositoryImpl`:

```kotlin
    override suspend fun findMemberIdByUser(teamId: UUID, userId: UUID): UUID? = transaction {
        NdsMembersTable.select(NdsMembersTable.id)
            .where { (NdsMembersTable.teamId eq teamId) and (NdsMembersTable.userId eq userId) }
            .map { it[NdsMembersTable.id] }
            .firstOrNull()
    }

    override suspend fun isProvisionalUser(userId: UUID): Boolean = transaction {
        UsersTable.select(UsersTable.provisional)
            .where { UsersTable.id eq userId }
            .map { it[UsersTable.provisional] }
            .singleOrNull() == true
    }
```

If a private helper with the name `isProvisionalUser` already exists in this file (`NdsRepository.kt:437`), keep only the interface-backed `override` version and update its call sites to use it.

- [ ] **Step 4: Add the guards to the route**

In `NdsRoutes.kt`, in the `/link` handler, insert these checks after the existing `userRepository.findById(userId) == null` check and before `teamRepository.addMember(...)`:

```kotlin
            if (ndsRepository.isProvisionalUser(userId))
                return@post call.respond(HttpStatusCode.BadRequest, "Provisorische Konten können nicht verknüpft werden")

            val clubId = teamRepository.getClubId(teamId)
            if (clubId != null && !clubRepository.isMember(userId, clubId))
                return@post call.respond(HttpStatusCode.BadRequest, "Nutzer ist kein Mitglied dieses Clubs")

            val existingMemberId = ndsRepository.findMemberIdByUser(teamId, userId)
            if (existingMemberId != null && existingMemberId != memberId)
                return@post call.respond(
                    HttpStatusCode.Conflict,
                    "Dieses Konto ist bereits mit einem anderen Mitglied dieses Teams verknüpft"
                )
```

`clubRepository` is already injected in `ndsRoutes()` (`NdsRoutes.kt:87`).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`

Expected: PASS, including Task 1's test and the pre-existing link/claim tests.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt \
        server/src/main/kotlin/ch/teamorg/domain/repositories/NdsRepository.kt \
        server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt
git commit -m "security(nds): guard member link against double-link and outside accounts"
```

---

### Task 3: Duplicate-suggestions endpoint

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/domain/models/Nds.kt` (new wire model)
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/NdsRepository.kt` (`listTeamUsersForMatching` filter, `listUnresolvedMembers`, `countMovableRows`)
- Modify: `server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt` (new route)
- Test: `server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt`

**Interfaces:**
- Consumes: `NdsMemberMatcher.suggest(rows: List<NdsMemberInput>, teamUsers: List<MatchCandidateUser>): List<MemberSuggestion>` (`infra/nds/NdsMemberMatcher.kt:35`); `MemberSuggestion.Candidate(userId, displayName, score, birthdateMatch)`.
- Produces: `GET /teams/{teamId}/nds/duplicate-suggestions` → `List<DuplicateSuggestion>`; `NdsRepository.listUnresolvedMembers(teamId: UUID): List<NdsMember>`; `NdsRepository.countMovableRows(userId: UUID, teamId: UUID): MovableCounts`.

- [ ] **Step 1: Add the wire models**

In `server/src/main/kotlin/ch/teamorg/domain/models/Nds.kt`, next to `MemberSuggestionDto`:

```kotlin
/** What a merge would carry over from the placeholder, shown in the confirm dialog. */
@Serializable
data class MovableCounts(
    val attendance: Int,
    val subgroups: Int,
    val rules: Int
)

/** One unresolved roster row plus the accounts it might be the same person as. */
@Serializable
data class DuplicateSuggestion(
    @Serializable(with = UUIDSerializer::class) val memberId: java.util.UUID,
    val lastName: String,
    val firstName: String,
    @Serializable(with = LocalDateSerializer::class) val birthDate: LocalDate?,
    val personNumber: String?,
    val funktion: String,
    val candidates: List<Candidate>,
    val willMove: MovableCounts
) {
    @Serializable
    data class Candidate(
        @Serializable(with = UUIDSerializer::class) val userId: java.util.UUID,
        val displayName: String,
        val score: String
    )
}
```

- [ ] **Step 2: Write the failing test**

Append to `NdsRoutesTest.kt`:

```kotlin
    @Test
    fun `duplicate suggestions match a self-registered account to its imported roster row`() = withTeamorgTestApplication {
        val mgr = register("nds_sugg_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "SuggClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        // Before any real account joins: placeholders must not suggest each other.
        val empty = createJsonClient().get("/teams/$teamId/nds/duplicate-suggestions") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<DuplicateSuggestion>>()
        assertTrue(empty.isEmpty(), "placeholders must not be matching candidates")

        val lara = createJsonClient().get("/teams/$teamId/nds/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<NdsMember>>().single { it.lastName == "Müller" }

        // Lara joins generically: her display name matches the roster row exactly.
        val laraUser = createJsonClient().post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("lara_sugg@example.com", "password123", "${lara.firstName} ${lara.lastName}"))
        }.body<AuthResponse>()
        createJsonClient().post("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId = laraUser.userId, role = "player"))
        }

        val suggestions = createJsonClient().get("/teams/$teamId/nds/duplicate-suggestions") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<DuplicateSuggestion>>()

        val forLara = suggestions.single { it.memberId == lara.id }
        assertEquals(laraUser.userId, forLara.candidates.single().userId.toString())
        assertEquals("HIGH", forLara.candidates.single().score)
        // The import writes a response per activity (confirmed when attended, declined otherwise),
        // so assert non-zero rather than a fixture-coupled count.
        assertTrue(forLara.willMove.attendance > 0)

        // After the merge the row is resolved and drops out of the list.
        createJsonClient().post("/teams/$teamId/nds/members/${lara.id}/link") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(NdsMemberLinkRequest(userId = laraUser.userId))
        }
        val after = createJsonClient().get("/teams/$teamId/nds/duplicate-suggestions") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<DuplicateSuggestion>>()
        assertTrue(after.none { it.memberId == lara.id })
    }

    @Test
    fun `duplicate suggestions require an elevated role`() = withTeamorgTestApplication {
        val mgr = register("nds_sugg_guard@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "SuggGuardClub")
        val res = importAll(mgr.token, clubId)
        val teamId = UUID.fromString(res.teamId)

        val player = register("sugg_player@example.com")
        createJsonClient().post("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId = player.userId, role = "player"))
        }

        val res2 = createJsonClient().get("/teams/$teamId/nds/duplicate-suggestions") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }
        assertEquals(HttpStatusCode.Forbidden, res2.status)
    }
```

Add the import: `import ch.teamorg.domain.models.DuplicateSuggestion`.

`register(...)` is the existing helper (`NdsRoutesTest.kt:45`) which sets `displayName = "User <email>"`; the first test deliberately calls `/auth/register` directly to control the display name, since matching is name-based.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest.duplicate suggestions*'`

Expected: FAIL with 404 — the route does not exist.

- [ ] **Step 4: Filter placeholders out of the matching candidates**

In `NdsRepositoryImpl.listTeamUsersForMatching` (`NdsRepository.kt:278`), the final `UsersTable` query is unfiltered. Add the provisional filter so placeholders are never offered as candidates:

```kotlin
        UsersTable.selectAll().where { (UsersTable.id inList userIds) and (UsersTable.provisional eq false) }
```

- [ ] **Step 5: Add the two repository queries**

Add to `interface NdsRepository`:

```kotlin
    /** Roster rows of [teamId] not backed by a real account (no user, or a provisional one). */
    suspend fun listUnresolvedMembers(teamId: UUID): List<NdsMember>
    /** How many rows a merge would carry off [userId] within [teamId]. */
    suspend fun countMovableRows(userId: UUID, teamId: UUID): MovableCounts
```

Add to `NdsRepositoryImpl`:

```kotlin
    override suspend fun listUnresolvedMembers(teamId: UUID): List<NdsMember> = transaction {
        val provisionalIds = UsersTable.select(UsersTable.id)
            .where { UsersTable.provisional eq true }
            .map { it[UsersTable.id] }
            .toSet()
        NdsMembersTable.selectAll().where { NdsMembersTable.teamId eq teamId }
            .filter { row ->
                val uid = row[NdsMembersTable.userId]
                uid == null || uid in provisionalIds
            }
            .map { it.toNdsMember() }
    }

    override suspend fun countMovableRows(userId: UUID, teamId: UUID): MovableCounts = transaction {
        val attendance = AttendanceResponsesTable.selectAll()
            .where { AttendanceResponsesTable.userId eq userId }.count().toInt()
        val teamSubGroupIds = SubGroupsTable.select(SubGroupsTable.id)
            .where { SubGroupsTable.teamId eq teamId }
            .map { it[SubGroupsTable.id] }
        val subgroups = if (teamSubGroupIds.isEmpty()) 0 else SubGroupMembersTable.selectAll()
            .where {
                (SubGroupMembersTable.userId eq userId) and
                    (SubGroupMembersTable.subGroupId inList teamSubGroupIds)
            }.count().toInt()
        val rules = AbwesenheitRulesTable.selectAll()
            .where { AbwesenheitRulesTable.userId eq userId }.count().toInt()
        MovableCounts(attendance = attendance, subgroups = subgroups, rules = rules)
    }
```

Add the import `import ch.teamorg.domain.models.MovableCounts` to `NdsRepository.kt`.

- [ ] **Step 6: Add the route**

In `NdsRoutes.kt`, next to the other `/teams/{teamId}/nds/...` routes (after the `/members` GET at line 428):

```kotlin
        // Unresolved roster rows plus the real accounts they might be duplicates of.
        get("/teams/{teamId}/nds/duplicate-suggestions") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@get

            val unresolved = ndsRepository.listUnresolvedMembers(teamId)
            if (unresolved.isEmpty()) return@get call.respond(emptyList<DuplicateSuggestion>())

            val teamUsers = ndsRepository.listTeamUsersForMatching(teamId)
            val rows = unresolved.map {
                NdsMemberInput(it.lastName, it.firstName, it.birthDate, it.personNumber, it.funktion)
            }
            val suggestionsByRowKey = NdsMemberMatcher.suggest(rows, teamUsers).associateBy { it.rowKey }

            val result = unresolved.mapNotNull { member ->
                val key = NdsMemberMatcher.rowKey(member.funktion, member.lastName, member.firstName)
                val candidates = suggestionsByRowKey[key]?.candidates.orEmpty()
                if (candidates.isEmpty()) return@mapNotNull null
                DuplicateSuggestion(
                    memberId = member.id,
                    lastName = member.lastName,
                    firstName = member.firstName,
                    birthDate = member.birthDate,
                    personNumber = member.personNumber,
                    funktion = member.funktion,
                    candidates = candidates.map {
                        DuplicateSuggestion.Candidate(it.userId, it.displayName, it.score)
                    },
                    willMove = member.userId
                        ?.let { ndsRepository.countMovableRows(it, teamId) }
                        ?: MovableCounts(0, 0, 0)
                )
            }
            call.respond(result)
        }
```

Add the imports `ch.teamorg.domain.models.DuplicateSuggestion` and `ch.teamorg.domain.models.MovableCounts` to `NdsRoutes.kt`. `NdsMemberMatcher` and `NdsMemberInput` are already imported there.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/domain/models/Nds.kt \
        server/src/main/kotlin/ch/teamorg/domain/repositories/NdsRepository.kt \
        server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt \
        server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt
git commit -m "feat(nds): suggest duplicate roster rows for self-registered accounts"
```

---

### Task 4: Hide provisional placeholders from ordinary team members

`GET /teams/{teamId}/members` (`TeamRoutes.kt:148`) is player-accessible and `TeamRepositoryImpl.listMembers` (`TeamRepositoryImpl.kt:141`) does not filter on `users.provisional`, so players see import placeholders as if they were teammates.

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/domain/models/ClubTeam.kt:39-46`
- Modify: `shared/src/commonMain/kotlin/ch/teamorg/domain/Club.kt:37-44`
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepository.kt:27`
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepositoryImpl.kt:141-153`
- Modify: `server/src/main/kotlin/ch/teamorg/routes/TeamRoutes.kt:148-153`
- Test: `server/src/test/kotlin/ch/teamorg/routes/TeamRoutesTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `TeamMember(userId, displayName, avatarUrl, role, jerseyNumber, position, provisional)`; `TeamRepository.listMembers(teamId: UUID, includeProvisional: Boolean = false): List<TeamMember>`.

- [ ] **Step 1: Write the failing test**

Append to `server/src/test/kotlin/ch/teamorg/routes/TeamRoutesTest.kt` (match the file's existing helper names for register/club/team creation — read the top of the file first and reuse them rather than inventing new ones):

```kotlin
    @Test
    fun `team members hides provisional placeholders from players and flags them for coaches`() = withTeamorgTestApplication {
        val mgr = register("prov_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "ProvClub")
        val teamId = createTeam(mgr.token, clubId, "Prov Team")

        val player = register("prov_player@example.com")
        createJsonClient().post("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId = player.userId, role = "player"))
        }

        // A placeholder exactly as an NDS import creates it: provisional user + team role.
        val ghostId = UUID.randomUUID()
        transaction {
            UsersTable.insert {
                it[id] = ghostId
                it[email] = "nds-$ghostId@import.teamorg.local"
                it[passwordHash] = "!"
                it[displayName] = "Ghost Member"
                it[provisional] = true
            }
            TeamRolesTable.insert {
                it[userId] = ghostId
                it[TeamRolesTable.teamId] = teamId
                it[role] = "player"
            }
        }

        val asPlayer = createJsonClient().get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }.body<List<TeamMember>>()
        assertTrue(asPlayer.none { it.userId == ghostId.toString() }, "player must not see placeholders")

        val asManager = createJsonClient().get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<TeamMember>>()
        val ghost = asManager.single { it.userId == ghostId.toString() }
        assertTrue(ghost.provisional)
        assertTrue(asManager.single { it.userId == player.userId }.provisional.not())
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.TeamRoutesTest.team members hides provisional placeholders from players and flags them for coaches'`

Expected: FAIL to compile — `TeamMember` has no `provisional` property.

- [ ] **Step 3: Add the field to both `TeamMember` copies**

`server/src/main/kotlin/ch/teamorg/domain/models/ClubTeam.kt`:

```kotlin
data class TeamMember(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: String,
    val jerseyNumber: Int?,
    val position: String?,
    val provisional: Boolean = false
)
```

`shared/src/commonMain/kotlin/ch/teamorg/domain/Club.kt` — identical change, same field order and default.

- [ ] **Step 4: Make the repository filter**

`TeamRepository.kt:27`:

```kotlin
    suspend fun listMembers(teamId: UUID, includeProvisional: Boolean = false): List<TeamMember>
```

`TeamRepositoryImpl.kt:141`:

```kotlin
    override suspend fun listMembers(teamId: UUID, includeProvisional: Boolean): List<TeamMember> = transaction {
        (TeamRolesTable innerJoin UsersTable).selectAll()
            .where {
                if (includeProvisional) TeamRolesTable.teamId eq teamId
                else (TeamRolesTable.teamId eq teamId) and (UsersTable.provisional eq false)
            }
            .map { row ->
                TeamMember(
                    userId = row[UsersTable.id].toString(),
                    displayName = row[UsersTable.displayName],
                    avatarUrl = row[UsersTable.avatarUrl],
                    role = row[TeamRolesTable.role],
                    jerseyNumber = row[TeamRolesTable.jerseyNumber],
                    position = row[TeamRolesTable.position],
                    provisional = row[UsersTable.provisional]
                )
            }
    }
```

- [ ] **Step 5: Gate it on the caller's role**

`TeamRoutes.kt:148`:

```kotlin
                get("/members") {
                    val teamId = UUID.fromString(call.parameters["teamId"])
                    if (!call.requireTeamRole(teamId, "coach", "player", "club_manager", teamRepository = teamRepository)) return@get
                    val callerId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
                    // Import placeholders are bookkeeping rows, not teammates: only the people who
                    // can merge them away get to see them.
                    val elevated = teamRepository.hasRole(callerId, teamId, "coach", "club_manager")
                    call.respond(teamRepository.listMembers(teamId, includeProvisional = elevated))
                }
```

Check that `io.ktor.server.auth.jwt.JWTPrincipal` is imported in `TeamRoutes.kt`; add it if not.

- [ ] **Step 6: Fix other `listMembers` call sites**

Run: `grep -rn "listMembers(" server/src/main/kotlin admin/src`

Every server call site keeps compiling thanks to the default argument. Review each one and decide explicitly: internal/coach-facing uses that need the full roster must pass `includeProvisional = true`. Do not change any call site whose result is returned to a player.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.TeamRoutesTest'` then `./gradlew :server:test`

Expected: PASS. If another test asserted a member count that included a placeholder, update that assertion — the new behaviour is intended.

- [ ] **Step 8: Verify the shared model still compiles for mobile**

Run: `./gradlew :shared:compileKotlinJvm`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/domain/models/ClubTeam.kt \
        shared/src/commonMain/kotlin/ch/teamorg/domain/Club.kt \
        server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepository.kt \
        server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepositoryImpl.kt \
        server/src/main/kotlin/ch/teamorg/routes/TeamRoutes.kt \
        server/src/test/kotlin/ch/teamorg/routes/TeamRoutesTest.kt
git commit -m "feat(teams): hide import placeholders from ordinary team members"
```

---

### Task 5: Filter placeholder attendance for non-elevated callers

Attendance responses are not name-joined server-side — clients resolve names from the team-members list. After Task 4 a player no longer receives placeholders, so placeholder attendance rows would render as unresolvable user ids and inflate RSVP counts. Two player-facing endpoints leak them: `GET /events/{id}/attendance` (`AttendanceRoutes.kt:91`) and `GET /teams/{teamId}/attendance` (`AttendanceRoutes.kt:238`).

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepository.kt` (two signatures)
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepositoryImpl.kt:13-17` and `:106-109`
- Modify: `server/src/main/kotlin/ch/teamorg/routes/AttendanceRoutes.kt:91-98` and `:238-247`
- Test: `server/src/test/kotlin/ch/teamorg/routes/AttendanceRoutesTest.kt`

**Interfaces:**
- Consumes: `TeamRepository.hasRole(userId, teamId, vararg roles)` (`TeamRepositoryImpl.kt:155`).
- Produces: `getEventAttendance(eventId: UUID, includeProvisional: Boolean = true)`, `getTeamAttendance(teamId: UUID, from: Instant?, to: Instant?, includeProvisional: Boolean = true)`. Default `true` so coach-side callers (finalize, export, preflight) keep the full picture without edits.

- [ ] **Step 1: Write the failing test**

Append to `server/src/test/kotlin/ch/teamorg/routes/AttendanceRoutesTest.kt`, reusing that file's existing helpers for creating a club, team, and event:

```kotlin
    @Test
    fun `event attendance hides provisional responses from players`() = withTeamorgTestApplication {
        val mgr = register("att_prov_mgr@example.com"); promoteToSuperAdmin(mgr.userId)
        val clubId = createClub(mgr.token, "AttProvClub")
        val teamId = createTeam(mgr.token, clubId, "AttProv Team")
        val eventId = createEvent(mgr.token, teamId)

        val player = register("att_prov_player@example.com")
        createJsonClient().post("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(userId = player.userId, role = "player"))
        }

        val ghostId = UUID.randomUUID()
        transaction {
            UsersTable.insert {
                it[id] = ghostId
                it[email] = "nds-$ghostId@import.teamorg.local"
                it[passwordHash] = "!"
                it[displayName] = "Ghost Member"
                it[provisional] = true
            }
            TeamRolesTable.insert {
                it[userId] = ghostId
                it[TeamRolesTable.teamId] = teamId
                it[role] = "player"
            }
            AttendanceResponsesTable.insert {
                it[AttendanceResponsesTable.eventId] = UUID.fromString(eventId)
                it[userId] = ghostId
                it[status] = "confirmed"
            }
        }

        val asPlayer = createJsonClient().get("/events/$eventId/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }.body<List<AttendanceResponseDto>>()
        assertTrue(asPlayer.none { it.userId == ghostId.toString() })

        val asManager = createJsonClient().get("/events/$eventId/attendance") {
            header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        }.body<List<AttendanceResponseDto>>()
        assertTrue(asManager.any { it.userId == ghostId.toString() })
    }
```

If `AttendanceResponsesTable` has additional non-null columns without defaults, set them in the insert — read `server/src/main/kotlin/ch/teamorg/db/tables/AttendanceTables.kt:16` and mirror how `NdsEventImporter.kt:194` inserts a response.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.AttendanceRoutesTest.event attendance hides provisional responses from players'`

Expected: FAIL — the player currently receives the ghost row.

- [ ] **Step 3: Add the flag to the repository**

`AttendanceRepository.kt`:

```kotlin
    suspend fun getEventAttendance(eventId: UUID, includeProvisional: Boolean = true): List<AttendanceResponseRow>
    suspend fun getTeamAttendance(
        teamId: UUID,
        from: Instant?,
        to: Instant?,
        includeProvisional: Boolean = true
    ): List<RawAttendanceRow>
```

Add this private helper to `AttendanceRepositoryImpl` — one query for the whole row set, not one per row:

```kotlin
    /** Ids among [userIds] that belong to import placeholder accounts. */
    private fun provisionalUserIds(userIds: List<UUID>): Set<UUID> {
        if (userIds.isEmpty()) return emptySet()
        return UsersTable.select(UsersTable.id)
            .where { (UsersTable.id inList userIds.distinct()) and (UsersTable.provisional eq true) }
            .map { it[UsersTable.id] }
            .toSet()
    }
```

and write `getEventAttendance` (`AttendanceRepositoryImpl.kt:13`) as:

```kotlin
    override suspend fun getEventAttendance(eventId: UUID, includeProvisional: Boolean): List<AttendanceResponseRow> = transaction {
        val rows = AttendanceResponsesTable.selectAll()
            .where { AttendanceResponsesTable.eventId eq eventId }
            .map(::rowToResponse)
        if (includeProvisional) rows
        else {
            val hidden = provisionalUserIds(rows.map { it.userId })
            rows.filterNot { it.userId in hidden }
        }
    }
```

Apply the same shape to `getTeamAttendance` (`AttendanceRepositoryImpl.kt:106`), filtering the `RawAttendanceRow` list returned by `buildRawQuery`. Add `import ch.teamorg.db.tables.UsersTable` if it is not already imported.

- [ ] **Step 4: Pass the caller's role from the routes**

`AttendanceRoutes.kt:91`:

```kotlin
        get("/events/{id}/attendance") {
            val eventId = UUID.fromString(call.parameters["id"])
            // Any member of the event's team(s) may read the roster (the app shows RSVP counts
            // to players); non-members are rejected.
            if (!call.requireEventAccess(eventId, "coach", "player", "club_manager", eventRepository = eventRepository, teamRepository = teamRepository)) return@get
            val callerId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            // Placeholders are hidden from players by /teams/{id}/members, so their responses must
            // be hidden too — otherwise the client shows RSVPs it cannot name.
            val elevated = eventRepository.findById(eventId)?.teamIds.orEmpty().any {
                teamRepository.hasRole(callerId, it, "coach", "club_manager")
            }
            val responses = attendanceRepo.getEventAttendance(eventId, includeProvisional = elevated)
            call.respond(responses.map { it.toDto() })
        }
```

`eventRepository.findById(eventId)` returns an event exposing `teamIds` — this is exactly how `requireEventAccess` resolves an event's teams (`middleware/RoleMiddleware.kt:81-96`).

`AttendanceRoutes.kt:238`:

```kotlin
        get("/teams/{teamId}/attendance") {
            val teamId = UUID.fromString(call.parameters["teamId"])
            if (!call.requireTeamRole(teamId, "coach", "player", "club_manager", teamRepository = teamRepository)) return@get
            val callerId = UUID.fromString(call.principal<JWTPrincipal>()!!.payload.subject)
            val elevated = teamRepository.hasRole(callerId, teamId, "coach", "club_manager")
            val from = call.parameters["from"]?.let { Instant.parse(it) }
            val to = call.parameters["to"]?.let { Instant.parse(it) }
            val rows = attendanceRepo.getTeamAttendance(teamId, from, to, includeProvisional = elevated)
            call.respond(rows.map { it.toDto() })
        }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :server:test`

Expected: PASS. `FinalizeAttendanceTest` and the NDS export tests must stay green — they go through `finalize` / `listExportAttendances`, which do not use these two methods and therefore still see placeholders.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepository.kt \
        server/src/main/kotlin/ch/teamorg/domain/repositories/AttendanceRepositoryImpl.kt \
        server/src/main/kotlin/ch/teamorg/routes/AttendanceRoutes.kt \
        server/src/test/kotlin/ch/teamorg/routes/AttendanceRoutesTest.kt
git commit -m "feat(attendance): hide placeholder responses from non-elevated callers"
```

---

### Task 6: Team-page duplicate banner, merge preview, and docs

The team page already renders the NDS roster with Einladen / "Konto verknüpfen" actions. This task adds the proactive banner, shows what a merge carries over, surfaces the new 409/400 errors properly, and tags placeholders in the members list.

No unit test: the change is template plus one fetch and one form field — `admin/` has no component-test setup (only pure-TS vitest specs like `src/lib/nds-import-wizard.test.ts`). Verification is `npm run check` plus the manual walkthrough in Step 6.

**Files:**
- Modify: `admin/src/routes/(shell)/app/teams/[teamId]/+page.server.ts`
- Modify: `admin/src/routes/(shell)/app/teams/[teamId]/+page.svelte`
- Modify: `docs/invite-flow-contract.md`

**Interfaces:**
- Consumes: `GET /teams/{teamId}/nds/duplicate-suggestions` → `DuplicateSuggestion[]` from Task 3; `TeamMember.provisional` from Task 4; the 409 / 400 responses from Task 2.
- Produces: nothing downstream.

- [ ] **Step 1: Load the suggestions**

In `+page.server.ts`, add the interfaces next to `NdsMember`:

```ts
interface DuplicateCandidate {
	userId: string;
	displayName: string;
	score: string;
}

interface DuplicateSuggestion {
	memberId: string;
	lastName: string;
	firstName: string;
	birthDate: string | null;
	personNumber: string | null;
	funktion: string;
	candidates: DuplicateCandidate[];
	willMove: { attendance: number; subgroups: number; rules: number };
}
```

Add `provisional: boolean;` to the existing `Member` interface.

Inside the `if (canManage)` block, after the `ndsMembers` fetch, add:

```ts
			try {
				ndsDuplicates = await apiGet<DuplicateSuggestion[]>(
					`/teams/${teamId}/nds/duplicate-suggestions`,
					token
				);
			} catch {
				ndsDuplicates = [];
			}
```

Declare `let ndsDuplicates: DuplicateSuggestion[] = [];` alongside `let ndsMembers`, and add `ndsDuplicates` to the returned object.

- [ ] **Step 2: Surface the merge errors**

Replace the `catch` in the `linkNdsMember` action so the new statuses get distinct messages:

```ts
		} catch (e) {
			if (e instanceof ApiError && e.status === 409) return fail(409, { ndsError: 'alreadyLinked' });
			if (e instanceof ApiError && e.status === 400) return fail(400, { ndsError: 'notLinkable' });
			return fail(500, { ndsError: 'failed' });
		}
```

- [ ] **Step 3: Render the banner and preview**

In `+page.svelte`, insert directly above the `<div class="flex flex-col gap-2">` roster list (currently line 217):

```svelte
			{#if data.ndsDuplicates.length > 0}
				<div class="mb-4 rounded-2xl bg-surface-container-high px-4 py-3">
					<p class="text-[13px] font-medium text-on-surface">
						{data.ndsDuplicates.length} mögliche {data.ndsDuplicates.length === 1
							? 'Dublette'
							: 'Dubletten'} — Konto und importiertes Mitglied zusammenführen?
					</p>
					<div class="mt-3 flex flex-col gap-3">
						{#each data.ndsDuplicates as d (d.memberId)}
							<div class="rounded-xl bg-surface-container px-3 py-2">
								<div class="text-[13px] text-on-surface">
									<span class="font-medium">{d.firstName} {d.lastName}</span>
									<span class="text-on-surface-variant">
										{d.birthDate ? ` · ${d.birthDate}` : ''}{d.personNumber
											? ` · Nr. ${d.personNumber}`
											: ''} · {d.funktion}
									</span>
								</div>
								<p class="mt-1 text-[12px] text-on-surface-variant">
									Wird übernommen: {d.willMove.attendance} Anwesenheiten, {d.willMove.subgroups}
									Gruppen, {d.willMove.rules} Abwesenheitsregeln. Das provisorische Konto wird
									gelöscht — nicht umkehrbar.
								</p>
								<form method="POST" action="?/linkNdsMember" class="mt-2 flex items-center gap-2">
									<input type="hidden" name="memberId" value={d.memberId} />
									<select
										name="userId"
										required
										class="flex-1 rounded-lg bg-surface-container-high px-2 py-1 text-[13px] text-on-surface"
									>
										{#each d.candidates as c (c.userId)}
											<option value={c.userId}>
												{c.displayName}{c.score === 'HIGH' ? ' (sehr wahrscheinlich)' : ''}
											</option>
										{/each}
									</select>
									<button
										type="submit"
										class="cursor-pointer rounded-full border-none bg-primary px-4 py-1 text-[12px] font-bold text-on-primary hover:opacity-90"
									>Zusammenführen</button>
								</form>
							</div>
						{/each}
					</div>
				</div>
			{/if}
```

- [ ] **Step 4: Extend the error line and tag placeholders**

Replace the existing `form?.ndsError` paragraph (currently line 301):

```svelte
			{#if form?.ndsError}
				<p class="mt-3 text-[12px] font-medium text-error">
					{form.ndsError === 'badNumber'
						? 'Ungültige Personennummer.'
						: form.ndsError === 'alreadyLinked'
							? 'Dieses Konto ist bereits mit einem anderen Mitglied dieses Teams verknüpft.'
							: form.ndsError === 'notLinkable'
								? 'Dieses Konto kann nicht verknüpft werden.'
								: 'Aktion fehlgeschlagen.'}
				</p>
			{/if}
```

In the team-members list (the section rendering `data.members`, above the NDS section), add a tag beside the display name:

```svelte
							{#if m.provisional}
								<span class="rounded-full bg-surface-container px-2 py-0.5 text-[11px] text-on-surface-variant">Provisorisch</span>
							{/if}
```

- [ ] **Step 5: Verify types and build**

Run: `cd admin && npm run check`

Expected: 0 errors, 0 warnings. Fix any type error before continuing — CI fails on this.

- [ ] **Step 6: Manual verification**

Start the stack (`docker-compose up -d` for Postgres, `./gradlew :server:run`, `cd admin && npm run dev`), then as a club manager:

1. Import an NDS roster into a team.
2. Register a second account whose display name matches a roster row, and add it to the team via the members list.
3. Reload the team page — the banner reports 1 possible duplicate with that account preselected and non-zero "Anwesenheiten".
4. Click Zusammenführen → success message; the banner disappears and the roster row shows "verknüpft".
5. Try Zusammenführen again for a second roster row with the same account → the "bereits mit einem anderen Mitglied verknüpft" error appears.

- [ ] **Step 7: Update the invite-flow contract doc**

In `docs/invite-flow-contract.md`, add a short section stating: generic invites (reusable link and 8-character short code) carry no `nds_member_id`, so a joiner who also exists as an imported roster member is not linked automatically; the team page surfaces the duplicate and `POST /teams/{teamId}/nds/members/{id}/link` performs the merge. Per-member invites and the import wizard's `map` decision link automatically and need no follow-up.

- [ ] **Step 8: Commit**

```bash
git add admin/src/routes/'(shell)'/app/teams/'[teamId]'/+page.server.ts \
        admin/src/routes/'(shell)'/app/teams/'[teamId]'/+page.svelte \
        docs/invite-flow-contract.md
git commit -m "feat(web): surface and merge duplicate NDS roster members"
```

---

## Final verification

- [ ] `./gradlew :server:test` — all green
- [ ] `./gradlew :shared:compileKotlinJvm` — green
- [ ] `cd admin && npm run check` — 0 errors
- [ ] Manual walkthrough from Task 6 Step 6 passes end to end
