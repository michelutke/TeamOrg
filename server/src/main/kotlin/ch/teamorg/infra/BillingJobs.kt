package ch.teamorg.infra

import ch.teamorg.domain.ZURICH
import ch.teamorg.domain.computeBilledCount
import ch.teamorg.domain.repositories.BillingRepository
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

private val logger = LoggerFactory.getLogger("BillingJobs")

fun Application.startBillingJobs() {
    val billingRepository by inject<BillingRepository>()
    val stripeService by inject<StripeService>()

    launch(Dispatchers.IO) {
        while (isActive) {
            delay(1.hours)
            try {
                val now = ZonedDateTime.now(ZURICH)
                runSampling(billingRepository, now, Random)
                runYearEnd(billingRepository, stripeService, now)
                runPendingCleanup(billingRepository, now)
            } catch (e: Exception) {
                logger.error("Billing jobs error", e)
            }
        }
    }
}

fun runSampling(billingRepository: BillingRepository, now: ZonedDateTime, random: Random) {
    for (clubId in billingRepository.clubsDueForSampling(now.toInstant())) {
        val count = billingRepository.countActiveMembers(clubId)
        billingRepository.insertSample(clubId, count, now.toInstant())
        val nextDays = if (now.monthValue >= 10) random.nextInt(5, 10) else random.nextInt(21, 36)
        val nextHour = random.nextInt(6, 23)
        billingRepository.setNextSampleAt(clubId, now.plusDays(nextDays.toLong()).withHour(nextHour).toInstant())
    }
}

fun runYearEnd(billingRepository: BillingRepository, stripeService: StripeService, now: ZonedDateTime) {
    if (now.monthValue != 12 || now.dayOfMonth != 31) return
    val q4From = ZonedDateTime.of(now.year, 10, 1, 0, 0, 0, 0, ZURICH).toInstant()
    for (clubId in billingRepository.activeStripeClubs()) {
        val marker = "yearend-${now.year}-$clubId"
        if (!billingRepository.recordEvent(marker, clubId, "yearend", "{}")) continue
        val snapshot = billingRepository.countActiveMembers(clubId)
        val samples = billingRepository.sampleCountsBetween(clubId, q4From, now.toInstant())
        val billed = computeBilledCount(snapshot, samples)
        val subscriptionId = billingRepository.findByClubId(clubId)?.stripeSubscriptionId ?: continue
        try {
            stripeService.updateSubscriptionQuantity(subscriptionId, billed)
            logger.info("Year-end quantity for club $clubId set to $billed (snapshot=$snapshot, samples=${samples.size})")
        } catch (e: Exception) {
            logger.error("Failed year-end quantity update for club $clubId", e)
        }
    }
}

fun runPendingCleanup(billingRepository: BillingRepository, now: ZonedDateTime) {
    val deleted = billingRepository.deleteAbandonedPendingClubs(now.minusHours(48).toInstant())
    if (deleted > 0) logger.info("Deleted $deleted abandoned pending clubs")
}
