package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.*
import ch.teamorg.domain.models.UpdateNotificationSettingsRequest
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class DueReminderRow(
    val id: UUID,
    val userId: UUID,
    val eventId: UUID,
    val fireAt: Instant
)

data class AttendanceSummary(
    val accepted: Int,
    val declined: Int,
    val unsure: Int,
    val noResponse: Int,
    val total: Int
)

data class EventReminderInfo(
    val eventId: UUID,
    val eventTitle: String,
    val teamIds: List<UUID>
)

data class NotificationRow(
    val id: UUID,
    val userId: UUID,
    val type: String,
    val title: String,
    val body: String,
    val entityId: UUID?,
    val entityType: String?,
    val isRead: Boolean,
    val createdAt: Instant
)

data class NotificationSettingsRow(
    val userId: UUID,
    val teamId: UUID,
    val eventsNew: Boolean,
    val eventsEdit: Boolean,
    val eventsCancel: Boolean,
    val remindersEnabled: Boolean,
    val reminderLeadMinutes: Int,
    val coachResponseMode: String,
    val absencesEnabled: Boolean,
    val svGames: Boolean
)

interface NotificationRepository {
    suspend fun createNotification(
        userId: UUID,
        type: String,
        title: String,
        body: String,
        entityId: UUID?,
        entityType: String?,
        idempotencyKey: String
    ): Boolean

    suspend fun createBatch(
        userIds: List<UUID>,
        type: String,
        title: String,
        body: String,
        entityId: UUID?,
        entityType: String?,
        idempotencyKey: String
    ): List<UUID>

    suspend fun getNotifications(userId: UUID, limit: Int = 50, offset: Int = 0): List<NotificationRow>
    suspend fun markRead(userId: UUID, notificationId: UUID): Boolean
    suspend fun markAllRead(userId: UUID): Int
    suspend fun deleteAll(userId: UUID)
    suspend fun getUnreadCount(userId: UUID): Long
    suspend fun getSettings(userId: UUID, teamId: UUID): NotificationSettingsRow?
    suspend fun upsertSettings(userId: UUID, teamId: UUID, settings: UpdateNotificationSettingsRequest)
    suspend fun isUserEligible(userId: UUID, teamId: UUID, type: String): Boolean
    suspend fun isDuplicate(userId: UUID, idempotencyKey: String): Boolean
    suspend fun getTeamMemberIds(teamId: UUID): List<UUID>
    suspend fun getReminderOverride(userId: UUID, eventId: UUID): Int?
    suspend fun upsertReminderOverride(userId: UUID, eventId: UUID, leadMinutes: Int?)
    suspend fun deleteOldNotifications(olderThanDays: Int = 90)

    // Reminder row management
    suspend fun insertReminderRows(eventId: UUID, userIdToFireAt: Map<UUID, Instant>)
    suspend fun deleteReminderRowsForEvent(eventId: UUID)
    suspend fun getDueReminders(): List<DueReminderRow>
    suspend fun markReminderSent(reminderId: UUID)
    suspend fun getCoachIdsForTeam(teamId: UUID): List<UUID>
    suspend fun getManagerIdsForClub(clubId: UUID): List<UUID>
    suspend fun getEventAttendanceSummary(eventId: UUID): AttendanceSummary
    suspend fun getUpcomingEventsForCoachSummary(withinMinutes: Int = 120): List<EventReminderInfo>

    /**
     * Returns distinct user IDs of coaches (direct team role) and club_managers (via club)
     * who have at least one awaiting-check-in event (end_at < now, check_in_completed_at IS NULL,
     * not cancelled). Used by the scheduler to fan-out push reminders.
     */
    suspend fun getUsersWithAwaitingCheckIn(): List<UUID>
}

class NotificationRepositoryImpl : NotificationRepository {

    override suspend fun createNotification(
        userId: UUID,
        type: String,
        title: String,
        body: String,
        entityId: UUID?,
        entityType: String?,
        idempotencyKey: String
    ): Boolean = transaction {
        val inserted = NotificationsTable.insertIgnore {
            it[NotificationsTable.userId] = userId
            it[NotificationsTable.type] = type
            it[NotificationsTable.title] = title
            it[NotificationsTable.body] = body
            it[NotificationsTable.entityId] = entityId
            it[NotificationsTable.entityType] = entityType
            it[NotificationsTable.idempotencyKey] = idempotencyKey
        }
        inserted.insertedCount > 0
    }

