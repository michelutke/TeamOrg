# Account Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user delete their own account immediately, from the mobile app and the web app, satisfying Apple guideline 5.1.1(v) and Google Play's account-deletion requirement.

**Architecture:** Deletion is an **anonymization**: six foreign keys reference `users(id)` with `ON DELETE RESTRICT`, so the row cannot be removed without rewriting event authorship and audit history. Instead, all personal rows are deleted, the `users` row is scrubbed, and `deleted_at` is set. Existing JWTs are killed by a check in the JWT `validate` block — the only choke point every route passes through.

**Tech Stack:** Ktor + Exposed + Flyway + Postgres (Testcontainers for tests); Kotlin Multiplatform `shared` with Ktor client; Compose Multiplatform `composeApp`; SvelteKit 2 / Svelte 5 runes admin app with `de`/`en` i18n.

**Spec:** `docs/superpowers/specs/2026-08-05-account-deletion-design.md`

## Global Constraints

- **Anonymize in place. No physical row deletion, no FK migrations.** The `users` row survives, scrubbed.
- **Immediate and irreversible.** No grace period, no scheduler, no undo, no confirmation email.
- **Password re-entry is required** to confirm. Never a typed magic word.
- Exact scrub values: `email` → `deleted-<userId>@deleted.invalid`, `display_name` → `Gelöschtes Konto`, `avatar_url` → `NULL`, `password_hash` → `!`, `deleted_at` → `now()`.
- **A club owner is blocked**, not auto-cancelled: HTTP **409** with body `{"reason":"owns_clubs","clubs":["<name>", ...]}` when the caller owns a club whose `status <> 'deleted'`.
- **The last coach of a team MAY delete**, with a warning shown in the UI. Never blocked.
- Endpoint: **`DELETE /auth/me`**, inside `authenticate("jwt")` and `rateLimit(RateLimits.AUTH)`. Success is **204 No Content**; wrong password is **401**.
- **Admin web copy is German-first but the app is i18n'd**: every new string goes into the `Dict` type and BOTH the `de` and `en` objects in `admin/src/lib/i18n/index.ts`. TypeScript will not compile otherwise.
- **Mobile copy is English.** `composeApp` has no i18n and its existing strings are English.
- Test frameworks by source set: `server` = `kotlin.test` + `IntegrationTestBase`; `shared` jvmTest = `kotlin.test` + Ktor `MockEngine` (**no Kotest**); `composeApp` = Kotest matchers with `kotlin.test` annotations, `UnconfinedTestDispatcher`, hand-written fakes under `commonTest/kotlin/ch/teamorg/fake/`.
- TDD: write the failing test first, watch it fail, then implement.
- Verification gates: `./gradlew :server:test` (**4 known pre-existing failures — the count must not grow**), `./gradlew :composeApp:testDebugUnitTest`, `./gradlew :shared:jvmTest`, `./gradlew :composeApp:compileDebugKotlinAndroid`, `./gradlew :shared:compileKotlinIosSimulatorArm64`, `cd admin && npm run check` (0 errors; ~47 pre-existing warnings are fine).
- **Run gradle in the FOREGROUND, one invocation at a time.** Concurrent Testcontainers runs wedged this machine's Docker daemon earlier in the session.
- Commit messages must not contain `Co-Authored-By` or any AI-authorship hint.
- `commit.gpgsign=true`. If a 1Password/GPG prompt stalls, use `git -c commit.gpgsign=false commit`.

## File Structure

| File | Responsibility |
|---|---|
| `server/src/main/resources/db/migrations/V19__user_deletion.sql` | One column: `users.deleted_at` |
| `server/src/main/kotlin/ch/teamorg/db/tables/UsersTable.kt` | Exposed mapping for the new column |
| `server/src/main/kotlin/ch/teamorg/plugins/Auth.kt` | Rejects tokens of deleted users — the single enforcement point |
| `server/src/main/kotlin/ch/teamorg/domain/repositories/UserRepository.kt` | Excludes deleted rows from email lookups so login fails and the email is reusable |
| `server/src/main/kotlin/ch/teamorg/domain/repositories/UserDeletionRepository.kt` | New. The whole deletion transaction and its precondition |
| `server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt` | `DELETE /auth/me`: password check → repository → status mapping |
| `shared/src/commonMain/kotlin/ch/teamorg/domain/DeleteAccountResult.kt` | New. Sealed result so no raw status reaches a UI |
| `shared/src/commonMain/kotlin/ch/teamorg/data/repository/AuthRepositoryImpl.kt` | Status → result mapping |
| `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileViewModel.kt` | Delete state machine and error copy |
| `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileScreen.kt` | Entry row + confirm dialog with the expandable disclosure |
| `admin/src/routes/(shell)/app/profile/delete/+page.svelte` + `+page.server.ts` | New. The web confirm page |
| `admin/src/lib/i18n/index.ts` | `Dict.profile` gains the deletion keys, in `de` and `en` |

---

### Task 1: Schema, session invalidation, and login exclusion

**Files:**
- Create: `server/src/main/resources/db/migrations/V19__user_deletion.sql`
- Modify: `server/src/main/kotlin/ch/teamorg/db/tables/UsersTable.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/plugins/Auth.kt:26-33`
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/UserRepository.kt`
- Test: `server/src/test/kotlin/ch/teamorg/routes/AccountDeletionGuardTest.kt` (create)

**Interfaces:**
- Produces, consumed by Task 2: `UsersTable.deletedAt` — an Exposed `timestamp("deleted_at").nullable()` column.
- Produces: `UserRepository.findByEmail` and `getPasswordHash` now ignore rows with `deleted_at IS NOT NULL`. Signatures are unchanged.

**Why this task exists separately:** the session-invalidation guarantee is the one part where a
silent failure means a "deleted" user keeps full API access. It gets its own tests and its own
review gate, before any deletion code can create such a user.

**Critical context — do not place the check in `authenticateUser`:** eleven of sixteen route
files never call it (`AbwesenheitRoutes`, `AttendanceRoutes`, `EventRoutes`, `NdsRoutes`,
`NotificationRoutes`, `SubGroupRoutes`, `IntegrationRoutes`, `ImpersonationRoutes`,
`AdminRoutes`, `ContactRoutes`, `StripeWebhookRoutes`) — they read the JWT principal directly.
Only the `validate` block in `plugins/Auth.kt` covers every route.

- [ ] **Step 1: Write the failing test**

Create `server/src/test/kotlin/ch/teamorg/routes/AccountDeletionGuardTest.kt`:

```kotlin
package ch.teamorg.routes

import ch.teamorg.db.tables.UsersTable
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountDeletionGuardTest : IntegrationTestBase() {

    /** Marks a user deleted the way the repository will in Task 2, without depending on it. */
    private fun markDeleted(userId: String) {
        transaction {
            UsersTable.update({ UsersTable.id eq UUID.fromString(userId) }) {
                it[deletedAt] = Instant.now()
                it[email] = "deleted-$userId@deleted.invalid"
                it[displayName] = "Gelöschtes Konto"
                it[passwordHash] = "!"
            }
        }
    }

    @Test
    fun `token of a deleted user is rejected on a route that does not use authenticateUser`() =
        withTeamorgTestApplication {
            val client = createJsonClient()
            val auth = client.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("guard1@example.com", "password123", "Guard One"))
            }.body<AuthResponse>()

            // Sanity: the token works before deletion. /users/me/abwesenheit is in
            // AbwesenheitRoutes, which reads the principal directly.
            val before = client.get("/users/me/abwesenheit") { bearerAuth(auth.token) }
            assertEquals(HttpStatusCode.OK, before.status)

            markDeleted(auth.userId)

            val after = client.get("/users/me/abwesenheit") { bearerAuth(auth.token) }
            assertEquals(HttpStatusCode.Unauthorized, after.status)
        }

    @Test
    fun `token of a deleted user is rejected on auth me`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("guard2@example.com", "password123", "Guard Two"))
        }.body<AuthResponse>()

        markDeleted(auth.userId)

        val response = client.get("/auth/me") { bearerAuth(auth.token) }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `login with the original email of a deleted user fails`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("guard3@example.com", "password123", "Guard Three"))
        }.body<AuthResponse>()

        markDeleted(auth.userId)

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("guard3@example.com", "password123"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `the email of a deleted user can be registered again`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("guard4@example.com", "password123", "Guard Four"))
        }.body<AuthResponse>()

        markDeleted(auth.userId)

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("guard4@example.com", "newpassword123", "Guard Four Again"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val fresh = response.body<AuthResponse>()
        assertEquals("Guard Four Again", fresh.displayName)
        // A genuinely new row, not the scrubbed one revived.
        assert(fresh.userId != auth.userId)
    }
}
```

Note on the last test: `users.email` has a `UNIQUE` index, and the scrub rewrites the email to
`deleted-<uuid>@deleted.invalid`, which is why re-registration can succeed. The
`existsByEmail`/`findByEmail` exclusion added in step 4 covers the case where a caller looks up
the original address.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :server:test --tests '*AccountDeletionGuardTest'`
Expected: FAIL — compilation error, `UsersTable.deletedAt` is unresolved.

