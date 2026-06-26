package ch.teamorg.infra

import ch.teamorg.db.tables.ClubIntegrationsTable
import ch.teamorg.db.tables.ClubRolesTable
import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.db.tables.NotificationsTable
import ch.teamorg.db.tables.TeamSvLinksTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.test.IntegrationTestBase
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import org.koin.ktor.ext.get
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SwissVolleyRolloverTest : IntegrationTestBase() {

    /** Stub returning a canned team list for listTeams (rollover only reads /indoor/teams). */
    private class StubSwissVolleyClient(
        var teams: List<SVTeam> = emptyList()
    ) : SwissVolleyClient {
        override suspend fun listTeams(apiKey: String): List<SVTeam> = teams
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

    private fun koinOverride(client: SwissVolleyClient) = module {
        single<SwissVolleyClient> { client }
        single<PushService> { StubPushService() }
    }

    private data class Fixture(
        val clubId: UUID,
        val managerId: UUID,
        val teamId: UUID
    )

    /**
     * Seeds a club with a valid integration, a club_manager, and one team carrying an
     * active SV link to [svTeamId].
     */
    private fun seedClubWithLink(svTeamId: Int, suffix: String): Fixture = transaction {
        val clubId = ClubsTable.insert { it[name] = "RolloverClub-$suffix" } get ClubsTable.id
        val managerId = UsersTable.insert {
            it[email] = "rollover-mgr-$suffix@example.com"
            it[passwordHash] = "x"
            it[displayName] = "Mgr $suffix"
        } get UsersTable.id
        ClubRolesTable.insert {
            it[userId] = managerId
            it[ClubRolesTable.clubId] = clubId
            it[role] = "club_manager"
        }
        val teamId = TeamsTable.insert {
            it[TeamsTable.clubId] = clubId
            it[name] = "Team-$suffix"
        } get TeamsTable.id
        TeamSvLinksTable.insert {
            it[TeamSvLinksTable.teamId] = teamId
            it[TeamSvLinksTable.svTeamId] = svTeamId
            it[svLeagueCaption] = "OldLeague"
            it[svGender] = "f"
        }
        ClubIntegrationsTable.insert {
            it[ClubIntegrationsTable.clubId] = clubId
            it[apiKey] = "valid-key"
            it[keyValid] = true
        }
        Fixture(clubId, managerId, teamId)
    }

    private fun linkFor(teamId: UUID, svTeamId: Int) = transaction {
        TeamSvLinksTable.selectAll()
            .where { (TeamSvLinksTable.teamId eq teamId) and (TeamSvLinksTable.svTeamId eq svTeamId) }
            .single()
    }

    private fun countNotifications(userId: UUID, type: String): Long = transaction {
        NotificationsTable.selectAll()
            .where { (NotificationsTable.userId eq userId) and (NotificationsTable.type eq type) }
            .count()
    }

    private fun teamCount(clubId: UUID): Long = transaction {
        TeamsTable.selectAll().where { TeamsTable.clubId eq clubId }.count()
    }

    @Test
    fun `existing link is refreshed with new seasonal id league and gender`() {
        val stub = StubSwissVolleyClient()
        withTeamorgTestApplication(koinOverride(stub)) {
            startApplication()
            val fx = seedClubWithLink(svTeamId = 101, suffix = "refresh")
            stub.teams = listOf(
                SVTeam(
                    teamId = 101,
                    seasonalTeamId = 7777,
                    caption = "Damen 1",
                    gender = "m",
                    league = SVLeague(leagueId = 9, caption = "NewLeague")
                )
            )

            val service = application.get<SwissVolleySyncService>()
            runBlocking { service.refreshClubTeams(fx.clubId) }

            val link = linkFor(fx.teamId, 101)
            assertEquals(7777, link[TeamSvLinksTable.svSeasonalTeamId], "seasonal id updated")
            assertEquals("NewLeague", link[TeamSvLinksTable.svLeagueCaption], "league updated")
            assertEquals("m", link[TeamSvLinksTable.svGender], "gender updated")
            assertNull(link[TeamSvLinksTable.deprecatedAt], "still active")
        }
    }

    @Test
    fun `link whose sv team vanished gets deprecated`() {
        val stub = StubSwissVolleyClient()
        withTeamorgTestApplication(koinOverride(stub)) {
            startApplication()
            val fx = seedClubWithLink(svTeamId = 202, suffix = "vanish")
            // Feed no longer lists svTeamId 202.
            stub.teams = emptyList()

            val service = application.get<SwissVolleySyncService>()
            runBlocking { service.refreshClubTeams(fx.clubId) }

            val link = linkFor(fx.teamId, 202)
            assertNotNull(link[TeamSvLinksTable.deprecatedAt], "deprecated_at set when sv_team_id vanished")
        }
    }

    @Test
    fun `brand-new sv team notifies manager and does not auto-create a team`() {
        val stub = StubSwissVolleyClient()
        withTeamorgTestApplication(koinOverride(stub)) {
            startApplication()
            val fx = seedClubWithLink(svTeamId = 303, suffix = "newteam")
            val teamsBefore = teamCount(fx.clubId)

            // Existing 303 still present + a brand-new 404 not linked anywhere.
            stub.teams = listOf(
                SVTeam(teamId = 303, seasonalTeamId = 3030, caption = "Existing", gender = "f", league = SVLeague(caption = "L")),
                SVTeam(teamId = 404, seasonalTeamId = 4040, caption = "New Team", gender = "m", league = SVLeague(caption = "L2"))
            )

            val service = application.get<SwissVolleySyncService>()
            runBlocking { service.refreshClubTeams(fx.clubId) }

            // Manager alerted about the new SV team.
            assertEquals(1, countNotifications(fx.managerId, "sv_team_available"))

            // No team auto-created: count unchanged and no link to 404.
            assertEquals(teamsBefore, teamCount(fx.clubId), "no team auto-created")
            val newLinks = transaction {
                TeamSvLinksTable.selectAll()
                    .where { TeamSvLinksTable.svTeamId eq 404 }
                    .count()
            }
            assertEquals(0, newLinks, "no link created for the new sv team")
        }
    }
}
