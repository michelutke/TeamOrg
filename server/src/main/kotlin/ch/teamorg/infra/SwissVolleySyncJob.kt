package ch.teamorg.infra

import ch.teamorg.domain.repositories.IntegrationRepository
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.hours

private val logger = LoggerFactory.getLogger("SwissVolleySyncJob")

// Per-club cadence (design §6): 12h if a synced game falls within ±14 days, else 48h.
private val WINDOW = Duration.ofDays(14)
private val HOT_INTERVAL = Duration.ofHours(12)
private val COLD_INTERVAL = Duration.ofHours(48)

fun Application.startSwissVolleySyncJob() {
    val syncService by inject<SwissVolleySyncService>()
    val integrationRepository by inject<IntegrationRepository>()
    val eventRepository by inject<ch.teamorg.domain.repositories.EventRepository>()

    launch(Dispatchers.IO) {
        runPass(syncService, integrationRepository, eventRepository)
        while (isActive) {
            delay(1.hours)
            runPass(syncService, integrationRepository, eventRepository)
        }
    }
}

private suspend fun runPass(
    syncService: SwissVolleySyncService,
    integrationRepository: IntegrationRepository,
    eventRepository: ch.teamorg.domain.repositories.EventRepository
) {
    val clubIds = try {
        integrationRepository.listClubIdsWithIntegration()
    } catch (e: Exception) {
        logger.error("SwissVolleySyncJob: failed to list clubs with integration", e)
        return
    }

    for (clubId in clubIds) {
        try {
            if (isDue(clubId, integrationRepository, eventRepository)) {
                syncService.syncClub(clubId)
            }
        } catch (e: Exception) {
            logger.error("SwissVolleySyncJob: sync failed for club $clubId", e)
        }
    }
}

private suspend fun isDue(
    clubId: UUID,
    integrationRepository: IntegrationRepository,
    eventRepository: ch.teamorg.domain.repositories.EventRepository
): Boolean {
    val now = Instant.now()
    val lastSyncedAt = integrationRepository.getState(clubId)?.lastSyncedAt
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: return true // never synced -> due now

    val hot = eventRepository.hasSyncedGameWithin(clubId, now.minus(WINDOW), now.plus(WINDOW))
    val interval = if (hot) HOT_INTERVAL else COLD_INTERVAL
    return Duration.between(lastSyncedAt, now) >= interval
}
