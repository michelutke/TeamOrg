package ch.teamorg.routes

import ch.teamorg.db.tables.InviteLinksTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.domain.models.*
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InviteRoutesTest : IntegrationTestBase() {

    private suspend fun ApplicationTestBuilder.setupAuthUser(email: String): AuthResponse {
        val client = createJsonClient()
        return client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "User $email"))
        }.body<AuthResponse>()
    }

    private suspend fun ApplicationTestBuilder.setupClubAndTeam(token: String): String {
        val client = createJsonClient()
        val clubResp = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to "Test Club", "sportType" to "Football"))
        }.body<Club>()

        val teamResp = client.post("/clubs/${clubResp.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to "Test Team"))
        }.body<Team>()

        return teamResp.id
    }

    @Test
    fun `full invite flow - create, get details, redeem`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val coachAuth = setupAuthUser("coach@test.com")
        promoteToSuperAdmin(coachAuth.userId)
        val teamId = setupClubAndTeam(coachAuth.token)

        val inviteResp = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }
        assertEquals(HttpStatusCode.Created, inviteResp.status)
        val invite = inviteResp.body<InviteResponse>()
        assertNotNull(invite.token)
        assertTrue(invite.inviteUrl.contains(invite.token))

        val detailsResp = client.get("/invites/${invite.token}")
        assertEquals(HttpStatusCode.OK, detailsResp.status)
        val details = detailsResp.body<InviteDetails>()
        assertEquals("Test Team", details.teamName)
        assertEquals("player", details.role)
        assertEquals(false, details.alreadyRedeemed)

        val playerAuth = setupAuthUser("player@test.com")
        val redeemResp = client.post("/invites/${invite.token}/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
        }
        assertEquals(HttpStatusCode.OK, redeemResp.status)

        val detailsAfter = client.get("/invites/${invite.token}").body<InviteDetails>()
        assertEquals(true, detailsAfter.alreadyRedeemed)
    }

    @Test
    fun `redeem - expired token returns 410`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = setupAuthUser("coachexp@test.com")
        promoteToSuperAdmin(coachAuth.userId)
        val teamId = setupClubAndTeam(coachAuth.token)

        // Insert invite directly with a past expiresAt
        val expiredToken = UUID.randomUUID().toString()
        val coachUserId = UUID.fromString(coachAuth.userId)
        val teamUUID = UUID.fromString(teamId)
        transaction {
            InviteLinksTable.insert {
                it[token] = expiredToken
                it[InviteLinksTable.teamId] = teamUUID
                it[invitedByUserId] = coachUserId
                it[role] = "player"
                it[expiresAt] = Instant.now().minusSeconds(3600)
            }
        }

        val playerAuth = setupAuthUser("playerexp@test.com")
        val response = client.post("/invites/$expiredToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
        }

        assertEquals(HttpStatusCode.Gone, response.status)
    }

    @Test
    fun `redeem - second different user also becomes member returns 200`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = setupAuthUser("coach2@test.com")
        promoteToSuperAdmin(coachAuth.userId)
        val teamId = setupClubAndTeam(coachAuth.token)

        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }.body<InviteResponse>().token

        val player1 = setupAuthUser("p1@test.com")
        client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${player1.token}")
        }

        val player2 = setupAuthUser("p2@test.com")
        val response = client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${player2.token}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `redeem - already member returns 200 idempotent`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = setupAuthUser("coach3@test.com")
        promoteToSuperAdmin(coachAuth.userId)
        val teamId = setupClubAndTeam(coachAuth.token)

        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }.body<InviteResponse>().token

        val player = setupAuthUser("p3@test.com")

        client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }

        val response = client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `redeem - unauthenticated returns 401`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val response = client.post("/invites/some-token/redeem")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `create invite - unauthorized returns 403`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = setupAuthUser("coach4@test.com")
        promoteToSuperAdmin(coachAuth.userId)
        val teamId = setupClubAndTeam(coachAuth.token)

        val otherUser = setupAuthUser("other@test.com")
        val response = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer ${otherUser.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `create invite - invalid role returns 400`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = setupAuthUser("coach5@test.com")
        promoteToSuperAdmin(coachAuth.userId)
        val teamId = setupClubAndTeam(coachAuth.token)

        val response = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "admin"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `get invite - nonexistent token returns 404`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val response = client.get("/invites/nonexistent-token-12345")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `create invite unauthenticated returns 401`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = setupAuthUser("coach6@test.com")
        promoteToSuperAdmin(coachAuth.userId)
        val teamId = setupClubAndTeam(coachAuth.token)

        val response = client.post("/teams/$teamId/invites") {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `redeem invite - user added to team members`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val coachAuth = setupAuthUser("coach7@test.com")
        promoteToSuperAdmin(coachAuth.userId)
        val teamId = setupClubAndTeam(coachAuth.token)

        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }.body<InviteResponse>().token

        val playerAuth = setupAuthUser("player7@test.com")
        client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
        }

        val members = client.get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${coachAuth.token}")
        }.body<List<TeamMember>>()

        assertEquals(1, members.size)
        assertEquals(playerAuth.userId, members[0].userId)
        assertEquals("player", members[0].role)
    }
}
