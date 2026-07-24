package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.BillingEventsTable
import ch.teamorg.db.tables.ClubBillingTable
import ch.teamorg.db.tables.ClubRolesTable
import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.db.tables.MemberCountSamplesTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.TeamsTable
import ch.teamorg.db.tables.UsersTable
import ch.teamorg.infra.CardInfo
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInSubQuery
import org.jetbrains.exposed.sql.transactions.transaction

class BillingRepositoryImpl : BillingRepository {
    override fun createBilling(clubId: UUID, stripeCustomerId: String, billingEmail: String, setupIntentId: String): Unit = transaction {
        ClubBillingTable.insert {
            it[ClubBillingTable.clubId] = clubId
            it[ClubBillingTable.stripeCustomerId] = stripeCustomerId
            it[ClubBillingTable.billingEmail] = billingEmail
            it[ClubBillingTable.setupIntentId] = setupIntentId
        }
    }

    override fun setSetupIntent(clubId: UUID, setupIntentId: String): Unit = transaction {
        ClubBillingTable.update({ ClubBillingTable.clubId eq clubId }) {
            it[ClubBillingTable.setupIntentId] = setupIntentId
            it[ClubBillingTable.updatedAt] = Instant.now()
        }
    }

    override fun activate(clubId: UUID, subscriptionId: String?, card: CardInfo?): Unit = transaction {
        ClubBillingTable.update({ ClubBillingTable.clubId eq clubId }) {
            if (subscriptionId != null) it[ClubBillingTable.stripeSubscriptionId] = subscriptionId
            if (card != null) {
                it[ClubBillingTable.cardBrand] = card.brand
                it[ClubBillingTable.cardLast4] = card.last4
                it[ClubBillingTable.cardExpMonth] = card.expMonth
                it[ClubBillingTable.cardExpYear] = card.expYear
            }
            it[ClubBillingTable.updatedAt] = Instant.now()
        }
    }

    override fun findByClubId(clubId: UUID): ClubBilling? = transaction {
        ClubBillingTable.selectAll().where { ClubBillingTable.clubId eq clubId }
            .map(::rowToClubBilling)
            .singleOrNull()
    }

    override fun findClubIdByCustomerId(customerId: String): UUID? = transaction {
        ClubBillingTable.selectAll().where { ClubBillingTable.stripeCustomerId eq customerId }
            .map { it[ClubBillingTable.clubId] }
            .singleOrNull()
    }

    override fun setBillingStatus(clubId: UUID, status: String): Unit = transaction {
        ClubsTable.update({ ClubsTable.id eq clubId }) {
            it[ClubsTable.billingStatus] = status
            it[ClubsTable.updatedAt] = Instant.now()
        }
    }

    override fun recordEvent(stripeEventId: String, clubId: UUID?, type: String, payload: String): Boolean = transaction {
        val alreadyProcessed = !BillingEventsTable.selectAll()
            .where { BillingEventsTable.stripeEventId eq stripeEventId }
            .empty()
        if (alreadyProcessed) return@transaction false
        try {
            BillingEventsTable.insert {
                it[BillingEventsTable.stripeEventId] = stripeEventId
                it[BillingEventsTable.clubId] = clubId
                it[BillingEventsTable.type] = type
                it[BillingEventsTable.payload] = payload
            }
            true
        } catch (e: ExposedSQLException) {
            // Only a unique violation on stripe_event_id means "concurrent duplicate";
            // anything else is a real failure and must surface.
            if (e.sqlState == "23505") false else throw e
        }
    }

