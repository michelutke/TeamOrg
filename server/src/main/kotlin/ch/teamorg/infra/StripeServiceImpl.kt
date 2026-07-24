package ch.teamorg.infra

import com.stripe.StripeClient
import com.stripe.net.RequestOptions
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import com.stripe.param.CustomerUpdateParams
import com.stripe.param.SetupIntentCreateParams
import com.stripe.param.SubscriptionCreateParams
import com.stripe.param.SubscriptionItemUpdateParams
import java.util.UUID

class StripeServiceImpl(
    secretKey: String,
    private val webhookSecret: String,
    private val priceId: String,
) : StripeService {
    private val client = StripeClient(secretKey)

    override fun createCustomer(email: String, name: String, clubId: UUID): String =
        client.customers().create(
            CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .putMetadata("club_id", clubId.toString())
                .build()
        ).id

    override fun createSetupIntent(customerId: String): SetupIntentResult {
        val si = client.setupIntents().create(
            SetupIntentCreateParams.builder()
                .setCustomer(customerId)
                .addPaymentMethodType("card")
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                .build()
        )
        return SetupIntentResult(si.id, si.clientSecret)
    }

    override fun getSetupIntent(setupIntentId: String): SetupIntentStatus {
        val si = client.setupIntents().retrieve(setupIntentId)
        return SetupIntentStatus(si.status, si.paymentMethod)
    }

    override fun getCard(paymentMethodId: String): CardInfo? {
        val pm = client.paymentMethods().retrieve(paymentMethodId)
        val card = pm.card ?: return null
        return CardInfo(card.brand, card.last4, card.expMonth.toInt(), card.expYear.toInt())
    }

    override fun setDefaultPaymentMethod(customerId: String, paymentMethodId: String) {
        client.customers().update(
            customerId,
            CustomerUpdateParams.builder()
                .setInvoiceSettings(
                    CustomerUpdateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(paymentMethodId)
                        .build()
                )
                .build()
        )
    }

    override fun createYearlySubscription(customerId: String, quantity: Int, anchorEpochSeconds: Long, idempotencyKey: String): String =
        client.subscriptions().create(
            SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(
                    SubscriptionCreateParams.Item.builder()
                        .setPrice(priceId)
                        .setQuantity(quantity.toLong().coerceAtLeast(1))
                        .build()
                )
                .setBillingCycleAnchor(anchorEpochSeconds)
                .setProrationBehavior(SubscriptionCreateParams.ProrationBehavior.NONE)
                .build(),
            RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()
        ).id

    override fun updateSubscriptionQuantity(subscriptionId: String, quantity: Int) {
        val sub = client.subscriptions().retrieve(subscriptionId)
        val itemId = sub.items.data.first().id
        client.subscriptionItems().update(
            itemId,
            SubscriptionItemUpdateParams.builder()
                .setQuantity(quantity.toLong().coerceAtLeast(1))
                .setProrationBehavior(SubscriptionItemUpdateParams.ProrationBehavior.NONE)
                .build()
        )
    }

    override fun constructWebhookEvent(payload: String, signatureHeader: String): StripeWebhookEvent {
        val event = try {
            Webhook.constructEvent(payload, signatureHeader, webhookSecret)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Stripe webhook signature", e)
        }
        val obj = event.dataObjectDeserializer.`object`.orElse(null)
        val customerId = when (obj) {
            is com.stripe.model.Invoice -> obj.customer
            is com.stripe.model.Subscription -> obj.customer
            else -> null
        }
        val subscriptionStatus = (obj as? com.stripe.model.Subscription)?.status
        return StripeWebhookEvent(event.id, event.type, customerId, subscriptionStatus, payload)
    }
}
