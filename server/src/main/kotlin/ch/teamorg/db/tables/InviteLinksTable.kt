package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp


object InviteLinksTable : Table("invite_links") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val token = text("token").uniqueIndex() // generated in application code via UUID.randomUUID()
    val teamId = uuid("team_id").references(TeamsTable.id, onDelete = ReferenceOption.CASCADE).nullable()
    val clubId = uuid("club_id").references(ClubsTable.id, onDelete = ReferenceOption.CASCADE).nullable()
    val invitedByUserId = uuid("invited_by_user_id").references(UsersTable.id)
    val invitedEmail = text("invited_email").nullable()
    val role = text("role").default("player") // coach, player, club_manager
    val reusable = bool("reusable").default(false)
    val active = bool("active").default(true)
    val expiresAt = timestamp("expires_at") // set by application code
    val redeemedAt = timestamp("redeemed_at").nullable()
    val redeemedByUserId = uuid("redeemed_by_user_id").references(UsersTable.id).nullable()
    val ndsMemberId = uuid("nds_member_id").references(NdsMembersTable.id, onDelete = ReferenceOption.CASCADE).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}
