package ch.teamorg.data

import ch.teamorg.db.TeamorgDb
import ch.teamorg.domain.AbwesenheitRule
import ch.teamorg.domain.AttendanceResponse
import ch.teamorg.domain.Event
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.MatchedTeam
import ch.teamorg.domain.Notification
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private fun newDb(): TeamorgDb = TeamorgDb(DatabaseDriverFactory().createDriver())

class AttendanceCacheManagerTest {

    private lateinit var cache: AttendanceCacheManager
    private val now = Clock.System.now()

    @BeforeTest
    fun setUp() {
        cache = AttendanceCacheManager(newDb())
    }

    private fun response(
        eventId: String,
        userId: String,
        status: String = "confirmed",
        reason: String? = null,
        manualOverride: Boolean = false
    ) = AttendanceResponse(
        eventId = eventId,
        userId = userId,
        status = status,
        reason = reason,
        manualOverride = manualOverride,
        respondedAt = now,
        updatedAt = now
    )

    private fun rule(id: String, userId: String = "u1", weekdays: List<Int>? = null) = AbwesenheitRule(
        id = id,
        userId = userId,
        presetType = "work",
        label = "Rule $id",
        ruleType = if (weekdays != null) "recurring" else "period",
        weekdays = weekdays,
        startDate = "2026-01-01",
        endDate = "2026-02-01",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z"
    )

    @Test
    fun saveResponses_roundTripsAllFields() {
        val saved = response("e1", "u1", status = "declined", reason = "sick", manualOverride = true)

        cache.saveResponses("e1", listOf(saved))

        val loaded = cache.getCachedResponse("e1", "u1")!!
        assertEquals("declined", loaded.status)
        assertEquals("sick", loaded.reason)
        assertEquals(true, loaded.manualOverride)
        assertEquals(now.toEpochMilliseconds(), loaded.updatedAt.toEpochMilliseconds())
        assertEquals(now.toEpochMilliseconds(), loaded.respondedAt?.toEpochMilliseconds())
    }

    @Test
    fun saveResponses_replacesPreviousResponsesOfThatEvent() {
        cache.saveResponses("e1", listOf(response("e1", "u1"), response("e1", "u2")))

        cache.saveResponses("e1", listOf(response("e1", "u3")))

        assertEquals(listOf("u3"), cache.getCachedResponses("e1").map { it.userId })
    }

    @Test
    fun saveResponses_doesNotTouchOtherEvents() {
        cache.saveResponses("e1", listOf(response("e1", "u1")))
        cache.saveResponses("e2", listOf(response("e2", "u1", status = "unsure")))

        cache.saveResponses("e1", emptyList())

        assertEquals(emptyList(), cache.getCachedResponses("e1"))
        assertEquals("unsure", cache.getCachedResponse("e2", "u1")?.status)
    }

    @Test
    fun getCachedResponse_unknown_returnsNull() {
        assertNull(cache.getCachedResponse("e1", "u1"))
    }

    @Test
    fun saveRules_replacesRulesOfThatUserOnly() {
        cache.saveRules("u1", listOf(rule("r1"), rule("r2")))
        cache.saveRules("u2", listOf(rule("r3", userId = "u2")))

        cache.saveRules("u1", listOf(rule("r4")))

        assertEquals(listOf("r4"), cache.getCachedRules("u1").map { it.id })
        assertEquals(listOf("r3"), cache.getCachedRules("u2").map { it.id })
    }

    @Test
    fun saveRules_roundTripsWeekdays() {
        cache.saveRules("u1", listOf(rule("r1", weekdays = listOf(0, 2, 4)), rule("r2", weekdays = null)))

        val byId = cache.getCachedRules("u1").associateBy { it.id }

        assertEquals(listOf(0, 2, 4), byId.getValue("r1").weekdays)
        assertNull(byId.getValue("r2").weekdays)
    }

    @Test
    fun deleteRule_removesOnlyThatRule() {
        cache.saveRules("u1", listOf(rule("r1"), rule("r2")))

        cache.deleteRule("r1")

        assertEquals(listOf("r2"), cache.getCachedRules("u1").map { it.id })
    }
}

class EventCacheManagerTest {

    private lateinit var db: TeamorgDb
    private lateinit var cache: EventCacheManager
    private val now = Clock.System.now()

    @BeforeTest
    fun setUp() {
        db = newDb()
        cache = EventCacheManager(db)
    }

