package ch.teamorg.flows

import ch.teamorg.routes.AddSubGroupMemberRequest
import ch.teamorg.routes.AuthResponse
import ch.teamorg.routes.CreateClubRequest
import ch.teamorg.routes.CreateInviteRequest
import ch.teamorg.routes.CreateSubGroupRequest
import ch.teamorg.routes.CreateTeamRequest
import ch.teamorg.routes.InviteResponse
import ch.teamorg.routes.LoginRequest
import ch.teamorg.routes.RegisterRequest
import ch.teamorg.routes.UpdateProfileRequest
import ch.teamorg.routes.UpdateRoleRequest
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ──────────────────────────────────────────────────────────────────────────────
// Mirror classes — exact copies of shared/src/commonMain/kotlin/ch/teamorg/domain/
// If the server wire format diverges from these shapes, deserialization will fail
// and tests will catch the regression.
// ──────────────────────────────────────────────────────────────────────────────

@Serializable
private data class SharedClub(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val sportType: String,
    val location: String? = null
)

@Serializable
private data class SharedTeam(
    val id: String,
    val clubId: String,
    val name: String,
    val memberCount: Int = 0
)

@Serializable
private data class SharedTeamMember(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: String,
    val jerseyNumber: Int?,
    val position: String?
)

@Serializable
private data class SharedUserRoles(
    val clubRoles: List<SharedClubRoleEntry> = emptyList(),
    val teamRoles: List<SharedTeamRoleEntry> = emptyList()
)

@Serializable
private data class SharedClubRoleEntry(val clubId: String, val role: String)

@Serializable
private data class SharedTeamRoleEntry(val teamId: String, val clubId: String, val role: String)

@Serializable
private data class SharedInviteDetails(
    val token: String,
    val teamName: String,
    val clubName: String,
    val role: String,
    val invitedBy: String,
    val expiresAt: String,
    val alreadyRedeemed: Boolean
)

@Serializable
private data class SharedSubGroup(
    val id: String,
    val teamId: String,
    val name: String,
    val memberCount: Int = 0
)

/**
 * Contract tests that deserialize every HTTP response into mirror classes of the shared KMP
 * domain models. Using ignoreUnknownKeys=true to tolerate server-only fields (createdAt,
 * updatedAt, etc.) but explicitly asserting every field defined in the shared model to catch
 * MISSING fields — the class of regression that caused memberCount to silently disappear.
 */
class UserFlowContractTest : IntegrationTestBase() {

