package ch.teamorg.billing

import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import java.util.UUID
import kotlin.test.*

class StripeWebhookTest : IntegrationTestBase() {

    private val fake = FakeStripeService()
    private val stripeOverride = module { single<StripeService> { fake } }

    private suspend fun io.ktor.client.HttpClient.register(email: String): String {
        val res = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"$email"}""")
        }
        return Json.parseToJsonElement(res.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    private fun billingStatus(clubId: String): String = transaction {
        ClubsTable.selectAll().where { ClubsTable.id eq UUID.fromString(clubId) }.single()[ClubsTable.billingStatus]
    }

    // Fetch the real Stripe customer id for this club instead of assuming a literal —
    // see ch.teamorg.billing.FakeStripeService: its id counter is a shared companion-object
    // AtomicInteger across every test in the JVM run, so "cus_fake_1" is not reliable here.
    private fun customerIdFor(clubId: String): String = transaction {
        ch.teamorg.db.tables.ClubBillingTable.selectAll()
            .where { ch.teamorg.db.tables.ClubBillingTable.clubId eq UUID.fromString(clubId) }
            .single()[ch.teamorg.db.tables.ClubBillingTable.stripeCustomerId]
    }

    @Test
    fun `payment_failed then subscription unpaid freezes club, invoice paid recovers`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("wh@x.ch")
        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"kind":"club","name":"VC Hook","billingEmail":"wh@x.ch"}""")
        }
        val clubId = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["clubId"]!!.jsonPrimitive.content
        client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_x"}""")
        }
        val customerId = customerIdFor(clubId)

        suspend fun hook(id: String, type: String, subStatus: String? = null): HttpStatusCode =
            client.post("/stripe/webhook") {
                contentType(ContentType.Application.Json)
                header("Stripe-Signature", "t=1,v1=fake")
                setBody(buildString {
                    append("""{"id":"$id","type":"$type","customerId":"$customerId"""")
                    if (subStatus != null) append(""","subscriptionStatus":"$subStatus"""")
                    append("}")
                })
            }.status

        assertEquals(HttpStatusCode.OK, hook("evt_a", "invoice.payment_failed"))
        assertEquals("past_due", billingStatus(clubId))
        assertEquals(HttpStatusCode.OK, hook("evt_b", "customer.subscription.updated", "unpaid"))
        assertEquals("frozen", billingStatus(clubId))
        assertEquals(HttpStatusCode.OK, hook("evt_c", "invoice.paid"))
        assertEquals("active", billingStatus(clubId))
        // duplicate replay: no state change, still 200
        assertEquals(HttpStatusCode.OK, hook("evt_b", "customer.subscription.updated", "unpaid"))
        assertEquals("active", billingStatus(clubId))
    }
}