    private fun event(
        id: String,
        startsIn: kotlin.time.Duration,
        type: String = "training",
        teamIds: List<String> = listOf("team-a"),
        seriesId: String? = null,
        matchedTeams: List<MatchedTeam> = emptyList()
    ) = EventWithTeams(
        event = Event(
            id = id,
            title = "Event $id",
            type = type,
            startAt = now + startsIn,
            endAt = now + startsIn + 2.hours,
            status = "active",
            seriesId = seriesId,
            seriesSequence = seriesId?.let { 2 },
            createdBy = "u-coach",
            createdAt = now,
            updatedAt = now,
            teamIds = teamIds,
            subgroupIds = listOf("sg1", "sg2"),
            location = "Hall",
            minAttendees = 6
        ),
        matchedTeams = matchedTeams
    )

    private fun window() = (now - 1.days).toEpochMilliseconds() to (now + 30.days).toEpochMilliseconds()

    @Test
    fun saveEvents_roundTripsFieldsTeamsAndSubgroups() {
        val saved = event("e1", 1.days, seriesId = "s1", teamIds = listOf("team-a", "team-b"),
            matchedTeams = listOf(MatchedTeam("team-a", "U17")))

        cache.saveEvents(listOf(saved))

        val loaded = cache.getEventById("e1")!!
        assertEquals("Event e1", loaded.event.title)
        assertEquals("Hall", loaded.event.location)
        assertEquals(6, loaded.event.minAttendees)
        assertEquals("s1", loaded.event.seriesId)
        assertEquals(2, loaded.event.seriesSequence)
        assertEquals(listOf("team-a", "team-b"), loaded.event.teamIds)
        assertEquals(listOf("sg1", "sg2"), loaded.event.subgroupIds)
        assertEquals(saved.event.startAt.toEpochMilliseconds(), loaded.event.startAt.toEpochMilliseconds())
        assertEquals(listOf(MatchedTeam("team-a", "U17")), loaded.matchedTeams)
    }

    @Test
    fun getEventById_unknown_returnsNull() {
        assertNull(cache.getEventById("nope"))
    }

    @Test
    fun saveEvents_sameIdTwice_overwrites() {
        cache.saveEvents(listOf(event("e1", 1.days)))
        val edited = event("e1", 1.days)

        cache.saveEvents(listOf(edited.copy(event = edited.event.copy(title = "Renamed"))))

        val (from, to) = window()
        assertEquals(listOf("Renamed"), cache.getOfflineEvents(from, to).map { it.event.title })
    }

    @Test
    fun getOfflineEvents_returnsEventsInWindowSortedByStart() {
        cache.saveEvents(listOf(event("late", 5.days), event("early", 1.days), event("outside", 60.days)))

        val (from, to) = window()

        assertEquals(listOf("early", "late"), cache.getOfflineEvents(from, to).map { it.event.id })
    }

    @Test
    fun getFilteredOfflineEvents_filtersByTypeAndTeam() {
        cache.saveEvents(
            listOf(
                event("train-a", 1.days, type = "training", teamIds = listOf("team-a")),
                event("match-a", 2.days, type = "match", teamIds = listOf("team-a")),
                event("match-b", 3.days, type = "match", teamIds = listOf("team-b"))
            )
        )
        val (from, to) = window()

        assertEquals(listOf("match-a", "match-b"), cache.getFilteredOfflineEvents(from, to, "match", null).map { it.event.id })
        assertEquals(listOf("train-a", "match-a"), cache.getFilteredOfflineEvents(from, to, null, "team-a").map { it.event.id })
        assertEquals(listOf("match-b"), cache.getFilteredOfflineEvents(from, to, "match", "team-b").map { it.event.id })
        assertEquals(3, cache.getFilteredOfflineEvents(from, to, null, null).size)
    }

    @Test
    fun cleanup_dropsEventsCachedMoreThanSevenDaysAgoOnly() {
        cache.saveEvents(listOf(event("fresh", 1.days)))
        val stale = event("stale", 1.days).event
        db.eventQueries.upsertEvent(
            id = stale.id, title = stale.title, type = stale.type,
            start_at = stale.startAt.toEpochMilliseconds(), end_at = stale.endAt.toEpochMilliseconds(),
            meetup_at = null, location = null, description = null, min_attendees = null,
            status = "active", cancelled_at = null, series_id = null, series_sequence = null,
            series_override = 0L, created_by = "u", team_ids = "", subgroup_ids = "",
            matched_teams_json = "[]", cached_at = (now - 8.days).toEpochMilliseconds()
        )

        cache.cleanup()

        assertEquals("fresh", cache.getEventById("fresh")?.event?.id)
        assertNull(cache.getEventById("stale"))
    }

