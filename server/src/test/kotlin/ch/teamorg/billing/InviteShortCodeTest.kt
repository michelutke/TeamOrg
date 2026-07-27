package ch.teamorg.billing

import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.koin.dsl.module
import kotlin.test.*

class InviteShortCodeTest : IntegrationTestBase() {

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
    fun `reusable team invite has short code resolvable to token and redeemable`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val ownerToken = client.register("sc@x.ch")
        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(ownerToken); contentType(ContentType.Application.Json)
            setBody("""{"kind":"team","name":"Coders","billingEmail":"sc@x.ch"}""")
        }
        val body = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val clubId = body["clubId"]!!.jsonPrimitive.content
        val teamId = body["teamId"]!!.jsonPrimitive.content
        client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(ownerToken); contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_x"}""")
        }

        val inviteRes = client.post("/teams/$teamId/invites") {
            bearerAuth(ownerToken); contentType(ContentType.Application.Json)
            setBody("""{"reusable":true,"role":"player"}""")
        }
        assertEquals(HttpStatusCode.Created, inviteRes.status)
        val shortCode = Json.parseToJsonElement(inviteRes.bodyAsText()).jsonObject["shortCode"]!!.jsonPrimitive.content
        assertEquals(8, shortCode.length)
        assertFalse(shortCode.any { it in "0O1IL" })

        val lookupRes = client.get("/invites/code/$shortCode")
        assertEquals(HttpStatusCode.OK, lookupRes.status)
        val token = Json.parseToJsonElement(lookupRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        // codes are case-insensitive for manual entry
        val lowercaseRes = client.get("/invites/code/${shortCode.lowercase()}")
        assertEquals(HttpStatusCode.OK, lowercaseRes.status)

        val playerToken = client.register("newplayer@x.ch")
        val redeemRes = client.post("/invites/$token/redeem") { bearerAuth(playerToken) }
        assertEquals(HttpStatusCode.OK, redeemRes.status)

        assertEquals(HttpStatusCode.NotFound, client.get("/invites/code/ZZZZZZZZ").status)

        // personal (non-reusable) invites get no short code
        val personalRes = client.post("/teams/$teamId/invites") {
            bearerAuth(ownerToken); contentType(ContentType.Application.Json)
            setBody("""{"email":"personal@x.ch","role":"player"}""")
        }
        assertEquals(HttpStatusCode.Created, personalRes.status)
        val personalShortCode = Json.parseToJsonElement(personalRes.bodyAsText()).jsonObject["shortCode"]
        assertTrue(personalShortCode == null || personalShortCode is JsonNull)
    }
}