- [ ] **Step 3: Add the migration and the Exposed column**

Create `server/src/main/resources/db/migrations/V19__user_deletion.sql`:

```sql
-- Account self-deletion is an anonymization: six FKs reference users(id) with ON DELETE
-- RESTRICT (event authorship, attendance_records.set_by, invites, audit_log.actor_id), so the
-- row must survive. deleted_at is what blocks login and invalidates existing tokens.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ NULL;
```

In `server/src/main/kotlin/ch/teamorg/db/tables/UsersTable.kt`, add after the `provisional`
column:

```kotlin
    val deletedAt = timestamp("deleted_at").nullable()
```

- [ ] **Step 4: Exclude deleted rows from email lookups**

In `server/src/main/kotlin/ch/teamorg/domain/repositories/UserRepository.kt`, change the three
email-keyed queries in `UserRepositoryImpl` so a scrubbed row is invisible to them.

`findByEmail`:

```kotlin
    override fun findByEmail(email: String): User? = transaction {
        UsersTable.selectAll()
            .where { (UsersTable.email eq email) and (UsersTable.deletedAt eq null) }
            .map(::rowToUser)
            .singleOrNull()
    }
```

`existsByEmail`:

```kotlin
    override fun existsByEmail(email: String): Boolean = transaction {
        !UsersTable.selectAll()
            .where { (UsersTable.email eq email) and (UsersTable.deletedAt eq null) }
            .empty()
    }
```

`getPasswordHash`:

```kotlin
    override fun getPasswordHash(email: String): String? = transaction {
        UsersTable.select(UsersTable.passwordHash)
            .where { (UsersTable.email eq email) and (UsersTable.deletedAt eq null) }
            .map { it[UsersTable.passwordHash] }
            .singleOrNull()
    }
```

Add `import org.jetbrains.exposed.sql.and` if it is not already imported. Leave `findById`
alone — admin tooling and `authenticateUser` legitimately resolve a scrubbed row by id, and the
token guard in step 5 is what stops the deleted user's own requests.

- [ ] **Step 5: Reject deleted users' tokens in the JWT validate block**

In `server/src/main/kotlin/ch/teamorg/plugins/Auth.kt`, replace the `validate` block:

```kotlin
            validate { credential ->
                val subject = credential.payload.subject ?: return@validate null
                val userId = try {
                    UUID.fromString(subject)
                } catch (e: IllegalArgumentException) {
                    return@validate null
                }
                // A self-deleted account must stop working immediately, not when its JWT
                // expires. This is the ONLY place every route passes through: eleven route
                // files read the principal directly and never call authenticateUser, so a
                // check placed there would leave them reachable with a deleted user's token.
                // Cost is one indexed primary-key lookup per authenticated request.
                if (isUserDeleted(userId)) return@validate null
                JWTPrincipal(credential.payload)
            }
```

Add at the bottom of the same file:

```kotlin
private fun isUserDeleted(userId: UUID): Boolean = transaction {
    UsersTable.select(UsersTable.deletedAt)
        .where { UsersTable.id eq userId }
        .singleOrNull()
        ?.get(UsersTable.deletedAt) != null
}
```

and these imports:

```kotlin
import ch.teamorg.db.tables.UsersTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
```

A missing row yields `null` from `singleOrNull()` and therefore `false` — not deleted, merely
absent, which the existing `authenticateUser` "User not found" path already handles for the five
route files that use it.

- [ ] **Step 6: Run the new test and confirm it passes**

Run: `./gradlew :server:test --tests '*AccountDeletionGuardTest'`
Expected: PASS, 4 tests.

- [ ] **Step 7: Run the whole server suite**

Run, in the foreground: `./gradlew :server:test`
Expected: the 4 known pre-existing failures and no others (3 date-boundary `NdsRoutesTest`
failures plus `IdorHardeningTest > user attendance is scoped to teams shared with the caller`).
If a fifth failure appears, it is yours — fix it before committing. Report the exact count.

- [ ] **Step 8: Commit**

```bash
git add server/src/main/resources/db/migrations/V19__user_deletion.sql \
        server/src/main/kotlin/ch/teamorg/db/tables/UsersTable.kt \
        server/src/main/kotlin/ch/teamorg/plugins/Auth.kt \
        server/src/main/kotlin/ch/teamorg/domain/repositories/UserRepository.kt \
        server/src/test/kotlin/ch/teamorg/routes/AccountDeletionGuardTest.kt
git commit -m "feat(server): invalidate sessions and logins for deleted accounts"
```

---

### Task 2: The deletion transaction and the endpoint

**Files:**
- Create: `server/src/main/kotlin/ch/teamorg/domain/repositories/UserDeletionRepository.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/plugins/Koin.kt:26-32` (register the new repository)
- Modify: `server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt` (request DTO near the other DTOs; route inside the existing `authenticate("jwt")` block)
- Test: `server/src/test/kotlin/ch/teamorg/routes/AccountDeletionTest.kt` (create)

**Interfaces:**
- Consumes from Task 1: `UsersTable.deletedAt`.
- Produces, consumed by Task 3: `DELETE /auth/me` with body `{"password":"..."}`. Responses:
  **204** no body; **401** on wrong password; **409** with body
  `{"reason":"owns_clubs","clubs":["Club A"]}`.
- Produces (server-internal): `UserDeletionRepository.deleteAccount(userId: UUID): DeleteAccountOutcome`
  where `DeleteAccountOutcome` is `Deleted` or `OwnsClubs(clubNames: List<String>)`.

**Table names you need** (verified against the migrations — use exactly these Exposed objects,
and check the real column names in each table file before writing the query):
`AbwesenheitRulesTable`, `AttendanceResponsesTable`, `AttendanceRecordsTable` (in
`db/tables/AttendanceTables.kt`); `NotificationsTable`, `NotificationSettingsTable`,
`NotificationRemindersTable`, `EventReminderOverridesTable` (`NotificationTables.kt`);
`SubGroupMembersTable` (`SubGroupsTable.kt`); `TeamRolesTable`; `ClubRolesTable`, `ClubsTable`
(`ClubsTable.kt`); `NdsMembersTable` (`NdsTables.kt`); `AuditLogTable`; `UsersTable`.

If an expected table object does not exist under `db/tables/`, list the directory and use the
actual name — do not invent one, and do not add a new table object.

- [ ] **Step 1: Write the failing test**

Create `server/src/test/kotlin/ch/teamorg/routes/AccountDeletionTest.kt`:

