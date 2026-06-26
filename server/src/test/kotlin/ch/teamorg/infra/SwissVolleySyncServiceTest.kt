package ch.teamorg.infra

import ch.teamorg.db.tables.ClubIntegrationsTable
import ch.teamorg.db.tables.ClubRolesTable
import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.NotificationsTable
import ch.teamorg.db.tables.SvSyncStateTable
import ch.teamorg.db.tables.SystemUsers
import ch.teamorg.db.tables.TeamRolesTable
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
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SwissVolleySyncServiceTest : IntegrationTestBase() {

    private val EXTERNAL_SOURCE = "swissvolley"

    /** Stub client returning a canned game list (or throwing) regardless of api key. */
    private class StubSwissVolleyClient(
        private val invalidKey: Boolean = false,
        var games: List<SVGame> = emptyList()
    ) : SwissVolleyClient {
        override suspend fun listTeams(apiKey: String): List<SVTeam> = emptyList()
        override suspend fun listGames(apiKey: String): List<SVGame> {
            if (invalidKey) throw InvalidApiKeyException()
            return games
        }
    }

    /** No-op push so notify* paths never make real HTTP calls. */
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

    // --- DB seeding helpers (sync engine reads the DB directly, so seed it directly) ----

    /** Future start so synced games are neither finished nor live (facts stay mutable). */
    // Truncate to micros: Postgres timestamptz has microsecond precision, so a higher-precision
    // clock (e.g. CI's Linux JVM gives nanos) would make the stored value != the parsed expectation.
    private fun futureUtc(daysFromNow: Long): String =
        Instant.now().truncatedTo(ChronoUnit.MICROS).plus(Duration.ofDays(daysFromNow)).toString()

    private data class Fixture(
        val clubId: UUID,
        val teamId: UUID,
        val coachId: UUID
    )

    /**
     * Seeds: club, a coach user + team with a coach role, an SV link (games_sync_enabled),
     * and a valid integration. Returns the ids the tests assert against.
     */
    private fun seedSyncedClub(svTeamId: Int, suffix: String): Fixture = transaction {
        val clubId = ClubsTable.insert { it[name] = "SyncClub-$suffix" } get ClubsTable.id
        val coachId = UsersTable.insert {
            it[email] = "coach-$suffix@example.com"
            it[passwordHash] = "x"
            it[displayName] = "Coach $suffix"
        } get UsersTable.id
        ClubRolesTable.insert {
            it[userId] = coachId
            it[ClubRolesTable.clubId] = clubId
            it[role] = "club_manager"
        }
        val teamId = TeamsTable.insert {
            it[TeamsTable.clubId] = clubId
            it[name] = "Team-$suffix"
            it[gamesSyncEnabled] = true
        } get TeamsTable.id
        TeamRolesTable.insert {
            it[userId] = coachId
            it[TeamRolesTable.teamId] = teamId
            it[role] = "coach"
        }
        TeamSvLinksTable.insert {
            it[TeamSvLinksTable.teamId] = teamId
            it[TeamSvLinksTable.svTeamId] = svTeamId
        }
        ClubIntegrationsTable.insert {
            it[ClubIntegrationsTable.clubId] = clubId
            it[apiKey] = "valid-key"
            it[keyValid] = true
        }
        Fixture(clubId, teamId, coachId)
    }

    private fun addSyncedTeam(clubId: UUID, svTeamId: Int, suffix: String): UUID = transaction {
        val coachId = UsersTable.insert {
            it[email] = "coach2-$suffix@example.com"
            it[passwordHash] = "x"
            it[displayName] = "Coach2 $suffix"
        } get UsersTable.id
        val teamId = TeamsTable.insert {
            it[TeamsTable.clubId] = clubId
            it[name] = "Team2-$suffix"
            it[gamesSyncEnabled] = true
        } get TeamsTable.id
        TeamRolesTable.insert {
            it[userId] = coachId
            it[TeamRolesTable.teamId] = teamId
            it[role] = "coach"
        }
        TeamSvLinksTable.insert {
            it[TeamSvLinksTable.teamId] = teamId
            it[TeamSvLinksTable.svTeamId] = svTeamId
        }
        teamId
    }

    private fun game(
        gameId: Int,
        homeSvId: Int,
        awaySvId: Int,
        playDateUtc: String,
        hallId: Int? = 5,
        homeCaption: String = "Home",
        awayCaption: String = "Away",
        setResults: List<SVSetResult>? = null,
        resultSummary: SVResultSummary? = null
    ) = SVGame(
        gameId = gameId,
        playDateUtc = playDateUtc,
        teams = SVGameTeams(
            home = SVGameTeam(teamId = homeSvId, caption = homeCaption),
            away = SVGameTeam(teamId = awaySvId, caption = awayCaption)
        ),
        hall = SVHall(hallId = hallId, caption = "Hall", city = "Town"),
        setResults = setResults,
        resultSummary = resultSummary
    )

    // --- query helpers ---------------------------------------------------------------

    private fun eventsForGame(gameId: Long) = transaction {
        EventsTable.selectAll()
            .where { (EventsTable.externalSource eq EXTERNAL_SOURCE) and (EventsTable.externalGameId eq gameId) }
            .toList()
    }

    private fun eventTeamIds(eventId: UUID): Set<UUID> = transaction {
        EventTeamsTable.select(EventTeamsTable.teamId)
            .where { EventTeamsTable.eventId eq eventId }
            .map { it[EventTeamsTable.teamId] }
            .toSet()
    }

    private fun countNotifications(userId: UUID, type: String): Long = transaction {
        NotificationsTable.selectAll()
            .where { (NotificationsTable.userId eq userId) and (NotificationsTable.type eq type) }
            .count()
    }

    // --- tests -------------------------------------------------------------------------

    @Test
    fun `new game creates one match event with derived end and notifies coach`() {
        val stub = StubSwissVolleyClient()
        withTeamorgTestApplication(koinOverride(stub)) {
            startApplication()
            val fx = seedSyncedClub(svTeamId = 101, suffix = "new")
            val start = futureUtc(7)
            stub.games = listOf(game(gameId = 9001, homeSvId = 101, awaySvId = 999, playDateUtc = start))

            val service = application.get<SwissVolleySyncService>()
            runBlocking { service.syncClub(fx.clubId) }

            val rows = eventsForGame(9001)
            assertEquals(1, rows.size, "Exactly one event for the synced game")
            val row = rows.single()

            assertEquals("match", row[EventsTable.type].name)
            assertEquals(SystemUsers.VOLLEY_MANAGER, row[EventsTable.createdBy], "Author is VolleyManager system user")

            val startAt = row[EventsTable.startAt]
            val endAt = row[EventsTable.endAt]
            assertEquals(Instant.parse(start), startAt)
            assertEquals(startAt.plusSeconds(2 * 60 * 60), endAt, "end_at = start_at + 2h")

            // Linked to the synced team via event_teams.
            assertEquals(setOf(fx.teamId), eventTeamIds(row[EventsTable.id]))

            // Coach was notified.
            assertEquals(1, countNotifications(fx.coachId, "sv_game_new"))
        }
    }

    @Test
    fun `changed facts on re-sync update facts flag review notify once`() {
        val stub = StubSwissVolleyClient()
        withTeamorgTestApplication(koinOverride(stub)) {
            startApplication()
            val fx = seedSyncedClub(svTeamId = 202, suffix = "changed")
            val origStart = futureUtc(10)
            stub.games = listOf(game(gameId = 9002, homeSvId = 202, awaySvId = 888, playDateUtc = origStart))

            val service = application.get<SwissVolleySyncService>()
            runBlocking { service.syncClub(fx.clubId) }

            assertEquals(1, countNotifications(fx.coachId, "sv_game_new"))
            val firstHash = eventsForGame(9002).single()[EventsTable.externalHash]

            // Reschedule: new playDate => new facts.
            val newStart = futureUtc(11)
            stub.games = listOf(game(gameId = 9002, homeSvId = 202, awaySvId = 888, playDateUtc = newStart))
            runBlocking { service.syncClub(fx.clubId) }

            val afterChange = eventsForGame(9002).single()
            assertEquals(Instant.parse(newStart), afterChange[EventsTable.startAt], "Facts updated")
            assertNotEquals(firstHash, afterChange[EventsTable.externalHash], "Hash changed")
            assertTrue(afterChange[EventsTable.needsReview], "needs_review flagged")
            assertEquals(1, countNotifications(fx.coachId, "sv_game_changed"), "Coach notified once on change")

            // A second identical sync must NOT re-notify (idempotent on the same hash).
            runBlocking { service.syncClub(fx.clubId) }
            assertEquals(1, countNotifications(fx.coachId, "sv_game_changed"), "No duplicate change notification")
        }
    }

    @Test
    fun `finished game does not overwrite facts`() {
        val stub = StubSwissVolleyClient()
        withTeamorgTestApplication(koinOverride(stub)) {
            startApplication()
            val fx = seedSyncedClub(svTeamId = 303, suffix = "finished")
            val origStart = futureUtc(5)
            stub.games = listOf(game(gameId = 9003, homeSvId = 303, awaySvId = 777, playDateUtc = origStart))

            val service = application.get<SwissVolleySyncService>()
            runBlocking { service.syncClub(fx.clubId) }
            val origRow = eventsForGame(9003).single()
            val origStartAt = origRow[EventsTable.startAt]
            val origHash = origRow[EventsTable.externalHash]

            // Same game now finished (results present) AND with changed facts (new date/hall).
            stub.games = listOf(
                game(
                    gameId = 9003,
                    homeSvId = 303,
                    awaySvId = 777,
                    playDateUtc = futureUtc(6),
                    hallId = 42,
                    setResults = listOf(SVSetResult(home = 25, away = 20)),
                    resultSummary = SVResultSummary(winner = "home", wonSetsHomeTeam = 3, wonSetsAwayTeam = 1)
                )
            )
            runBlocking { service.syncClub(fx.clubId) }

            val after = eventsForGame(9003).single()
            assertEquals(origStartAt, after[EventsTable.startAt], "Facts NOT overwritten for finished game")
            assertEquals(origHash, after[EventsTable.externalHash], "Hash unchanged for finished game")
            assertFalse(after[EventsTable.needsReview], "No review flag for finished game")
        }
    }

    @Test
    fun `game vanished from feed is marked postponed not cancelled or deleted`() {
        val stub = StubSwissVolleyClient()
        withTeamorgTestApplication(koinOverride(stub)) {
            startApplication()
            val fx = seedSyncedClub(svTeamId = 404, suffix = "vanished")
            stub.games = listOf(game(gameId = 9004, homeSvId = 404, awaySvId = 666, playDateUtc = futureUtc(8)))

            val service = application.get<SwissVolleySyncService>()
            runBlocking { service.syncClub(fx.clubId) }
            assertEquals(1, eventsForGame(9004).size)

            // Feed no longer lists the game.
            stub.games = emptyList()
            runBlocking { service.syncClub(fx.clubId) }

            val rows = eventsForGame(9004)
            assertEquals(1, rows.size, "Event still present (not deleted)")
            val row = rows.single()
            assertEquals("postponed", row[EventsTable.externalStatus], "Marked postponed")
            assertEquals("active", row[EventsTable.status].name, "Not cancelled")
        }
    }

    @Test
    fun `derby with two synced teams creates one event with two event_teams`() {
        val stub = StubSwissVolleyClient()
        withTeamorgTestApplication(koinOverride(stub)) {
            startApplication()
            val fx = seedSyncedClub(svTeamId = 505, suffix = "derby")
            val teamB = addSyncedTeam(fx.clubId, svTeamId = 606, suffix = "derby")
            stub.games = listOf(game(gameId = 9005, homeSvId = 505, awaySvId = 606, playDateUtc = futureUtc(9)))

            val service = application.get<SwissVolleySyncService>()
            runBlocking { service.syncClub(fx.clubId) }

            val rows = eventsForGame(9005)
            assertEquals(1, rows.size, "Exactly one event for the derby")
            assertEquals(
                setOf(fx.teamId, teamB),
                eventTeamIds(rows.single()[EventsTable.id]),
                "Two event_teams rows — one per synced team"
            )
        }
    }

    @Test
    fun `invalid api key pauses sync marks key invalid and notifies manager`() {
        val stub = StubSwissVolleyClient(invalidKey = true)
        withTeamorgTestApplication(koinOverride(stub)) {
            startApplication()
            val fx = seedSyncedClub(svTeamId = 707, suffix = "badkey")

            val service = application.get<SwissVolleySyncService>()
            runBlocking { service.syncClub(fx.clubId) }

            val integration = transaction {
                ClubIntegrationsTable.selectAll()
                    .where { ClubIntegrationsTable.clubId eq fx.clubId }
                    .single()
            }
            assertEquals(false, integration[ClubIntegrationsTable.keyValid], "key_valid set false")

            val state = transaction {
                SvSyncStateTable.selectAll()
                    .where { SvSyncStateTable.clubId eq fx.clubId }
                    .singleOrNull()
            }
            assertNotNull(state, "Sync state row created")
            assertEquals("paused", state[SvSyncStateTable.lastStatus], "Sync paused")

            // Manager (the seeded club_manager) was alerted.
            assertEquals(1, countNotifications(fx.coachId, "sv_key_invalid"))

            // Nothing was synced.
            assertTrue(eventsForGame(9999).isEmpty())
        }
    }
}
