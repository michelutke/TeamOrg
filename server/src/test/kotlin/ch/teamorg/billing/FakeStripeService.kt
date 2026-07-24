package ch.teamorg.billing

import ch.teamorg.infra.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class FakeStripeService : StripeService {
    val quantityUpdates = mutableListOf<Pair<String, Int>>()
    val createdSubscriptions = mutableListOf<Triple<String, Int, Long>>()
    var nextSetupIntentStatus = SetupIntentStatus("succeeded", "pm_fake")

    companion object {
        // Shared across instances: JUnit creates a fresh FakeStripeService per test method,
        // but the underlying Postgres testcontainer (and its unique customer-id constraint)
        // persists for the whole test class, so per-instance ids would collide.
        private val counter = AtomicInteger(0)
    }

    override fun createCustomer(email: String, name: String, clubId: UUID) = "cus_fake_${counter.incrementAndGet()}"
    override fun createSetupIntent(customerId: String) = SetupIntentResult("seti_fake_${counter.incrementAndGet()}", "seti_secret_fake")
    override fun getSetupIntent(setupIntentId: String) = nextSetupIntentStatus
    override fun getCard(paymentMethodId: String) = CardInfo("visa", "4242", 12, 2030)
    override fun setDefaultPaymentMethod(customerId: String, paymentMethodId: String) {}
    override fun createYearlySubscription(customerId: String, quantity: Int, anchorEpochSeconds: Long): String {
        val id = "sub_fake_${counter.incrementAndGet()}"
        createdSubscriptions += Triple(customerId, quantity, anchorEpochSeconds)
        return id
    }
    override fun updateSubscriptionQuantity(subscriptionId: String, quantity: Int) {
        quantityUpdates += subscriptionId to quantity
    }
    /** Parses {"id":..,"type":..,"customerId":..,"subscriptionStatus":..} — no signature check. */
    override fun constructWebhookEvent(payload: String, signatureHeader: String): StripeWebhookEvent {
        val json = Json.parseToJsonElement(payload).jsonObject
        return StripeWebhookEvent(
            id = json["id"]!!.jsonPrimitive.content,
            type = json["type"]!!.jsonPrimitive.content,
            customerId = json["customerId"]?.jsonPrimitive?.content,
            subscriptionStatus = json["subscriptionStatus"]?.jsonPrimitive?.content,
            rawJson = payload
        )
    }
}
