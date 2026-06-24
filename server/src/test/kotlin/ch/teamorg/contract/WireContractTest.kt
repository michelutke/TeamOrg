package ch.teamorg.contract

import ch.teamorg.routes.CreateClubRequest
import ch.teamorg.routes.CreateInviteRequest
import ch.teamorg.routes.CreateTeamRequest
import ch.teamorg.routes.InviteResponse
import ch.teamorg.routes.RegisterRequest
import ch.teamorg.routes.AuthResponse
import ch.teamorg.domain.models.Club
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wire contract tests verify that JSON field names in server responses exactly match
 * what the client domain models expect. These tests use raw bodyAsText() + string
 * contains checks so any field rename is caught immediately.
 */
class WireContractTest : IntegrationTestBase() {

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.registerAndGetToken(email: String): String {
        val client = createJsonClient()
        val auth = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "password123", "Contract Test User"))
        }.body<AuthResponse>()
        promoteToSuperAdmin(auth.userId)
        return auth.token
    }

    @Test
    fun `AuthResponse wire format contains expected fields`() = withTeamorgTestApplication {
        val rawJson = createJsonClient().post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("contract.auth@example.com", "password123", "Auth Contract"))
        }.bodyAsText()

        assertTrue(rawJson.contains("\"token\""), "AuthResponse must contain 'token' field, got: $rawJson")
        assertTrue(rawJson.contains("\"userId\""), "AuthResponse must contain 'userId' field, got: $rawJson")
        assertTrue(rawJson.contains("\"displayName\""), "AuthResponse must contain 'displayName' field, got: $rawJson")
    }

    @Test
    fun `Club wire format contains logoUrl not logoPath`() = withTeamorgTestApplication {
        val token = registerAndGetToken("contract.club@example.com")

        val rawJson = createJsonClient().post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Contract Club", "volleyball", "Zurich"))
        }.bodyAsText()

        assertTrue(rawJson.contains("\"logoUrl\""), "Club must contain 'logoUrl' field (not 'logoPath'), got: $rawJson")
        assertTrue(!rawJson.contains("\"logoPath\""), "Club must NOT contain 'logoPath' field, got: $rawJson")
        assertTrue(rawJson.contains("\"sportType\""), "Club must contain 'sportType' field, got: $rawJson")
        assertTrue(rawJson.contains("\"name\""), "Club must contain 'name' field, got: $rawJson")
        assertTrue(rawJson.contains("\"id\""), "Club must contain 'id' field, got: $rawJson")
        assertTrue(rawJson.contains("\"location\""), "Club must contain 'location' field, got: $rawJson")
    }

    @Test
    fun `Team wire format contains expected fields`() = withTeamorgTestApplication {
        val token = registerAndGetToken("contract.team@example.com")
        val client = createJsonClient()

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Contract Team Club"))
        }.body<Club>().id

        val rawJson = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Contract Team"))
        }.bodyAsText()

        assertTrue(rawJson.contains("\"clubId\""), "Team must contain 'clubId' field, got: $rawJson")
        assertTrue(rawJson.contains("\"name\""), "Team must contain 'name' field, got: $rawJson")
        assertTrue(rawJson.contains("\"id\""), "Team must contain 'id' field, got: $rawJson")
    }

    @Test
    fun `InviteDetails wire format contains expected fields`() = withTeamorgTestApplication {
        val token = registerAndGetToken("contract.invite@example.com")
        val client = createJsonClient()

        val clubId = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Contract Invite Club"))
        }.body<Club>().id

        val teamId = client.post("/clubs/$clubId/teams") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest("Contract Invite Team"))
        }.body<ch.teamorg.domain.models.Team>().id

        val inviteToken = client.post("/teams/$teamId/invites") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(role = "player"))
        }.body<InviteResponse>().token

        val rawJson = client.get("/invites/$inviteToken").bodyAsText()

        assertTrue(rawJson.contains("\"teamName\""), "InviteDetails must contain 'teamName' field, got: $rawJson")
        assertTrue(rawJson.contains("\"clubName\""), "InviteDetails must contain 'clubName' field, got: $rawJson")
        assertTrue(rawJson.contains("\"alreadyRedeemed\""), "InviteDetails must contain 'alreadyRedeemed' field, got: $rawJson")
        assertTrue(rawJson.contains("\"role\""), "InviteDetails must contain 'role' field, got: $rawJson")
        assertTrue(rawJson.contains("\"expiresAt\""), "InviteDetails must contain 'expiresAt' field, got: $rawJson")
    }

    @Test
    fun `Club GET by id returns same wire format as Club POST`() = withTeamorgTestApplication {
        val token = registerAndGetToken("contract.getclub@example.com")
        val client = createJsonClient()

        val createJson = client.post("/clubs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateClubRequest("Wire Format Club", "football", "Basel"))
        }
        assertEquals(HttpStatusCode.Created, createJson.status)
        val clubId = createJson.body<Club>().id

        val getJson = client.get("/clubs/$clubId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()

        assertTrue(getJson.contains("\"logoUrl\""), "GET Club must contain 'logoUrl' field, got: $getJson")
        assertTrue(getJson.contains("\"sportType\""), "GET Club must contain 'sportType' field, got: $getJson")
        assertTrue(!getJson.contains("\"logoPath\""), "GET Club must NOT contain 'logoPath' field, got: $getJson")
    }
}
