package ch.teamorg.repository

import ch.teamorg.data.repository.AuthRepositoryImpl
import ch.teamorg.data.repository.ClubRepositoryImpl
import ch.teamorg.data.repository.InviteRepositoryImpl
import ch.teamorg.data.repository.TeamRepositoryImpl
import ch.teamorg.domain.AuthResponse
import ch.teamorg.domain.RedeemResult
import ch.teamorg.domain.RegisterRequest
import ch.teamorg.module as serverModule
import ch.teamorg.preferences.UserPreferences
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.AfterClass
import org.junit.BeforeClass
import org.testcontainers.containers.PostgreSQLContainer
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for shared client repository implementations.
 *
 * Starts a real Ktor/Netty server on a random free TCP port, backed by a Testcontainers
 * PostgreSQL instance. The tests instantiate the actual [TeamRepositoryImpl],
 * [ClubRepositoryImpl], [InviteRepositoryImpl] and [AuthRepositoryImpl] from the shared module
 * and exercise the real HTTP client code path — including Content-Type headers, body
 * serialization, and response deserialization.
 */
class ClientRepositoryFlowTest {

    companion object {

        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("teamorg_test_client")
            .withUsername("test")
            .withPassword("test")

        private var serverPort: Int = 0
        private lateinit var server: io.ktor.server.engine.EmbeddedServer<*, *>

        @JvmStatic
        @BeforeClass
        fun startServer() {
            postgres.start()
            serverPort = findFreePort()

            val appConfig = MapApplicationConfig(
                "jwt.secret" to "test_secret_32_chars_long_minimum_required",
                "jwt.issuer" to "teamorg",
                "jwt.audience" to "teamorg-users",
                "jwt.realm" to "teamorg",
                "jwt.expiry-days" to "30",
                "database.url" to postgres.jdbcUrl,
                "database.driver" to "org.postgresql.Driver",
                "database.username" to postgres.username,
                "database.password" to postgres.password
            )

            val env = applicationEnvironment { config = appConfig }

            server = embeddedServer(
                factory = Netty,
                environment = env,
                configure = { connector { port = serverPort } },
                module = { serverModule() }
            )

            server.start(wait = false)
            waitForPort(serverPort)
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            server.stop(100, 500)
            postgres.stop()
        }

        private fun findFreePort(): Int = ServerSocket(0).use { it.localPort }

        private fun waitForPort(port: Int, retries: Int = 30, delayMs: Long = 200) {
            repeat(retries) {
                try {
                    java.net.Socket("localhost", port).close()
                    return
                } catch (_: Exception) {
                    Thread.sleep(delayMs)
                }
            }
            error("Server did not start on port $port within ${retries * delayMs}ms")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test infrastructure
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds a Ktor HttpClient configured identically to the shared module's
     * [ch.teamorg.data.network.HttpClientFactory], pointed at the test server.
     * [token] is evaluated per-request via the [DefaultRequest] lambda.
     */
    private fun buildClient(token: () -> String?): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(DefaultRequest) {
            url("http://localhost:$serverPort")
            contentType(ContentType.Application.Json)
            val t = token()
            if (t != null) header("Authorization", "Bearer $t")
        }
    }

    /**
     * Registers a new user and returns the [AuthResponse].
     *
     * This calls [AuthRepositoryImpl.register] directly — the real shared code path.
     * [UserPreferences] is a final actual class backed by a single JVM preferences node,
     * so we cannot have independent instances per test. Instead we:
     * 1. Create a fresh prefs + unauthenticated client
     * 2. Call register (which stores the token in prefs)
     * 3. Return the [AuthResponse] so the caller can extract the token
     *
     * The caller then builds a *new* client that captures the token directly from the
     * [AuthResponse], bypassing the shared prefs node entirely.
     */
    private suspend fun registerUser(email: String, displayName: String, password: String = "Password1!"): AuthResponse {
        val prefs = UserPreferences()
        prefs.clearToken()
        val unauthClient = buildClient { null }
        return AuthRepositoryImpl(unauthClient, prefs)
            .register(RegisterRequest(email, password, displayName))
            .getOrThrow()
    }

    /**
     * Club creation is now restricted to super-admins. Flip the flag directly in the
     * test database (the guard re-reads isSuperAdmin from the DB per request, so the
     * already-issued JWT stays valid).
     */
    private fun promoteToSuperAdmin(userId: String) {
        postgres.createConnection("").use { conn ->
            conn.prepareStatement("UPDATE users SET is_super_admin = true WHERE id = ?::uuid").use { st ->
                st.setString(1, userId)
                st.executeUpdate()
            }
        }
    }

    /** Registers a user and immediately promotes them to super-admin (for club creation). */
    private suspend fun registerSuperAdmin(email: String, displayName: String): AuthResponse =
        registerUser(email, displayName).also { promoteToSuperAdmin(it.userId) }

    // ──────────────────────────────────────────────────────────────────────────
    // Flow 2: register → create club → create team → createInvite → redeem → roster
    // Tests that createInvite sends a correct Content-Type header (the original bug).
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow2 createInvite sends correct Content-Type and roster is updated after redeem`() = runBlocking {
        // Register CM and build an authenticated client with its token
        val cmAuth = registerSuperAdmin("client.flow2.cm@test.local", "CM ClientFlow2")
        val cmClient = buildClient { cmAuth.token }

        // Wire up the shared repositories to the CM client
        val cmClubRepo = ClubRepositoryImpl(cmClient)
        val cmTeamRepo = TeamRepositoryImpl(cmClient)

        val club = cmClubRepo.createClub("Client Flow2 Club", "volleyball", "Bern").getOrThrow()
        assertNotNull(club.id)

        val team = cmClubRepo.createTeam(club.id, "Client Flow2 Team", null).getOrThrow()
        assertNotNull(team.id)

        // Critical: exercises real HTTP POST with JSON body + Content-Type header.
        // A missing Content-Type would cause the server to return 415 Unsupported Media Type,
        // which makes getOrThrow() throw and the test fail.
        val inviteUrl = cmTeamRepo.createInvite(team.id, "player", null).getOrThrow()
        assertNotNull(inviteUrl)
        val inviteToken = inviteUrl.substringAfterLast("/")
        assertTrue(inviteToken.isNotBlank(), "invite token must be non-blank")

        // Register player and build an authenticated client with its token
        val playerAuth = registerUser("client.flow2.player@test.local", "Player ClientFlow2")
        val playerClient = buildClient { playerAuth.token }
        val playerInviteRepo = InviteRepositoryImpl(playerClient)

        // Get invite details via InviteRepositoryImpl
        val inviteDetails = playerInviteRepo.getInviteDetails(inviteToken).getOrThrow()
        assertFalse(inviteDetails.alreadyRedeemed)
        assertTrue(!inviteDetails.teamName.isNullOrBlank())
        assertTrue(inviteDetails.clubName.isNotBlank())

        // Redeem invite via InviteRepositoryImpl
        assertEquals(RedeemResult.Success, playerInviteRepo.redeemInvite(inviteToken))

        // Verify roster via TeamRepositoryImpl (CM perspective)
        val roster = cmTeamRepo.getTeamRoster(team.id).getOrThrow()
        val playerEntry = roster.find { it.userId == playerAuth.userId }
        assertNotNull(playerEntry, "Player must appear in roster after redeem")
        kotlin.test.assertEquals("player", playerEntry.role)
        assertNull(playerEntry.jerseyNumber, "jerseyNumber must be null initially")
        assertNull(playerEntry.position, "position must be null initially")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Auth: register + isLoggedIn
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `auth register saves token and AuthRepositoryImpl reports isLoggedIn`() = runBlocking {
        val prefs = UserPreferences()
        prefs.clearToken()
        val unauthClient = buildClient { null }
        val authRepo = AuthRepositoryImpl(unauthClient, prefs)

        val result = authRepo.register(
            RegisterRequest("client.flow3@test.local", "Password1!", "Flow3 User")
        )
        assertTrue(result.isSuccess)
        val authResponse = result.getOrThrow()
        assertNotNull(authResponse.token)
        assertNotNull(authResponse.userId)

        // AuthRepositoryImpl must have saved the token to prefs
        assertTrue(authRepo.isLoggedIn())
        assertTrue(prefs.getToken() != null)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Roles: getMyRoles after club creation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `getMyRoles returns club_manager role after creating a club`() = runBlocking {
        val cmAuth = registerSuperAdmin("client.flow4.cm@test.local", "CM ClientFlow4")
        val cmClient = buildClient { cmAuth.token }
        val clubRepo = ClubRepositoryImpl(cmClient)
        val teamRepo = TeamRepositoryImpl(cmClient)

        val club = clubRepo.createClub("Client Flow4 Club", "football", null).getOrThrow()

        val roles = teamRepo.getMyRoles().getOrThrow()
        assertTrue(
            roles.clubRoles.any { it.clubId == club.id && it.role == "club_manager" },
            "clubRoles must contain club_manager entry for club ${club.id}"
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // removeMember: CM removes player from team roster
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `removeMember removes player from team roster`() = runBlocking {
        val cmAuth = registerSuperAdmin("client.flow5.cm@test.local", "CM ClientFlow5")
        val cmClient = buildClient { cmAuth.token }
        val cmClubRepo = ClubRepositoryImpl(cmClient)
        val cmTeamRepo = TeamRepositoryImpl(cmClient)

        val club = cmClubRepo.createClub("Client Flow5 Club", "volleyball", null).getOrThrow()
        val team = cmClubRepo.createTeam(club.id, "Client Flow5 Team", null).getOrThrow()
        val inviteUrl = cmTeamRepo.createInvite(team.id, "player", null).getOrThrow()
        val inviteToken = inviteUrl.substringAfterLast("/")

        val playerAuth = registerUser("client.flow5.player@test.local", "Player ClientFlow5")
        val playerClient = buildClient { playerAuth.token }
        assertEquals(RedeemResult.Success, InviteRepositoryImpl(playerClient).redeemInvite(inviteToken))

        // Assert player is in roster before removal
        assertNotNull(
            cmTeamRepo.getTeamRoster(team.id).getOrThrow().find { it.userId == playerAuth.userId },
            "Player must be in roster before removal"
        )

        // Remove via TeamRepositoryImpl (requires club_manager role)
        cmTeamRepo.removeMember(team.id, playerAuth.userId).getOrThrow()

        // Assert player is gone
        assertNull(
            cmTeamRepo.getTeamRoster(team.id).getOrThrow().find { it.userId == playerAuth.userId },
            "Player must not appear in roster after removal"
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Auth token lifecycle: register on a pre-existing client, then getMe
    //
    // Reproduces the real app flow: a SINGLE HttpClient is created at startup
    // (before any token exists), then register saves a token to UserPreferences,
    // then checkAuthState calls getMe on the SAME client. The client must pick
    // up the newly-saved token at request time, not at creation time.
    //
    // This test would have caught the bug where DefaultRequest captured
    // userPreferences.getToken() once at HttpClient creation (returning null),
    // causing all post-login requests to be unauthenticated (401).
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds a client that mirrors the real [HttpClientFactory.create] pattern:
     * a single HttpClient whose auth header reads the token from [prefs] at
     * request time via a requestPipeline interceptor — NOT captured at creation.
     */
    private fun buildClientWithPrefs(prefs: UserPreferences): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(DefaultRequest) {
                url("http://localhost:$serverPort")
                contentType(ContentType.Application.Json)
            }
        }.also { client ->
            client.requestPipeline.intercept(io.ktor.client.request.HttpRequestPipeline.Before) {
                val token = prefs.getToken()
                if (token != null) {
                    context.bearerAuth(token)
                }
            }
        }
    }

    @Test
    fun `single HttpClient picks up token saved after creation and getMe succeeds`() = runBlocking {
        val prefs = UserPreferences()
        prefs.clearToken()

        // Create the client BEFORE any token exists — mirrors app startup
        val client = buildClientWithPrefs(prefs)
        val authRepo = AuthRepositoryImpl(client, prefs)

        // Register — this saves the token into prefs
        val registerResult = authRepo.register(
            RegisterRequest("client.token-lifecycle@test.local", "Password1!", "TokenLifecycle User")
        )
        assertTrue(registerResult.isSuccess, "register must succeed")
        assertTrue(authRepo.isLoggedIn(), "isLoggedIn must be true after register")

        // getMe on the SAME client — must use the token saved during register.
        // Before the fix, this returned 401 because the token was captured as null.
        val getMeResult = authRepo.getMe()
        assertTrue(getMeResult.isSuccess, "getMe must succeed on same client after register — " +
            "failure here means auth token is not sent (captured at creation instead of per-request)")
        val user = getMeResult.getOrThrow()
        kotlin.test.assertEquals("TokenLifecycle User", user.displayName)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Full invite-join journey: register CM → create club → create team →
    // create invite → new user registers → redeem invite → verify team
    // membership via /auth/me/roles
    //
    // This is the comprehensive end-to-end test that covers the FULL user
    // journey. A green test here means the entire invite flow works at the
    // HTTP/repository level.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `full invite journey - new user redeems invite and gains team role visible in roles endpoint`() = runBlocking {
        // 1. CM registers and creates club + team
        val cmAuth = registerSuperAdmin("full-journey.cm@test.local", "CM FullJourney")
        val cmClient = buildClient { cmAuth.token }
        val cmClubRepo = ClubRepositoryImpl(cmClient)
        val cmTeamRepo = TeamRepositoryImpl(cmClient)

        val club = cmClubRepo.createClub("FullJourney Club", "basketball", "Zurich").getOrThrow()
        val team = cmClubRepo.createTeam(club.id, "FullJourney Team", null).getOrThrow()

        // 2. CM creates invite
        val inviteUrl = cmTeamRepo.createInvite(team.id, "player", null).getOrThrow()
        val inviteToken = inviteUrl.substringAfterLast("/")

        // 3. New player registers
        val playerAuth = registerUser("full-journey.player@test.local", "Player FullJourney")
        val playerClient = buildClient { playerAuth.token }
        val playerInviteRepo = InviteRepositoryImpl(playerClient)
        val playerTeamRepo = TeamRepositoryImpl(playerClient)
        // Build an AuthRepositoryImpl for the player to test hasTeam()
        val playerPrefs = UserPreferences().also { it.saveToken(playerAuth.token) }
        val playerAuthRepo = AuthRepositoryImpl(playerClient, playerPrefs)

        // 4. Player views invite details
        val details = playerInviteRepo.getInviteDetails(inviteToken).getOrThrow()
        kotlin.test.assertEquals(team.name, details.teamName, "teamName must match")
        kotlin.test.assertEquals(club.name, details.clubName, "clubName must match")
        kotlin.test.assertEquals("player", details.role, "role must match")
        assertFalse(details.alreadyRedeemed, "invite must not be redeemed yet")

        // 5. Player redeems invite
        assertEquals(RedeemResult.Success, playerInviteRepo.redeemInvite(inviteToken))

        // 6. Verify: player's roles include team membership
        val playerRoles = playerTeamRepo.getMyRoles().getOrThrow()
        assertTrue(
            playerRoles.teamRoles.any { it.teamId == team.id && it.role == "player" },
            "Player must have 'player' role on team ${team.id} after redeeming invite. " +
            "Actual roles: ${playerRoles.teamRoles}"
        )

        // 7. Verify: hasTeam returns true for player after redeem
        val hasTeam = playerAuthRepo.hasTeam()
        assertTrue(hasTeam, "hasTeam must be true after redeeming invite (player is on a team)")

        // 8. Verify: CM sees player in roster
        val roster = cmTeamRepo.getTeamRoster(team.id).getOrThrow()
        val playerEntry = roster.find { it.userId == playerAuth.userId }
        assertNotNull(playerEntry, "Player must appear in CM's roster view after redeem")
        kotlin.test.assertEquals("player", playerEntry.role)
    }

    @Test
    fun `redeeming same invite twice returns idempotent success or 409`() = runBlocking {
        val cmAuth = registerSuperAdmin("idempotent.cm@test.local", "CM Idempotent")
        val cmClient = buildClient { cmAuth.token }
        val cmClubRepo = ClubRepositoryImpl(cmClient)
        val cmTeamRepo = TeamRepositoryImpl(cmClient)

        val club = cmClubRepo.createClub("Idempotent Club", "soccer", null).getOrThrow()
        val team = cmClubRepo.createTeam(club.id, "Idempotent Team", null).getOrThrow()
        val inviteUrl = cmTeamRepo.createInvite(team.id, "player", null).getOrThrow()
        val inviteToken = inviteUrl.substringAfterLast("/")

        val playerAuth = registerUser("idempotent.player@test.local", "Player Idempotent")
        val playerClient = buildClient { playerAuth.token }
        val playerInviteRepo = InviteRepositoryImpl(playerClient)

        // First redeem
        assertEquals(RedeemResult.Success, playerInviteRepo.redeemInvite(inviteToken))

        // Second redeem — idempotent: 409/already-member maps to Success
        val secondResult = playerInviteRepo.redeemInvite(inviteToken)
        assertEquals(
            RedeemResult.Success,
            secondResult,
            "Second redeem must be idempotent (Success), got: $secondResult"
        )
        // Either way, player is still on the team
        val roster = cmTeamRepo.getTeamRoster(team.id).getOrThrow()
        assertNotNull(roster.find { it.userId == playerAuth.userId }, "Player must still be on team")
        Unit
    }

    @Test
    fun `single HttpClient picks up token saved after creation and hasTeam succeeds`() = runBlocking {
        val prefs = UserPreferences()
        prefs.clearToken()

        val client = buildClientWithPrefs(prefs)
        val authRepo = AuthRepositoryImpl(client, prefs)

        authRepo.register(
            RegisterRequest("client.token-lifecycle-roles@test.local", "Password1!", "TokenRoles User")
        ).getOrThrow()

        // hasTeam calls /auth/me/roles on the SAME client
        val hasTeam = authRepo.hasTeam()
        // New user has no team, but the call itself must not fail with 401
        assertFalse(hasTeam, "newly registered user must not have a team")
    }
}
