package ch.teamorg.routes

import ch.teamorg.domain.ZURICH
import ch.teamorg.domain.computeBilledCount
import ch.teamorg.domain.nextJanuaryFirstEpochSeconds
import ch.teamorg.domain.repositories.BillingRepository
import ch.teamorg.domain.repositories.ClubRepository
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.domain.repositories.UserRepository
import ch.teamorg.infra.StripeService
import ch.teamorg.middleware.authenticateUser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.ZonedDateTime
import java.util.UUID

@Serializable
data class SelfServeCreateRequest(
    val kind: String, // club | team
    val name: String,
    val sportType: String = "volleyball",
    val location: String? = null,
    val billingEmail: String,
)

@Serializable
data class SelfServeCreateResponse(val clubId: String, val teamId: String? = null, val setupIntentClientSecret: String, val publishableKey: String)

@Serializable
data class BillingConfirmRequest(val setupIntentId: String)

@Serializable
data class BillingConfirmResponse(val status: String)

@Serializable
data class ConvertRequest(val targetKind: String)

@Serializable
data class BillingInfoResponse(
    val billingEmail: String,
    val cardBrand: String?,
    val cardLast4: String?,
    val cardExpMonth: Int?,
    val cardExpYear: Int?,
    val currentMemberCount: Int,
    val projectedBilledCount: Int,
    val billingStatus: String,
    val billingMode: String,
    val kind: String,
)

