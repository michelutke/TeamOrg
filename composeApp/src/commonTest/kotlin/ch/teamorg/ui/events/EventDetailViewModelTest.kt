package ch.teamorg.ui.events

import ch.teamorg.domain.Event
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.FinalizeResult
import ch.teamorg.domain.Notification
import ch.teamorg.domain.NotificationSettings
import ch.teamorg.domain.ReminderOverride
import ch.teamorg.domain.TeamMember
import ch.teamorg.domain.UpdateNotificationSettingsRequest
import ch.teamorg.fake.FakeAttendanceRepository
import ch.teamorg.fake.FakeTeamRepository
import ch.teamorg.repository.EventRepository
import ch.teamorg.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

// Minimal fake for EventRepository — only getEventDetail needed here
private class FakeEventRepository(private val event: EventWithTeams) : EventRepository {
    override suspend fun getEventDetail(id: String): Result<EventWithTeams> = Result.success(event)
    override suspend fun getMyEvents(from: String?, to: String?, type: String?, teamId: String?) = Result.success(emptyList<EventWithTeams>())
    override suspend fun createEvent(request: ch.teamorg.domain.CreateEventRequest) = Result.failure<Event>(NotImplementedError())
    override suspend fun editEvent(id: String, request: ch.teamorg.domain.EditEventRequest) = Result.failure<Event>(NotImplementedError())
    override suspend fun cancelEvent(id: String, scope: String) = Result.success(Unit)
    override suspend fun uncancelEvent(id: String, scope: String) = Result.success(Unit)
    override suspend fun duplicateEvent(id: String) = Result.failure<Event>(NotImplementedError())
    override suspend fun getSubGroups(teamId: String) = Result.success(emptyList<ch.teamorg.domain.SubGroup>())
}

// Minimal fake for NotificationRepository
private class FakeNotificationRepository : NotificationRepository {
    override suspend fun getNotifications(limit: Int, offset: Int) = Result.success(emptyList<Notification>())
    override suspend fun getUnreadCount() = Result.success(0L)
    override suspend fun markRead(notificationId: String) = Result.success(false)
    override suspend fun markAllRead() = Result.success(0)
    override suspend fun deleteAll() = Result.success(Unit)
    override suspend fun getSettings(teamId: String) = Result.failure<NotificationSettings>(NotImplementedError())
    override suspend fun updateSettings(teamId: String, request: UpdateNotificationSettingsRequest) = Result.success(Unit)
    override suspend fun getReminderOverride(eventId: String) = Result.success(ReminderOverride(reminderLeadMinutes = null))
    override suspend fun setReminderOverride(eventId: String, leadMinutes: Int?) = Result.success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeAttendanceRepo = FakeAttendanceRepository()
    private val fakeTeamRepo = FakeTeamRepository()
    private val now = Clock.System.now()

    private fun makeEvent(
        checkInStatus: String = "open",
        teamIds: List<String> = emptyList()
    ): EventWithTeams {
        val event = Event(
            id = "e1",
            title = "Training",
            type = "training",
            startAt = now,
            endAt = now,
            status = "active",
            createdBy = "u-coach",
            createdAt = now,
            updatedAt = now,
            checkInStatus = checkInStatus,
            teamIds = teamIds
        )
        return EventWithTeams(event = event)
    }

    private fun makeMember(userId: String, name: String) = TeamMember(
        userId = userId,
        displayName = name,
        avatarUrl = null,
        role = "player",
        jerseyNumber = null,
        position = null
    )