    override suspend fun createBatch(
        userIds: List<UUID>,
        type: String,
        title: String,
        body: String,
        entityId: UUID?,
        entityType: String?,
        idempotencyKey: String
    ): List<UUID> = transaction {
        userIds.filter { userId ->
            val inserted = NotificationsTable.insertIgnore {
                it[NotificationsTable.userId] = userId
                it[NotificationsTable.type] = type
                it[NotificationsTable.title] = title
                it[NotificationsTable.body] = body
                it[NotificationsTable.entityId] = entityId
                it[NotificationsTable.entityType] = entityType
                it[NotificationsTable.idempotencyKey] = idempotencyKey
            }
            inserted.insertedCount > 0
        }
    }

    override suspend fun getNotifications(userId: UUID, limit: Int, offset: Int): List<NotificationRow> = transaction {
        NotificationsTable.selectAll()
            .where { NotificationsTable.userId eq userId }
            .orderBy(NotificationsTable.createdAt, SortOrder.DESC)
            .limit(limit, offset.toLong())
            .map(::rowToNotification)
    }

    override suspend fun markRead(userId: UUID, notificationId: UUID): Boolean = transaction {
        val count = NotificationsTable.update({
            (NotificationsTable.id eq notificationId) and (NotificationsTable.userId eq userId)
        }) {
            it[isRead] = true
        }
        count > 0
    }

    override suspend fun markAllRead(userId: UUID): Int = transaction {
        NotificationsTable.update({ NotificationsTable.userId eq userId }) {
            it[isRead] = true
        }
    }

    override suspend fun deleteAll(userId: UUID): Unit = transaction {
        NotificationsTable.deleteWhere { NotificationsTable.userId eq userId }
        Unit
    }

    override suspend fun getUnreadCount(userId: UUID): Long = transaction {
        NotificationsTable.selectAll()
            .where { (NotificationsTable.userId eq userId) and (NotificationsTable.isRead eq false) }
            .count()
    }

    override suspend fun getSettings(userId: UUID, teamId: UUID): NotificationSettingsRow? = transaction {
        NotificationSettingsTable.selectAll()
            .where { (NotificationSettingsTable.userId eq userId) and (NotificationSettingsTable.teamId eq teamId) }
            .map(::rowToSettings)
            .singleOrNull()
    }

    override suspend fun upsertSettings(userId: UUID, teamId: UUID, settings: UpdateNotificationSettingsRequest): Unit = transaction {
        val existing = NotificationSettingsTable.selectAll()
            .where { (NotificationSettingsTable.userId eq userId) and (NotificationSettingsTable.teamId eq teamId) }
            .singleOrNull()

        if (existing == null) {
            NotificationSettingsTable.insert {
                it[NotificationSettingsTable.userId] = userId
                it[NotificationSettingsTable.teamId] = teamId
                settings.eventsNew?.let { v -> it[eventsNew] = v }
                settings.eventsEdit?.let { v -> it[eventsEdit] = v }
                settings.eventsCancel?.let { v -> it[eventsCancel] = v }
                settings.remindersEnabled?.let { v -> it[remindersEnabled] = v }
                settings.reminderLeadMinutes?.let { v -> it[reminderLeadMinutes] = v }
                settings.coachResponseMode?.let { v -> it[coachResponseMode] = v }
                settings.absencesEnabled?.let { v -> it[absencesEnabled] = v }
            }
        } else {
            NotificationSettingsTable.update({
                (NotificationSettingsTable.userId eq userId) and (NotificationSettingsTable.teamId eq teamId)
            }) {
                settings.eventsNew?.let { v -> it[eventsNew] = v }
                settings.eventsEdit?.let { v -> it[eventsEdit] = v }
                settings.eventsCancel?.let { v -> it[eventsCancel] = v }
                settings.remindersEnabled?.let { v -> it[remindersEnabled] = v }
                settings.reminderLeadMinutes?.let { v -> it[reminderLeadMinutes] = v }
                settings.coachResponseMode?.let { v -> it[coachResponseMode] = v }
                settings.absencesEnabled?.let { v -> it[absencesEnabled] = v }
            }
        }
    }

    override suspend fun isUserEligible(userId: UUID, teamId: UUID, type: String): Boolean = transaction {
        val row = NotificationSettingsTable.selectAll()
            .where { (NotificationSettingsTable.userId eq userId) and (NotificationSettingsTable.teamId eq teamId) }
            .singleOrNull() ?: return@transaction true

        when (type) {
            "event_new" -> row[NotificationSettingsTable.eventsNew]
            "event_edit" -> row[NotificationSettingsTable.eventsEdit]
            "event_cancel" -> row[NotificationSettingsTable.eventsCancel]
            "reminder" -> row[NotificationSettingsTable.remindersEnabled]
            "absence" -> row[NotificationSettingsTable.absencesEnabled]
            "sv_game_new", "sv_game_changed" -> row[NotificationSettingsTable.svGames]
            else -> true
        }
    }

