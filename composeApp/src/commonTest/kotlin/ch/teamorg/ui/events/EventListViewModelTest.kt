package ch.teamorg.ui.events

import ch.teamorg.domain.Event
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.MatchedTeam
import ch.teamorg.domain.SubGroup
import ch.teamorg.domain.TeamRoleEntry
import ch.teamorg.domain.UserRoles
import ch.teamorg.fake.FakeAttendanceRepository
import ch.teamorg.preferences.UserPreferences
import com.russhwolf.settings.MapSettings
import ch.teamorg.repository.EventRepository
import ch.teamorg.repository.TeamRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private class FakeEventsRepository(private val events: List<EventWithTeams>) : EventRepository {
    override suspend fun getMyEvents(from: String?, to: String?, type: String?, teamId: String?) =
        Result.success(events)
    override suspend fun getEventDetail(id: String) = Result.failure<EventWithTeams>(NotImplementedError())
    override suspend fun createEvent(request: ch.teamorg.domain.CreateEventRequest) =
        Result.failure<Event>(NotImplementedError())
    override suspend fun editEvent(id: String, request: ch.teamorg.domain.EditEventRequest) =
        Result.failure<Event>(NotImplementedError())
    override suspend fun cancelEvent(id: String, scope: String) = Result.success(Unit)
    override suspend fun uncancelEvent(id: String, scope: String) = Result.success(Unit)
    override suspend fun duplicateEvent(id: String) = Result.failure<Event>(NotImplementedError())
    override suspend fun getSubGroups(teamId: String) = Result.success(emptyList<SubGroup>())
}

private class FakeTeamRepo : TeamRepository {
    override suspend fun getMyRoles(): Result<UserRoles> = Result.success(UserRoles())
    override suspend fun getTeamRoster(teamId: String) = Result.success(emptyList<ch.teamorg.domain.TeamMember>())
    override suspend fun removeMember(teamId: String, userId: String) = Result.success(Unit)
    override suspend fun createInvite(teamId: String, role: String, email: String?) = Result.success("")
    override suspend fun updateMemberRole(teamId: String, userId: String, role: String) =
        Result.failure<ch.teamorg.domain.TeamMember>(NotImplementedError())
    override suspend fun updateMemberProfile(teamId: String, userId: String, jerseyNumber: Int?, position: String?) =
        Result.failure<ch.teamorg.domain.TeamMember>(NotImplementedError())
    override suspend fun leaveTeam(teamId: String) = Result.success(Unit)
    override suspend fun getSubGroups(teamId: String) = Result.success(emptyList<SubGroup>())
    override suspend fun createSubGroup(teamId: String, name: String) =
        Result.failure<SubGroup>(NotImplementedError())
    override suspend fun deleteSubGroup(teamId: String, subGroupId: String) = Result.success(Unit)
    override suspend fun addSubGroupMember(teamId: String, subGroupId: String, userId: String) = Result.success(Unit)
    override suspend fun removeSubGroupMember(teamId: String, subGroupId: String, userId: String) = Result.success(Unit)
    override suspend fun uploadAvatar(imageBytes: ByteArray, extension: String) = Result.success(Unit)
    override suspend fun addMember(teamId: String, userId: String, role: String) = Result.success(Unit)
    override suspend fun linkNdsMember(teamId: String, memberId: String, userId: String) = Result.success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class EventListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeAttendanceRepo = FakeAttendanceRepository()
    private val now: Instant = Clock.System.now()

    private fun makeEvent(id: String, checkInStatus: String = "open"): EventWithTeams {
        val event = Event(
            id = id,
            title = "Event $id",
            type = "training",
            startAt = now,
            endAt = now,
            status = "active",
            createdBy = "u-coach",
            createdAt = now,
            updatedAt = now,
            checkInStatus = checkInStatus
        )
        return EventWithTeams(event = event)
    }

    private fun makeViewModel(events: List<EventWithTeams>): EventListViewModel =
        EventListViewModel(
            eventRepository = FakeEventsRepository(events),
            teamRepository = FakeTeamRepo(),
            userPreferences = UserPreferences(MapSettings()),
            attendanceRepository = fakeAttendanceRepo
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAttendanceRepo.reset()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region — awaiting filter

    @Test
    fun toggleAwaitingFilter_restrictsEventsToAwaitingCheckIn() = runTest(testDispatcher) {
        val events = listOf(
            makeEvent("e1", checkInStatus = "open"),
            makeEvent("e2", checkInStatus = "awaiting_checkin"),
            makeEvent("e3", checkInStatus = "done"),
            makeEvent("e4", checkInStatus = "awaiting_checkin")
        )
        val vm = makeViewModel(events)
        advanceUntilIdle()

        vm.state.value.events shouldHaveSize 4
        vm.state.value.showAwaitingOnly shouldBe false

        vm.toggleAwaitingFilter()
        advanceUntilIdle()

        vm.state.value.showAwaitingOnly shouldBe true
        val filtered = vm.state.value.events
        filtered shouldHaveSize 2
        filtered.all { it.event.checkInStatus == "awaiting_checkin" } shouldBe true
    }

    @Test
    fun toggleAwaitingFilter_toggleOff_restoresAllEvents() = runTest(testDispatcher) {
        val events = listOf(
            makeEvent("e1", checkInStatus = "open"),
            makeEvent("e2", checkInStatus = "awaiting_checkin")
        )
        val vm = makeViewModel(events)
        advanceUntilIdle()

        vm.toggleAwaitingFilter()
        advanceUntilIdle()
        vm.state.value.events shouldHaveSize 1

        vm.toggleAwaitingFilter()
        advanceUntilIdle()
        vm.state.value.events shouldHaveSize 2
        vm.state.value.showAwaitingOnly shouldBe false
    }

    @Test
    fun awaitingFilter_composesWith_teamFilter() = runTest(testDispatcher) {
        val events = listOf(
            makeEvent("e1", checkInStatus = "awaiting_checkin").let { ewt ->
                ewt.copy(matchedTeams = listOf(MatchedTeam(id = "team-a", name = "Team A")))
            },
            makeEvent("e2", checkInStatus = "awaiting_checkin").let { ewt ->
                ewt.copy(matchedTeams = listOf(MatchedTeam(id = "team-b", name = "Team B")))
            },
            makeEvent("e3", checkInStatus = "open").let { ewt ->
                ewt.copy(matchedTeams = listOf(MatchedTeam(id = "team-a", name = "Team A")))
            }
        )
        val vm = makeViewModel(events)
        advanceUntilIdle()

        vm.toggleTeamFilter("team-a")
        vm.toggleAwaitingFilter()
        advanceUntilIdle()

        val filtered = vm.state.value.events
        filtered shouldHaveSize 1
        filtered.first().event.id shouldBe "e1"
    }

    // endregion
}
