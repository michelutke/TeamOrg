package ch.teamorg.infra

import ch.teamorg.db.tables.BillingEventsTable
import ch.teamorg.db.tables.ClubBillingTable
import ch.teamorg.db.tables.ClubRolesTable
import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.db.tables.InviteLinksTable
import ch.teamorg.db.tables.MemberCountSamplesTable
import ch.teamorg.db.tables.SubGroupMembersTable
import ch.teamorg.db.tables.SubGroupsTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import javax.sql.DataSource

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var initialized = false

    fun init(config: ApplicationConfig) {
        if (initialized) {
            logger.info("DatabaseFactory already initialized, skipping")
            return
        }
        initialized = true

        val driverClassName = config.property("database.driver").getString()
        val jdbcUrl = config.property("database.url").getString()
        val dbUsername = config.propertyOrNull("database.username")?.getString()
        val dbPassword = config.propertyOrNull("database.password")?.getString()

        val dataSource = createHikariDataSource(driverClassName, jdbcUrl, dbUsername, dbPassword)

        Database.connect(dataSource)

        if (driverClassName.contains("h2", ignoreCase = true)) {
            // H2 test database: create tables directly with Exposed (skip Flyway)
            transaction {
                SchemaUtils.create(
                    UsersTable,
                    ClubsTable,
                    ClubRolesTable,
                    TeamsTable,
                    TeamRolesTable,
                    InviteLinksTable,
                    SubGroupsTable,
                    SubGroupMembersTable,
                    ClubBillingTable,
                    MemberCountSamplesTable,
                    BillingEventsTable
                )
            }
            logger.info("H2 test database initialised with SchemaUtils")
        } else {
            runFlyway(dataSource)
            logger.info("Database connection established and migrations applied")
        }
    }

    private fun createHikariDataSource(driver: String, url: String, user: String?, password: String?): DataSource {
        val config = HikariConfig().apply {
            driverClassName = driver
            jdbcUrl = url
            if (user != null) username = user
            if (password != null) this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(config)
    }

    private fun runFlyway(dataSource: DataSource) {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migrations")
            .load()
        flyway.migrate()
    }
}
