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
import ch.teamorg.routes.RegisterRequest
import ch.teamorg.routes.TeamRoleEntry
import ch.teamorg.routes.UserRolesResponse
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests the scenario where a user who already manages their own club+team
 * redeems an invite from a different manager's team. After redeeming, the
 * user should have roles in BOTH teams.
 */
class CrossTeamInviteTest : IntegrationTestBase() {

    @Test
    fun `manager with own team redeems invite from another team — gets roles in both`() =
        withTeamorgTestApplication {
            val client = createJsonClient()

            // ── User 1: creates club + team ────────────────────────────
            val user1Auth = client.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("manager1@cross.test", "password123", "Manager One"))
            }.body<AuthResponse>()
            promoteToSuperAdmin(user1Auth.userId)

            val club1 = client.post("/clubs") {
                header(HttpHeaders.Authorization, "Bearer ${user1Auth.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateClubRequest("Club Alpha", "Football"))
            }.body<Club>()

            val team1 = client.post("/clubs/${club1.id}/teams") {
                header(HttpHeaders.Authorization, "Bearer ${user1Auth.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateTeamRequest("Team Alpha"))
            }.body<Team>()

            // ── User 2: creates their own club + team ──────────────────
            val user2Auth = client.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("manager2@cross.test", "password123", "Manager Two"))
            }.body<AuthResponse>()
            promoteToSuperAdmin(user2Auth.userId)

            val club2 = client.post("/clubs") {
                header(HttpHeaders.Authorization, "Bearer ${user2Auth.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateClubRequest("Club Beta", "Football"))
            }.body<Club>()

            val team2 = client.post("/clubs/${club2.id}/teams") {
                header(HttpHeaders.Authorization, "Bearer ${user2Auth.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateTeamRequest("Team Beta"))
            }.body<Team>()

            // Verify user2 already has club roles (club_manager of their own club)
            val rolesBefore = client.get("/auth/me/roles") {
                header(HttpHeaders.Authorization, "Bearer ${user2Auth.token}")
            }.body<UserRolesResponse>()
            assertTrue(rolesBefore.clubRoles.isNotEmpty(), "User2 should already have club roles")

            // ── User 1 creates invite for Team Alpha ───────────────────
            val invite = client.post("/teams/${team1.id}/invites") {
                header(HttpHeaders.Authorization, "Bearer ${user1Auth.token}")
                contentType(ContentType.Application.Json)
                setBody(CreateInviteRequest(role = "coach"))
            }.body<InviteResponse>()

            // ── User 2 redeems the invite ──────────────────────────────
            val redeemResp = client.post("/invites/${invite.token}/redeem") {
                header(HttpHeaders.Authorization, "Bearer ${user2Auth.token}")
            }
            assertEquals(HttpStatusCode.OK, redeemResp.status, "Redeem should succeed for user with existing teams")

            // ── Verify user2 now has roles after redeem ───────────────
            val rolesAfter = client.get("/auth/me/roles") {
                header(HttpHeaders.Authorization, "Bearer ${user2Auth.token}")
            }.body<UserRolesResponse>()

            // User2 should still have their club_manager role
            assertTrue(
                rolesAfter.clubRoles.any { it.clubId == club2.id },
                "User2 should still have club_manager role for their own club"
            )

            // User2 should now have a team role in Team Alpha (from invite)
            val teamIds = rolesAfter.teamRoles.map { it.teamId }.toSet()
            assertTrue(teamIds.contains(team1.id), "User2 should now have role in Team Alpha")

            val alphaRole = rolesAfter.teamRoles.first { it.teamId == team1.id }
            assertEquals("coach", alphaRole.role, "User2 should have coach role in Team Alpha")

            // ── Verify user2 appears in Team Alpha's members ───────────
            val members = client.get("/teams/${team1.id}/members") {
                header(HttpHeaders.Authorization, "Bearer ${user1Auth.token}")
            }.body<List<TeamMember>>()
            assertTrue(members.any { it.userId == user2Auth.userId && it.role == "coach" })

            // ── Verify hasTeam returns true (teamRoles non-empty) ──────
            assertTrue(rolesAfter.teamRoles.isNotEmpty(), "hasTeam check should return true after redeeming invite")
        }
}