    override suspend fun isDuplicate(userId: UUID, idempotencyKey: String): Boolean = transaction {
        NotificationsTable.selectAll()
            .where { (NotificationsTable.userId eq userId) and (NotificationsTable.idempotencyKey eq idempotencyKey) }
            .count() > 0
    }

    override suspend fun getTeamMemberIds(teamId: UUID): List<UUID> = transaction {
        TeamRolesTable.select(TeamRolesTable.userId)
            .where { (TeamRolesTable.teamId eq teamId) and (TeamRolesTable.userId.isNotNull()) }
            .mapNotNull { it[TeamRolesTable.userId] }
    }

    override suspend fun getReminderOverride(userId: UUID, eventId: UUID): Int? = transaction {
        EventReminderOverridesTable.selectAll()
            .where { (EventReminderOverridesTable.userId eq userId) and (EventReminderOverridesTable.eventId eq eventId) }
            .singleOrNull()
            ?.get(EventReminderOverridesTable.reminderLeadMinutes)
    }

    override suspend fun upsertReminderOverride(userId: UUID, eventId: UUID, leadMinutes: Int?): Unit = transaction {
        val existing = EventReminderOverridesTable.selectAll()
            .where { (EventReminderOverridesTable.userId eq userId) and (EventReminderOverridesTable.eventId eq eventId) }
            .singleOrNull()

        if (existing == null) {
            EventReminderOverridesTable.insert {
                it[EventReminderOverridesTable.userId] = userId
                it[EventReminderOverridesTable.eventId] = eventId
                it[reminderLeadMinutes] = leadMinutes
            }
        } else {
            EventReminderOverridesTable.update({
                (EventReminderOverridesTable.userId eq userId) and (EventReminderOverridesTable.eventId eq eventId)
            }) {
                it[reminderLeadMinutes] = leadMinutes
            }
        }
    }

    override suspend fun deleteOldNotifications(olderThanDays: Int): Unit = transaction {
        val cutoff = Instant.now().minusSeconds(olderThanDays.toLong() * 24 * 3600)
        NotificationsTable.deleteWhere { NotificationsTable.createdAt less cutoff }
    }

    override suspend fun insertReminderRows(eventId: UUID, userIdToFireAt: Map<UUID, Instant>): Unit = transaction {
        for ((userId, fireAt) in userIdToFireAt) {
            NotificationRemindersTable.insertIgnore {
                it[NotificationRemindersTable.userId] = userId
                it[NotificationRemindersTable.eventId] = eventId
                it[NotificationRemindersTable.fireAt] = fireAt
            }
        }
    }

    override suspend fun deleteReminderRowsForEvent(eventId: UUID): Unit = transaction {
        NotificationRemindersTable.deleteWhere { NotificationRemindersTable.eventId eq eventId }
    }

    override suspend fun getDueReminders(): List<DueReminderRow> = transaction {
        val now = Instant.now()
        (NotificationRemindersTable innerJoin EventsTable)
            .select(
                NotificationRemindersTable.id,
                NotificationRemindersTable.userId,
                NotificationRemindersTable.eventId,
                NotificationRemindersTable.fireAt
            )
            .where {
                (NotificationRemindersTable.fireAt lessEq now) and
                (NotificationRemindersTable.sent eq false) and
                (EventsTable.status eq EventStatus.active) and
                (EventsTable.startAt greaterEq now)
            }
            .map {
                DueReminderRow(
                    id = it[NotificationRemindersTable.id],
                    userId = it[NotificationRemindersTable.userId],
                    eventId = it[NotificationRemindersTable.eventId],
                    fireAt = it[NotificationRemindersTable.fireAt]
                )
            }
    }

    override suspend fun markReminderSent(reminderId: UUID): Unit = transaction {
        NotificationRemindersTable.update({ NotificationRemindersTable.id eq reminderId }) {
            it[sent] = true
        }
    }

    override suspend fun getCoachIdsForTeam(teamId: UUID): List<UUID> = transaction {
        TeamRolesTable.select(TeamRolesTable.userId)
            .where {
                (TeamRolesTable.teamId eq teamId) and
                (TeamRolesTable.role eq "coach") and
                (TeamRolesTable.userId.isNotNull())
            }
            .mapNotNull { it[TeamRolesTable.userId] }
    }

    override suspend fun getManagerIdsForClub(clubId: UUID): List<UUID> = transaction {
        ClubRolesTable.select(ClubRolesTable.userId)
            .where {
                (ClubRolesTable.clubId eq clubId) and
                (ClubRolesTable.role eq "club_manager")
            }
            .map { it[ClubRolesTable.userId] }
    }

