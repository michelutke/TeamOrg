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

/**
 * Records a member-count sample for every due stripe club and schedules the next sample at a
 * randomized future point: 5-9 days out during Oct-Dec (billing-relevant season), 21-35 days
 * otherwise. Randomization keeps the sampling schedule unpredictable (anti-gaming).
 */
fun runSampling(billingRepository: BillingRepository, now: ZonedDateTime, random: Random) {
    for (clubId in billingRepository.clubsDueForSampling(now.toInstant())) {
        try {
            val count = billingRepository.countActiveMembers(clubId)
            billingRepository.insertSample(clubId, count, now.toInstant())
            val nextDays = if (now.monthValue >= 10) random.nextInt(5, 10) else random.nextInt(21, 36)
            val nextHour = random.nextInt(6, 23)
            billingRepository.setNextSampleAt(clubId, now.plusDays(nextDays.toLong()).withHour(nextHour).toInstant())
        } catch (e: Exception) {
            // Isolate per club: the club stays due and self-heals on the next hourly tick.
            logger.error("Failed to sample member count for club $clubId", e)
        }
    }
}

/**
 * Dec 31 (Zurich) only: sets each active stripe club's subscription quantity to
 * computeBilledCount(today's snapshot, Oct-Dec samples) so Stripe's Jan 1 renewal invoice
 * bills the right member count. Runs on every hourly tick that day; the marker makes it
 * per-club once-per-year.
 *
 * DELIBERATE ORDERING: the idempotency marker is recorded BEFORE the Stripe call. If the
 * quantity update then fails, it is logged as ERROR and NOT retried automatically — retrying
 * behind the marker would require distinguishing our failure from a Stripe-side success we
 * failed to observe, risking double updates. A logged failure here means: fix the quantity
 * manually in the Stripe dashboard. Do not "fix" the ordering without revisiting that risk.
 */
fun runYearEnd(billingRepository: BillingRepository, stripeService: StripeService, now: ZonedDateTime) {
    if (now.monthValue != 12 || now.dayOfMonth != 31) return
    val q4From = ZonedDateTime.of(now.year, 10, 1, 0, 0, 0, 0, ZURICH).toInstant()
    for (clubId in billingRepository.activeStripeClubs()) {
        val marker = "yearend-${now.year}-$clubId"
        if (!billingRepository.recordEvent(marker, clubId, "yearend", "{}")) continue
        val snapshot = billingRepository.countActiveMembers(clubId)
        val samples = billingRepository.sampleCountsBetween(clubId, q4From, now.toInstant())
        val billed = computeBilledCount(snapshot, samples)
        val subscriptionId = billingRepository.findByClubId(clubId)?.stripeSubscriptionId
        if (subscriptionId == null) {
            logger.warn("Active stripe club $clubId has no subscription id; year-end marker recorded but no quantity update possible")
            continue
        }
        try {
            stripeService.updateSubscriptionQuantity(subscriptionId, billed)
            logger.info("Year-end quantity for club $clubId set to $billed (snapshot=$snapshot, samples=${samples.size})")
        } catch (e: Exception) {
            logger.error("Failed year-end quantity update for club $clubId", e)
        }
    }
}

/** Deletes clubs stuck in status='pending' (abandoned before card setup) older than 48h. */
fun runPendingCleanup(billingRepository: BillingRepository, now: ZonedDateTime) {
    val deleted = billingRepository.deleteAbandonedPendingClubs(now.minusHours(48).toInstant())
    if (deleted > 0) logger.info("Deleted $deleted abandoned pending clubs")
}
