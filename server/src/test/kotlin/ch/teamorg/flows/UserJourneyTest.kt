package ch.teamorg.flows

import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.InviteDetails
import ch.teamorg.domain.models.Team
import ch.teamorg.domain.models.TeamMember
import ch.teamorg.routes.AuthResponse
import ch.teamorg.routes.CreateClubRequest
import ch.teamorg.routes.CreateInviteRequest
import ch.teamorg.routes.CreateTeamRequest
import ch.teamorg.routes.InviteResponse
import ch.teamorg.routes.LoginRequest
import ch.teamorg.routes.RegisterRequest
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserJourneyTest : IntegrationTestBase() {

    @Test
    fun `full onboarding flow - register, login, create club, create team, invite and onboard player`() =
        withTeamorgTestApplication {
            val strict = createStrictJsonClient()
            val lenient = createJsonClient()

            // Register manager
            val managerAuth = lenient.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("journey.manager@example.com", "password123", "Journey Manager"))
            }.body<AuthResponse>()
            assertNotNull(managerAuth.token)
            assertNotNull(managerAuth.userId)
            promoteToSuperAdmin(managerAuth.userId)

            // Login returns same data
            val loginAuth = lenient.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest("journey.manager@example.com", "password123"))
            }.body<AuthResponse>()
            assertEquals(managerAuth.userId, loginAuth.userId)
            assertEquals("Journey Manager", loginAuth.displayName)

            // Create club — use strict client to catch any field mismatch
            val clubResp = strict.post("/clubs") {
                header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateClubRequest("Journey Club", "volleyball", "Zurich"))
            }
            assertEquals(HttpStatusCode.Created, clubResp.status)
            val club = clubResp.body<Club>()
            assertEquals("Journey Club", club.name)
            assertEquals("Zurich", club.location)
            // Verify logoUrl field exists on Club (not logoPath)
            assertNotNull(club.id)
            // logoUrl is nullable — just confirming the field name is correct by deserialization success

            // Create team — use strict client
            val teamResp = strict.post("/clubs/${club.id}/teams") {
                header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateTeamRequest("First Team", "Our starting team"))
            }
            assertEquals(HttpStatusCode.Created, teamResp.status)
            val team = teamResp.body<Team>()
            assertEquals("First Team", team.name)
            assertEquals(club.id, team.clubId)

            // Create invite
            val inviteResp = lenient.post("/teams/${team.id}/invites") {
                header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateInviteRequest(role = "player"))
            }
            assertEquals(HttpStatusCode.Created, inviteResp.status)
            val invite = inviteResp.body<InviteResponse>()
            assertNotNull(invite.token)
            assertTrue(invite.inviteUrl.contains(invite.token))

            // Register player
            val playerAuth = lenient.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("journey.player@example.com", "password123", "Journey Player"))
            }.body<AuthResponse>()

            // Player views invite details
            val detailsResp = strict.get("/invites/${invite.token}")
            assertEquals(HttpStatusCode.OK, detailsResp.status)
            val details = detailsResp.body<InviteDetails>()
            assertEquals("First Team", details.teamName)
            assertEquals("player", details.role)
            assertEquals(false, details.alreadyRedeemed)

            // Player redeems invite
            val redeemResp = lenient.post("/invites/${invite.token}/redeem") {
                header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
            }
            assertEquals(HttpStatusCode.OK, redeemResp.status)

            // Verify player appears in team members
            val members = lenient.get("/teams/${team.id}/members") {
                header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            }.body<List<TeamMember>>()
            assertEquals(1, members.size)
            assertEquals(playerAuth.userId, members[0].userId)
            assertEquals("player", members[0].role)

            // Verify invite now shows alreadyRedeemed = true
            val detailsAfter = lenient.get("/invites/${invite.token}").body<InviteDetails>()
            assertEquals(true, detailsAfter.alreadyRedeemed)
        }

    @Test
    fun `club manager cannot access another managers club`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val manager1 = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("mgr1.journey@example.com", "password123", "Manager One"))
        }.body<AuthResponse>()

        val manager2 = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("mgr2.journey@example.com", "password123", "Manager Two"))
        }.body<AuthResponse>()

        promoteToSuperAdmin(manager1.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${manager1.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Exclusive Club"))
        }.body<Club>().id

        val response = client.patch("/clubs/$clubId") {
            header(HttpHeaders.Authorization, "Bearer ${manager2.token}")
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to "Stolen Club"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `player cannot create invites`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val manager = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("mgr.invitetest@example.com", "password123", "Manager"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(manager.userId)

        val club = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${manager.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Invite Test Club"))
        }.body<Club>()

        val team = client.post("/clubs/${club.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer ${manager.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Invite Test Team"))
        }.body<Team>()

        // Player joins via invite
        val inviteToken = client.post("/teams/${team.id}/invites") {
            header(HttpHeaders.Authorization, "Bearer ${manager.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }.body<InviteResponse>().token

        val player = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("player.invitetest@example.com", "password123", "Player"))
        }.body<AuthResponse>()

        client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }

        // Player attempts to create a new invite
        val response = client.post("/teams/${team.id}/invites") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `invite flow with new user registration`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val manager = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("mgr.newuser@example.com", "password123", "New User Manager"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(manager.userId)

        val club = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${manager.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("New User Club"))
        }.body<Club>()

        val team = client.post("/clubs/${club.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer ${manager.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("New User Team"))
        }.body<Team>()

        val inviteToken = client.post("/teams/${team.id}/invites") {
            header(HttpHeaders.Authorization, "Bearer ${manager.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "coach"))
        }.body<InviteResponse>().token

        // New user checks invite before registering
        val details = client.get("/invites/$inviteToken").body<InviteDetails>()
        assertEquals("New User Team", details.teamName)
        assertEquals("New User Club", details.clubName)
        assertEquals("coach", details.role)
        assertEquals(false, details.alreadyRedeemed)

        // New user registers and redeems
        val newUser = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("newuser.coach@example.com", "password123", "New Coach"))
        }.body<AuthResponse>()

        val redeemResp = client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${newUser.token}")
        }
        assertEquals(HttpStatusCode.OK, redeemResp.status)

        val members = client.get("/teams/${team.id}/members") {
            header(HttpHeaders.Authorization, "Bearer ${manager.token}")
        }.body<List<TeamMember>>()

        assertEquals(1, members.size)
        assertEquals("coach", members[0].role)
        assertEquals("New Coach", members[0].displayName)
    }
}