```kotlin
package ch.teamorg.routes

import ch.teamorg.db.tables.AbwesenheitRulesTable
import ch.teamorg.db.tables.AuditLogTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionTest : IntegrationTestBase() {

    private suspend fun register(
        client: io.ktor.client.HttpClient,
        email: String,
        password: String = "password123",
        name: String = "Test User"
    ): AuthResponse = client.post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email, password, name))
    }.body()

    @Test
    fun `wrong password returns 401 and leaves the account working`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = register(client, "del1@example.com")

        val response = client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("wrong-password"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val stillWorks = client.get("/auth/me") { bearerAuth(auth.token) }
        assertEquals(HttpStatusCode.OK, stillWorks.status)
    }

    @Test
    fun `deletion scrubs the user row`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = register(client, "del2@example.com", name = "Delete Me")

        val response = client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("password123"))
        }
        assertEquals(HttpStatusCode.NoContent, response.status)

        transaction {
            val row = UsersTable.selectAll()
                .where { UsersTable.id eq UUID.fromString(auth.userId) }
                .single()
            assertNotNull(row[UsersTable.deletedAt])
            assertEquals("deleted-${auth.userId}@deleted.invalid", row[UsersTable.email])
            assertEquals("Gelöschtes Konto", row[UsersTable.displayName])
            assertNull(row[UsersTable.avatarUrl])
            assertEquals("!", row[UsersTable.passwordHash])
        }
    }

    @Test
    fun `deletion removes personal rows`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = register(client, "del3@example.com")

        // Give the user an absence rule so there is something personal to delete.
        val ruleResponse = client.post("/users/me/abwesenheit") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonBody(
                    """{"presetType":"holidays","label":"Ferien","ruleType":"period","startDate":"2026-09-01","endDate":"2026-09-14"}"""
                )
            )
        }
        assertTrue(ruleResponse.status.isSuccess(), "absence rule setup failed: ${ruleResponse.bodyAsText()}")

        client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("password123"))
        }

        transaction {
            val userId = UUID.fromString(auth.userId)
            assertTrue(
                AbwesenheitRulesTable.selectAll().where { AbwesenheitRulesTable.userId eq userId }.empty(),
                "absence rules survived deletion"
            )
            assertTrue(
                TeamRolesTable.selectAll().where { TeamRolesTable.userId eq userId }.empty(),
                "team roles survived deletion"
            )
        }
    }

    @Test
    fun `deletion writes an audit log entry`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = register(client, "del4@example.com")

        client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("password123"))
        }

        transaction {
            val entries = AuditLogTable.selectAll()
                .where { AuditLogTable.actorId eq UUID.fromString(auth.userId) }
                .map { it[AuditLogTable.action] }
            assertTrue("user.self_delete" in entries, "expected a user.self_delete audit entry, got $entries")
        }
    }

    @Test
    fun `a club owner is blocked with 409 and nothing is deleted`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val auth = register(client, "owner@example.com", name = "Club Owner")

        // Self-serve club creation sets clubs.owner_user_id to the caller.
        val created = client.post("/self-serve/clubs") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(buildJsonBody("""{"name":"Owner Club","sportType":"volleyball","kind":"club"}"""))
        }
        assertTrue(created.status.isSuccess(), "club setup failed: ${created.bodyAsText()}")

        val response = client.delete("/auth/me") {
            bearerAuth(auth.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("password123"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("owns_clubs", body["reason"]?.jsonPrimitive?.content)
        assertEquals("Owner Club", body["clubs"]?.jsonArray?.first()?.jsonPrimitive?.content)

        // Nothing may have been deleted or scrubbed by a rejected request.
        transaction {
            val row = UsersTable.selectAll()
                .where { UsersTable.id eq UUID.fromString(auth.userId) }
                .single()
            assertNull(row[UsersTable.deletedAt])
            assertEquals("owner@example.com", row[UsersTable.email])
        }
        val stillWorks = client.get("/auth/me") { bearerAuth(auth.token) }
        assertEquals(HttpStatusCode.OK, stillWorks.status)
    }
}
```

`buildJsonBody` does not exist yet. Add it as a private helper in the test file:

```kotlin
private fun buildJsonBody(json: String) = io.ktor.http.content.TextContent(json, ContentType.Application.Json)
```

**Two setup details to verify before assuming, and adjust the test to match reality:**
1. The self-serve club-creation route path and request shape — read
   `server/src/main/kotlin/ch/teamorg/routes/SelfServeRoutes.kt`. If it requires Stripe or a
   different payload, set `clubs.owner_user_id` directly via `transaction { ClubsTable.insert { … } }`
   instead. What matters is that a club exists with `owner_user_id = auth.userId` and
   `status <> 'deleted'`.
2. The absence-rule request shape — read
   `server/src/main/kotlin/ch/teamorg/routes/AbwesenheitRoutes.kt` and its DTO, and prefer the
   typed request class over raw JSON if one is exported.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :server:test --tests '*AccountDeletionTest'`
Expected: FAIL — `DeleteAccountRequest` is unresolved.

- [ ] **Step 3: Write the deletion repository**

Create `server/src/main/kotlin/ch/teamorg/domain/repositories/UserDeletionRepository.kt`:

```kotlin
package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

/** Result of a self-deletion attempt. */
sealed interface DeleteAccountOutcome {
    data object Deleted : DeleteAccountOutcome
    /** The caller owns these still-live clubs and must hand them over first. */
    data class OwnsClubs(val clubNames: List<String>) : DeleteAccountOutcome
}

/**
 * Self-deletion is an anonymization, not a row delete: six foreign keys reference users(id)
 * with ON DELETE RESTRICT (event authorship, attendance_records.set_by, invites,
 * audit_log.actor_id), so removing the row would fail for any user who ever created an event.
 * Personal rows go; the users row is scrubbed and marked deleted.
 *
 * Returns the avatar path (if any) so the caller can delete the file outside the transaction.
 */
interface UserDeletionRepository {
    fun deleteAccount(userId: UUID): DeleteAccountOutcome
    /** Avatar path of a user, read before deletion so the file can be removed afterwards. */
    fun avatarPath(userId: UUID): String?
}

class UserDeletionRepositoryImpl : UserDeletionRepository {

    override fun avatarPath(userId: UUID): String? = transaction {
        UsersTable.select(UsersTable.avatarUrl)
            .where { UsersTable.id eq userId }
            .singleOrNull()
            ?.get(UsersTable.avatarUrl)
    }

    override fun deleteAccount(userId: UUID): DeleteAccountOutcome = transaction {
        // Checked inside the transaction so a concurrent ownership transfer cannot slip
        // between the check and the scrub.
        val ownedClubs = ClubsTable
            .select(ClubsTable.name)
            .where { (ClubsTable.ownerUserId eq userId) and (ClubsTable.status neq "deleted") }
            .map { it[ClubsTable.name] }
        if (ownedClubs.isNotEmpty()) {
            return@transaction DeleteAccountOutcome.OwnsClubs(ownedClubs)
        }

        // Personal data. Nothing cascades here — the row survives — so every table is explicit.
        AbwesenheitRulesTable.deleteWhere { AbwesenheitRulesTable.userId eq userId }
        AttendanceResponsesTable.deleteWhere { AttendanceResponsesTable.userId eq userId }
        AttendanceRecordsTable.deleteWhere { AttendanceRecordsTable.userId eq userId }
        NotificationsTable.deleteWhere { NotificationsTable.userId eq userId }
        NotificationSettingsTable.deleteWhere { NotificationSettingsTable.userId eq userId }
        NotificationRemindersTable.deleteWhere { NotificationRemindersTable.userId eq userId }
        EventReminderOverridesTable.deleteWhere { EventReminderOverridesTable.userId eq userId }
        SubGroupMembersTable.deleteWhere { SubGroupMembersTable.userId eq userId }
        TeamRolesTable.deleteWhere { TeamRolesTable.userId eq userId }
        ClubRolesTable.deleteWhere { ClubRolesTable.userId eq userId }

        // The imported roster row survives as unclaimed, exactly like a never-registered
        // import — the duplicate-merge flow can re-link it later.
        NdsMembersTable.update({ NdsMembersTable.userId eq userId }) {
            it[NdsMembersTable.userId] = null
        }

        UsersTable.update({ UsersTable.id eq userId }) {
            it[email] = "deleted-$userId@deleted.invalid"
            it[displayName] = "Gelöschtes Konto"
            it[avatarUrl] = null
            it[passwordHash] = "!"
            it[deletedAt] = Instant.now()
        }

        AuditLogTable.insert {
            it[actorId] = userId
            it[action] = "user.self_delete"
        }

        DeleteAccountOutcome.Deleted
    }
}
```

`AuditLogTable` has more columns than `actorId`/`action` (see
`server/src/main/kotlin/ch/teamorg/db/tables/AuditLogTable.kt` and how `AdminRoutes.kt:215`
writes an entry). Read both and fill every non-nullable column the same way the existing call
site does — reuse `AuditLogRepository` if it already exposes a suitable method, rather than
inserting directly.

Column names (`AttendanceResponsesTable.userId` etc.) must be checked against the actual table
objects; the migrations use `user_id` but the Exposed property names are what compile.

- [ ] **Step 4: Register it in Koin**

In `server/src/main/kotlin/ch/teamorg/plugins/Koin.kt`, beside the other repository bindings
(around line 26):

```kotlin
    single<UserDeletionRepository> { UserDeletionRepositoryImpl() }