    @Test
    fun corruptMatchedTeamsJson_degradesToEmptyList() {
        val e = event("e1", 1.days).event
        db.eventQueries.upsertEvent(
            id = e.id, title = e.title, type = e.type,
            start_at = e.startAt.toEpochMilliseconds(), end_at = e.endAt.toEpochMilliseconds(),
            meetup_at = null, location = null, description = null, min_attendees = null,
            status = "active", cancelled_at = null, series_id = null, series_sequence = null,
            series_override = 0L, created_by = "u", team_ids = "", subgroup_ids = "",
            matched_teams_json = "not json", cached_at = now.toEpochMilliseconds()
        )

        val loaded = cache.getEventById("e1")!!

        assertEquals(emptyList(), loaded.matchedTeams)
        assertEquals(emptyList(), loaded.event.teamIds)
    }
}

class NotificationCacheManagerTest {

    private lateinit var cache: NotificationCacheManager
    private val now = Clock.System.now()

    @BeforeTest
    fun setUp() {
        cache = NotificationCacheManager(newDb())
    }

    private fun notification(id: String, age: kotlin.time.Duration, isRead: Boolean = false) = Notification(
        id = id,
        type = "event_new",
        title = "T $id",
        body = "B $id",
        entityId = "e-$id",
        entityType = "event",
        isRead = isRead,
        createdAt = Instant.fromEpochMilliseconds((now - age).toEpochMilliseconds()).toString()
    )

    @Test
    fun saveNotifications_roundTripsAndReturnsNewestFirst() {
        cache.saveNotifications(listOf(notification("old", 3.hours), notification("new", 1.hours, isRead = true)))

        val loaded = cache.getCachedNotifications(limit = 10, offset = 0)

        assertEquals(listOf("new", "old"), loaded.map { it.id })
        assertEquals(true, loaded[0].isRead)
        assertEquals("e-new", loaded[0].entityId)
        assertEquals("event", loaded[0].entityType)
        assertEquals("T new", loaded[0].title)
    }

    @Test
    fun getCachedNotifications_respectsLimitAndOffset() {
        cache.saveNotifications((1..5).map { notification("n$it", it.hours) })

        assertEquals(listOf("n1", "n2"), cache.getCachedNotifications(2, 0).map { it.id })
        assertEquals(listOf("n3", "n4"), cache.getCachedNotifications(2, 2).map { it.id })
    }

    @Test
    fun saveNotifications_sameIdTwice_overwritesInsteadOfDuplicating() {
        cache.saveNotifications(listOf(notification("n1", 1.hours)))

        cache.saveNotifications(listOf(notification("n1", 1.hours, isRead = true)))

        val loaded = cache.getCachedNotifications(10, 0)
        assertEquals(1, loaded.size)
        assertEquals(true, loaded.single().isRead)
    }

    @Test
    fun unreadCount_followsMarkReadAndMarkAllRead() {
        cache.saveNotifications(listOf(notification("a", 1.hours), notification("b", 2.hours), notification("c", 3.hours)))
        assertEquals(3L, cache.getUnreadCount())

        cache.markRead("a")
        assertEquals(2L, cache.getUnreadCount())

        cache.markAllRead()
        assertEquals(0L, cache.getUnreadCount())
    }

    @Test
    fun clearAll_removesEverything() {
        cache.saveNotifications(listOf(notification("a", 1.hours)))

        cache.clearAll()

        assertTrue(cache.getCachedNotifications(10, 0).isEmpty())
        assertEquals(0L, cache.getUnreadCount())
    }

    @Test
    fun cleanup_removesOnlyNotificationsOlderThanCutoff() {
        cache.saveNotifications(listOf(notification("old", 10.days), notification("new", 1.hours)))

        cache.cleanup((now - 7.days).toEpochMilliseconds())

        assertEquals(listOf("new"), cache.getCachedNotifications(10, 0).map { it.id })
    }

    @Test
    fun saveNotifications_malformedTimestamp_doesNotThrow() {
        val bad = notification("bad", 1.hours).copy(createdAt = "not-a-date")

        cache.saveNotifications(listOf(bad))

        assertTrue(cache.getCachedNotifications(10, 0).isEmpty())
    }

    @Test
    fun withoutSchema_everyOperationDegradesInsteadOfThrowing() {
        val bare = NotificationCacheManager(TeamorgDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)))

        bare.saveNotifications(listOf(notification("a", 1.hours)))
        bare.markRead("a")
        bare.markAllRead()
        bare.cleanup(0L)
        bare.clearAll()

        assertTrue(bare.getCachedNotifications(10, 0).isEmpty())
        assertEquals(0L, bare.getUnreadCount())
    }
}
