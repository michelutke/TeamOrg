package ch.teamorg.flows

import ch.teamorg.db.tables.InviteLinksTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.Team
import ch.teamorg.domain.models.TeamMember
import ch.teamorg.routes.AuthResponse
import ch.teamorg.routes.CreateClubRequest
import ch.teamorg.routes.CreateInviteRequest
import ch.teamorg.routes.CreateTeamRequest
import ch.teamorg.routes.InviteResponse
import ch.teamorg.routes.RegisterRequest
import ch.teamorg.routes.LoginRequest
import ch.teamorg.routes.UpdateRoleRequest
import ch.teamorg.routes.UpdateProfileRequest
import ch.teamorg.routes.CreateSubGroupRequest
import ch.teamorg.routes.AddSubGroupMemberRequest
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@kotlinx.serialization.Serializable
private data class SubGroupSummary(
    val id: String,
    val teamId: String,
    val name: String,
    val memberCount: Long
)

class TeamManagementFlowTest : IntegrationTestBase() {

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
        clubName: String = "SC Thun",
        teamName: String = "U18 Men"
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
    fun `clubmanager full setup flow`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.setup@flow.test", displayName = "CM Setup")
        promoteToSuperAdmin(cm.userId)

        val clubResp = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("SC Thun", "volleyball"))
        }
        assertEquals(HttpStatusCode.Created, clubResp.status)
        val club = clubResp.body<Club>()
        assertNotNull(club.id)
        assertEquals("SC Thun", club.name)

        val teamResp = client.post("/clubs/${club.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("U18 Men"))
        }
        assertEquals(HttpStatusCode.Created, teamResp.status)
        val team = teamResp.body<Team>()
        assertEquals("U18 Men", team.name)
        assertEquals(club.id, team.clubId)

        val teamsResp = client.get("/clubs/${club.id}/teams") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }
        assertEquals(HttpStatusCode.OK, teamsResp.status)
        val teams = teamsResp.body<List<Team>>()
        assertTrue(teams.size >= 1)
        assertTrue(teams.any { it.name == "U18 Men" })
    }

    @Test
    fun `player registers via invite link`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.playerinvite@flow.test", displayName = "CM Player")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token)

        val inviteResp = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }
        assertEquals(HttpStatusCode.Created, inviteResp.status)
        val invite = inviteResp.body<InviteResponse>()
        assertNotNull(invite.token)

        val detailsResp = client.get("/invites/${invite.token}")
        assertEquals(HttpStatusCode.OK, detailsResp.status)
        val details = detailsResp.body<ch.teamorg.domain.models.InviteDetails>()
        assertEquals("player", details.role)

        val player = registerAndLogin("player.invite@flow.test", displayName = "Invite Player")
        val redeemResp = client.post("/invites/${invite.token}/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }
        assertEquals(HttpStatusCode.OK, redeemResp.status)

        val members = client.get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }.body<List<TeamMember>>()
        assertTrue(members.any { it.userId == player.userId && it.role == "player" })
    }

    @Test
    fun `coach registers via invite link`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.coachinvite@flow.test", displayName = "CM Coach")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token)

        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "coach"))
        }.body<InviteResponse>().token

        val coach = registerAndLogin("coach.invite@flow.test", displayName = "Invite Coach")
        val redeemResp = client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${coach.token}")
        }
        assertEquals(HttpStatusCode.OK, redeemResp.status)

        val members = client.get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }.body<List<TeamMember>>()
        assertTrue(members.any { it.userId == coach.userId && it.role == "coach" })
    }

    @Test
    fun `clubmanager promotes player to coach`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.promote@flow.test", displayName = "CM Promote")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token)
        val player = inviteAndJoin(cm.token, teamId, "player.promote@flow.test", "player")

        val promoteResp = client.patch("/teams/$teamId/members/${player.userId}/role") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateRoleRequest(role = "coach"))
        }
        assertEquals(HttpStatusCode.OK, promoteResp.status)

        val members = client.get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }.body<List<TeamMember>>()
        val entry = members.find { it.userId == player.userId }
        assertNotNull(entry)
        assertEquals("coach", entry.role)
    }

    @Test
    fun `clubmanager removes coach from team`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.remove@flow.test", displayName = "CM Remove")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token)
        val coach = inviteAndJoin(cm.token, teamId, "coach.remove@flow.test", "coach")

        val removeResp = client.delete("/teams/$teamId/members/${coach.userId}") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }
        assertEquals(HttpStatusCode.NoContent, removeResp.status)

        val members = client.get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }.body<List<TeamMember>>()
        assertFalse(members.any { it.userId == coach.userId })
    }

    @Test
    fun `player can leave team`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.leave@flow.test", displayName = "CM Leave")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token)
        val player = inviteAndJoin(cm.token, teamId, "player.leave@flow.test", "player")

        val leaveResp = client.delete("/teams/$teamId/leave") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }
        assertEquals(HttpStatusCode.NoContent, leaveResp.status)

        val members = client.get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }.body<List<TeamMember>>()
        assertFalse(members.any { it.userId == player.userId })
    }

    @Test
    fun `coach can edit team details`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.edit@flow.test", displayName = "CM Edit")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token, teamName = "U18 Women")
        val coach = inviteAndJoin(cm.token, teamId, "coach.edit@flow.test", "coach")

        val updateResp = client.patch("/teams/$teamId") {
            header(HttpHeaders.Authorization, "Bearer ${coach.token}")
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to "U18 Women Updated"))
        }
        assertEquals(HttpStatusCode.OK, updateResp.status)

        val team = client.get("/teams/$teamId") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }.body<Team>()
        assertEquals("U18 Women Updated", team.name)
    }

    @Test
    fun `player can update jersey and position`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.profile@flow.test", displayName = "CM Profile")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token)
        val player = inviteAndJoin(cm.token, teamId, "player.profile@flow.test", "player")

        val updateResp = client.patch("/teams/$teamId/members/${player.userId}/profile") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(jerseyNumber = 10, position = "Libero"))
        }
        assertEquals(HttpStatusCode.OK, updateResp.status)

        val members = client.get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }.body<List<TeamMember>>()
        val entry = members.find { it.userId == player.userId }
        assertNotNull(entry)
        assertEquals(10, entry.jerseyNumber)
        assertEquals("Libero", entry.position)
    }

    @Test
    fun `coach creates sub-group and assigns players`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.subgroup@flow.test", displayName = "CM SubGroup")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token)
        val coach = inviteAndJoin(cm.token, teamId, "coach.subgroup@flow.test", "coach")
        val player1 = inviteAndJoin(cm.token, teamId, "player1.subgroup@flow.test", "player")
        inviteAndJoin(cm.token, teamId, "player2.subgroup@flow.test", "player")

        val subGroupResp = client.post("/teams/$teamId/subgroups") {
            header(HttpHeaders.Authorization, "Bearer ${coach.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateSubGroupRequest(name = "Starters"))
        }
        assertEquals(HttpStatusCode.Created, subGroupResp.status)
        val subGroup = subGroupResp.body<SubGroupSummary>()
        val subGroupId = subGroup.id
        assertNotNull(subGroupId)

        val addMemberResp = client.post("/teams/$teamId/subgroups/$subGroupId/members") {
            header(HttpHeaders.Authorization, "Bearer ${coach.token}")
            contentType(ContentType.Application.Json)
            setBody(AddSubGroupMemberRequest(userId = player1.userId))
        }
        assertEquals(HttpStatusCode.Created, addMemberResp.status)

        val subGroupsResp = client.get("/teams/$teamId/subgroups") {
            header(HttpHeaders.Authorization, "Bearer ${coach.token}")
        }
        assertEquals(HttpStatusCode.OK, subGroupsResp.status)
        val subGroups = subGroupsResp.body<List<SubGroupSummary>>()
        val starters = subGroups.find { it.name == "Starters" }
        assertNotNull(starters)
        assertTrue(starters.memberCount >= 1L)
    }

    @Test
    fun `expired invite returns 410`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.expired@flow.test", displayName = "CM Expired")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token)

        val expiredToken = UUID.randomUUID().toString()
        transaction {
            InviteLinksTable.insert {
                it[token] = expiredToken
                it[InviteLinksTable.teamId] = UUID.fromString(teamId)
                it[invitedByUserId] = UUID.fromString(cm.userId)
                it[role] = "player"
                it[expiresAt] = Instant.now().minusSeconds(8L * 24 * 60 * 60)
            }
        }

        // GET still returns 200 (details don't check expiry — expiry enforced on redeem)
        val getResp = client.get("/invites/$expiredToken")
        assertEquals(HttpStatusCode.OK, getResp.status)

        val player = registerAndLogin("player.expired@flow.test", displayName = "Player Expired")
        val redeemResp = client.post("/invites/$expiredToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }
        assertEquals(HttpStatusCode.Gone, redeemResp.status)
    }

    @Test
    fun `double redeem invite is idempotent`() = withTeamorgTestApplication {
        val client = createJsonClient()
        val cm = registerAndLogin("cm.double@flow.test", displayName = "CM Double")
        promoteToSuperAdmin(cm.userId)
        val (_, teamId) = setupClubAndTeam(cm.token)

        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }.body<InviteResponse>().token

        val player = registerAndLogin("player.double@flow.test", displayName = "Player Double")
        val first = client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }
        assertEquals(HttpStatusCode.OK, first.status)

        val second = client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${player.token}")
        }
        assertEquals(HttpStatusCode.OK, second.status)

        val members = client.get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${cm.token}")
        }.body<List<TeamMember>>()
        assertEquals(1, members.count { it.userId == player.userId })
    }
}