```

plus the import for the new types.

- [ ] **Step 5: Add the endpoint**

In `server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt`, add the DTOs beside the existing
ones (after `ChangePasswordRequest`):

```kotlin
@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class DeleteAccountConflict(val reason: String, val clubs: List<String>)
```

Inject the repository beside the others at the top of `authRoutes()`:

```kotlin
    val userDeletionRepository by inject<UserDeletionRepository>()
```

Inside the existing `authenticate("jwt")` block, next to `/change-password` and wrapped in the
same rate limiter:

```kotlin
            rateLimit(RateLimits.AUTH) {
                delete("/me") {
                    val request = call.receive<DeleteAccountRequest>()
                    call.authenticateUser(userRepository) { user ->
                        val userId = UUID.fromString(user.id)
                        val currentHash = userRepository.getPasswordHashById(userId)
                        if (currentHash == null ||
                            !BCrypt.checkpw(request.password.take(MAX_PASSWORD_LENGTH), currentHash)
                        ) {
                            return@authenticateUser call.respond(
                                HttpStatusCode.Unauthorized,
                                "Password is incorrect"
                            )
                        }
                        // Read before the scrub nulls the column.
                        val avatar = userDeletionRepository.avatarPath(userId)
                        when (val outcome = userDeletionRepository.deleteAccount(userId)) {
                            is DeleteAccountOutcome.OwnsClubs -> call.respond(
                                HttpStatusCode.Conflict,
                                DeleteAccountConflict("owns_clubs", outcome.clubNames)
                            )
                            DeleteAccountOutcome.Deleted -> {
                                // Outside the transaction on purpose: an orphaned file is a
                                // lesser harm than a rolled-back deletion.
                                if (avatar != null) {
                                    runCatching { fileStorageService.delete(avatar) }
                                        .onFailure { call.application.log.warn("avatar cleanup failed for $userId", it) }
                                }
                                call.respond(HttpStatusCode.NoContent)
                            }
                        }
                    }
                }
            }
```

Add the imports for `DeleteAccountOutcome`, `UserDeletionRepository`, and `io.ktor.server.routing.delete`
if the wildcard routing import does not already cover it.

`fileStorageService` is already injected in `authRoutes()` and used by the avatar upload route.
Check `FileStorageService.delete`'s expected argument: `avatar_url` may be a URL path while
`delete` may want a storage path — read `LocalFileStorageService.kt` and pass what the avatar
*upload* route stored, converting if needed.

- [ ] **Step 6: Run the new tests and confirm they pass**

Run: `./gradlew :server:test --tests '*AccountDeletionTest'`
Expected: PASS, 5 tests.

- [ ] **Step 7: Run the whole server suite**

Run, in the foreground: `./gradlew :server:test`
Expected: the same 4 pre-existing failures, no new ones. Report the exact count.

- [ ] **Step 8: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/domain/repositories/UserDeletionRepository.kt \
        server/src/main/kotlin/ch/teamorg/plugins/Koin.kt \
        server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt \
        server/src/test/kotlin/ch/teamorg/routes/AccountDeletionTest.kt
git commit -m "feat(server): DELETE /auth/me anonymizes the caller's account"
```

---

### Task 3: Shared client — typed delete result

**Files:**
- Create: `shared/src/commonMain/kotlin/ch/teamorg/domain/DeleteAccountResult.kt`
- Modify: `shared/src/commonMain/kotlin/ch/teamorg/repository/AuthRepository.kt`
- Modify: `shared/src/commonMain/kotlin/ch/teamorg/data/repository/AuthRepositoryImpl.kt`
- Modify: `composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeAuthRepository.kt`
- Modify: `composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/fakes/FakeAuthRepository.kt`
- Test: `shared/src/jvmTest/kotlin/ch/teamorg/repository/AuthRepositoryDeleteAccountTest.kt` (create)

**Interfaces:**
- Consumes from Task 2: `DELETE /auth/me`, body `{"password":"..."}`; 204 success; 401 wrong
  password; 409 body `{"reason":"owns_clubs","clubs":[...]}`.
- Produces, consumed by Task 4:
  - `sealed interface DeleteAccountResult` with `Success`, `InvalidPassword`,
    `OwnsClubs(clubNames: List<String>)`, `Error(message: String)`
  - `AuthRepository.deleteAccount(password: String): DeleteAccountResult`

**Pattern to follow:** `shared/src/commonMain/kotlin/ch/teamorg/domain/RedeemResult.kt` and
`LinkMemberResult.kt` — the repository returns the sealed type directly, no `Result` wrapper,
and the ViewModel maps it to copy.

**`shared/build.gradle.kts` already has `implementation(libs.ktor.clientMock)` in jvmTest** (added
for `TeamRepositoryLinkResultTest`); no build change is needed. Read that test file for the
MockEngine setup idiom before writing yours.

- [ ] **Step 1: Write the failing test**

Create `shared/src/jvmTest/kotlin/ch/teamorg/repository/AuthRepositoryDeleteAccountTest.kt`:

```kotlin
package ch.teamorg.repository

import ch.teamorg.data.repository.AuthRepositoryImpl
import ch.teamorg.domain.DeleteAccountResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthRepositoryDeleteAccountTest {

    private fun repositoryReturning(
        status: HttpStatusCode,
        body: String = ""
    ): Pair<AuthRepositoryImpl, FakeUserPreferences> {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val prefs = FakeUserPreferences().also { it.saveToken("token123") }
        return AuthRepositoryImpl(client, prefs) to prefs
    }

    @Test
    fun `204 maps to Success and clears the stored token`() = runTest {
        val (repository, prefs) = repositoryReturning(HttpStatusCode.NoContent)
        assertEquals(DeleteAccountResult.Success, repository.deleteAccount("password123"))
        assertEquals(null, prefs.getToken())
    }

    @Test
    fun `200 maps to Success`() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.OK)
        assertEquals(DeleteAccountResult.Success, repository.deleteAccount("password123"))
    }

    @Test
    fun `401 maps to InvalidPassword and keeps the token`() = runTest {
        val (repository, prefs) = repositoryReturning(HttpStatusCode.Unauthorized)
        assertEquals(DeleteAccountResult.InvalidPassword, repository.deleteAccount("wrong"))
        assertEquals("token123", prefs.getToken())
    }

    @Test
    fun `409 maps to OwnsClubs with the club names`() = runTest {
        val (repository, _) = repositoryReturning(
            HttpStatusCode.Conflict,
            """{"reason":"owns_clubs","clubs":["Owner Club","Second Club"]}"""
        )
        val result = repository.deleteAccount("password123")
        assertIs<DeleteAccountResult.OwnsClubs>(result)
        assertEquals(listOf("Owner Club", "Second Club"), result.clubNames)
    }

    @Test
    fun `409 with an unparseable body still maps to OwnsClubs with no names`() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.Conflict, "not json")
        val result = repository.deleteAccount("password123")
        assertIs<DeleteAccountResult.OwnsClubs>(result)
        assertTrue(result.clubNames.isEmpty())
    }

    @Test
    fun `500 maps to Error`() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.InternalServerError)
        assertIs<DeleteAccountResult.Error>(repository.deleteAccount("password123"))
    }
}
```

`FakeUserPreferences` may not exist in `shared/src/jvmTest`. Check first: if
`TeamRepositoryLinkResultTest` uses a fake or a real `UserPreferences`, reuse whatever it uses.
If nothing suitable exists, add a minimal `FakeUserPreferences` in the same package implementing
the `UserPreferences` interface with in-memory storage, and note in your report that you created
it.

The unparseable-409 test matters: a body-shape change on the server must not turn a conflict into
a generic error that tells the user nothing about what to do.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :shared:jvmTest --tests '*AuthRepositoryDeleteAccountTest'`
Expected: FAIL — `DeleteAccountResult` and `deleteAccount` are unresolved.

- [ ] **Step 3: Add the sealed result**

Create `shared/src/commonMain/kotlin/ch/teamorg/domain/DeleteAccountResult.kt`:

```kotlin
package ch.teamorg.domain