    override fun countActiveMembers(clubId: UUID): Int = transaction {
        val clubRoleUserIds = (ClubRolesTable innerJoin UsersTable)
            .select(UsersTable.id)
            .where { (ClubRolesTable.clubId eq clubId) and (UsersTable.provisional eq false) }
            .map { it[UsersTable.id] }

        val teamRoleUserIds = (TeamRolesTable innerJoin TeamsTable innerJoin UsersTable)
            .select(UsersTable.id)
            .where {
                (TeamsTable.clubId eq clubId) and
                    (TeamsTable.archivedAt.isNull()) and
                    (UsersTable.provisional eq false)
            }
            .map { it[UsersTable.id] }

        (clubRoleUserIds + teamRoleUserIds).distinct().size
    }

    override fun insertSample(clubId: UUID, count: Int, sampledAt: Instant): Unit = transaction {
        MemberCountSamplesTable.insert {
            it[MemberCountSamplesTable.clubId] = clubId
            it[MemberCountSamplesTable.memberCount] = count
            it[MemberCountSamplesTable.sampledAt] = sampledAt
        }
    }

    override fun sampleCountsBetween(clubId: UUID, from: Instant, to: Instant): List<Int> = transaction {
        MemberCountSamplesTable.selectAll()
            .where {
                (MemberCountSamplesTable.clubId eq clubId) and
                    (MemberCountSamplesTable.sampledAt greaterEq from) and
                    (MemberCountSamplesTable.sampledAt lessEq to)
            }
            .orderBy(MemberCountSamplesTable.sampledAt, SortOrder.ASC)
            .map { it[MemberCountSamplesTable.memberCount] }
    }

    override fun clubsDueForSampling(now: Instant): List<UUID> = transaction {
        (ClubsTable innerJoin ClubBillingTable)
            .select(ClubsTable.id)
            .where {
                (ClubsTable.billingMode eq "stripe") and
                    (ClubsTable.status eq "active") and
                    (ClubBillingTable.nextSampleAt.isNull() or (ClubBillingTable.nextSampleAt lessEq now))
            }
            .map { it[ClubsTable.id] }
    }

    override fun setNextSampleAt(clubId: UUID, at: Instant): Unit = transaction {
        ClubBillingTable.update({ ClubBillingTable.clubId eq clubId }) {
            it[ClubBillingTable.nextSampleAt] = at
            it[ClubBillingTable.updatedAt] = Instant.now()
        }
    }

    override fun activeStripeClubs(): List<UUID> = transaction {
        (ClubsTable innerJoin ClubBillingTable)
            .select(ClubsTable.id)
            .where {
                (ClubsTable.billingMode eq "stripe") and
                    (ClubsTable.status eq "active") and
                    (ClubBillingTable.stripeSubscriptionId.isNotNull())
            }
            .map { it[ClubsTable.id] }
    }

    override fun deleteAbandonedPendingClubs(cutoff: Instant): Int = transaction {
        // A pending club with a live Stripe subscription means confirm partially completed
        // (subscription created, but the club status update didn't land) — that needs manual
        // attention, not deletion, so it would strand a live paid subscription otherwise.
        ClubsTable.deleteWhere {
            (ClubsTable.status eq "pending") and (ClubsTable.createdAt less cutoff) and
                (ClubsTable.id notInSubQuery (
                    ClubBillingTable.select(ClubBillingTable.clubId).where { ClubBillingTable.stripeSubscriptionId.isNotNull() }
                ))
        }
    }

    private fun rowToClubBilling(row: org.jetbrains.exposed.sql.ResultRow) = ClubBilling(
        clubId = row[ClubBillingTable.clubId].toString(),
        stripeCustomerId = row[ClubBillingTable.stripeCustomerId],
        stripeSubscriptionId = row[ClubBillingTable.stripeSubscriptionId],
        setupIntentId = row[ClubBillingTable.setupIntentId],
        billingEmail = row[ClubBillingTable.billingEmail],
        cardBrand = row[ClubBillingTable.cardBrand],
        cardLast4 = row[ClubBillingTable.cardLast4],
        cardExpMonth = row[ClubBillingTable.cardExpMonth],
        cardExpYear = row[ClubBillingTable.cardExpYear],
    )
}
