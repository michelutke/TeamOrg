package ch.teamorg.routes

import ch.teamorg.db.tables.AttendanceResponsesTable
import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.db.tables.EventTeamsTable
import ch.teamorg.db.tables.EventType
import ch.teamorg.db.tables.EventsTable
import ch.teamorg.db.tables.NotificationsTable
import ch.teamorg.db.tables.TeamRolesTable
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
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReconcileTest : IntegrationTestBase() {

    private val EXTERNAL_SOURCE = "swissvolley"

    companion object {
        // Shared across test instances (JUnit news up one per method) so the
        // (external_source, external_game_id) unique constraint never collides on the shared DB.
        private val nextGameId = java.util.concurrent.atomic.AtomicLong(9100L)
    }

    // --- stubs (reused from the SwissVolley route/service tests) ----------------------

    /** Reconcile never touches SV; an empty stub is enough to satisfy DI. */
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

    private fun koinOverride(client: SwissVolleyClient) = module {
        single<SwissVolleyClient> { client }
        single<PushService> { StubPushService() }
    }

    // --- wire DTO (route's ReconcileRequest is private) -------------------------------

    @Serializable
    private data class ReconcileBody(
        val meetupAt: String? = null,
        val notes: String? = null,
        val minAttendees: Int? = null,
        val resetAvailability: Boolean = false
    )

    // --- HTTP helpers (same shape as GameSyncToggleTest) ------------------------------

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val c = createJsonClient()
        return c.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "User $email"))
        }.body()
    }

    // --- DB seeding -------------------------------------------------------------------

    private data class Fixture(
        val clubId: UUID,
        val teamId: UUID,
        val eventId: UUID,
        val startAt: Instant,
        val endAt: Instant
    )

    /** Add [userId] to [teamId] with the given role (team-level role). */
    private fun addRole(teamId: UUID, userId: UUID, role: String) = transaction {
        TeamRolesTable.insert {
            it[TeamRolesTable.userId] = userId
            it[TeamRolesTable.teamId] = teamId
            it[TeamRolesTable.role] = role
        }
    }

    private fun addResponse(eventId: UUID, userId: UUID, status: String) = transaction {
        AttendanceResponsesTable.insert {
            it[AttendanceResponsesTable.eventId] = eventId
            it[AttendanceResponsesTable.userId] = userId
            it[AttendanceResponsesTable.status] = status
            it[AttendanceResponsesTable.respondedAt] = Instant.now()
        }
    }

    /**
     * Seeds a club + team and a *synced* SwissVolley match event that is flagged
     * `needs_review = true`. The event is linked to the team via event_teams (so the
     * route's requireEventAccess can resolve a role). [authorId] is the event author.
     */
    private fun seedSyncedEvent(authorId: UUID, suffix: String): Fixture = transaction {
        val clubId = ClubsTable.insert { it[name] = "RecClub-$suffix" } get ClubsTable.id
        val teamId = TeamsTable.insert {
            it[TeamsTable.clubId] = clubId
            it[name] = "RecTeam-$suffix"
        } get TeamsTable.id

        val start = Instant.now().plus(Duration.ofDays(7))
        val end = start.plus(Duration.ofHours(2))
        val eventId = EventsTable.insert {
            it[title] = "SV Match $suffix"
            it[type] = EventType.match
            it[startAt] = start
            it[endAt] = end
            it[location] = "SV Hall"
            it[externalSource] = EXTERNAL_SOURCE
            // Unique per test — (external_source, external_game_id) has a unique constraint and the
            // Postgres container is shared across the test methods.
            it[externalGameId] = nextGameId.getAndIncrement()
            it[externalHash] = "hash-$suffix"
            it[needsReview] = true
            it[createdBy] = authorId
        } get EventsTable.id
        EventTeamsTable.insert {
            it[EventTeamsTable.eventId] = eventId
            it[EventTeamsTable.teamId] = teamId
        }
        Fixture(clubId, teamId, eventId, start, end)
    }

    // --- query helpers ----------------------------------------------------------------

    private fun event(eventId: UUID) = transaction {
        EventsTable.selectAll().where { EventsTable.id eq eventId }.single()
    }

    private fun responseStatus(eventId: UUID, userId: UUID): String = transaction {
        AttendanceResponsesTable.selectAll()
            .where { (AttendanceResponsesTable.eventId eq eventId) and (AttendanceResponsesTable.userId eq userId) }
            .single()[AttendanceResponsesTable.status]
    }

    private fun countNotifications(userId: UUID, type: String): Long = transaction {
        NotificationsTable.selectAll()
            .where { (NotificationsTable.userId eq userId) and (NotificationsTable.type eq type) }
            .count()
    }

    // --- tests ------------------------------------------------------------------------

    @Test
    fun `coach reconcile without reset updates coach fields keeps SV facts and responses and does not notify`() {
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient())) {
            val client = createJsonClient()
            val coach = register("rec_keep_coach@example.com")
            val coachId = UUID.fromString(coach.userId)
            val fx = seedSyncedEvent(authorId = coachId, suffix = "keep")
            addRole(fx.teamId, coachId, "coach")

            // Players with existing RSVPs that must NOT be touched when resetAvailability=false.
            val pConfirmed = register("rec_keep_p1@example.com")
            val pDeclined = register("rec_keep_p2@example.com")
            addRole(fx.teamId, UUID.fromString(pConfirmed.userId), "player")
            addRole(fx.teamId, UUID.fromString(pDeclined.userId), "player")
            addResponse(fx.eventId, UUID.fromString(pConfirmed.userId), "confirmed")
            addResponse(fx.eventId, UUID.fromString(pDeclined.userId), "declined")

            val newMeetup = fx.startAt.minus(Duration.ofMinutes(45))
            val response = client.post("/events/${fx.eventId}/reconcile") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
                contentType(ContentType.Application.Json)
                setBody(ReconcileBody(
                    meetupAt = newMeetup.toString(),
                    notes = "Bring two balls",
                    minAttendees = 6,
                    resetAvailability = false
                ))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            delay(300) // let any (unexpected) async dispatch settle

            val row = event(fx.eventId)
            // needs_review cleared.
            assertFalse(row[EventsTable.needsReview], "needs_review cleared")
            // Coach-owned fields updated.
            assertEquals(newMeetup, row[EventsTable.meetupAt], "meetupAt updated")
            assertEquals("Bring two balls", row[EventsTable.description], "notes updated")
            assertEquals(6, row[EventsTable.minAttendees], "minAttendees updated")
            // SV-owned facts UNCHANGED.
            assertEquals("SV Match keep", row[EventsTable.title], "title unchanged")
            assertEquals(fx.startAt, row[EventsTable.startAt], "startAt unchanged")
            assertEquals(fx.endAt, row[EventsTable.endAt], "endAt unchanged")
            assertEquals("SV Hall", row[EventsTable.location], "location unchanged")
            // Attendance responses UNCHANGED.
            assertEquals("confirmed", responseStatus(fx.eventId, UUID.fromString(pConfirmed.userId)))
            assertEquals("declined", responseStatus(fx.eventId, UUID.fromString(pDeclined.userId)))
            // Players NOT notified.
            assertEquals(0, countNotifications(UUID.fromString(pConfirmed.userId), "event_edit"))
            assertEquals(0, countNotifications(UUID.fromString(pDeclined.userId), "event_edit"))
        }
    }

    @Test
    fun `coach reconcile with reset clears manual RSVPs keeps declined-auto and notifies players`() {
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient())) {
            val client = createJsonClient()
            val coach = register("rec_reset_coach@example.com")
            val coachId = UUID.fromString(coach.userId)
            val fx = seedSyncedEvent(authorId = coachId, suffix = "reset")
            addRole(fx.teamId, coachId, "coach")

            val pConfirmed = register("rec_reset_p1@example.com")
            val pDeclined = register("rec_reset_p2@example.com")
            val pUnsure = register("rec_reset_p3@example.com")
            val pAuto = register("rec_reset_p4@example.com")
            addRole(fx.teamId, UUID.fromString(pConfirmed.userId), "player")
            addRole(fx.teamId, UUID.fromString(pDeclined.userId), "player")
            addRole(fx.teamId, UUID.fromString(pUnsure.userId), "player")
            addRole(fx.teamId, UUID.fromString(pAuto.userId), "player")
            addResponse(fx.eventId, UUID.fromString(pConfirmed.userId), "confirmed")
            addResponse(fx.eventId, UUID.fromString(pDeclined.userId), "declined")
            addResponse(fx.eventId, UUID.fromString(pUnsure.userId), "unsure")
            addResponse(fx.eventId, UUID.fromString(pAuto.userId), "declined-auto")

            val response = client.post("/events/${fx.eventId}/reconcile") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
                contentType(ContentType.Application.Json)
                setBody(ReconcileBody(notes = "Rescheduled", resetAvailability = true))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            delay(300) // async player notification dispatch

            // Manual RSVPs reset to 'no-response'.
            assertEquals("no-response", responseStatus(fx.eventId, UUID.fromString(pConfirmed.userId)))
            assertEquals("no-response", responseStatus(fx.eventId, UUID.fromString(pDeclined.userId)))
            assertEquals("no-response", responseStatus(fx.eventId, UUID.fromString(pUnsure.userId)))
            // Auto-decline row left intact.
            assertEquals("declined-auto", responseStatus(fx.eventId, UUID.fromString(pAuto.userId)))

            // event_edit player notification fired (coach is excluded as the actor).
            assertEquals(1, countNotifications(UUID.fromString(pConfirmed.userId), "event_edit"))
            assertEquals(1, countNotifications(UUID.fromString(pAuto.userId), "event_edit"))
        }
    }

    @Test
    fun `reconcile on event with needs_review false returns 409`() {
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient())) {
            val client = createJsonClient()
            val coach = register("rec_409_coach@example.com")
            val coachId = UUID.fromString(coach.userId)
            val fx = seedSyncedEvent(authorId = coachId, suffix = "409")
            addRole(fx.teamId, coachId, "coach")
            // Already reconciled: clear the flag so the event is no longer up for review.
            transaction {
                EventsTable.update({ EventsTable.id eq fx.eventId }) { it[needsReview] = false }
            }

            val response = client.post("/events/${fx.eventId}/reconcile") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
                contentType(ContentType.Application.Json)
                setBody(ReconcileBody(resetAvailability = false))
            }
            assertEquals(HttpStatusCode.Conflict, response.status)
        }
    }

    @Test
    fun `reconcile on a non-SwissVolley event returns 409`() {
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient())) {
            val client = createJsonClient()
            val coach = register("rec_nonsv_coach@example.com")
            val coachId = UUID.fromString(coach.userId)
            val fx = seedSyncedEvent(authorId = coachId, suffix = "nonsv")
            addRole(fx.teamId, coachId, "coach")
            // Strip the external source: a coach-created event is not reconcilable even if flagged.
            transaction {
                EventsTable.update({ EventsTable.id eq fx.eventId }) { it[externalSource] = null }
            }

            val response = client.post("/events/${fx.eventId}/reconcile") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
                contentType(ContentType.Application.Json)
                setBody(ReconcileBody(resetAvailability = false))
            }
            assertEquals(HttpStatusCode.Conflict, response.status)
        }
    }

    @Test
    fun `player reconcile returns 403 and leaves the event untouched`() {
        withTeamorgTestApplication(koinOverride(StubSwissVolleyClient())) {
            val client = createJsonClient()
            val coach = register("rec_403_coach@example.com")
            val coachId = UUID.fromString(coach.userId)
            val fx = seedSyncedEvent(authorId = coachId, suffix = "403")
            addRole(fx.teamId, coachId, "coach")

            // A team player (role "player") must not be able to reconcile (no-IDOR).
            val player = register("rec_403_player@example.com")
            addRole(fx.teamId, UUID.fromString(player.userId), "player")

            val response = client.post("/events/${fx.eventId}/reconcile") {
                header(HttpHeaders.Authorization, "Bearer ${player.token}")
                contentType(ContentType.Application.Json)
                setBody(ReconcileBody(notes = "hacked", resetAvailability = true))
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)

            // Flag still set, coach fields untouched.
            val row = event(fx.eventId)
            assertTrue(row[EventsTable.needsReview], "needs_review still set after rejected reconcile")
            assertEquals(null, row[EventsTable.description], "notes untouched")
        }
    }
}