/** Outcome of a self-deletion request. The raw HTTP status never reaches the UI. */
sealed interface DeleteAccountResult {
    data object Success : DeleteAccountResult
    /** 401 — the confirmation password did not match. */
    data object InvalidPassword : DeleteAccountResult
    /** 409 — the caller still owns these clubs and must hand them over first. */
    data class OwnsClubs(val clubNames: List<String>) : DeleteAccountResult
    data class Error(val message: String) : DeleteAccountResult
}
```

- [ ] **Step 4: Extend the interface and the implementation**

In `shared/src/commonMain/kotlin/ch/teamorg/repository/AuthRepository.kt` add:

```kotlin
    suspend fun deleteAccount(password: String): DeleteAccountResult
```

with the import for `ch.teamorg.domain.DeleteAccountResult`.

In `shared/src/commonMain/kotlin/ch/teamorg/data/repository/AuthRepositoryImpl.kt` add:

```kotlin
    override suspend fun deleteAccount(password: String): DeleteAccountResult {
        return try {
            val response = client.delete("/auth/me") {
                setBody(DeleteAccountRequest(password))
            }
            when (response.status) {
                HttpStatusCode.NoContent, HttpStatusCode.OK -> {
                    // The token is dead server-side regardless; leaving it on disk would only
                    // produce confusing 401s.
                    logout()
                    DeleteAccountResult.Success
                }
                HttpStatusCode.Unauthorized -> DeleteAccountResult.InvalidPassword
                HttpStatusCode.Conflict -> {
                    val clubs = runCatching { response.body<DeleteAccountConflict>().clubs }
                        .getOrDefault(emptyList())
                    DeleteAccountResult.OwnsClubs(clubs)
                }
                else -> DeleteAccountResult.Error("Delete failed: ${response.status}")
            }
        } catch (e: Exception) {
            DeleteAccountResult.Error(e.message ?: "Delete failed")
        }
    }
```

and, in the same file or beside the other DTOs in `shared/src/commonMain/kotlin/ch/teamorg/domain/`:

```kotlin
@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class DeleteAccountConflict(val reason: String = "", val clubs: List<String> = emptyList())
```

Every field defaulted, because the KMP client sets `ignoreUnknownKeys = true` and must tolerate
server shape drift. Put these next to the other request/response models in `domain/` rather than
inside the repository file, matching how `LoginRequest` and `AuthResponse` are organized.

Imports needed in the impl: `io.ktor.client.request.delete`, `io.ktor.client.call.body`, and the
two new DTOs.

- [ ] **Step 5: Update both test fakes**

`composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeAuthRepository.kt` — add beside the other
settable results:

```kotlin
    var deleteAccountResult: DeleteAccountResult = DeleteAccountResult.Success
    var deleteAccountPasswords: MutableList<String> = mutableListOf()
```

reset them in `reset()`:

```kotlin
        deleteAccountResult = DeleteAccountResult.Success
        deleteAccountPasswords = mutableListOf()
```

and implement:

```kotlin
    override suspend fun deleteAccount(password: String): DeleteAccountResult {
        deleteAccountPasswords.add(password)
        return deleteAccountResult
    }
```

`composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/fakes/FakeAuthRepository.kt` — add the same
override, returning `DeleteAccountResult.Success`. That fake exists to satisfy the interface for
Compose screen tests; it needs no recording.

- [ ] **Step 6: Run the tests and confirm they pass**

Run, one at a time, in the foreground:

```bash
./gradlew :shared:jvmTest --tests '*AuthRepositoryDeleteAccountTest'
./gradlew :shared:jvmTest
./gradlew :composeApp:testDebugUnitTest
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Expected: the new test 6/6; `shared:jvmTest` green; `composeApp` unit tests green (adding an
interface method breaks compilation of every fake — if a third implementor appears that this plan
did not list, implement it the same way and report it); iOS framework compiles.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/ch/teamorg/domain/DeleteAccountResult.kt \
        shared/src/commonMain/kotlin/ch/teamorg/domain/ \
        shared/src/commonMain/kotlin/ch/teamorg/repository/AuthRepository.kt \
        shared/src/commonMain/kotlin/ch/teamorg/data/repository/AuthRepositoryImpl.kt \
        shared/src/jvmTest/kotlin/ch/teamorg/repository/AuthRepositoryDeleteAccountTest.kt \
        composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeAuthRepository.kt \
        composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/fakes/FakeAuthRepository.kt
git commit -m "feat(shared): typed account-deletion result"
```

---

### Task 4: Mobile UI — profile entry, confirm dialog, disclosure

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt:273-301`
- Modify: `composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt:36`
- Test: `composeApp/src/commonTest/kotlin/ch/teamorg/ui/team/PlayerProfileViewModelTest.kt` (create)

**Interfaces:**
- Consumes from Task 3: `AuthRepository.deleteAccount(password): DeleteAccountResult`,
  `FakeAuthRepository.deleteAccountResult` / `.deleteAccountPasswords`.
- Produces: `PlayerProfileState` gains `showDeleteDialog: Boolean`, `deleteInProgress: Boolean`,
  `deleteError: String?`, `accountDeleted: Boolean`; the ViewModel gains
  `openDeleteDialog()`, `closeDeleteDialog()`, `deleteAccount(password: String)`.

**Two facts that contradict a natural assumption — read before designing the UI:**
1. **There is no Logout button anywhere in the app's main UI.** `TeamorgApp.kt:112` defines an
   `onLogout` lambda, but `AppNavigation` passes it only to `InviteScreen` (for the
   wrong-account case). So the delete entry cannot be placed "below Logout" — it goes in its own
   destructive section. Do NOT add a logout button; that is a separate gap and out of scope.
2. `PlayerProfileScreen` is used both as the bottom-nav profile tab (`isNavProfile = true`) and
   as another member's detail page (`isNavProfile = false`). The delete entry must be gated on
   `isNavProfile && state.isOwnProfile` — never offer to delete while looking at someone else.

Place the entry near the existing "Leave team" button (`PlayerProfileScreen.kt:385`), following
its styling for a destructive action.

- [ ] **Step 1: Write the failing test**

Create `composeApp/src/commonTest/kotlin/ch/teamorg/ui/team/PlayerProfileViewModelTest.kt`.
First read an existing sibling — `composeApp/src/commonTest/kotlin/ch/teamorg/ui/team/TeamRosterViewModelTest.kt`
— for the exact dispatcher setup and fake wiring idiom, and construct
`PlayerProfileViewModel` with the same fakes it uses plus `FakeAuthRepository`:

```kotlin
package ch.teamorg.ui.team

import ch.teamorg.domain.DeleteAccountResult
import ch.teamorg.fake.FakeAuthRepository
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerProfileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var viewModel: PlayerProfileViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
        viewModel = buildViewModel(authRepository)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `successful deletion sets accountDeleted and clears progress`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.Success

        viewModel.deleteAccount("password123")

        viewModel.state.value.accountDeleted.shouldBeTrue()
        viewModel.state.value.deleteInProgress.shouldBeFalse()
        viewModel.state.value.deleteError shouldBe null
        authRepository.deleteAccountPasswords shouldBe listOf("password123")
    }

    @Test
    fun `invalid password maps to a sentence and keeps the dialog open`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.InvalidPassword
        viewModel.openDeleteDialog()

        viewModel.deleteAccount("wrong")

        viewModel.state.value.deleteError shouldBe "That password is incorrect."
        viewModel.state.value.showDeleteDialog.shouldBeTrue()
        viewModel.state.value.accountDeleted.shouldBeFalse()
    }

    @Test
    fun `owning a club maps to a sentence naming the club`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.OwnsClubs(listOf("Owner Club"))
        viewModel.openDeleteDialog()

        viewModel.deleteAccount("password123")

        viewModel.state.value.deleteError shouldBe
            "You own Owner Club. Transfer ownership to another club manager, or delete the club, before deleting your account."
        viewModel.state.value.showDeleteDialog.shouldBeTrue()
    }

    @Test
    fun `owning several clubs lists them all`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.OwnsClubs(listOf("A", "B"))

        viewModel.deleteAccount("password123")

        viewModel.state.value.deleteError shouldBe
            "You own A, B. Transfer ownership to another club manager, or delete the club, before deleting your account."
    }

    @Test
    fun `a conflict with no club names still gives an actionable sentence`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.OwnsClubs(emptyList())

        viewModel.deleteAccount("password123")

        viewModel.state.value.deleteError shouldBe
            "You still own a club. Transfer ownership to another club manager, or delete the club, before deleting your account."
    }

    @Test
    fun `a generic error maps to the retry sentence`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.Error("boom")

        viewModel.deleteAccount("password123")

        viewModel.state.value.deleteError shouldBe "Couldn't delete your account. Please try again."
    }

    @Test
    fun `opening the dialog clears a stale error`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.InvalidPassword
        viewModel.deleteAccount("wrong")
        viewModel.state.value.deleteError shouldBe "That password is incorrect."

        viewModel.closeDeleteDialog()
        viewModel.openDeleteDialog()

        viewModel.state.value.deleteError shouldBe null
        viewModel.state.value.showDeleteDialog.shouldBeTrue()
    }
}
```

