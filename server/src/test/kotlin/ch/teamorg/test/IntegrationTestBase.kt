package ch.teamorg.test

import ch.teamorg.db.tables.UsersTable
import ch.teamorg.module
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.module.Module
import org.koin.ktor.ext.getKoin
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID

abstract class IntegrationTestBase {

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("teamorg_test")
            .withUsername("test")
            .withPassword("test")
            .also { it.start() }
    }

    fun withTeamorgTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) =
        withTeamorgTestApplication(koinOverride = null, block = block)

    /**
     * Variant that loads an extra Koin module (with override enabled) after the production
     * [module] has wired the default bindings — lets a test swap a `single` (e.g. the
     * SwissVolleyClient) for a fake/stub.
     */
    fun withTeamorgTestApplication(
        koinOverride: Module?,
        block: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        val testConfig = MapApplicationConfig(
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

        environment { config = testConfig }
        application {
            module()
            if (koinOverride != null) {
                getKoin().loadModules(listOf(koinOverride), allowOverride = true)
            }
        }
        block()
    }

    fun ApplicationTestBuilder.createJsonClient(): HttpClient = createClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /** Promote an already-registered user (by userId string) to super-admin so they can create clubs. */
    fun promoteToSuperAdmin(userId: String) {
        transaction {
            UsersTable.update({ UsersTable.id eq UUID.fromString(userId) }) {
                it[isSuperAdmin] = true
            }
        }
    }

    /** Strict client — fails if any unexpected field is present or expected field is missing. */
    fun ApplicationTestBuilder.createStrictJsonClient(): HttpClient = createClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = false
                isLenient = false
            })
        }
    }
}
