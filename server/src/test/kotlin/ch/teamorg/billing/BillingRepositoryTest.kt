package ch.teamorg.billing

import ch.teamorg.db.tables.*
import ch.teamorg.domain.repositories.BillingRepository
import ch.teamorg.test.IntegrationTestBase
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.getKoin
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class BillingRepositoryTest : IntegrationTestBase() {

    private fun makeUser(email: String, provisional: Boolean = false): UUID = transaction {
        UsersTable.insert {
            it[UsersTable.email] = email
            it[passwordHash] = "x"
            it[displayName] = email
            it[UsersTable.provisional] = provisional
        } get UsersTable.id
    }

    @Test
    fun `countActiveMembers dedupes and excludes provisional and archived teams`() = withTeamorgTestApplication {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        startApplication()
        val clubId = transaction { ClubsTable.insert { it[name] = "Count Club" } get ClubsTable.id }
        val teamA = transaction { TeamsTable.insert { it[TeamsTable.clubId] = clubId; it[name] = "A" } get TeamsTable.id }
        val teamArchived = transaction {
            TeamsTable.insert { it[TeamsTable.clubId] = clubId; it[name] = "Old"; it[archivedAt] = Instant.now() } get TeamsTable.id
        }
        val manager = makeUser("m@x.ch")        // club_manager AND player in teamA -> counts once
        val player = makeUser("p@x.ch")          // player in teamA
        val ghost = makeUser("g@x.ch", provisional = true) // provisional -> excluded
        val oldie = makeUser("o@x.ch")           // only in archived team -> excluded
        transaction {
            ClubRolesTable.insert { it[userId] = manager; it[ClubRolesTable.clubId] = clubId; it[role] = "club_manager" }
            TeamRolesTable.insert { it[userId] = manager; it[teamId] = teamA; it[role] = "player" }
            TeamRolesTable.insert { it[userId] = player; it[teamId] = teamA; it[role] = "player" }
            TeamRolesTable.insert { it[userId] = ghost; it[teamId] = teamA; it[role] = "player" }
            TeamRolesTable.insert { it[userId] = oldie; it[teamId] = teamArchived; it[role] = "player" }
        }
        assertEquals(2, repo.countActiveMembers(clubId))
    }

    @Test
    fun `recordEvent is idempotent per stripeEventId`() = withTeamorgTestApplication {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        startApplication()
        assertTrue(repo.recordEvent("evt_1", null, "invoice.paid", "{}"))
        assertFalse(repo.recordEvent("evt_1", null, "invoice.paid", "{}"))
    }

    @Test
    fun `sampling due list and samples roundtrip`() = withTeamorgTestApplication {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        startApplication()
        val clubId = transaction {
            ClubsTable.insert { it[name] = "S"; it[billingMode] = "stripe"; it[status] = "active" } get ClubsTable.id
        }
        repo.createBilling(clubId, "cus_s", "t@x.ch", "seti_1")
        val now = Instant.parse("2026-11-15T10:00:00Z")
        assertTrue(clubId in repo.clubsDueForSampling(now)) // nextSampleAt null -> due
        repo.insertSample(clubId, 25, now)
        repo.setNextSampleAt(clubId, now.plusSeconds(7 * 86400))
        assertTrue(clubId !in repo.clubsDueForSampling(now))
        assertEquals(listOf(25), repo.sampleCountsBetween(clubId, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z")))
    }
}