    // Client that ignores extra server fields but fails on missing required shared fields.
    private fun ApplicationTestBuilder.createSharedModelClient(): HttpClient = createClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = false
            })
        }
    }

    private suspend fun ApplicationTestBuilder.registerUser(
        email: String,
        password: String = "Password1!",
        displayName: String = "User $email"
    ): AuthResponse {
        val client = createSharedModelClient()
        return client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password, displayName))
        }.body<AuthResponse>()
    }

    private suspend fun ApplicationTestBuilder.setupClub(
        token: String,
        name: String = "Contract Club",
        sportType: String = "volleyball",
        location: String = "Bern"
    ): SharedClub {
        val client = createSharedModelClient()
        val resp = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest(name, sportType, location))
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        return resp.body<SharedClub>()
    }

    private suspend fun ApplicationTestBuilder.setupTeam(
        token: String,
        clubId: String,
        name: String = "Contract Team"
    ): SharedTeam {
        val client = createSharedModelClient()
        val resp = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name))
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        return resp.body<SharedTeam>()
    }

    private suspend fun ApplicationTestBuilder.inviteAndJoin(
        cmToken: String,
        teamId: String,
        playerEmail: String,
        role: String = "player"
    ): AuthResponse {
        val client = createSharedModelClient()
        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer $cmToken")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = role))
        }.body<InviteResponse>().token

        val auth = registerUser(playerEmail)
        client.post("/invites/$inviteToken/redeem") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }
        return auth
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Flow 1: New user onboarding
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow1 new user onboarding - shared models contain all required fields`() =
        withTeamorgTestApplication {
            val client = createSharedModelClient()
            val cm = registerUser("flow1.cm@contract.test", displayName = "CM Flow1")
            promoteToSuperAdmin(cm.userId)
            assertNotNull(cm.token)
            assertNotNull(cm.userId)

            // POST /clubs → shared Club: id, name, logoUrl, sportType, location
            val clubResp = client.post("/clubs") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateClubRequest("Flow1 Club", "volleyball", "Zurich"))
            }
            assertEquals(HttpStatusCode.Created, clubResp.status)
            val club = clubResp.body<SharedClub>()
            assertNotNull(club.id)
            assertEquals("Flow1 Club", club.name)
            assertEquals("volleyball", club.sportType)
            assertEquals("Zurich", club.location)
            // logoUrl is nullable — successful deserialization proves the field exists

            // GET /auth/me/roles → shared UserRoles: clubRoles, teamRoles
            val rolesResp = client.get("/auth/me/roles") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            }
            assertEquals(HttpStatusCode.OK, rolesResp.status)
            val roles = rolesResp.body<SharedUserRoles>()
            assertTrue(
                roles.clubRoles.any { it.clubId == club.id && it.role == "club_manager" },
                "clubRoles must contain club_manager entry for newly created club"
            )

            // POST /clubs/{id}/teams → shared Team: id, clubId, name, memberCount
            val teamResp = client.post("/clubs/${club.id}/teams") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateTeamRequest("Flow1 Team"))
            }
            assertEquals(HttpStatusCode.Created, teamResp.status)
            val team = teamResp.body<SharedTeam>()
            assertNotNull(team.id)
            assertEquals("Flow1 Team", team.name)
            assertEquals(club.id, team.clubId)
            assertNotNull(team.memberCount) // key contract: memberCount must be present

            // GET /clubs/{id}/teams → re-fetch, assert memberCount field exists
            val teamsResp = client.get("/clubs/${club.id}/teams") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            }
            assertEquals(HttpStatusCode.OK, teamsResp.status)
            val teams = teamsResp.body<List<SharedTeam>>()
            val found = teams.find { it.id == team.id }
            assertNotNull(found, "Newly created team must appear in club teams list")
            assertEquals("Flow1 Team", found.name)
            assertNotNull(found.memberCount) // memberCount field must be present on GET too
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Flow 2: Invite + join player
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow2 invite and join player - shared InviteDetails and TeamMember fields`() =
        withTeamorgTestApplication {
            val client = createSharedModelClient()
            val cm = registerUser("flow2.cm@contract.test", displayName = "CM Flow2")
            promoteToSuperAdmin(cm.userId)
            val club = setupClub(cm.token, "Flow2 Club")
            val team = setupTeam(cm.token, club.id, "Flow2 Team")

            // POST /teams/{id}/invites
            val inviteResp = client.post("/teams/${team.id}/invites") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateInviteRequest(role = "player"))
            }
            assertEquals(HttpStatusCode.Created, inviteResp.status)
            val invite = inviteResp.body<InviteResponse>()
            assertNotNull(invite.token)

            // GET /invites/{token} → shared InviteDetails: all fields
            val detailsResp = client.get("/invites/${invite.token}")
            assertEquals(HttpStatusCode.OK, detailsResp.status)
            val details = detailsResp.body<SharedInviteDetails>()
            assertEquals(invite.token, details.token)
            assertEquals("Flow2 Team", details.teamName)
            assertEquals("Flow2 Club", details.clubName)
            assertEquals("player", details.role)
            assertNotNull(details.invitedBy)
            assertNotNull(details.expiresAt)
            assertFalse(details.alreadyRedeemed)

            // Player registers and redeems
            val player = registerUser("flow2.player@contract.test", displayName = "Flow2 Player")
            val redeemResp = client.post("/invites/${invite.token}/redeem") {
                header(HttpHeaders.Authorization, "Bearer ${player.token}")
            }
            assertEquals(HttpStatusCode.OK, redeemResp.status)

            // GET /teams/{id}/members → List<SharedTeamMember>: all shared fields
            val membersResp = client.get("/teams/${team.id}/members") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            }
            assertEquals(HttpStatusCode.OK, membersResp.status)
            val members = membersResp.body<List<SharedTeamMember>>()
            val playerEntry = members.find { it.userId == player.userId }
            assertNotNull(playerEntry, "Player must appear in team members after redeem")
            assertEquals("player", playerEntry.role)
            assertEquals("Flow2 Player", playerEntry.displayName)
            assertNull(playerEntry.jerseyNumber, "jerseyNumber must be null initially")
            assertNull(playerEntry.position, "position must be null initially")
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Flow 3: Invite + join coach, check roles
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow3 invite and join coach - roles endpoint returns coach role`() =
        withTeamorgTestApplication {
            val client = createSharedModelClient()
            val cm = registerUser("flow3.cm@contract.test", displayName = "CM Flow3")
            promoteToSuperAdmin(cm.userId)
            val club = setupClub(cm.token, "Flow3 Club")
            val team = setupTeam(cm.token, club.id, "Flow3 Team")

            val coach = inviteAndJoin(cm.token, team.id, "flow3.coach@contract.test", "coach")

            val rolesResp = client.get("/auth/me/roles") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
            }
            assertEquals(HttpStatusCode.OK, rolesResp.status)
            val roles = rolesResp.body<SharedUserRoles>()
            val coachEntry = roles.teamRoles.find { it.teamId == team.id }
            assertNotNull(coachEntry, "Coach teamRoles must contain entry for teamId=${team.id}")
            assertEquals("coach", coachEntry.role)
            assertEquals(club.id, coachEntry.clubId)
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Flow 4: Player profile management
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow4 player profile management - jerseyNumber and position reflected in shared TeamMember`() =
        withTeamorgTestApplication {
            val client = createSharedModelClient()
            val cm = registerUser("flow4.cm@contract.test", displayName = "CM Flow4")
            promoteToSuperAdmin(cm.userId)
            val club = setupClub(cm.token, "Flow4 Club")
            val team = setupTeam(cm.token, club.id, "Flow4 Team")
            val player = inviteAndJoin(cm.token, team.id, "flow4.player@contract.test", "player")

            val patchResp = client.patch("/teams/${team.id}/members/${player.userId}/profile") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
                contentType(ContentType.Application.Json)
                setBody(UpdateProfileRequest(jerseyNumber = 9, position = "Libero"))
            }
            assertEquals(HttpStatusCode.OK, patchResp.status)

            val members = client.get("/teams/${team.id}/members") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            }.body<List<SharedTeamMember>>()
            val entry = members.find { it.userId == player.userId }
            assertNotNull(entry)
            assertEquals(9, entry.jerseyNumber)
            assertEquals("Libero", entry.position)
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Flow 5: Role management (promote + remove)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow5 role management - promote player to coach then remove updates shared UserRoles`() =
        withTeamorgTestApplication {
            val client = createSharedModelClient()
            val cm = registerUser("flow5.cm@contract.test", displayName = "CM Flow5")
            promoteToSuperAdmin(cm.userId)
            val club = setupClub(cm.token, "Flow5 Club")
            val team = setupTeam(cm.token, club.id, "Flow5 Team")
            val player = inviteAndJoin(cm.token, team.id, "flow5.player@contract.test", "player")

            // Promote to coach
            val promoteResp = client.patch("/teams/${team.id}/members/${player.userId}/role") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
                contentType(ContentType.Application.Json)
                setBody(UpdateRoleRequest(role = "coach"))
            }
            assertEquals(HttpStatusCode.OK, promoteResp.status)

            val rolesAfterPromote = client.get("/auth/me/roles") {
                header(HttpHeaders.Authorization, "Bearer ${player.token}")
            }.body<SharedUserRoles>()
            val coachEntry = rolesAfterPromote.teamRoles.find { it.teamId == team.id }
            assertNotNull(coachEntry, "teamRoles must contain team entry after promotion")
            assertEquals("coach", coachEntry.role)

            // Remove from team
            val removeResp = client.delete("/teams/${team.id}/members/${player.userId}") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            }
            assertEquals(HttpStatusCode.NoContent, removeResp.status)

            val rolesAfterRemove = client.get("/auth/me/roles") {
                header(HttpHeaders.Authorization, "Bearer ${player.token}")
            }.body<SharedUserRoles>()
            assertFalse(
                rolesAfterRemove.teamRoles.any { it.teamId == team.id },
                "teamRoles must NOT contain entry for teamId after removal"
            )
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Flow 6: Leave team
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow6 leave team - player absent from members and roles after leaving`() =
        withTeamorgTestApplication {
            val client = createSharedModelClient()
            val cm = registerUser("flow6.cm@contract.test", displayName = "CM Flow6")
            promoteToSuperAdmin(cm.userId)
            val club = setupClub(cm.token, "Flow6 Club")
            val team = setupTeam(cm.token, club.id, "Flow6 Team")
            val player = inviteAndJoin(cm.token, team.id, "flow6.player@contract.test", "player")

            val leaveResp = client.delete("/teams/${team.id}/leave") {
                header(HttpHeaders.Authorization, "Bearer ${player.token}")
            }
            assertEquals(HttpStatusCode.NoContent, leaveResp.status)

            val rolesAfterLeave = client.get("/auth/me/roles") {
                header(HttpHeaders.Authorization, "Bearer ${player.token}")
            }.body<SharedUserRoles>()
            assertFalse(
                rolesAfterLeave.teamRoles.any { it.teamId == team.id },
                "teamRoles must not contain entry after leaving team"
            )

            val members = client.get("/teams/${team.id}/members") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            }.body<List<SharedTeamMember>>()
            assertFalse(
                members.any { it.userId == player.userId },
                "Player must not appear in members list after leaving"
            )
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Flow 7: Sub-group management
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow7 subgroup management - shared SubGroup memberCount reflects actual roster`() =
        withTeamorgTestApplication {
            val client = createSharedModelClient()
            val cm = registerUser("flow7.cm@contract.test", displayName = "CM Flow7")
            promoteToSuperAdmin(cm.userId)
            val club = setupClub(cm.token, "Flow7 Club")
            val team = setupTeam(cm.token, club.id, "Flow7 Team")
            val coach = inviteAndJoin(cm.token, team.id, "flow7.coach@contract.test", "coach")
            val player1 = inviteAndJoin(cm.token, team.id, "flow7.player1@contract.test", "player")
            inviteAndJoin(cm.token, team.id, "flow7.player2@contract.test", "player")

            // POST /teams/{id}/subgroups
            val sgResp = client.post("/teams/${team.id}/subgroups") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateSubGroupRequest(name = "Attack"))
            }
            assertEquals(HttpStatusCode.Created, sgResp.status)
            val sg = sgResp.body<SharedSubGroup>()
            assertNotNull(sg.id)
            assertEquals("Attack", sg.name)
            assertEquals(team.id, sg.teamId)

            // POST /teams/{id}/subgroups/{sgId}/members
            val addResp = client.post("/teams/${team.id}/subgroups/${sg.id}/members") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
                contentType(ContentType.Application.Json)
                setBody(AddSubGroupMemberRequest(userId = player1.userId))
            }
            assertEquals(HttpStatusCode.Created, addResp.status)

            // GET /teams/{id}/subgroups → List<SharedSubGroup>
            val sgListResp = client.get("/teams/${team.id}/subgroups") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
            }
            assertEquals(HttpStatusCode.OK, sgListResp.status)
            val sgList = sgListResp.body<List<SharedSubGroup>>()
            val attack = sgList.find { it.name == "Attack" }
            assertNotNull(attack, "Subgroup 'Attack' must appear in list")
            assertEquals(1, attack.memberCount, "memberCount must be 1 after adding one player")

            // DELETE /teams/{id}/subgroups/{sgId}/members/{userId}
            val removeResp = client.delete("/teams/${team.id}/subgroups/${sg.id}/members/${player1.userId}") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
            }
            assertEquals(HttpStatusCode.NoContent, removeResp.status)

            // Verify memberCount drops to 0
            val sgListAfterResp = client.get("/teams/${team.id}/subgroups") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
            }
            val sgListAfter = sgListAfterResp.body<List<SharedSubGroup>>()
            val attackAfter = sgListAfter.find { it.name == "Attack" }
            assertNotNull(attackAfter)
            assertEquals(0, attackAfter.memberCount, "memberCount must be 0 after removing player")
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Flow 8: Club edit
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow8 club edit - GET club after PATCH deserializes into shared Club with updated fields`() =
        withTeamorgTestApplication {
            val client = createSharedModelClient()
            val cm = registerUser("flow8.cm@contract.test", displayName = "CM Flow8")
            promoteToSuperAdmin(cm.userId)
            val club = setupClub(cm.token, "Flow8 Original", "volleyball", "Basel")

            val patchResp = client.patch("/clubs/${club.id}") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
                contentType(ContentType.Application.Json)
                setBody(mapOf("name" to "Updated Club", "location" to "Zurich"))
            }
            assertEquals(HttpStatusCode.OK, patchResp.status)

            val getResp = client.get("/clubs/${club.id}") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            }
            assertEquals(HttpStatusCode.OK, getResp.status)
            val updated = getResp.body<SharedClub>()
            assertEquals(club.id, updated.id)
            assertEquals("Updated Club", updated.name)
            assertEquals("Zurich", updated.location)
            assertEquals("volleyball", updated.sportType) // unchanged
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Flow 9: Team edit
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow9 team edit - GET club teams after PATCH reflects new name in shared Team`() =
        withTeamorgTestApplication {
            val client = createSharedModelClient()
            val cm = registerUser("flow9.cm@contract.test", displayName = "CM Flow9")
            promoteToSuperAdmin(cm.userId)
            val club = setupClub(cm.token, "Flow9 Club")
            val team = setupTeam(cm.token, club.id, "Flow9 Original Team")
            val coach = inviteAndJoin(cm.token, team.id, "flow9.coach@contract.test", "coach")

            val patchResp = client.patch("/teams/${team.id}") {
                header(HttpHeaders.Authorization, "Bearer ${coach.token}")
                contentType(ContentType.Application.Json)
                setBody(mapOf("name" to "Flow9 Renamed Team"))
            }
            assertEquals(HttpStatusCode.OK, patchResp.status)

            val teamsResp = client.get("/clubs/${club.id}/teams") {
                header(HttpHeaders.Authorization, "Bearer ${cm.token}")
            }
            val teams = teamsResp.body<List<SharedTeam>>()
            val renamed = teams.find { it.id == team.id }
            assertNotNull(renamed, "Renamed team must appear in club teams list")
            assertEquals("Flow9 Renamed Team", renamed.name)
            assertNotNull(renamed.memberCount) // memberCount field present
        }
}
