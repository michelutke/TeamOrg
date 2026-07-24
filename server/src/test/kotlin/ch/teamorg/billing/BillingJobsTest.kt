package ch.teamorg.billing

import ch.teamorg.db.tables.*
import ch.teamorg.domain.repositories.BillingRepository
import ch.teamorg.domain.ZURICH
import ch.teamorg.infra.runPendingCleanup
import ch.teamorg.infra.runSampling
import ch.teamorg.infra.runYearEnd
import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.random.Random
import kotlin.test.*

class BillingJobsTest : IntegrationTestBase() {

    private val fake = FakeStripeService()
    private val stripeOverride = module { single<StripeService> { fake } }

    private fun makeStripeClub(repo: BillingRepository, name: String, memberCount: Int, subId: String? = "sub_x_$name"): UUID {
        val clubId = transaction {
            ClubsTable.insert {
                it[ClubsTable.name] = name
                it[billingMode] = "stripe"
                it[status] = "active"
            } get ClubsTable.id
        }
        repo.createBilling(clubId, "cus_$name", "t@x.ch", "seti")
        if (subId != null) repo.activate(clubId, subId, null)
        val teamId = transaction { TeamsTable.insert { it[TeamsTable.clubId] = clubId; it[TeamsTable.name] = name } get TeamsTable.id }
        repeat(memberCount) { i ->
            val u = transaction {
                UsersTable.insert {
                    it[email] = "$name$i@x.ch"; it[passwordHash] = "x"; it[displayName] = "u$i"
                } get UsersTable.id
            }
            transaction { TeamRolesTable.insert { it[userId] = u; it[TeamRolesTable.teamId] = teamId; it[role] = "player" } }
        }
        return clubId
    }

    @Test
    fun `sampling records count and schedules q4 window 5-9 days`() = withTeamorgTestApplication(stripeOverride) {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        startApplication()
        val clubId = makeStripeClub(repo, "smp", memberCount = 4)
        val now = ZonedDateTime.of(2026, 11, 10, 12, 0, 0, 0, ZURICH)
        runSampling(repo, now, Random(42))
        assertEquals(listOf(4), repo.sampleCountsBetween(clubId, now.minusDays(1).toInstant(), now.plusDays(1).toInstant()))
        assertTrue(clubId !in repo.clubsDueForSampling(now.toInstant()))
        assertTrue(clubId in repo.clubsDueForSampling(now.plusDays(10).toInstant())) // next sample within 5-9 days
    }

    @Test
    fun `year end uses max of snapshot and q4 median, runs once`() = withTeamorgTestApplication(stripeOverride) {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        startApplication()
        val clubId = makeStripeClub(repo, "ye", memberCount = 2) // gamed-down snapshot of 2
        // season samples say 10
        listOf(10, 10, 11).forEachIndexed { i, c ->
            repo.insertSample(clubId, c, ZonedDateTime.of(2026, 10 + i, 5, 12, 0, 0, 0, ZURICH).toInstant())
        }
        val dec31 = ZonedDateTime.of(2026, 12, 31, 21, 0, 0, 0, ZURICH)
        runYearEnd(repo, fake, dec31)
        assertEquals(listOf("sub_x_ye" to 10), fake.quantityUpdates.filter { it.first == "sub_x_ye" })
        runYearEnd(repo, fake, dec31.plusHours(1)) // idempotent
        assertEquals(1, fake.quantityUpdates.count { it.first == "sub_x_ye" })
        // not Dec 31 -> no-op
        runYearEnd(repo, fake, ZonedDateTime.of(2026, 12, 30, 21, 0, 0, 0, ZURICH))
        assertEquals(1, fake.quantityUpdates.count { it.first == "sub_x_ye" })
    }

    @Test
    fun `pending cleanup removes only stale pending clubs`() = withTeamorgTestApplication(stripeOverride) {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        startApplication()
        val stale = transaction {
            ClubsTable.insert { it[name] = "stale"; it[status] = "pending" } get ClubsTable.id
        }
        transaction {
            ClubsTable.update({ ClubsTable.id eq stale }) { it[createdAt] = Instant.now().minusSeconds(3 * 86400) }
        }
        val fresh = transaction { ClubsTable.insert { it[name] = "fresh"; it[status] = "pending" } get ClubsTable.id }
        runPendingCleanup(repo, ZonedDateTime.now(ZURICH))
        val remaining = transaction { ClubsTable.selectAll().map { it[ClubsTable.id] } }
        assertTrue(stale !in remaining)
        assertTrue(fresh in remaining)
    }
}
