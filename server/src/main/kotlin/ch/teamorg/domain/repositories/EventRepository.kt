package ch.teamorg.domain.repositories

import ch.teamorg.domain.models.*
import java.time.Instant
import java.util.UUID

interface EventRepository {
    suspend fun create(request: CreateEventRequest, createdBy: UUID): Event
    suspend fun findById(id: UUID): Event?
    suspend fun findByIdWithTeams(id: UUID): EventWithTeams?
    suspend fun findEventsForUser(
        userId: UUID,
        from: Instant? = null,
        to: Instant? = null,
        type: String? = null,
        teamId: UUID? = null
    ): List<EventWithTeams>
    suspend fun findEventsForTeam(teamId: UUID, from: Instant?, to: Instant?): List<Event>
    suspend fun update(id: UUID, request: EditEventRequest): Event?
    suspend fun cancel(id: UUID): Event?
    suspend fun duplicate(id: UUID, createdBy: UUID): Event?
    suspend fun createSeries(request: CreateEventRequest, createdBy: UUID): EventSeries
    suspend fun findSeriesById(id: UUID): EventSeries?
    suspend fun updateSeriesTemplate(seriesId: UUID, request: EditEventRequest)
    suspend fun materialiseUpcomingOccurrences(): Int
    suspend fun cancelFutureInSeries(seriesId: UUID, fromSequence: Int): Int
    suspend fun uncancel(id: UUID): Event?
    suspend fun uncancelFutureInSeries(seriesId: UUID, fromSequence: Int): Int
    suspend fun updateFutureInSeries(seriesId: UUID, fromSequence: Int, request: EditEventRequest): Int

    suspend fun findByExternalGameId(source: String, gameId: Long): SyncedEventRef?
    suspend fun createSyncedMatch(
        title: String,
        startAt: Instant,
        endAt: Instant,
        location: String?,
        externalSource: String,
        externalGameId: Long,
        externalHash: String,
        createdBy: UUID,
        teamIds: List<UUID>
    ): Event
    suspend fun updateSyncedFacts(
        eventId: UUID,
        title: String,
        startAt: Instant,
        endAt: Instant,
        location: String?,
        newHash: String
    ): Event?
    suspend fun markPostponed(eventId: UUID): Event?
    suspend fun clearPostponedToSynced(eventId: UUID): Event?
    suspend fun clearNeedsReview(eventId: UUID): Event?
    suspend fun listImportableSeries(teamId: UUID): ImportableSeriesResult
    suspend fun listSyncedExternalGameIds(clubId: UUID): List<Long>
    suspend fun hasSyncedGameWithin(clubId: UUID, windowStart: Instant, windowEnd: Instant): Boolean

    /**
     * Events where the user is coach or club_manager of one of the event's teams,
     * end_at < now, check_in_completed_at IS NULL, status != cancelled.
     * Used for the "awaiting check-in" coach filter.
     */
    suspend fun listAwaitingCheckInForUser(userId: UUID): List<EventWithTeams>
}
