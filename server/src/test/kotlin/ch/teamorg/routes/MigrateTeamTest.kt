package ch.teamorg.routes

import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.TeamSvLinksTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.infra.PushService
import ch.teamorg.infra.SVGame
import ch.teamorg.infra.SVTeam
import ch.teamorg.infra.SwissVolleyClient
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MigrateTeamTest : IntegrationTestBase() {

    // --- stubs (migrate-to never touches SwissVolley; empty stub satisfies DI) ---------
    private class StubSwissVolleyClient : SwissVolleyClient {
        override suspend fun listTeams(apiKey: String): List<SVTeam> = emptyList()
        override suspend fun listGames(apiKey: String): List<SVGame> = emptyList()
    }

    private class StubPushService : PushService {
        override suspend fun sendToUsers(
            userIds: List<String>,
            title: String,
            body: String,
            data: Map<String, String>
        ) {
            // no-op
        }
    }

    private fun koinOverride() = module {
        single<SwissVolleyClient> { StubSwissVolleyClient() }
        single<PushService> { StubPushService() }
    }

    // --- HTTP helpers -----------------------------------------------------------------

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val c = createJsonClient()
        return c.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "User $email"))
        }.body()
    }

    /** Creates a club owned by [token] (creator becomes club_manager) and returns its id. */
    private suspend fun ApplicationTestBuilder.createClub(token: String, name: String): String {
        val c = createJsonClient()
        return c.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("$name Club"))
        }.body<ch.teamorg.domain.models.Club>().id
    }

    private suspend fun ApplicationTestBuilder.migrate(
        clubId: String,
        sourceTeamId: UUID,
        targetTeamId: UUID,
        token: String
    ) = createJsonClient().post("/clubs/$clubId/teams/$sourceTeamId/migrate-to") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(MigrateTeamRequest(targetTeamId.toString()))
    }

    // --- DB seeding -------------------------------------------------------------------

    private fun createUser(email: String): UUID = transaction {
        UsersTable.insert {
            it[UsersTable.email] = email
            it[passwordHash] = "x"
            it[displayName] = "U $email"
        } get UsersTable.id
    }

    private fun createTeam(clubId: UUID, name: String, gamesSync: Boolean = false): UUID = transaction {
        TeamsTable.insert {
            it[TeamsTable.clubId] = clubId
            it[TeamsTable.name] = name
            it[gamesSyncEnabled] = gamesSync
        } get TeamsTable.id
    }

    private fun addRole(teamId: UUID, userId: UUID, role: String) = transaction {
        TeamRolesTable.insert {
            it[TeamRolesTable.userId] = userId
            it[TeamRolesTable.teamId] = teamId
            it[TeamRolesTable.role] = role
        }
    }

    /** SV link for a team; pass [deprecated] = true to mark it deprecated. */
    private fun addLink(teamId: UUID, svTeamId: Int, deprecated: Boolean) = transaction {
        TeamSvLinksTable.insert {
            it[TeamSvLinksTable.teamId] = teamId
            it[TeamSvLinksTable.svTeamId] = svTeamId
            if (deprecated) it[deprecatedAt] = Instant.now()
        }
    }

    // --- query helpers ----------------------------------------------------------------

    private fun teamRoleUserIds(teamId: UUID): Set<Pair<UUID, String>> = transaction {
        TeamRolesTable.selectAll()
            .where { TeamRolesTable.teamId eq teamId }
            .map { it[TeamRolesTable.userId]!! to it[TeamRolesTable.role] }
            .toSet()
    }

    private fun teamArchivedAt(teamId: UUID): Instant? = transaction {
        TeamsTable.selectAll().where { TeamsTable.id eq teamId }
            .single()[TeamsTable.archivedAt]
    }

    private fun predecessorOf(teamId: UUID): UUID? = transaction {
        TeamsTable.selectAll().where { TeamsTable.id eq teamId }
            .single()[TeamsTable.predecessorTeamId]
    }

    // --- tests ------------------------------------------------------------------------

    @Test
    fun `club_manager migrates deprecated source into live target moving members with dedupe`() =
        withTeamorgTestApplication(koinOverride()) {
            val manager = register("migrate_ok@example.com")
            promoteToSuperAdmin(manager.userId)
            val clubId = createClub(manager.token, "MigrateOk")
            val clubUuid = UUID.fromString(clubId)

            // Source = deprecated SV-linked team with two members.
            val source = createTeam(clubUuid, "Source")
            addLink(source, svTeamId = 101, deprecated = true)
            val coach = createUser("mig_coach@example.com")
            val playerA = createUser("mig_playerA@example.com")
            addRole(source, coach, "coach")
            addRole(source, playerA, "player")

            // Target = live SV-linked team that ALREADY has the coach (dedupe case).
            val target = createTeam(clubUuid, "Target")
            addLink(target, svTeamId = 202, deprecated = false)
            addRole(target, coach, "coach")

            val response = migrate(clubId, source, target, manager.token)
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<MigrateTeamResponse>()

            // Only playerA is newly inserted on the target (the coach already existed).
            assertEquals(1, body.movedMembers)
            assertEquals(target.toString(), body.targetTeamId)

            // Target has both members; no duplicate coach row.
            assertEquals(
                setOf(coach to "coach", playerA to "player"),
                teamRoleUserIds(target)
            )

            // Source archived; target lineage set.
            assertNotNull(teamArchivedAt(source), "source.archived_at set")
            assertEquals(source, predecessorOf(target), "target.predecessor_team_id = source.id")
        }

    @Test
    fun `non-manager caller gets 403 on migrate`() =
        withTeamorgTestApplication(koinOverride()) {
            val manager = register("migrate_403_mgr@example.com")
            promoteToSuperAdmin(manager.userId)
            val clubId = createClub(manager.token, "Migrate403")
            val clubUuid = UUID.fromString(clubId)

            val source = createTeam(clubUuid, "Source")
            addLink(source, svTeamId = 111, deprecated = true)
            val target = createTeam(clubUuid, "Target")
            addLink(target, svTeamId = 222, deprecated = false)

            val outsider = register("migrate_403_outsider@example.com")
            val response = migrate(clubId, source, target, outsider.token)
            assertEquals(HttpStatusCode.Forbidden, response.status)

            // Nothing migrated.
            assertNull(teamArchivedAt(source))
            assertNull(predecessorOf(target))
        }

    @Test
    fun `migrating a non-deprecated source returns 409`() =
        withTeamorgTestApplication(koinOverride()) {
            val manager = register("migrate_live_src@example.com")
            promoteToSuperAdmin(manager.userId)
            val clubId = createClub(manager.token, "MigrateLiveSrc")
            val clubUuid = UUID.fromString(clubId)

            // Source has a LIVE link -> not deprecated.
            val source = createTeam(clubUuid, "Source")
            addLink(source, svTeamId = 131, deprecated = false)
            val target = createTeam(clubUuid, "Target")
            addLink(target, svTeamId = 232, deprecated = false)

            val response = migrate(clubId, source, target, manager.token)
            assertEquals(HttpStatusCode.Conflict, response.status)
            assertNull(teamArchivedAt(source))
        }

    @Test
    fun `migrating a team to itself returns 400`() =
        withTeamorgTestApplication(koinOverride()) {
            val manager = register("migrate_self@example.com")
            promoteToSuperAdmin(manager.userId)
            val clubId = createClub(manager.token, "MigrateSelf")
            val clubUuid = UUID.fromString(clubId)

            val source = createTeam(clubUuid, "Source")
            addLink(source, svTeamId = 141, deprecated = true)

            val response = migrate(clubId, source, source, manager.token)
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `migrating across clubs returns 400`() =
        withTeamorgTestApplication(koinOverride()) {
            val manager = register("migrate_cross@example.com")
            promoteToSuperAdmin(manager.userId)
            val clubId = createClub(manager.token, "MigrateCross")
            val clubUuid = UUID.fromString(clubId)

            val source = createTeam(clubUuid, "Source")
            addLink(source, svTeamId = 151, deprecated = true)

            // Target lives in a different club.
            val otherClub = transaction { ClubsTable.insert { it[name] = "OtherClub" } get ClubsTable.id }
            val target = createTeam(otherClub, "TargetOther")
            addLink(target, svTeamId = 252, deprecated = false)

            val response = migrate(clubId, source, target, manager.token)
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertNull(teamArchivedAt(source))
        }
}
