package ch.teamorg.routes

import ch.teamorg.domain.repositories.BillingRepository
import ch.teamorg.infra.StripeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StripeWebhook")

fun Route.stripeWebhookRoutes() {
    val billingRepository by inject<BillingRepository>()
    val stripeService by inject<StripeService>()

    post("/stripe/webhook") {
        val payload = call.receiveText()
        val signature = call.request.header("Stripe-Signature")
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing signature")
        val event = try {
            stripeService.constructWebhookEvent(payload, signature)
        } catch (e: IllegalArgumentException) {
            logger.warn("Rejected Stripe webhook with invalid signature")
            return@post call.respond(HttpStatusCode.BadRequest, "Invalid signature")
        }

        // Recording the event and applying its status transition must commit or roll back
        // together: if setBillingStatus failed after recordEvent alone committed, Stripe's
        // retry would hit the duplicate check and never reapply the transition.
        // Dispatchers.IO keeps the blocking transaction off the Netty worker thread while
        // preserving the thread-local join semantics of the nested repository transactions.
        val response = withContext(Dispatchers.IO) { transaction {
            val clubId = event.customerId?.let { billingRepository.findClubIdByCustomerId(it) }
            if (!billingRepository.recordEvent(event.id, clubId, event.type, event.rawJson)) {
                return@transaction HttpStatusCode.OK to "Already processed"
            }
            if (clubId == null) {
                logger.warn("Stripe event ${event.type} without matching club, ignoring")
                return@transaction HttpStatusCode.OK to "No club"
            }

            when (event.type) {
                "invoice.paid" -> billingRepository.setBillingStatus(clubId, "active")
                "invoice.payment_failed" -> billingRepository.setBillingStatus(clubId, "past_due")
                "customer.subscription.updated" ->
                    if (event.subscriptionStatus in setOf("unpaid", "canceled")) {
                        billingRepository.setBillingStatus(clubId, "frozen")
                    }
                "customer.subscription.deleted" -> billingRepository.setBillingStatus(clubId, "frozen")
                else -> logger.info("Ignoring Stripe event type ${event.type}")
            }
            HttpStatusCode.OK to "OK"
        } }
        call.respond(response.first, response.second)
    }
}