    override suspend fun getEventAttendanceSummary(eventId: UUID): AttendanceSummary = transaction {
        val rows = AttendanceResponsesTable.selectAll()
            .where { AttendanceResponsesTable.eventId eq eventId }
            .toList()
        val accepted = rows.count { it[AttendanceResponsesTable.status] == "accepted" }
        val declined = rows.count { it[AttendanceResponsesTable.status].startsWith("declined") }
        val unsure = rows.count { it[AttendanceResponsesTable.status] == "unsure" }
        AttendanceSummary(
            accepted = accepted,
            declined = declined,
            unsure = unsure,
            noResponse = 0,
            total = rows.size
        )
    }

    override suspend fun getUpcomingEventsForCoachSummary(withinMinutes: Int): List<EventReminderInfo> = transaction {
        val now = Instant.now()
        val windowEnd = now.plusSeconds(withinMinutes.toLong() * 60)
        EventsTable.selectAll()
            .where {
                (EventsTable.startAt greaterEq now) and
                (EventsTable.startAt lessEq windowEnd) and
                (EventsTable.status eq EventStatus.active)
            }
            .map { row ->
                val eventId = row[EventsTable.id]
                val teamIds = EventTeamsTable.select(EventTeamsTable.teamId)
                    .where { EventTeamsTable.eventId eq eventId }
                    .map { it[EventTeamsTable.teamId] }
                EventReminderInfo(
                    eventId = eventId,
                    eventTitle = row[EventsTable.title],
                    teamIds = teamIds
                )
            }
    }

    override suspend fun getUsersWithAwaitingCheckIn(): List<UUID> = transaction {
        val now = Instant.now()

        // Awaiting-check-in event ids: ended, not finalized, not cancelled
        val awaitingEventIds = EventsTable
            .select(EventsTable.id)
            .where {
                (EventsTable.endAt less now) and
                EventsTable.checkInCompletedAt.isNull() and
                (EventsTable.status neq EventStatus.cancelled)
            }
            .map { it[EventsTable.id] }

        if (awaitingEventIds.isEmpty()) return@transaction emptyList()

        // Teams linked to those events
        val awaitingTeamIds = EventTeamsTable.select(EventTeamsTable.teamId)
            .where { EventTeamsTable.eventId inList awaitingEventIds }
            .map { it[EventTeamsTable.teamId] }
            .distinct()

        if (awaitingTeamIds.isEmpty()) return@transaction emptyList()

        // Direct coaches on those teams
        val coachIds = TeamRolesTable.select(TeamRolesTable.userId)
            .where {
                (TeamRolesTable.teamId inList awaitingTeamIds) and
                (TeamRolesTable.role eq "coach") and
                TeamRolesTable.userId.isNotNull()
            }
            .mapNotNull { it[TeamRolesTable.userId] }

        // Club managers whose managed clubs contain those teams
        val clubIds = TeamsTable.select(TeamsTable.clubId)
            .where { TeamsTable.id inList awaitingTeamIds }
            .map { it[TeamsTable.clubId] }
            .distinct()
        val managerIds = if (clubIds.isNotEmpty()) {
            ClubRolesTable.select(ClubRolesTable.userId)
                .where {
                    (ClubRolesTable.clubId inList clubIds) and
                    (ClubRolesTable.role eq "club_manager")
                }
                .map { it[ClubRolesTable.userId] }
        } else emptyList()

        (coachIds + managerIds).distinct()
    }

    private fun rowToNotification(row: ResultRow) = NotificationRow(
        id = row[NotificationsTable.id],
        userId = row[NotificationsTable.userId],
        type = row[NotificationsTable.type],
        title = row[NotificationsTable.title],
        body = row[NotificationsTable.body],
        entityId = row[NotificationsTable.entityId],
        entityType = row[NotificationsTable.entityType],
        isRead = row[NotificationsTable.isRead],
        createdAt = row[NotificationsTable.createdAt]
    )

    private fun rowToSettings(row: ResultRow) = NotificationSettingsRow(
        userId = row[NotificationSettingsTable.userId],
        teamId = row[NotificationSettingsTable.teamId],
        eventsNew = row[NotificationSettingsTable.eventsNew],
        eventsEdit = row[NotificationSettingsTable.eventsEdit],
        eventsCancel = row[NotificationSettingsTable.eventsCancel],
        remindersEnabled = row[NotificationSettingsTable.remindersEnabled],
        reminderLeadMinutes = row[NotificationSettingsTable.reminderLeadMinutes],
        coachResponseMode = row[NotificationSettingsTable.coachResponseMode],
        absencesEnabled = row[NotificationSettingsTable.absencesEnabled],
        svGames = row[NotificationSettingsTable.svGames]
    )
}
