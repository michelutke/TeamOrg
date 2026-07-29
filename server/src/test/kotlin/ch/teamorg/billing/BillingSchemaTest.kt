package ch.teamorg.billing

import ch.teamorg.db.tables.ClubBillingTable
import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.test.IntegrationTestBase
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class BillingSchemaTest : IntegrationTestBase() {

    @Test
    fun `new billing columns and tables exist with correct defaults`() = withTeamorgTestApplication {
        startApplication()
        val clubId = transaction {
            ClubsTable.insert {
                it[name] = "Schema Test Club"
            } get ClubsTable.id
        }
        transaction {
            val row = ClubsTable.selectAll().where { ClubsTable.id eq clubId }.single()
            assertEquals("club", row[ClubsTable.kind])
            assertEquals("manual", row[ClubsTable.billingMode])
            assertEquals("active", row[ClubsTable.billingStatus])
            assertEquals(null, row[ClubsTable.ownerUserId])

            ClubBillingTable.insert {
                it[ClubBillingTable.clubId] = clubId
                it[stripeCustomerId] = "cus_test"
                it[billingEmail] = "treasurer@example.com"
            }
            val billing = ClubBillingTable.selectAll().where { ClubBillingTable.clubId eq clubId }.single()
            assertEquals("cus_test", billing[ClubBillingTable.stripeCustomerId])
        }
    }
}