`buildViewModel(authRepository)` is a private helper you write in this test file: it constructs
`PlayerProfileViewModel` with the fakes for its existing five dependencies
(`TeamRepository`, `UserPreferences`, `AbwesenheitRepository`, `AttendanceRepository`,
`EventRepository` — see `UiModule.kt:36`) plus the new `AuthRepository`. Use the fakes that
already exist under `composeApp/src/commonTest/kotlin/ch/teamorg/fake/`; if a needed fake is
missing (e.g. for `UserPreferences` or `EventRepository`), find what `TeamRosterViewModelTest`
or another existing ViewModel test uses and reuse it rather than writing a new one.

The empty-club-names case is deliberate: the server always sends names, but the client's 409
fallback yields an empty list, and a message saying "You own ." would be a bug users see.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*PlayerProfileViewModelTest'`
Expected: FAIL — `deleteAccount`, `openDeleteDialog`, and the new state fields are unresolved.

- [ ] **Step 3: Extend the ViewModel**

In `PlayerProfileViewModel.kt`, add to `PlayerProfileState`:

```kotlin
    val showDeleteDialog: Boolean = false,
    val deleteInProgress: Boolean = false,
    val deleteError: String? = null,
    val accountDeleted: Boolean = false
```

Add `private val authRepository: AuthRepository` as the last constructor parameter, with the
import `ch.teamorg.repository.AuthRepository`.

Add the actions:

```kotlin
    fun openDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = true, deleteError = null) }
    }

    fun closeDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = false, deleteError = null) }
    }

    fun deleteAccount(password: String) {
        viewModelScope.launch {
            _state.update { it.copy(deleteInProgress = true, deleteError = null) }
            when (val result = authRepository.deleteAccount(password)) {
                DeleteAccountResult.Success ->
                    _state.update {
                        it.copy(deleteInProgress = false, showDeleteDialog = false, accountDeleted = true)
                    }
                DeleteAccountResult.InvalidPassword ->
                    _state.update {
                        it.copy(deleteInProgress = false, deleteError = "That password is incorrect.")
                    }
                is DeleteAccountResult.OwnsClubs ->
                    _state.update {
                        it.copy(deleteInProgress = false, deleteError = ownsClubsMessage(result.clubNames))
                    }
                is DeleteAccountResult.Error ->
                    _state.update {
                        it.copy(
                            deleteInProgress = false,
                            deleteError = "Couldn't delete your account. Please try again."
                        )
                    }
            }
        }
    }

    private fun ownsClubsMessage(clubNames: List<String>): String {
        val subject = if (clubNames.isEmpty()) "a club" else clubNames.joinToString(", ")
        return "You own $subject. Transfer ownership to another club manager, or delete the club, " +
            "before deleting your account."
    }
```

with the import `ch.teamorg.domain.DeleteAccountResult`. Note `ownsClubsMessage` produces
"You still own a club." for the empty case — write it so the assertion in the test matches
exactly, i.e. special-case the empty list to the full sentence
`"You still own a club. Transfer ownership to another club manager, or delete the club, before deleting your account."`

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*PlayerProfileViewModelTest'`
Expected: PASS, 7 tests.

- [ ] **Step 5: Wire the new dependency into Koin**

In `composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt:36`, add one `get()`:

```kotlin
    factory { PlayerProfileViewModel(get(), get(), get(), get(), get(), get()) }
```

The `AuthRepository` binding already exists in the Koin graph (`AuthViewModel`, `LoginViewModel`
and others take it), so no new `single` is needed. Verify by running the app's DI check —
`./gradlew :composeApp:testDebugUnitTest` — and by grepping for the existing `AuthRepository`
binding in the shared/data Koin module.

- [ ] **Step 6: Add the profile entry and the confirm dialog**

In `PlayerProfileScreen.kt`:

Add local state beside the existing dialog flags (line ~45):

```kotlin
    var deletePassword by remember { mutableStateOf("") }
    var disclosureExpanded by remember { mutableStateOf(false) }
```

Navigate away once the account is gone — add beside the existing `LaunchedEffect(state.leftTeam)`:

```kotlin
    LaunchedEffect(state.accountDeleted) {
        if (state.accountDeleted) onAccountDeleted()
    }
```

Add `onAccountDeleted: () -> Unit = {}` as a parameter of `PlayerProfileScreen`, after
`onLeftTeam`.

Add the entry near the "Leave team" button, gated so it appears only on the user's own profile
tab:

```kotlin
                            if (isNavProfile && state.isOwnProfile) {
                                TextButton(
                                    onClick = {
                                        deletePassword = ""
                                        disclosureExpanded = false
                                        viewModel.openDeleteDialog()
                                    }
                                ) {
                                    Text(
                                        "Delete account",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
```

Add the dialog at the end of the composable, beside the leave-confirmation dialog:

```kotlin
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.deleteInProgress) viewModel.closeDeleteDialog() },
            title = { Text("Delete account", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "This permanently deletes your personal data.",
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { disclosureExpanded = !disclosureExpanded }) {
                            Icon(Icons.Outlined.Info, contentDescription = "What gets deleted")
                        }
                    }
                    if (disclosureExpanded) {
                        Spacer(Modifier.height(8.dp))
                        Text("Deleted:", fontWeight = FontWeight.Bold)
                        Text("Your email address and name, profile picture, attendance replies, absences, notifications and their settings, and your team and club memberships.")
                        Spacer(Modifier.height(8.dp))
                        Text("Kept for your team:", fontWeight = FontWeight.Bold)
                        Text("Events you created and attendance you recorded stay with your team. Your name is replaced there.")
                        if (state.isCoachOrManager) {
                            Spacer(Modifier.height(8.dp))
                            Text("Teams you coach will have no coach until a club manager assigns one.")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Your password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !state.deleteInProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.deleteError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("This cannot be undone.", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAccount(deletePassword) },
                    enabled = deletePassword.isNotBlank() && !state.deleteInProgress
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.closeDeleteDialog() },
                    enabled = !state.deleteInProgress
                ) { Text("Cancel") }
            }
        )
    }
```

Add whatever imports this needs (`AlertDialog`, `OutlinedTextField`, `IconButton`, `Icon`,
`Icons.Outlined.Info`, `PasswordVisualTransformation`, `Spacer`, `Column`, `Row`, `Alignment`,
`Modifier.fillMaxWidth`, `dp`) — match the import style already in the file, and check whether
the file's Material icons dependency exposes `Icons.Outlined.Info`; if not, use an icon that is
already imported in this file rather than adding a dependency.

Note the dialog uses `state.isCoachOrManager` for the last-coach warning. That flag already
exists in `PlayerProfileState` and is the closest available signal; it is a superset (a club
manager who coaches nothing also sees the line), which is acceptable for a warning.

- [ ] **Step 7: Wire the navigation callback**

In `composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt`, in the
`Screen.Profile ->` branch (~line 273-301) where `PlayerProfileScreen(...)` is called with
`isNavProfile = true`, add:

