package ch.teamorg.billing

import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.koin.dsl.module
import kotlin.test.*

class SelfServeFlowTest : IntegrationTestBase() {

    private val fake = FakeStripeService()
    private val stripeOverride = module { single<StripeService> { fake } }

    /** Registers user via POST /auth/register, returns JWT. */
    private suspend fun io.ktor.client.HttpClient.register(email: String): String {
        val res = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"$email"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        return Json.parseToJsonElement(res.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    @Test
    fun `create team self-serve then confirm billing activates club`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("owner@x.ch")

        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"team","name":"Volley Crew","billingEmail":"owner@x.ch"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val body = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val clubId = body["clubId"]!!.jsonPrimitive.content
        assertNotNull(body["teamId"], "team kind must auto-create its single team")
        assertEquals("seti_secret_fake", body["setupIntentClientSecret"]!!.jsonPrimitive.content)
        assertNotNull(body["publishableKey"])

        val confirmRes = client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_fake_1"}""")
        }
        assertEquals(HttpStatusCode.OK, confirmRes.status)
        assertEquals(1, fake.createdSubscriptions.size)
        assertEquals(1, fake.createdSubscriptions[0].second) // quantity = 1 member (the owner)
    }

    @Test
    fun `confirming billing twice does not create a duplicate subscription`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("owner2@x.ch")

        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"team","name":"Volley Crew 2","billingEmail":"owner2@x.ch"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val clubId = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["clubId"]!!.jsonPrimitive.content

        val firstConfirmRes = client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_fake_1"}""")
        }
        assertEquals(HttpStatusCode.OK, firstConfirmRes.status)

        val secondConfirmRes = client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_fake_1"}""")
        }
        assertEquals(HttpStatusCode.OK, secondConfirmRes.status)

        assertEquals(1, fake.createdSubscriptions.size)
    }

    @Test
    fun `confirm rejected for non-owner`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val ownerToken = client.register("own2@x.ch")
        val strangerToken = client.register("stranger@x.ch")
        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(ownerToken)
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"club","name":"VC Bern","billingEmail":"own2@x.ch"}""")
        }
        val clubId = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["clubId"]!!.jsonPrimitive.content
        val res = client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(strangerToken)
            contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_fake_1"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, res.status)
    }

    @Test
    fun `confirm with unsucceeded setup intent returns 402`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("own3@x.ch")
        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"club","name":"VC Thun","billingEmail":"own3@x.ch"}""")
        }
        val clubId = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["clubId"]!!.jsonPrimitive.content
        fake.nextSetupIntentStatus = ch.teamorg.infra.SetupIntentStatus("requires_payment_method", null)
        val res = client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_fake_1"}""")
        }
        assertEquals(HttpStatusCode.PaymentRequired, res.status)
    }
}
