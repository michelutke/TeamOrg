package ch.teamorg.routes

import ch.teamorg.domain.models.Club
import ch.teamorg.domain.models.Team
import ch.teamorg.domain.models.TeamMember
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TeamRoutesTest : IntegrationTestBase() {

    @Test
    fun `create team success`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("teamcr@example.com", "password123", "Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Team Club"))
        }.body<Club>().id

        val response = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("First Team", "Our best team"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val team = response.body<Team>()
        assertEquals("First Team", team.name)
        assertEquals(clubId, team.clubId)
    }

    @Test
    fun `get team success`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("teamget@example.com", "password123", "Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Team Club"))
        }.body<Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Get Me"))
        }.body<Team>().id

        val response = client.get("/teams/$teamId") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Get Me", response.body<Team>().name)
    }

    @Test
    fun `update team name success`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("teamupd@example.com", "password123", "Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Team Club"))
        }.body<Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Old Name"))
        }.body<Team>().id

        val response = client.patch("/teams/$teamId") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateTeamRequest(name = "New Team Name"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("New Team Name", response.body<Team>().name)
    }

    @Test
    fun `archive team success`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("teamarc@example.com", "password123", "Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Team Club"))
        }.body<Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("To Archive"))
        }.body<Team>().id

        val response = client.delete("/teams/$teamId") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.body<Team>().archivedAt)

        val listResponse = client.get("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }
        val teams = listResponse.body<List<Team>>()
        assertEquals(0, teams.size)
    }

    @Test
    fun `get team members`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("teammem@example.com", "password123", "Creator"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Team Club"))
        }.body<Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Member Team"))
        }.body<Team>().id

        val response = client.get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val members = response.body<List<TeamMember>>()
        assertEquals(0, members.size)
    }

    @Test
    fun `delete team as non-manager returns 403`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val managerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("delmgr@example.com", "password123", "Manager"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(managerAuth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Delete Club"))
        }.body<Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Delete Me"))
        }.body<Team>().id

        val otherAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("delother@example.com", "password123", "Non Manager"))
        }.body<AuthResponse>()

        val response = client.delete("/teams/$teamId") {
            header(HttpHeaders.Authorization, "Bearer ${otherAuth.token}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `update team as non-member returns 403`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val managerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("updmgr@example.com", "password123", "Manager"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(managerAuth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Update Club"))
        }.body<Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Protected Team"))
        }.body<Team>().id

        val otherAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("updother@example.com", "password123", "Non Member"))
        }.body<AuthResponse>()

        val response = client.patch("/teams/$teamId") {
            header(HttpHeaders.Authorization, "Bearer ${otherAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateTeamRequest(name = "Hijacked Name"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `get nonexistent team returns 404`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("team404@example.com", "password123", "Creator"))
        }.body<AuthResponse>()

        val response = client.get("/teams/00000000-0000-0000-0000-000000000000") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `members list contains user after invite redeem`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val managerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("memmgr@example.com", "password123", "Manager"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(managerAuth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Members Club"))
        }.body<Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Members Team"))
        }.body<Team>().id

        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }.body<InviteResponse>().token

        val playerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("memplayer@example.com", "password123", "Player User"))
        }.body<AuthResponse>()

        client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
        }

        val response = client.get("/teams/$teamId/members") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val members = response.body<List<TeamMember>>()
        assertEquals(1, members.size)
        assertEquals("Player User", members[0].displayName)
        assertEquals("player", members[0].role)
    }

    @Test
    fun `team memberCount reflects actual members after invite redeem`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val managerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("mctmgr@example.com", "password123", "Manager"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(managerAuth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Count Club"))
        }.body<Club>().id

        val team = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Count Team"))
        }.body<Team>()

        // Freshly created team has 0 members
        assertEquals(0, team.memberCount)

        // Create invite + redeem
        val inviteToken = client.post("/teams/${team.id}/invites") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }.body<InviteResponse>().token

        val playerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("mctplayer@example.com", "password123", "Player"))
        }.body<AuthResponse>()

        client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
        }

        // GET /teams/{id} should now report memberCount = 1
        val teamAfter = client.get("/teams/${team.id}") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
        }.body<Team>()
        assertEquals(1, teamAfter.memberCount)

        // GET /clubs/{id}/teams should also report memberCount = 1
        val clubTeams = client.get("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
        }.body<List<Team>>()
        assertEquals(1, clubTeams.size)
        assertEquals(1, clubTeams[0].memberCount)
    }

    @Test
    fun `player sees team role in me-roles after invite redeem`() = withTeamorgTestApplication {
        val client = createJsonClient()

        val managerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("rolemgr@example.com", "password123", "Manager"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(managerAuth.userId)

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Role Club"))
        }.body<Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Role Team"))
        }.body<Team>().id

        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer ${managerAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }.body<InviteResponse>().token

        val playerAuth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("roleplayer@example.com", "password123", "Player"))
        }.body<AuthResponse>()

        client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
        }

        // Player should see a team role with the correct clubId
        val roles = client.get("/auth/me/roles") {
            header(HttpHeaders.Authorization, "Bearer ${playerAuth.token}")
        }.body<UserRolesResponse>()

        assertEquals(0, roles.clubRoles.size) // player has no club role
        assertEquals(1, roles.teamRoles.size)
        assertEquals(teamId, roles.teamRoles[0].teamId)
        assertEquals(clubId, roles.teamRoles[0].clubId)
        assertEquals("player", roles.teamRoles[0].role)
    }
}