```kotlin
                    onAccountDeleted = onLogout,
```

`onLogout` is already a parameter of `AppNavigation` (line 58) and is wired in `TeamorgApp.kt:112`
to clear auth state and return to login — which is exactly the post-deletion behaviour. Do not
add it to the `is Screen.PlayerProfile ->` branch (someone else's profile).

- [ ] **Step 8: Run the tests and the compile gates**

Run, one at a time, in the foreground:

```bash
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Expected: all green, 0 failures.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileViewModel.kt \
        composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileScreen.kt \
        composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt \
        composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt \
        composeApp/src/commonTest/kotlin/ch/teamorg/ui/team/PlayerProfileViewModelTest.kt
git commit -m "feat(mobile): delete account from the profile tab"
```

- [ ] **Step 10: Record what still needs a human**

State in your report that the banner → dialog → disclosure → delete → 409 flow is verified by
unit tests and compilation only; no Compose UI test covers this screen (the repo has no harness
for it). An on-device walkthrough on Android and iOS is owed.

---

### Task 5: Web UI — profile section and confirm page (German + English)

**Files:**
- Modify: `admin/src/lib/i18n/index.ts` (the `Dict` type's `profile` block, plus the `de` and `en` objects)
- Modify: `admin/src/lib/server/guards.ts` (`ApiError` carries the parsed payload)
- Modify: `admin/src/lib/server/api.ts` (a DELETE that sends a body and surfaces the error payload)
- Modify: `admin/src/routes/(shell)/app/profile/+page.svelte` (link to the confirm page)
- Create: `admin/src/routes/(shell)/app/profile/delete/+page.server.ts`
- Create: `admin/src/routes/(shell)/app/profile/delete/+page.svelte`

**Interfaces:**
- Consumes from Task 2: `DELETE /auth/me`, body `{"password":"..."}`, 204 / 401 / 409 with
  `{"reason":"owns_clubs","clubs":[...]}`.
- Produces: nothing consumed by later tasks.

**Facts you need:**
- The admin app is **i18n'd**: `admin/src/lib/i18n/index.ts` exports a `Dict` type plus `de` and
  `en` objects and `messages: Record<Locale, Dict>`. `Dict` is a type, not a partial — adding a
  key to it without adding it to **both** locale objects fails `npm run check`.
- The existing profile page reads its copy via
  `getMessages(resolveLocale(cookies.get('lang'))).profile` and uses SvelteKit **form actions**
  (`admin/src/routes/(shell)/app/profile/+page.server.ts`). Follow that exact shape.
- `logout(cookies)` from `$lib/server/auth` clears the session; `admin/src/routes/logout/+page.server.ts`
  is the pattern for clearing then redirecting.
- `apiDelete` currently sends **no body** and `ApiError` carries only a status, so neither the
  password nor the 409 club names can travel through them as they stand.
- The app is Svelte 5 runes. Match the surrounding components' idiom (`$props()`, `$state`), do
  not introduce Svelte 4 syntax.

- [ ] **Step 1: Add the i18n keys**

In `admin/src/lib/i18n/index.ts`, extend the `profile` block of the `Dict` type (after
`passwordChangeFailed`):

```ts
		deleteSectionTitle: string;
		deleteSectionBody: string;
		deleteSectionLink: string;
		deleteTitle: string;
		deleteIntro: string;
		deleteRemovedTitle: string;
		deleteRemovedBody: string;
		deleteKeptTitle: string;
		deleteKeptBody: string;
		deleteCoachWarning: string;
		deleteIrreversible: string;
		deletePasswordLabel: string;
		deleteButton: string;
		deleteCancel: string;
		deleteWrongPassword: string;
		deleteOwnsClubs: string;
		deleteFailed: string;
```

In the `de` object's `profile` block:

```ts
		deleteSectionTitle: 'Konto löschen',
		deleteSectionBody: 'Dein Konto und deine persönlichen Daten endgültig löschen.',
		deleteSectionLink: 'Konto löschen',
		deleteTitle: 'Konto endgültig löschen',
		deleteIntro: 'Wenn du dein Konto löschst, werden deine persönlichen Daten sofort entfernt.',
		deleteRemovedTitle: 'Das wird gelöscht',
		deleteRemovedBody:
			'E-Mail-Adresse und Name, Profilbild, alle Anwesenheits-Rückmeldungen und Abwesenheiten, Benachrichtigungen und deren Einstellungen, Team- und Vereins-Mitgliedschaften.',
		deleteKeptTitle: 'Das bleibt für dein Team erhalten',
		deleteKeptBody:
			'Events, die du erstellt hast, und Anwesenheiten, die du erfasst hast, bleiben für dein Team erhalten. Dein Name wird dort durch "Gelöschtes Konto" ersetzt.',
		deleteCoachWarning:
			'Teams, die du als Trainer betreust, haben danach keinen Trainer mehr, bis ein Vereinsmanager einen neuen zuweist.',
		deleteIrreversible: 'Das kann nicht rückgängig gemacht werden.',
		deletePasswordLabel: 'Passwort bestätigen',
		deleteButton: 'Konto löschen',
		deleteCancel: 'Abbrechen',
		deleteWrongPassword: 'Das Passwort ist falsch.',
		deleteOwnsClubs:
			'Du bist Besitzer von {clubs}. Übertrage die Besitzer-Rolle an einen anderen Vereinsmanager oder lösche den Verein, bevor du dein Konto löschst.',
		deleteFailed: 'Konto konnte nicht gelöscht werden. Bitte versuche es erneut.',
```

In the `en` object's `profile` block:

```ts
		deleteSectionTitle: 'Delete account',
		deleteSectionBody: 'Permanently delete your account and personal data.',
		deleteSectionLink: 'Delete account',
		deleteTitle: 'Permanently delete your account',
		deleteIntro: 'Deleting your account removes your personal data immediately.',
		deleteRemovedTitle: 'What gets deleted',
		deleteRemovedBody:
			'Your email address and name, profile picture, all attendance replies and absences, notifications and their settings, and your team and club memberships.',
		deleteKeptTitle: 'What stays with your team',
		deleteKeptBody:
			'Events you created and attendance you recorded stay with your team. Your name is replaced there with "Gelöschtes Konto".',
		deleteCoachWarning:
			'Teams you coach will have no coach until a club manager assigns a new one.',
		deleteIrreversible: 'This cannot be undone.',
		deletePasswordLabel: 'Confirm your password',
		deleteButton: 'Delete account',
		deleteCancel: 'Cancel',
		deleteWrongPassword: 'That password is incorrect.',
		deleteOwnsClubs:
			'You own {clubs}. Transfer ownership to another club manager, or delete the club, before deleting your account.',
		deleteFailed: 'Could not delete your account. Please try again.',
```

`{clubs}` is substituted with `.replace('{clubs}', names.join(', '))` in the server action.
Check whether the codebase already has an interpolation helper for messages with placeholders
(grep for `replace('{`) and use it if so.

- [ ] **Step 2: Let the API helper send a body and surface the error payload**

In `admin/src/lib/server/guards.ts`, add an optional third constructor parameter to `ApiError`:

```ts
export class ApiError extends Error {
	constructor(
		public readonly status: number,
		message: string,
		public readonly payload?: unknown
	) {
		super(message);
		this.name = 'ApiError';
	}
}
```

Optional and last, so every existing `new ApiError(status, message)` call keeps compiling.

In `admin/src/lib/server/api.ts`, add beside `apiDelete`:

```ts
/**
 * DELETE with a JSON body, attaching the parsed error body to the thrown ApiError. Needed by
 * account deletion: the password travels in the body and a 409 carries the club names that the
 * user has to act on.
 */
export async function apiDeleteJson(path: string, token: string, body: unknown): Promise<void> {
	const res = await fetch(`${API_BASE}${path}`, {
		method: 'DELETE',
		headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
		body: JSON.stringify(body)
	});
	if (!res.ok) {
		const text = await res.text();
		let payload: unknown = undefined;
		try {
			payload = text ? JSON.parse(text) : undefined;
		} catch {
			payload = undefined;
		}
		throw new ApiError(res.status, `API error: ${res.status} ${res.statusText}`, payload);
	}
}
```

- [ ] **Step 3: Create the confirm page's server module**

Create `admin/src/routes/(shell)/app/profile/delete/+page.server.ts`:

```ts
import { fail, redirect } from '@sveltejs/kit';
import { requireUser, ApiError } from '$lib/server/guards';
import { apiDeleteJson } from '$lib/server/api';
import { logout } from '$lib/server/auth';
import { getMessages, resolveLocale } from '$lib/i18n';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
	requireUser(locals);
	// Drives the "your teams will have no coach" warning. locals.user already carries the
	// caller's team roles, so this needs no extra request.
	return { isCoach: locals.user!.teamRoles.some((r) => r.role === 'coach') };
};

export const actions: Actions = {
	default: async ({ request, locals, cookies }) => {
		requireUser(locals);
		const m = getMessages(resolveLocale(cookies.get('lang'))).profile;
		const form = await request.formData();
		const password = form.get('password') as string;

		if (!password) {
			return fail(400, { error: m.deleteWrongPassword });
		}

		try {
			await apiDeleteJson('/auth/me', locals.token!, { password });
		} catch (e) {
			if (e instanceof ApiError) {
				if (e.status === 401) return fail(401, { error: m.deleteWrongPassword });
				if (e.status === 409) {
					const clubs = (e.payload as { clubs?: string[] } | undefined)?.clubs ?? [];
					const names = clubs.length > 0 ? clubs.join(', ') : '—';
					return fail(409, { error: m.deleteOwnsClubs.replace('{clubs}', names) });
				}
			}
			return fail(500, { error: m.deleteFailed });
		}

		logout(cookies);
		throw redirect(303, '/login');
	}
};
```

Verify `requireUser`'s actual signature and what `locals.user` exposes — `guards.ts` defines
`teamRolesFor(user, teamId)` and `user.teamRoles` with `{ teamId, role }` entries, so the
`isCoach` expression above should compile; adjust it to the real shape if not. The redirect must
be **outside** the `try`, because SvelteKit implements `redirect` by throwing and a `catch` would
swallow it.

- [ ] **Step 4: Create the confirm page**

Create `admin/src/routes/(shell)/app/profile/delete/+page.svelte`. Read
`admin/src/routes/(shell)/app/profile/+page.svelte` first and match its component vocabulary,
Tailwind classes, form-action wiring and heading styles; do not invent a new visual language.
The page must contain:

- the `m.profile.deleteTitle` heading and `deleteIntro`
- a "what gets deleted" block (`deleteRemovedTitle` / `deleteRemovedBody`)
- a "what stays" block (`deleteKeptTitle` / `deleteKeptBody`)
- `deleteCoachWarning`, rendered only when `data.isCoach`
- `deleteIrreversible`, visually emphasised
- a `method="POST"` form with a `type="password"` input named `password`, a destructive submit
  button labelled `m.profile.deleteButton`, and a cancel link back to `/app/profile` labelled
  `deleteCancel`
- `form?.error` rendered as an error message when present

Get the locale messages the way the sibling profile page does. If that page receives `m` from a
parent layout's `data`, do the same rather than calling `getMessages` in the component.

- [ ] **Step 5: Link it from the profile page**

In `admin/src/routes/(shell)/app/profile/+page.svelte`, add a section at the bottom, visually
separated from the password form and styled as a destructive zone: the
`m.profile.deleteSectionTitle` heading, `deleteSectionBody` as explanatory text, and an anchor to
`/app/profile/delete` labelled `deleteSectionLink`.

A link, not an inline submit button: a mis-click on the profile page must not be one step away
from deleting the account.

- [ ] **Step 6: Type-check**

Run: `cd admin && npm run check`
Expected: **0 errors.** Around 47 warnings are pre-existing in untouched files; the count must not
grow. If `Dict` complains about a missing key, a locale object is missing one of the keys from
step 1.

- [ ] **Step 7: Commit**

```bash
git add admin/src/lib/i18n/index.ts admin/src/lib/server/guards.ts admin/src/lib/server/api.ts \
        "admin/src/routes/(shell)/app/profile/+page.svelte" \
        "admin/src/routes/(shell)/app/profile/delete/+page.server.ts" \
        "admin/src/routes/(shell)/app/profile/delete/+page.svelte"
git commit -m "feat(web): account deletion page"
```

- [ ] **Step 8: Record what still needs a human**

State in your report that no automated test covers the web flow: the admin app's E2E suite is
Playwright against production, which is read-only, so a destructive flow cannot be exercised
there. A browser walkthrough of profile → delete page → wrong password → 409 → success is owed.

---

### Task 6: Document the store answer

**Files:**
- Modify: `docs/store-data-safety.md` (created by the transport-encryption plan; if that plan has
  not run yet, create the file with just this section)

**Interfaces:** consumes the endpoint and both UIs from Tasks 2, 4 and 5.

- [ ] **Step 1: Append the deletion section**

```markdown
## Account deletion

Both stores require an account-deletion path. Apple guideline 5.1.1(v) requires it to be
initiated **inside the app**; Google Play additionally wants a URL.

| Requirement | Where it is satisfied |
|---|---|
| In-app deletion (iOS + Android) | Profile tab → "Delete account" → password-confirmed dialog (`composeApp/.../ui/team/PlayerProfileScreen.kt`) |
| Web URL for the Play Console form | `https://app.teamorg.ch/app/profile/delete` (sign-in required) |
| Endpoint | `DELETE /auth/me`, password in the body, rate-limited with the auth bucket |

**What deletion does:** personal data is deleted outright — email, name, avatar, attendance
replies, absences, notifications and their settings, team and club memberships. The `users` row
itself is retained in anonymized form (`deleted-<uuid>@deleted.invalid`, name "Gelöschtes Konto")
because event authorship, recorded attendance and the audit log reference it under
`ON DELETE RESTRICT`. The account cannot be logged into and every existing session token is
rejected immediately.

**Preconditions:** a user who still owns a club is refused with a 409 and told to transfer
ownership or delete the club first — deleting them silently would leave a billed club without an
owner.
```

- [ ] **Step 2: Commit**

```bash
git add docs/store-data-safety.md
git commit -m "docs(store): record the account-deletion answer"
```

---

## Self-Review

**Spec coverage:** §1 schema → Task 1 step 3. §2 session invalidation → Task 1 steps 5-6, with
the token-rejection test in step 1. §3 deletion transaction → Task 2 step 3. §4 endpoint → Task 2
step 5. §5 web UI → Task 5. §6 mobile UI → Task 4. Testing section → the test steps of Tasks 1-4
plus the "needs a human" records in Tasks 4 and 5. Docs → Task 6.

**Corrections to the spec, made deliberately here:**
1. The spec said the mobile delete entry goes "below Logout". **There is no Logout button in the
   app's main UI** — `onLogout` reaches only `InviteScreen`. Task 4 places the entry in its own
   destructive section near "Leave team" and explicitly forbids adding a logout button as
   out-of-scope.
2. The spec described the admin copy as German. The admin app is **i18n'd with a `Dict` type**,
   so German-only strings would not type-check; Task 5 adds both `de` and `en`.
3. The spec's mobile error copy for the conflict case assumed club names are always present. The
   client's 409 fallback can yield an empty list, so Task 4 specifies and tests a distinct
   sentence for that case.
4. `avatarPath` was added to `UserDeletionRepository` — the spec had the route deleting the avatar
   file but the scrub nulls `avatar_url`, so the path must be read before the transaction.

**Placeholder scan:** no TBDs. Two places deliberately instruct the implementer to read a file
and match reality (Exposed column names in Task 2, the sibling profile page's component
vocabulary in Task 5) rather than guess at values this plan cannot verify without writing the
code; each says exactly what to look for and what to do with the answer.

**Type consistency:** `DeleteAccountOutcome` (`Deleted` / `OwnsClubs`) is server-side;
`DeleteAccountResult` (`Success` / `InvalidPassword` / `OwnsClubs` / `Error`) is client-side.
`DeleteAccountRequest(password)` is the wire shape on both sides. `deleteAccountResult` /
`deleteAccountPasswords` are the fake's members in Tasks 3 and 4. State field names
(`showDeleteDialog`, `deleteInProgress`, `deleteError`, `accountDeleted`) are identical in the
test, the ViewModel and the screen.