    private fun makeViewModel(event: EventWithTeams): EventDetailViewModel {
        return EventDetailViewModel(
            eventRepository = FakeEventRepository(event),
            teamRepository = fakeTeamRepo,
            attendanceRepository = fakeAttendanceRepo,
            notificationRepository = FakeNotificationRepository()
        )
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAttendanceRepo.reset()
        fakeTeamRepo.reset()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region — finalize

    @Test
    fun finalize_success_triggersEventReload() = runTest {
        fakeAttendanceRepo.finalizeResult = FinalizeResult.Success
        val vm = makeViewModel(makeEvent(checkInStatus = "awaiting_checkin"))
        vm.loadEvent("e1")

        vm.finalize()

        // After success the VM reloads; isFinalizingOrReopening returns to false
        vm.state.value.isFinalizingOrReopening shouldBe false
        fakeAttendanceRepo.lastFinalizeEventId shouldBe "e1"
    }

    @Test
    fun finalize_blocked_unsure_surfacesBlockedState_withUserIdFallback() = runTest {
        fakeAttendanceRepo.finalizeResult = FinalizeResult.Blocked(
            reason = "unsure",
            userIds = listOf("u-alice", "u-bob")
        )
        // no roster loaded → fallback to userId
        val vm = makeViewModel(makeEvent(checkInStatus = "awaiting_checkin"))
        vm.loadEvent("e1")

        vm.finalize()

        val blocked = vm.state.value.finalizeBlocked
        blocked shouldNotBe null
        blocked!!.reason shouldBe "Unsichere Spieler müssen zuerst als anwesend oder abwesend markiert werden."
        blocked.memberNames shouldHaveSize 2
        blocked.memberNames[0] shouldBe "u-alice"
        blocked.memberNames[1] shouldBe "u-bob"
        vm.state.value.isFinalizingOrReopening shouldBe false
    }

    @Test
    fun finalize_blocked_resolvesDisplayNames_fromRoster() = runTest {
        fakeTeamRepo.getRosterResult = Result.success(
            listOf(
                makeMember("u-alice", "Alice Muster"),
                makeMember("u-bob", "Bob Trainer")
            )
        )
        fakeAttendanceRepo.finalizeResult = FinalizeResult.Blocked(
            reason = "unsure",
            userIds = listOf("u-alice", "u-bob")
        )
        val vm = makeViewModel(makeEvent(checkInStatus = "awaiting_checkin", teamIds = listOf("team-1")))
        vm.loadEvent("e1")

        vm.finalize()

        val blocked = vm.state.value.finalizeBlocked
        blocked shouldNotBe null
        blocked!!.memberNames[0] shouldBe "Alice Muster"
        blocked.memberNames[1] shouldBe "Bob Trainer"
    }

    @Test
    fun finalize_blocked_noResponse_surfacesBlockedState() = runTest {
        fakeAttendanceRepo.finalizeResult = FinalizeResult.Blocked(
            reason = "no-response",
            userIds = listOf("u-charlie")
        )
        val vm = makeViewModel(makeEvent(checkInStatus = "awaiting_checkin"))
        vm.loadEvent("e1")

        vm.finalize()

        val blocked = vm.state.value.finalizeBlocked
        blocked shouldNotBe null
        blocked!!.memberNames shouldHaveSize 1
        blocked.memberNames[0] shouldBe "u-charlie"  // userId fallback (no roster)
    }

    @Test
    fun rosterMap_populatedAfterLoad() = runTest {
        fakeTeamRepo.getRosterResult = Result.success(
            listOf(makeMember("u-alice", "Alice Muster"))
        )
        val vm = makeViewModel(makeEvent(teamIds = listOf("team-1")))
        vm.loadEvent("e1")

        vm.state.value.rosterMap["u-alice"]?.displayName shouldBe "Alice Muster"
    }

    @Test
    fun dismissFinalizeBlocked_clearsFinalizeBlockedState() = runTest {
        fakeAttendanceRepo.finalizeResult = FinalizeResult.Blocked(reason = "unsure", userIds = listOf("u1"))
        val vm = makeViewModel(makeEvent())
        vm.loadEvent("e1")
        vm.finalize()

        vm.dismissFinalizeBlocked()

        vm.state.value.finalizeBlocked shouldBe null
    }

    // endregion

    // region — reopen

    @Test
    fun reopen_success_triggersEventReload() = runTest {
        fakeAttendanceRepo.reopenResult = Result.success(Unit)
        val vm = makeViewModel(makeEvent(checkInStatus = "done"))
        vm.loadEvent("e1")

        vm.reopen()

        vm.state.value.isFinalizingOrReopening shouldBe false
        fakeAttendanceRepo.lastReopenEventId shouldBe "e1"
    }

    // endregion

    // region — setMemberResponse

    @Test
    fun setMemberResponse_forwardsToRepo() = runTest {
        val vm = makeViewModel(makeEvent())
        vm.loadEvent("e1")

        vm.setMemberResponse("u-alice", "declined", unexcused = true)

        fakeAttendanceRepo.lastSetMemberEventId shouldBe "e1"
        fakeAttendanceRepo.lastSetMemberUserId shouldBe "u-alice"
        fakeAttendanceRepo.lastSetMemberStatus shouldBe "declined"
        fakeAttendanceRepo.lastSetMemberUnexcused shouldBe true
    }

    // endregion

    // region — rsvp lock gating (state shape)

    @Test
    fun state_checkInStatus_open_rsvpNotLocked() = runTest {
        val vm = makeViewModel(makeEvent(checkInStatus = "open"))
        vm.loadEvent("e1")

        vm.state.value.event?.event?.checkInStatus shouldBe "open"
    }

    @Test
    fun state_checkInStatus_done_isPresent() = runTest {
        val vm = makeViewModel(makeEvent(checkInStatus = "done"))
        vm.loadEvent("e1")

        vm.state.value.event?.event?.checkInStatus shouldBe "done"
    }

    // endregion
}
