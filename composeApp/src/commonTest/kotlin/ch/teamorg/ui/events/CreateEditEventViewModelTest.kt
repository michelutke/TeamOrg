package ch.teamorg.ui.events

import ch.teamorg.domain.CreateEventRequest
import ch.teamorg.domain.EditEventRequest
import ch.teamorg.domain.Event
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.SubGroup
import ch.teamorg.fake.FakeClubRepository
import ch.teamorg.fake.FakeTeamRepository
import ch.teamorg.repository.EventRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private class FakeCreateEditEventRepository(
    private val event: EventWithTeams? = null,
    val capturedCreate: MutableList<CreateEventRequest> = mutableListOf(),
    val capturedEdit: MutableList<EditEventRequest> = mutableListOf()
) : EventRepository {
    override suspend fun getMyEvents(from: String?, to: String?, type: String?, teamId: String?) =
        Result.success(emptyList<EventWithTeams>())
    override suspend fun getEventDetail(id: String) =
        event?.let { Result.success(it) } ?: Result.failure(NotImplementedError())
    override suspend fun createEvent(request: CreateEventRequest): Result<Event> {
        capturedCreate.add(request)
        return Result.failure(NotImplementedError())
    }
    override suspend fun editEvent(id: String, request: EditEventRequest): Result<Event> {
        capturedEdit.add(request)
        return Result.failure(NotImplementedError())
    }
    override suspend fun cancelEvent(id: String, scope: String) = Result.success(Unit)
    override suspend fun uncancelEvent(id: String, scope: String) = Result.success(Unit)
    override suspend fun duplicateEvent(id: String) = Result.failure<Event>(NotImplementedError())
    override suspend fun getSubGroups(teamId: String) = Result.success(emptyList<SubGroup>())
}

@OptIn(ExperimentalCoroutinesApi::class)
class CreateEditEventViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val now = Clock.System.now()

    private fun makeEvent(defaultResponse: String = "none") = EventWithTeams(
        event = Event(
            id = "e1",
            title = "Training",
            type = "training",
            startAt = now,
            endAt = now,
            status = "active",
            createdBy = "u1",
            createdAt = now,
            updatedAt = now,
            teamIds = listOf("team1"),
            defaultResponse = defaultResponse
        )
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `defaultResponse defaults to none on fresh form`() = runTest {
        val vm = CreateEditEventViewModel(
            eventRepository = FakeCreateEditEventRepository(),
            clubRepository = FakeClubRepository(),
            teamRepository = FakeTeamRepository()
        )
        vm.state.value.defaultResponse shouldBe "none"
    }

    @Test
    fun `loadForEdit seeds defaultResponse from event`() = runTest {
        val repo = FakeCreateEditEventRepository(event = makeEvent(defaultResponse = "accepted"))
        val vm = CreateEditEventViewModel(
            eventRepository = repo,
            clubRepository = FakeClubRepository(),
            teamRepository = FakeTeamRepository()
        )
        vm.loadForEdit("e1")
        vm.state.value.defaultResponse shouldBe "accepted"
    }

    @Test
    fun `loadForEdit seeds declined defaultResponse from event`() = runTest {
        val repo = FakeCreateEditEventRepository(event = makeEvent(defaultResponse = "declined"))
        val vm = CreateEditEventViewModel(
            eventRepository = repo,
            clubRepository = FakeClubRepository(),
            teamRepository = FakeTeamRepository()
        )
        vm.loadForEdit("e1")
        vm.state.value.defaultResponse shouldBe "declined"
    }

    @Test
    fun `setDefaultResponse updates state`() = runTest {
        val vm = CreateEditEventViewModel(
            eventRepository = FakeCreateEditEventRepository(),
            clubRepository = FakeClubRepository(),
            teamRepository = FakeTeamRepository()
        )
        vm.setDefaultResponse("declined")
        vm.state.value.defaultResponse shouldBe "declined"
    }
}
