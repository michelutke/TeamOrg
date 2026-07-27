package ch.teamorg.domain.repositories

import ch.teamorg.infra.CardInfo
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class ClubBilling(
    val clubId: String,
    val stripeCustomerId: String,
    val stripeSubscriptionId: String?,
    val setupIntentId: String?,
    val billingEmail: String,
    val cardBrand: String?,
    val cardLast4: String?,
    val cardExpMonth: Int?,
    val cardExpYear: Int?,
)

interface BillingRepository {
    fun createBilling(clubId: UUID, stripeCustomerId: String, billingEmail: String, setupIntentId: String)
    fun setSetupIntent(clubId: UUID, setupIntentId: String)
    fun activate(clubId: UUID, subscriptionId: String?, card: CardInfo?) // sets card meta; subscriptionId only when newly created
    fun findByClubId(clubId: UUID): ClubBilling?
    fun findClubIdByCustomerId(customerId: String): UUID?
    fun setBillingStatus(clubId: UUID, status: String) // clubs.billing_status
    /** Insert webhook/job marker; returns false when stripeEventId already processed (idempotency). */
    fun recordEvent(stripeEventId: String, clubId: UUID?, type: String, payload: String): Boolean
    /** Distinct non-provisional users with any ClubRole in club or TeamRole in a non-archived team of club. */
    fun countActiveMembers(clubId: UUID): Int
    fun insertSample(clubId: UUID, count: Int, sampledAt: Instant)
    fun sampleCountsBetween(clubId: UUID, from: Instant, to: Instant): List<Int>
    fun clubsDueForSampling(now: Instant): List<UUID>   // billing_mode='stripe', status='active', next_sample_at null or <= now
    fun setNextSampleAt(clubId: UUID, at: Instant)
    fun activeStripeClubs(): List<UUID>                  // billing_mode='stripe', clubs.status='active', has subscription
    fun deleteAbandonedPendingClubs(cutoff: Instant): Int // clubs.status='pending' AND created_at < cutoff
}