fun Route.selfServeRoutes() {
    val clubRepository by inject<ClubRepository>()
    val teamRepository by inject<TeamRepository>()
    val billingRepository by inject<BillingRepository>()
    val userRepository by inject<UserRepository>()
    val stripeService by inject<StripeService>()
    val publishableKey = application.environment.config
        .propertyOrNull("stripe.publishable-key")?.getString() ?: ""

    authenticate("jwt") {
        post("/clubs/self-serve") {
            call.authenticateUser(userRepository) { user ->
                val userId = UUID.fromString(user.id)
                val request = call.receive<SelfServeCreateRequest>()
                if (request.kind !in setOf("club", "team")) {
                    return@authenticateUser call.respond(HttpStatusCode.BadRequest, "kind must be club or team")
                }
                if (request.name.isBlank()) return@authenticateUser call.respond(HttpStatusCode.BadRequest, "Name is required")
                if (!request.billingEmail.contains("@")) {
                    return@authenticateUser call.respond(HttpStatusCode.BadRequest, "Valid billing email required")
                }

                val clubId = clubRepository.createSelfServe(request.name, request.sportType, request.location, request.kind, userId)
                var teamId: UUID? = null
                if (request.kind == "team") {
                    val team = teamRepository.create(clubId, request.name, null)
                    teamId = UUID.fromString(team.id)
                    teamRepository.addMember(teamId, userId, "coach")
                }
                val customerId = stripeService.createCustomer(request.billingEmail, request.name, clubId)
                val setupIntent = stripeService.createSetupIntent(customerId)
                billingRepository.createBilling(clubId, customerId, request.billingEmail, setupIntent.id)
                call.respond(
                    HttpStatusCode.Created,
                    SelfServeCreateResponse(clubId.toString(), teamId?.toString(), setupIntent.clientSecret, publishableKey)
                )
            }
        }

        post("/clubs/{clubId}/billing/confirm") {
            call.authenticateUser(userRepository) { user ->
                val userId = UUID.fromString(user.id)
                val clubId = UUID.fromString(call.parameters["clubId"])
                if (clubRepository.findOwnerId(clubId) != userId) {
                    return@authenticateUser call.respond(HttpStatusCode.Forbidden, "Only the club owner can manage billing")
                }
                val request = call.receive<BillingConfirmRequest>()
                val billing = billingRepository.findByClubId(clubId)
                    ?: return@authenticateUser call.respond(HttpStatusCode.NotFound, "No billing record for club")

                val si = stripeService.getSetupIntent(request.setupIntentId)
                if (si.status != "succeeded" || si.paymentMethodId == null) {
                    return@authenticateUser call.respond(HttpStatusCode.PaymentRequired, "Card setup not completed")
                }
                stripeService.setDefaultPaymentMethod(billing.stripeCustomerId, si.paymentMethodId)
                val card = stripeService.getCard(si.paymentMethodId)

                // Idempotency key only guards the concurrent double-create race; a sequential re-confirm
                // after stripeSubscriptionId is already persisted takes the else branch (card-update only)
                // by design — that's expected/accepted behavior, not a bug.
                if (billing.stripeSubscriptionId == null) {
                    val quantity = billingRepository.countActiveMembers(clubId)
                    val anchor = nextJanuaryFirstEpochSeconds(ZonedDateTime.now(ZURICH))
                    val subscriptionId = stripeService.createYearlySubscription(billing.stripeCustomerId, quantity, anchor, "sub-create-$clubId")
                    billingRepository.activate(clubId, subscriptionId, card)
                    clubRepository.setStatus(clubId, "active")
                    billingRepository.setBillingStatus(clubId, "active")
                } else {
                    // Card-update only (subscription already exists). Do NOT touch billingStatus here:
                    // recovery from frozen/past_due happens exclusively via the invoice.paid Stripe
                    // webhook, not via re-confirming card setup.
                    billingRepository.activate(clubId, null, card)
                }
                call.respond(HttpStatusCode.OK, BillingConfirmResponse("active"))
            }
        }

        get("/clubs/{clubId}/billing") {
            call.authenticateUser(userRepository) { user ->
                val userId = UUID.fromString(user.id)
                val clubId = UUID.fromString(call.parameters["clubId"])
                if (clubRepository.findOwnerId(clubId) != userId) {
                    return@authenticateUser call.respond(HttpStatusCode.Forbidden, "Only the club owner can view billing")
                }
                val billing = billingRepository.findByClubId(clubId)
                    ?: return@authenticateUser call.respond(HttpStatusCode.NotFound, "No billing record for club")
                val club = clubRepository.findById(clubId)
                    ?: return@authenticateUser call.respond(HttpStatusCode.NotFound, "Club not found")
                val current = billingRepository.countActiveMembers(clubId)
                val now = ZonedDateTime.now(ZURICH)
                val q4From = ZonedDateTime.of(now.year, 10, 1, 0, 0, 0, 0, ZURICH).toInstant()
                val samples = billingRepository.sampleCountsBetween(clubId, q4From, now.toInstant())
                call.respond(
                    BillingInfoResponse(
                        billingEmail = billing.billingEmail,
                        cardBrand = billing.cardBrand,
                        cardLast4 = billing.cardLast4,
                        cardExpMonth = billing.cardExpMonth,
                        cardExpYear = billing.cardExpYear,
                        currentMemberCount = current,
                        projectedBilledCount = computeBilledCount(current, samples),
                        billingStatus = club.billingStatus,
                        billingMode = club.billingMode,
                        kind = clubRepository.findKind(clubId) ?: "club",
                    )
                )
            }
        }

        post("/clubs/{clubId}/billing/update-card") {
            call.authenticateUser(userRepository) { user ->
                val userId = UUID.fromString(user.id)
                val clubId = UUID.fromString(call.parameters["clubId"])
                if (clubRepository.findOwnerId(clubId) != userId) {
                    return@authenticateUser call.respond(HttpStatusCode.Forbidden, "Only the club owner can manage billing")
                }
                val billing = billingRepository.findByClubId(clubId)
                    ?: return@authenticateUser call.respond(HttpStatusCode.NotFound, "No billing record for club")
                val setupIntent = stripeService.createSetupIntent(billing.stripeCustomerId)
                billingRepository.setSetupIntent(clubId, setupIntent.id)
                call.respond(mapOf("setupIntentClientSecret" to setupIntent.clientSecret, "publishableKey" to publishableKey))
            }
        }

        post("/clubs/{clubId}/convert") {
            call.authenticateUser(userRepository) { user ->
                val userId = UUID.fromString(user.id)
                val clubId = UUID.fromString(call.parameters["clubId"])
                if (clubRepository.findOwnerId(clubId) != userId) {
                    return@authenticateUser call.respond(HttpStatusCode.Forbidden, "Only the club owner can convert")
                }
                val request = call.receive<ConvertRequest>()
                if (request.targetKind !in setOf("club", "team")) {
                    return@authenticateUser call.respond(HttpStatusCode.BadRequest, "targetKind must be club or team")
                }
                val currentKind = clubRepository.findKind(clubId)
                if (currentKind == request.targetKind) {
                    return@authenticateUser call.respond(HttpStatusCode.OK, mapOf("kind" to request.targetKind))
                }
                if (request.targetKind == "team" && clubRepository.countActiveTeams(clubId) != 1) {
                    return@authenticateUser call.respond(HttpStatusCode.Conflict, "Club must have exactly one active team to become a team")
                }
                clubRepository.setKind(clubId, request.targetKind)
                call.respond(mapOf("kind" to request.targetKind))
            }
        }
    }
}
