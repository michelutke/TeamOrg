package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.*
import ch.teamorg.domain.models.InviteDetails
import ch.teamorg.domain.models.InviteLink
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class InviteRepositoryImpl : InviteRepository {
    override suspend fun create(
        teamId: UUID,
        createdByUserId: UUID,
        role: String,
        email: String?,
        reusable: Boolean,
        expiresInDays: Int?
    ): InviteLink = transaction {
        val days = expiresInDays ?: if (reusable) 30 else 7
        val insertedId = InviteLinksTable.insert {
            it[InviteLinksTable.token] = UUID.randomUUID().toString()
            it[InviteLinksTable.teamId] = teamId
            it[InviteLinksTable.clubId] = null
            it[InviteLinksTable.invitedByUserId] = createdByUserId
            it[InviteLinksTable.role] = role
            it[InviteLinksTable.invitedEmail] = email
            it[InviteLinksTable.reusable] = reusable
            it[InviteLinksTable.active] = true
            it[InviteLinksTable.expiresAt] = Instant.now().plusSeconds(days.toLong() * 24 * 60 * 60)
        } get InviteLinksTable.id

        InviteLinksTable.selectAll().where { InviteLinksTable.id eq insertedId }
            .map(::rowToInviteLink)
            .single()
    }

    override suspend fun createClubInvite(
        clubId: UUID,
        createdByUserId: UUID,
        role: String,
        email: String,
        expiresInDays: Int?
    ): InviteLink = transaction {
        val days = expiresInDays ?: 7
        val insertedId = InviteLinksTable.insert {
            it[InviteLinksTable.token] = UUID.randomUUID().toString()
            it[InviteLinksTable.teamId] = null
            it[InviteLinksTable.clubId] = clubId
            it[InviteLinksTable.invitedByUserId] = createdByUserId
            it[InviteLinksTable.role] = role
            it[InviteLinksTable.invitedEmail] = email
            it[InviteLinksTable.reusable] = false
            it[InviteLinksTable.active] = true
            it[InviteLinksTable.expiresAt] = Instant.now().plusSeconds(days.toLong() * 24 * 60 * 60)
        } get InviteLinksTable.id

        InviteLinksTable.selectAll().where { InviteLinksTable.id eq insertedId }
            .map(::rowToInviteLink)
            .single()
    }

    override suspend fun findByToken(token: String): InviteLink? = transaction {
        InviteLinksTable.selectAll().where { InviteLinksTable.token eq token }
            .map(::rowToInviteLink)
            .singleOrNull()
    }

    override suspend fun getInviteDetails(token: String): InviteDetails? = transaction {
        val invite = InviteLinksTable.selectAll().where { InviteLinksTable.token eq token }
            .singleOrNull() ?: return@transaction null

        val invitedBy = UsersTable.select(UsersTable.displayName)
            .where { UsersTable.id eq invite[InviteLinksTable.invitedByUserId] }
            .map { it[UsersTable.displayName] }
            .singleOrNull() ?: ""

        val clubIdValue = invite[InviteLinksTable.clubId]
        val teamIdValue = invite[InviteLinksTable.teamId]

        val (scope, teamName, clubName) = if (clubIdValue != null) {
            val club = ClubsTable.select(ClubsTable.name)
                .where { ClubsTable.id eq clubIdValue }
                .map { it[ClubsTable.name] }
                .singleOrNull() ?: ""
            Triple("club", null, club)
        } else {
            val row = (TeamsTable innerJoin ClubsTable)
                .select(TeamsTable.name, ClubsTable.name)
                .where { TeamsTable.id eq teamIdValue!! }
                .singleOrNull()
            Triple("team", row?.get(TeamsTable.name) ?: "", row?.get(ClubsTable.name) ?: "")
        }

        val reusable = invite[InviteLinksTable.reusable]

        InviteDetails(
            token = invite[InviteLinksTable.token],
            scope = scope,
            teamName = teamName,
            clubName = clubName,
            role = invite[InviteLinksTable.role],
            invitedBy = invitedBy,
            invitedEmail = invite[InviteLinksTable.invitedEmail],
            reusable = reusable,
            expiresAt = invite[InviteLinksTable.expiresAt].toString(),
            alreadyRedeemed = if (reusable) false else invite[InviteLinksTable.redeemedAt] != null
        )
    }

    override suspend fun setActive(token: String, active: Boolean): Unit = transaction {
        InviteLinksTable.update({ InviteLinksTable.token eq token }) {
            it[InviteLinksTable.active] = active
        }
    }

    override suspend fun redeem(invite: InviteLink, userId: UUID): RedeemResult = transaction {
        val clubId = invite.clubId?.let { UUID.fromString(it) }
        val teamId = invite.teamId?.let { UUID.fromString(it) }

        if (clubId != null) {
            // Club scope → club_manager
            if (checkIsClubMember(clubId, userId, invite.role)) return@transaction RedeemResult.ALREADY_MEMBER
            ClubRolesTable.insert {
                it[ClubRolesTable.userId] = userId
                it[ClubRolesTable.clubId] = clubId
                it[ClubRolesTable.role] = invite.role
            }
            if (!invite.reusable) markRedeemed(invite.token, userId)
            RedeemResult.OK
        } else {
            requireNotNull(teamId) { "Invite has neither teamId nor clubId" }
            if (checkIsMember(teamId, userId, invite.role)) return@transaction RedeemResult.ALREADY_MEMBER

            if (invite.reusable) {
                // Reusable: do NOT touch redeemed*. insertIgnore (ON CONFLICT DO NOTHING)
                // makes a concurrent same-user double-redeem idempotent without aborting
                // the transaction (a try/catch on the unique violation would not, on Postgres).
                TeamRolesTable.insertIgnore {
                    it[TeamRolesTable.userId] = userId
                    it[TeamRolesTable.teamId] = teamId
                    it[TeamRolesTable.role] = invite.role
                }
                RedeemResult.OK
            } else {
                TeamRolesTable.insert {
                    it[TeamRolesTable.userId] = userId
                    it[TeamRolesTable.teamId] = teamId
                    it[TeamRolesTable.role] = invite.role
                }
                markRedeemed(invite.token, userId)
                RedeemResult.OK
            }
        }
    }

    override suspend fun isMember(teamId: UUID, userId: UUID, role: String): Boolean = transaction {
        checkIsMember(teamId, userId, role)
    }

    private fun markRedeemed(token: String, userId: UUID) {
        InviteLinksTable.update({ InviteLinksTable.token eq token }) {
            it[redeemedAt] = Instant.now()
            it[redeemedByUserId] = userId
        }
    }

    // Non-suspend versions for use inside transaction blocks
    private fun checkIsMember(teamId: UUID, userId: UUID, role: String): Boolean =
        !TeamRolesTable.selectAll().where {
            (TeamRolesTable.teamId eq teamId) and (TeamRolesTable.userId eq userId) and (TeamRolesTable.role eq role)
        }.empty()

    private fun checkIsClubMember(clubId: UUID, userId: UUID, role: String): Boolean =
        !ClubRolesTable.selectAll().where {
            (ClubRolesTable.clubId eq clubId) and (ClubRolesTable.userId eq userId) and (ClubRolesTable.role eq role)
        }.empty()

    private fun rowToInviteLink(row: ResultRow) = InviteLink(
        id = row[InviteLinksTable.id].toString(),
        token = row[InviteLinksTable.token],
        teamId = row[InviteLinksTable.teamId]?.toString(),
        clubId = row[InviteLinksTable.clubId]?.toString(),
        invitedByUserId = row[InviteLinksTable.invitedByUserId].toString(),
        invitedEmail = row[InviteLinksTable.invitedEmail],
        role = row[InviteLinksTable.role],
        reusable = row[InviteLinksTable.reusable],
        active = row[InviteLinksTable.active],
        expiresAt = row[InviteLinksTable.expiresAt].toString(),
        redeemedAt = row[InviteLinksTable.redeemedAt]?.toString(),
        redeemedByUserId = row[InviteLinksTable.redeemedByUserId]?.toString(),
        createdAt = row[InviteLinksTable.createdAt].toString()
    )
}
