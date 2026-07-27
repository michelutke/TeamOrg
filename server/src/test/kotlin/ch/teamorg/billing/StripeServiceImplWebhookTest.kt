package ch.teamorg.billing

import ch.teamorg.infra.StripeServiceImpl
import com.stripe.Stripe
import com.stripe.net.Webhook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private const val WEBHOOK_SECRET = "whsec_test"

private fun stripeService() =
    StripeServiceImpl(secretKey = "sk_test_dummy", webhookSecret = WEBHOOK_SECRET, priceId = "price_dummy")

private fun signatureHeader(payload: String, secret: String = WEBHOOK_SECRET, timestamp: Long = Webhook.Util.getTimeNow()): String {
    val signedPayload = "$timestamp.$payload"
    val signature = Webhook.Util.computeHmacSha256(secret, signedPayload)
    return "t=$timestamp,v1=$signature"
}

class StripeServiceImplWebhookTest {
    @Test fun `invoice payment failed event extracts customer id and no subscription status`() {
        val payload = """
            {
              "id": "evt_invoice_1",
              "object": "event",
              "api_version": "${Stripe.API_VERSION}",
              "created": 1700000000,
              "type": "invoice.payment_failed",
              "data": {
                "object": {
                  "id": "in_123",
                  "object": "invoice",
                  "customer": "cus_123"
                }
              }
            }
        """.trimIndent()

        val event = stripeService().constructWebhookEvent(payload, signatureHeader(payload))

        assertEquals("evt_invoice_1", event.id)
        assertEquals("invoice.payment_failed", event.type)
        assertEquals("cus_123", event.customerId)
        assertNull(event.subscriptionStatus)
    }

    @Test fun `subscription updated event extracts customer id and subscription status`() {
        val payload = """
            {
              "id": "evt_sub_1",
              "object": "event",
              "api_version": "${Stripe.API_VERSION}",
              "created": 1700000000,
              "type": "customer.subscription.updated",
              "data": {
                "object": {
                  "id": "sub_456",
                  "object": "subscription",
                  "customer": "cus_456",
                  "status": "unpaid"
                }
              }
            }
        """.trimIndent()

        val event = stripeService().constructWebhookEvent(payload, signatureHeader(payload))

        assertEquals("evt_sub_1", event.id)
        assertEquals("customer.subscription.updated", event.type)
        assertEquals("cus_456", event.customerId)
        assertEquals("unpaid", event.subscriptionStatus)
    }

    @Test fun `tampered signature throws IllegalArgumentException`() {
        val payload = """
            {
              "id": "evt_tampered",
              "object": "event",
              "api_version": "${Stripe.API_VERSION}",
              "created": 1700000000,
              "type": "invoice.payment_failed",
              "data": {
                "object": {
                  "id": "in_123",
                  "object": "invoice",
                  "customer": "cus_123"
                }
              }
            }
        """.trimIndent()
        val header = signatureHeader(payload, secret = "whsec_wrong")

        assertFailsWith<IllegalArgumentException> {
            stripeService().constructWebhookEvent(payload, header)
        }
    }
}
