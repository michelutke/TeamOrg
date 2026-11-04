package ch.teamorg.ui.events

import app.cash.turbine.test
import ch.teamorg.domain.AttendanceResponse
import ch.teamorg.domain.Event
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.ReminderOverride
import ch.teamorg.domain.SubmitResponseRequest
import ch.teamorg.fake.FakeAttendanceRepository
import ch.teamorg.fake.FakeEventRepository
import ch.teamorg.fake.FakeNotificationRepository
import ch.teamorg.fake.FakeTeamRepository
import io.kotest.matchers.collections.shouldHaveSize
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

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelRsvpTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val eventRepo = FakeEventRepository()
    private val teamRepo = FakeTeamRepository()
    private val attendanceRepo = FakeAttendanceRepository()
    private val notificationRepo = FakeNotificationRepository()
    private val now = Clock.System.now()

    private fun event(status: String = "active") = EventWithTeams(
        event = Event(
            id = "e1",
            title = "Training",
            type = "training",
            startAt = now,
            endAt = now,
            status = status,
            createdBy = "u-coach",
            createdAt = now,
            updatedAt = now
        )
    )

    private fun response(status: String, reason: String? = null) = AttendanceResponse(
        eventId = "e1", userId = "u1", status = status, reason = reason, updatedAt = now
    )

    private suspend fun loadedViewModel(): EventDetailViewModel {
        eventRepo.getEventDetailResult = Result.success(event())
        val vm = EventDetailViewModel(eventRepo, teamRepo, attendanceRepo, notificationRepo)
        vm.loadEvent("e1")
        return vm
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        attendanceRepo.reset()
        teamRepo.reset()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region submitResponse

    @Test
    fun submitResponse_confirmed_sendsStatusWithoutReason() = runTest(testDispatcher) {
        val vm = loadedViewModel()
        attendanceRepo.submitResponseResult = Result.success(response("confirmed"))
        attendanceRepo.getMyResponseResult = Result.success(response("confirmed"))

        vm.submitResponse("confirmed", null)

        attendanceRepo.submitCalls shouldBe listOf("e1" to SubmitResponseRequest(status = "confirmed", reason = null))
        vm.state.value.myResponse shouldBe "confirmed"
    }

    @Test
    fun submitResponse_declinedWithReason_forwardsReasonVerbatim() = runTest(testDispatcher) {
        val vm = loadedViewModel()
        attendanceRepo.submitResponseResult = Result.success(response("declined", "sick"))
        attendanceRepo.getMyResponseResult = Result.success(response("declined", "sick"))

        vm.submitResponse("declined", "sick")

        attendanceRepo.submitCalls.single().second shouldBe SubmitResponseRequest(status = "declined", reason = "sick")
        vm.state.value.myResponse shouldBe "declined"
    }

    @Test
    fun submitResponse_declinedWithoutReason_isNotBlockedByViewModel() = runTest(testDispatcher) {
        val vm = loadedViewModel()
        attendanceRepo.submitResponseResult = Result.success(response("declined"))
        attendanceRepo.getMyResponseResult = Result.success(response("declined"))

        vm.submitResponse("declined", null)

        attendanceRepo.submitCalls shouldHaveSize 1
        attendanceRepo.submitCalls.single().second.reason shouldBe null
        vm.state.value.myResponse shouldBe "declined"
    }

    @Test
    fun submitResponse_unsure_sendsUnsureStatus() = runTest(testDispatcher) {
        val vm = loadedViewModel()
        attendanceRepo.submitResponseResult = Result.success(response("unsure"))
        attendanceRepo.getMyResponseResult = Result.success(response("unsure"))

        vm.submitResponse("unsure", "maybe late")

        attendanceRepo.submitCalls.single().second shouldBe SubmitResponseRequest(status = "unsure", reason = "maybe late")
        vm.state.value.myResponse shouldBe "unsure"
    }

    @Test
    fun submitResponse_success_refreshesAttendanceCounts() = runTest(testDispatcher) {
        val vm = loadedViewModel()
        attendanceRepo.submitResponseResult = Result.success(response("confirmed"))
        attendanceRepo.getMyResponseResult = Result.success(response("confirmed"))
        attendanceRepo.getEventAttendanceResult = Result.success(
            listOf(
                response("confirmed"),
                AttendanceResponse(eventId = "e1", userId = "u2", status = "unsure", updatedAt = now),
                AttendanceResponse(eventId = "e1", userId = "u3", status = "declined", updatedAt = now),
                AttendanceResponse(eventId = "e1", userId = "u4", status = "declined-auto", updatedAt = now)
            )
        )

        vm.submitResponse("confirmed", null)

        vm.state.value.confirmedCount shouldBe 1
        vm.state.value.maybeCount shouldBe 1
        vm.state.value.declinedCount shouldBe 2
        vm.state.value.attendanceResponses shouldHaveSize 4
    }

    @Test
    fun submitResponse_serverStatusWinsOverOptimisticStatus() = runTest(testDispatcher) {
        val vm = loadedViewModel()
        attendanceRepo.submitResponseResult = Result.success(response("declined-auto"))
        attendanceRepo.getMyResponseResult = Result.success(response("declined-auto"))

        vm.submitResponse("confirmed", null)

        vm.state.value.myResponse shouldBe "declined-auto"
    }

    @Test
    fun submitResponse_failure_revertsToPreviousResponse() = runTest(testDispatcher) {
        attendanceRepo.getMyResponseResult = Result.success(response("confirmed"))
        val vm = loadedViewModel()
        vm.state.value.myResponse shouldBe "confirmed"
        attendanceRepo.submitResponseResult = Result.failure(RuntimeException("409 deadline"))

        vm.submitResponse("declined", "sick")

        vm.state.value.myResponse shouldBe "confirmed"
    }

    @Test
    fun submitResponse_failureWithoutPreviousResponse_clearsOptimisticValue() = runTest(testDispatcher) {
        val vm = loadedViewModel()
        attendanceRepo.submitResponseResult = Result.failure(RuntimeException("500"))

        vm.submitResponse("confirmed", null)

        vm.state.value.myResponse shouldBe null
    }

    @Test
    fun submitResponse_beforeEventLoaded_doesNothing() = runTest(testDispatcher) {
        val vm = EventDetailViewModel(eventRepo, teamRepo, attendanceRepo, notificationRepo)

        vm.submitResponse("confirmed", null)

        attendanceRepo.submitCalls shouldHaveSize 0
        vm.state.value.myResponse shouldBe null
    }

    // endregion

    // region cancel / uncancel

    @Test
    fun cancelEvent_defaultScope_isThisOnlyAndReloads() = runTest(testDispatcher) {
        val vm = loadedViewModel()
        val loadsBefore = eventRepo.getEventDetailCallCount

        vm.cancelEvent()

        eventRepo.cancelCalls shouldBe listOf("e1" to "this_only")
        eventRepo.getEventDetailCallCount shouldBe loadsBefore + 1
    }

    @Test
    fun cancelEvent_forwardsScope() = runTest(testDispatcher) {
        val vm = loadedViewModel()

        vm.cancelEvent("this_and_future")
        vm.cancelEvent("all")

        eventRepo.cancelCalls shouldBe listOf("e1" to "this_and_future", "e1" to "all")
    }

    @Test
    fun cancelEvent_success_emitsCancelledEvent() = runTest(testDispatcher) {
        val vm = loadedViewModel()

        vm.events.test {
            vm.cancelEvent()
            awaitItem() shouldBe DetailEvent.Cancelled
        }
    }

    @Test
    fun cancelEvent_failure_surfacesErrorAndDoesNotReload() = runTest(testDispatcher) {
        val vm = loadedViewModel()
        val loadsBefore = eventRepo.getEventDetailCallCount
        eventRepo.cancelEventResult = Result.failure(RuntimeException("403"))

        vm.cancelEvent()

        vm.state.value.error shouldBe "403"
        eventRepo.getEventDetailCallCount shouldBe loadsBefore
    }

    @Test
    fun cancelEvent_beforeEventLoaded_doesNothing() = runTest(testDispatcher) {
        val vm = EventDetailViewModel(eventRepo, teamRepo, attendanceRepo, notificationRepo)

        vm.cancelEvent()

        eventRepo.cancelCalls shouldHaveSize 0
    }

    @Test
    fun uncancelEvent_forwardsScopeAndEmitsUncancelled() = runTest(testDispatcher) {
        val vm = loadedViewModel()

        vm.events.test {
            vm.uncancelEvent("all")
            awaitItem() shouldBe DetailEvent.Uncancelled
        }

        eventRepo.uncancelCalls shouldBe listOf("e1" to "all")
    }

    // endregion

    // region reminders

    @Test
    fun loadEvent_loadsReminderOverride() = runTest(testDispatcher) {
        notificationRepo.getReminderOverrideResult = Result.success(ReminderOverride(reminderLeadMinutes = 45))

        val vm = loadedViewModel()

        vm.state.value.reminderLeadMinutes shouldBe 45
        vm.state.value.isLoadingReminder shouldBe false
    }

    @Test
    fun loadReminderOverride_failure_stopsLoadingAndKeepsDefault() = runTest(testDispatcher) {
        notificationRepo.getReminderOverrideResult = Result.failure(RuntimeException("500"))

        val vm = loadedViewModel()

        vm.state.value.reminderLeadMinutes shouldBe null
        vm.state.value.isLoadingReminder shouldBe false
    }

    @Test
    fun setReminderOverride_success_updatesState() = runTest(testDispatcher) {
        val vm = loadedViewModel()

        vm.setReminderOverride("e1", 60)

        notificationRepo.setReminderOverrideCalls shouldBe listOf("e1" to 60)
        vm.state.value.reminderLeadMinutes shouldBe 60
    }

    @Test
    fun setReminderOverride_null_resetsToDefault() = runTest(testDispatcher) {
        notificationRepo.getReminderOverrideResult = Result.success(ReminderOverride(reminderLeadMinutes = 45))
        val vm = loadedViewModel()

        vm.setReminderOverride("e1", null)

        notificationRepo.setReminderOverrideCalls shouldBe listOf("e1" to null)
        vm.state.value.reminderLeadMinutes shouldBe null
    }

    @Test
    fun setReminderOverride_failure_keepsPreviousValue() = runTest(testDispatcher) {
        notificationRepo.getReminderOverrideResult = Result.success(ReminderOverride(reminderLeadMinutes = 45))
        val vm = loadedViewModel()
        notificationRepo.setReminderOverrideResult = Result.failure(RuntimeException("500"))

        vm.setReminderOverride("e1", 60)

        vm.state.value.reminderLeadMinutes shouldBe 45
    }

    // endregion
}
