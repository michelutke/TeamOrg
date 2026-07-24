package ch.teamorg.billing

import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.koin.dsl.module
import kotlin.test.*

class BillingManagementTest : IntegrationTestBase() {

    private val fake = FakeStripeService()
    private val stripeOverride = module { single<StripeService> { fake } }

    private suspend fun io.ktor.client.HttpClient.register(email: String): String {
        val res = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"$email"}""")
        }
        return Json.parseToJsonElement(res.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    private suspend fun io.ktor.client.HttpClient.createAndConfirm(token: String, kind: String, name: String): String {
        val res = post("/clubs/self-serve") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"kind":"$kind","name":"$name","billingEmail":"b@x.ch"}""")
        }
        val clubId = Json.parseToJsonElement(res.bodyAsText()).jsonObject["clubId"]!!.jsonPrimitive.content
        post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_x"}""")
        }
        return clubId
    }

    @Test
    fun `billing info returns card meta and projected count for owner only`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("bi@x.ch")
        val clubId = client.createAndConfirm(token, "team", "Crew")

        val res = client.get("/clubs/$clubId/billing") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("4242", body["cardLast4"]!!.jsonPrimitive.content)
        assertEquals(1, body["currentMemberCount"]!!.jsonPrimitive.int)

        val stranger = client.register("nosy@x.ch")
        assertEquals(HttpStatusCode.Forbidden, client.get("/clubs/$clubId/billing") { bearerAuth(stranger) }.status)
    }

    @Test
    fun `team converts to club and back, club with 2 teams refuses downgrade`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("cv@x.ch")
        val clubId = client.createAndConfirm(token, "team", "Solo")

        var res = client.post("/clubs/$clubId/convert") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"targetKind":"club"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("club", Json.parseToJsonElement(res.bodyAsText()).jsonObject["kind"]!!.jsonPrimitive.content)

        res = client.post("/clubs/$clubId/convert") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"targetKind":"team"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)

        // back to club, add second team, downgrade must 409
        client.post("/clubs/$clubId/convert") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"targetKind":"club"}""")
        }
        client.post("/clubs/$clubId/teams") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"name":"Second"}""")
        }
        res = client.post("/clubs/$clubId/convert") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"targetKind":"team"}""")
        }
        assertEquals(HttpStatusCode.Conflict, res.status)
    }

    @Test
    fun `update-card returns fresh setup intent secret`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("uc@x.ch")
        val clubId = client.createAndConfirm(token, "club", "VC Aare")
        val res = client.post("/clubs/$clubId/billing/update-card") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("seti_secret_fake", Json.parseToJsonElement(res.bodyAsText()).jsonObject["setupIntentClientSecret"]!!.jsonPrimitive.content)
    }
}
