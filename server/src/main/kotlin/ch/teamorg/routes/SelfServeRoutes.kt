package ch.teamorg.routes

import ch.teamorg.domain.ZURICH
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
data class SelfServeCreateResponse(val clubId: String, val teamId: String? = null, val setupIntentClientSecret: String)

@Serializable
data class BillingConfirmRequest(val setupIntentId: String)

@Serializable
data class BillingConfirmResponse(val status: String)

fun Route.selfServeRoutes() {
    val clubRepository by inject<ClubRepository>()
    val teamRepository by inject<TeamRepository>()
    val billingRepository by inject<BillingRepository>()
    val userRepository by inject<UserRepository>()
    val stripeService by inject<StripeService>()

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
                    SelfServeCreateResponse(clubId.toString(), teamId?.toString(), setupIntent.clientSecret)
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

                if (billing.stripeSubscriptionId == null) {
                    val quantity = billingRepository.countActiveMembers(clubId)
                    val anchor = nextJanuaryFirstEpochSeconds(ZonedDateTime.now(ZURICH))
                    val subscriptionId = stripeService.createYearlySubscription(billing.stripeCustomerId, quantity, anchor)
                    billingRepository.activate(clubId, subscriptionId, card)
                    clubRepository.setStatus(clubId, "active")
                } else {
                    billingRepository.activate(clubId, null, card) // card update only
                }
                billingRepository.setBillingStatus(clubId, "active")
                call.respond(HttpStatusCode.OK, BillingConfirmResponse("active"))
            }
        }
    }
}
