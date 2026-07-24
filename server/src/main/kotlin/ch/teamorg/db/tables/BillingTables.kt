package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object ClubBillingTable : Table("club_billing") {
    val clubId = uuid("club_id").references(ClubsTable.id, onDelete = ReferenceOption.CASCADE)
    val stripeCustomerId = text("stripe_customer_id").uniqueIndex()
    val stripeSubscriptionId = text("stripe_subscription_id").nullable()
    val setupIntentId = text("setup_intent_id").nullable()
    val billingEmail = text("billing_email")
    val cardBrand = text("card_brand").nullable()
    val cardLast4 = text("card_last4").nullable()
    val cardExpMonth = integer("card_exp_month").nullable()
    val cardExpYear = integer("card_exp_year").nullable()
    val nextSampleAt = timestamp("next_sample_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(clubId)
}

object MemberCountSamplesTable : Table("member_count_samples") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val clubId = uuid("club_id").references(ClubsTable.id, onDelete = ReferenceOption.CASCADE)
    val sampledAt = timestamp("sampled_at")
    val memberCount = integer("member_count")

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_member_count_samples_club_time", false, clubId, sampledAt)
    }
}

object BillingEventsTable : Table("billing_events") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val stripeEventId = text("stripe_event_id").uniqueIndex()
    val clubId = uuid("club_id").references(ClubsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val type = text("type")
    val payload = text("payload")
    val processedAt = timestamp("processed_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}
