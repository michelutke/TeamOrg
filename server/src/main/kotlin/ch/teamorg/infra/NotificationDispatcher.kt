package ch.teamorg.infra

import ch.teamorg.domain.repositories.NotificationRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private val logger = LoggerFactory.getLogger("NotificationDispatcher")

class NotificationDispatcher(
    private val notificationRepo: NotificationRepository,
    private val pushService: PushService
) {
    suspend fun notifyTeamMembers(
        teamId: UUID,
        excludeUserId: UUID?,
        type: String,
        title: String,
        body: String,
        entityId: UUID?,
        entityType: String?,
        idempotencyKeySuffix: String
    ) {
        val memberIds = notificationRepo.getTeamMemberIds(teamId)
            .filter { it != excludeUserId }
        val eligibleIds = memberIds.filter { notificationRepo.isUserEligible(it, teamId, type) }
        if (eligibleIds.isEmpty()) return

        // Hour-bucket: callers pass static suffixes ("edit"/"cancel"/…), so the bucket lets a
        // repeated action (e.g. a later edit) re-notify while collapsing duplicates within the hour.
        // SV paths (notifyTeamCoaches/notifyUsers) deliberately omit it — their suffix (content hash)
        // is what governs de-dup (design §13.3).
        val epoch = Instant.now().epochSecond / 3600
        val key = "${type}:${entityId ?: "none"}:$epoch:$idempotencyKeySuffix"

        val insertedIds = notificationRepo.createBatch(eligibleIds, type, title, body, entityId, entityType, key)
        if (insertedIds.isNotEmpty()) {
            pushService.sendToUsers(
                insertedIds.map { it.toString() },
                title,
                body,
                buildMap {
                    entityId?.let { put("entity_id", it.toString()) }
                    entityType?.let { put("entity_type", it) }
                }
            )
        }
        logger.info("NotificationDispatcher: sent $type to ${insertedIds.size}/${eligibleIds.size} eligible members of team $teamId")
    }

    suspend fun notifyTeamCoaches(
        teamId: UUID,
        excludeUserId: UUID?,
        type: String,
        title: String,
        body: String,
        entityId: UUID?,
        entityType: String?,
        idempotencyKeySuffix: String
    ) {
        val coachIds = notificationRepo.getCoachIdsForTeam(teamId)
            .filter { it != excludeUserId }
        val eligibleIds = coachIds.filter { notificationRepo.isUserEligible(it, teamId, type) }
        if (eligibleIds.isEmpty()) return

        val key = "${type}:${entityId ?: "none"}:$idempotencyKeySuffix"

        val insertedIds = notificationRepo.createBatch(eligibleIds, type, title, body, entityId, entityType, key)
        if (insertedIds.isNotEmpty()) {
            pushService.sendToUsers(
                insertedIds.map { it.toString() },
                title,
                body,
                buildMap {
                    entityId?.let { put("entity_id", it.toString()) }
                    entityType?.let { put("entity_type", it) }
                }
            )
        }
        logger.info("NotificationDispatcher: sent $type to ${insertedIds.size}/${eligibleIds.size} eligible coaches of team $teamId")
    }

    suspend fun notifyUsers(
        userIds: List<UUID>,
        type: String,
        title: String,
        body: String,
        entityId: UUID?,
        entityType: String?,
        idempotencyKeySuffix: String
    ) {
        if (userIds.isEmpty()) return

        val key = "${type}:${entityId ?: "none"}:$idempotencyKeySuffix"

        val insertedIds = notificationRepo.createBatch(userIds, type, title, body, entityId, entityType, key)
        if (insertedIds.isNotEmpty()) {
            pushService.sendToUsers(
                insertedIds.map { it.toString() },
                title,
                body,
                buildMap {
                    entityId?.let { put("entity_id", it.toString()) }
                    entityType?.let { put("entity_type", it) }
                }
            )
        }
        logger.info("NotificationDispatcher: sent $type to ${insertedIds.size}/${userIds.size} explicit users")
    }
}
