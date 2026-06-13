package ch.teamorg.data

import ch.teamorg.db.TeamorgDb
import ch.teamorg.domain.Notification
import kotlinx.datetime.Instant

class NotificationCacheManager(private val db: TeamorgDb) {
    private val queries get() = db.notificationQueries

    // Local cache must degrade gracefully — a missing/stale schema or a malformed
    // timestamp must never propagate out and crash the app (e.g. uncaught on iOS).
    fun saveNotifications(notifications: List<Notification>) = runCatching {
        notifications.forEach { n ->
            queries.upsertNotification(
                id = n.id,
                type = n.type,
                title = n.title,
                body = n.body,
                entity_id = n.entityId,
                entity_type = n.entityType,
                is_read = if (n.isRead) 1L else 0L,
                created_at = parseIsoToEpochMillis(n.createdAt)
            )
        }
    }.let { }

    fun getCachedNotifications(limit: Long, offset: Long): List<Notification> = runCatching {
        queries.getNotifications(limit, offset).executeAsList().map { row ->
            Notification(
                id = row.id,
                type = row.type,
                title = row.title,
                body = row.body,
                entityId = row.entity_id,
                entityType = row.entity_type,
                isRead = row.is_read == 1L,
                createdAt = epochMillisToIso(row.created_at)
            )
        }
    }.getOrDefault(emptyList())

    fun getUnreadCount(): Long = runCatching { queries.getUnreadCount().executeAsOne() }.getOrDefault(0L)

    fun markRead(id: String) { runCatching { queries.markRead(id) } }

    fun markAllRead() { runCatching { queries.markAllRead() } }

    fun clearAll() { runCatching { queries.deleteAll() } }

    fun cleanup(olderThanMillis: Long) { runCatching { queries.deleteOlderThan(olderThanMillis) } }

    private fun parseIsoToEpochMillis(iso: String): Long =
        Instant.parse(iso).toEpochMilliseconds()

    private fun epochMillisToIso(millis: Long): String =
        Instant.fromEpochMilliseconds(millis).toString()
}
