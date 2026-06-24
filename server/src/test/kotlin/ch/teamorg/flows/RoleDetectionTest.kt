package ch.teamorg.flows

import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.Team
import ch.teamorg.domain.models.TeamMember
import ch.teamorg.routes.AuthResponse
import ch.teamorg.routes.ClubRoleEntry
import ch.teamorg.routes.CreateClubRequest
import ch.teamorg.routes.CreateInviteRequest
import ch.teamorg.routes.CreateTeamRequest
import ch.teamorg.routes.InviteResponse
import ch.teamorg.routes.LoginRequest
import ch.teamorg.routes.RegisterRequest
import ch.teamorg.routes.UpdateRoleRequest
import ch.teamorg.routes.UserRolesResponse
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoleDetectionTest : IntegrationTestBase() {

    private suspend fun ApplicationTestBuilder.registerAndLogin(
        email: String,
        password: String = "Password1!",
        displayName: String = "User $email"
    ): AuthResponse {
        val client = createJsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password, displayName))
        }
        return client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body<AuthResponse>()
    }

    private suspend fun ApplicationTestBuilder.setupClubAndTeam(
        token: String,
        clubName: String = "Test Club",
        teamName: String = "Test Team"
    ): Pair<String, String> {
        val client = createJsonClient()
        val club = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest(clubName, "volleyball"))
        }.body<Club>()
        val team = client.post("/clubs/${club.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(teamName))
        }.body<Team>()
        return Pair(club.id, team.id)
    }

    private suspend fun ApplicationTestBuilder.inviteAndJoin(
        cmToken: String,
        teamId: String,
        playerEmail: String,
        role: String = "player"
    ): AuthResponse {
        val client = createJsonClient()
        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer $cmToken")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = role))
        }.body<InviteResponse>().token

        val auth = registerAndLogin(playerEmail)
        client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }
        return auth
    }

    @Test
    fun `clubmanager role detected as coach`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.roledetect@roles.test", displayName = "CM RoleDetect")
        promoteToSuperAdmin(cm.userId)

        val club = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("RoleDetect Club", "volleyball"))
        }.body<Club>()

        val rolesResp = client.get("/auth/me/roles") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }
        assertEquals(HttpStatusCode.OK, rolesResp.status)
        val roles = rolesResp.body<UserRolesResponse>()

        val clubRoleEntry = roles.clubRoles.find { it.clubId == club.id }
        assertNotNull(clubRoleEntry)
        assertEquals("club_manager", clubRoleEntry.role)

        // isCoach logic: user has at least one club_manager or coach role
        val isCoach = roles.clubRoles.any { it.role in listOf("club_manager", "coach") } ||
            roles.teamRoles.any { it.role == "coach" }
        assertTrue(isCoach)
    }

    @Test
    fun `plain player has no coach role`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.plainplayer@roles.test", displayName = "CM PlainPlayer")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token, "PlainPlayer Club", "PlainPlayer Team")
        val player = inviteAndJoin(cm.token, teamId, "player.plain@roles.test", "player")

        val rolesResp = client.get("/auth/me/roles") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }
        assertEquals(HttpStatusCode.OK, rolesResp.status)
        val roles = rolesResp.body<UserRolesResponse>()

        assertTrue(roles.teamRoles.any { it.teamId == teamId && it.role == "player" })
        assertFalse(roles.clubRoles.any { it.role in listOf("club_manager", "coach") })
        assertFalse(roles.teamRoles.any { it.role == "coach" || it.role == "club_manager" })
    }

    @Test
    fun `promoted player gains coach role`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.promoted@roles.test", displayName = "CM Promoted")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token, "Promoted Club", "Promoted Team")
        val player = inviteAndJoin(cm.token, teamId, "player.promoted@roles.test", "player")

        client.patch("/teams/$teamId/members/${player.userId}/role") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateRoleRequest(role = "coach"))
        }

        val rolesResp = client.get("/auth/me/roles") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }
        assertEquals(HttpStatusCode.OK, rolesResp.status)
        val roles = rolesResp.body<UserRolesResponse>()

        assertTrue(roles.teamRoles.any { it.teamId == teamId && it.role == "coach" })
        assertFalse(roles.teamRoles.any { it.teamId == teamId && it.role == "player" })
    }

    @Test
    fun `removed member has no roles`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.removed@roles.test", displayName = "CM Removed")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token, "Removed Club", "Removed Team")
        val coach = inviteAndJoin(cm.token, teamId, "coach.removed@roles.test", "coach")

        client.delete("/teams/$teamId/members/${coach.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }

        val rolesResp = client.get("/auth/me/roles") {
            header(HttpHeaders.Authorization, "Bearer ${coach.token}")
        }
        assertEquals(HttpStatusCode.OK, rolesResp.status)
        val roles = rolesResp.body<UserRolesResponse>()

        assertFalse(roles.teamRoles.any { it.teamId == teamId })
    }
}
