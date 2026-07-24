package ch.teamorg.billing

import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.koin.dsl.module
import java.util.UUID
import kotlin.test.*

class FrozenClubEnforcementTest : IntegrationTestBase() {

    private val fake = FakeStripeService()
    private val stripeOverride = module { single<StripeService> { fake } }

    private suspend fun io.ktor.client.HttpClient.register(email: String): String {
        val res = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"$email"}""")
        }
        return Json.parseToJsonElement(res.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    @Test
    fun `frozen club blocks writes but allows reads and billing fix`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("fr@x.ch")
        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"kind":"club","name":"VC Ice","billingEmail":"fr@x.ch"}""")
        }
        val clubId = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["clubId"]!!.jsonPrimitive.content
        client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_x"}""")
        }
        transaction {
            ClubsTable.update({ ClubsTable.id eq UUID.fromString(clubId) }) { it[billingStatus] = "frozen" }
        }

        // write blocked with 402
        val teamRes = client.post("/clubs/$clubId/teams") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"name":"Nope"}""")
        }
        assertEquals(HttpStatusCode.PaymentRequired, teamRes.status)

        // read still works
        assertEquals(HttpStatusCode.OK, client.get("/clubs/$clubId") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/clubs/$clubId/teams") { bearerAuth(token) }.status)

        // billing endpoints must stay open to fix the card
        assertEquals(HttpStatusCode.OK, client.post("/clubs/$clubId/billing/update-card") { bearerAuth(token) }.status)
    }
}
