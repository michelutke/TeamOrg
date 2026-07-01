package ch.teamorg.infra

import ch.teamorg.domain.repositories.EventRepository
import ch.teamorg.domain.repositories.NotificationRepository
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

private val logger = LoggerFactory.getLogger("ReminderSchedulerJob")

fun Application.startReminderSchedulerJob() {
    val notificationRepo by inject<NotificationRepository>()
    val pushService by inject<PushService>()
    val eventRepository by inject<EventRepository>()

    launch(Dispatchers.IO) {
        while (isActive) {
            delay(1.minutes)
            try {
                fireDueReminders(notificationRepo, pushService)
                fireCoachSummaries(notificationRepo, pushService, eventRepository)
                fireAwaitingCheckInReminders(notificationRepo, pushService)
                notificationRepo.deleteOldNotifications(90)
            } catch (e: Exception) {
                logger.error("Reminder scheduler error", e)
            }
        }
    }
}

private suspend fun fireDueReminders(
    notificationRepo: NotificationRepository,
    pushService: PushService
) {
    val dueReminders = notificationRepo.getDueReminders()
    for (reminder in dueReminders) {
        val key = "reminder:${reminder.userId}:${reminder.eventId}"
        val inserted = notificationRepo.createNotification(
            userId = reminder.userId,
            type = "reminder",
            title = "Event Reminder",
            body = "You have an event coming up soon",
            entityId = reminder.eventId,
            entityType = "event",
            idempotencyKey = key
        )
        if (inserted) {
            pushService.sendToUsers(
                listOf(reminder.userId.toString()),
                "Event Reminder",
                "You have an event coming up soon",
                mapOf("entity_id" to reminder.eventId.toString(), "entity_type" to "event")
            )
        }
        notificationRepo.markReminderSent(reminder.id)
    }
    if (dueReminders.isNotEmpty()) {
        logger.info("ReminderSchedulerJob: fired ${dueReminders.size} due reminders")
    }
}

internal suspend fun fireAwaitingCheckInReminders(
    notificationRepo: NotificationRepository,
    pushService: PushService
) {
    val usersWithAwaiting = notificationRepo.getUsersWithAwaitingCheckIn()
    if (usersWithAwaiting.isEmpty()) return

    // 2-day dedup bucket: epoch / (2 * 24 * 3600)
    val twoDayBucket = System.currentTimeMillis() / 1000 / (2 * 24 * 3600)
    val notified = mutableListOf<String>()

    for (userId in usersWithAwaiting) {
        val key = "awaiting_checkin:$userId:$twoDayBucket"
        val inserted = notificationRepo.createNotification(
            userId = userId,
            type = "awaiting_checkin",
            title = "Check-in pending",
            body = "One or more past events are awaiting check-in",
            entityId = null,
            entityType = null,
            idempotencyKey = key
        )
        if (inserted) {
            pushService.sendToUsers(
                listOf(userId.toString()),
                "Check-in pending",
                "One or more past events are awaiting check-in",
                mapOf("type" to "awaiting_checkin")
            )
            notified.add(userId.toString())
        }
    }

    if (notified.isNotEmpty()) {
        logger.info("ReminderSchedulerJob: sent awaiting-check-in reminders to ${notified.size} users")
    }
}

internal suspend fun fireCoachSummaries(
    notificationRepo: NotificationRepository,
    pushService: PushService,
    eventRepository: EventRepository
) {
    val upcomingEvents = notificationRepo.getUpcomingEventsForCoachSummary(withinMinutes = 120)
    for (eventInfo in upcomingEvents) {
        val summary = notificationRepo.getEventAttendanceSummary(eventInfo.eventId)
        val summaryText = "${summary.accepted} accepted, ${summary.declined} declined, ${summary.unsure} unsure, ${summary.noResponse} no response for ${eventInfo.eventTitle}"
        for (teamId in eventInfo.teamIds) {
            val coachIds = notificationRepo.getCoachIdsForTeam(teamId)
            for (coachId in coachIds) {
                val settings = notificationRepo.getSettings(coachId, teamId)
                val mode = settings?.coachResponseMode ?: "per_response"
                if (mode == "summary") {
                    val key = "coach_summary:${coachId}:${eventInfo.eventId}"
                    val inserted = notificationRepo.createNotification(
                        userId = coachId,
                        type = "coach_summary",
                        title = "Pre-event Summary: ${eventInfo.eventTitle}",
                        body = summaryText,
                        entityId = eventInfo.eventId,
                        entityType = "event",
                        idempotencyKey = key
                    )
                    if (inserted) {
                        pushService.sendToUsers(
                            listOf(coachId.toString()),
                            "Pre-event Summary: ${eventInfo.eventTitle}",
                            summaryText,
                            mapOf("entity_id" to eventInfo.eventId.toString(), "entity_type" to "event")
                        )
                    }
                }
            }
        }
    }
    if (upcomingEvents.isNotEmpty()) {
        logger.info("ReminderSchedulerJob: processed ${upcomingEvents.size} events for coach summaries")
    }
}
