package ch.teamorg.infra

import java.util.UUID

data class SetupIntentResult(val id: String, val clientSecret: String)
data class SetupIntentStatus(val status: String, val paymentMethodId: String?) // status: succeeded | processing | requires_payment_method | ...
data class CardInfo(val brand: String, val last4: String, val expMonth: Int, val expYear: Int)
data class StripeWebhookEvent(val id: String, val type: String, val customerId: String?, val subscriptionStatus: String?, val rawJson: String)

interface StripeService {
    fun createCustomer(email: String, name: String, clubId: UUID): String
    fun createSetupIntent(customerId: String): SetupIntentResult
    fun getSetupIntent(setupIntentId: String): SetupIntentStatus
    fun getCard(paymentMethodId: String): CardInfo?
    fun setDefaultPaymentMethod(customerId: String, paymentMethodId: String)
    fun createYearlySubscription(customerId: String, quantity: Int, anchorEpochSeconds: Long): String
    fun updateSubscriptionQuantity(subscriptionId: String, quantity: Int)
    /** Verifies the Stripe-Signature header; throws IllegalArgumentException on bad signature. */
    fun constructWebhookEvent(payload: String, signatureHeader: String): StripeWebhookEvent
}
