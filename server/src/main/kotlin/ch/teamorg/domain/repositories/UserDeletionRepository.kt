package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.AbwesenheitRulesTable
import ch.teamorg.db.tables.AttendanceResponsesTable
import ch.teamorg.db.tables.ClubRolesTable
import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.db.tables.EventReminderOverridesTable
import ch.teamorg.db.tables.ImpersonationSessionsTable
import ch.teamorg.db.tables.InviteLinksTable
import ch.teamorg.db.tables.NdsMembersTable
import ch.teamorg.db.tables.NotificationRemindersTable
import ch.teamorg.db.tables.NotificationSettingsTable
import ch.teamorg.db.tables.NotificationsTable
import ch.teamorg.db.tables.SubGroupMembersTable
import ch.teamorg.db.tables.TeamRolesTable
import ch.teamorg.db.tables.UsersTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/** Result of a self-deletion attempt. */
sealed interface DeleteAccountOutcome {
    data object Deleted : DeleteAccountOutcome
    /** The caller owns these still-live clubs and must hand them over first. */
    data class OwnsClubs(val clubNames: List<String>) : DeleteAccountOutcome
}

/**
 * Self-deletion is an anonymization, not a row delete: several foreign keys reference users(id)
 * with ON DELETE RESTRICT (event authorship, attendance_records.set_by, invites,
 * audit_log.actor_id), so removing the row would fail for any user who ever created an event.
 * Personal rows go; the users row is scrubbed and marked deleted.
 */
interface UserDeletionRepository {
    fun deleteAccount(userId: UUID): DeleteAccountOutcome
    /** Avatar path of a user, read before deletion so the file can be removed afterwards. */
    fun avatarPath(userId: UUID): String?
}

class UserDeletionRepositoryImpl(
    private val auditLogRepository: AuditLogRepository
) : UserDeletionRepository {

    override fun avatarPath(userId: UUID): String? = transaction {
        UsersTable.select(UsersTable.avatarUrl)
            .where { UsersTable.id eq userId }
            .singleOrNull()
            ?.get(UsersTable.avatarUrl)
    }

    override fun deleteAccount(userId: UUID): DeleteAccountOutcome = transaction {
        // Checked inside the transaction so a concurrent ownership transfer cannot slip
        // between the check and the scrub.
        val ownedClubs = ClubsTable
            .select(ClubsTable.name)
            .where { (ClubsTable.ownerUserId eq userId) and (ClubsTable.status neq "deleted") }
            .map { it[ClubsTable.name] }
        if (ownedClubs.isNotEmpty()) {
            return@transaction DeleteAccountOutcome.OwnsClubs(ownedClubs)
        }

        // Personal data. Nothing cascades here — the row survives — so every table is explicit.
        AbwesenheitRulesTable.deleteWhere { AbwesenheitRulesTable.userId eq userId }
        AttendanceResponsesTable.deleteWhere { AttendanceResponsesTable.userId eq userId }
        NotificationsTable.deleteWhere { NotificationsTable.userId eq userId }
        NotificationSettingsTable.deleteWhere { NotificationSettingsTable.userId eq userId }
        NotificationRemindersTable.deleteWhere { NotificationRemindersTable.userId eq userId }
        EventReminderOverridesTable.deleteWhere { EventReminderOverridesTable.userId eq userId }
        SubGroupMembersTable.deleteWhere { SubGroupMembersTable.userId eq userId }
        TeamRolesTable.deleteWhere { TeamRolesTable.userId eq userId }
        ClubRolesTable.deleteWhere { ClubRolesTable.userId eq userId }

        // The imported roster row survives as unclaimed, exactly like a never-registered
        // import — the duplicate-merge flow can re-link it later.
        NdsMembersTable.update({ NdsMembersTable.userId eq userId }) {
            it[NdsMembersTable.userId] = null
        }

        // The invite rows must survive (RESTRICT FKs, and an unredeemed invite the user sent
        // stays usable by its recipient) — only the address they carry is personal data.
        val realEmail = UsersTable.select(UsersTable.email)
            .where { UsersTable.id eq userId }
            .singleOrNull()
            ?.get(UsersTable.email)
        if (realEmail != null) {
            InviteLinksTable.update({ InviteLinksTable.invitedEmail.lowerCase() eq realEmail.lowercase() }) {
                it[InviteLinksTable.invitedEmail] = null
            }
        }

        // A previously minted impersonation token stays valid as long as its session row is
        // live, so any session involving the user must end here. The rows themselves stay:
        // both FKs are RESTRICT and the audit trail must survive.
        ImpersonationSessionsTable.update({
            (ImpersonationSessionsTable.actorId eq userId) or (ImpersonationSessionsTable.targetId eq userId)
        }) {
            it[ImpersonationSessionsTable.isActive] = false
            it[ImpersonationSessionsTable.endedAt] = Instant.now()
        }

        val scrubbedEmail = "deleted-$userId@deleted.invalid"
        UsersTable.update({ UsersTable.id eq userId }) {
            it[email] = scrubbedEmail
            it[displayName] = "Gelöschtes Konto"
            it[avatarUrl] = null
            it[passwordHash] = "!"
            it[deletedAt] = Instant.now()
        }

        // The scrubbed address is logged on purpose: the audit trail must not retain the
        // real email of an anonymized account.
        auditLogRepository.log(
            actorId = userId,
            actorEmail = scrubbedEmail,
            action = "user.self_delete",
            targetType = "user",
            targetId = userId.toString()
        )

        DeleteAccountOutcome.Deleted
    }
}
