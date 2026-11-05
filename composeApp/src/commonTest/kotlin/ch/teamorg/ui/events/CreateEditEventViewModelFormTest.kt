package ch.teamorg.ui.events

import app.cash.turbine.test
import ch.teamorg.domain.Event
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.RecurringPattern
import ch.teamorg.fake.FakeClubRepository
import ch.teamorg.fake.FakeEventRepository
import ch.teamorg.fake.FakeTeamRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class CreateEditEventViewModelFormTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val eventRepo = FakeEventRepository()
    private val now = Clock.System.now()
    private val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

    private fun makeViewModel() = CreateEditEventViewModel(eventRepo, FakeClubRepository(), FakeTeamRepository())

    private fun savedEvent(seriesId: String? = null) = Event(
        id = "e1",
        title = "Training",
        type = "training",
        startAt = now,
        endAt = now + 1.5.hours,
        status = "active",
        seriesId = seriesId,
        createdBy = "u1",
        createdAt = now,
        updatedAt = now,
        teamIds = listOf("team1")
    )

    private fun CreateEditEventViewModel.fillValidForm() {
        setTitle("Training")
        toggleTeam("team1")
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region validation

    @Test
    fun save_blankTitle_setsTitleErrorAndSendsNothing() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.toggleTeam("team1")
        vm.setTitle("   ")

        vm.save()
        advanceUntilIdle()

        vm.state.value.titleError shouldNotBe null
        eventRepo.createRequests shouldHaveSize 0
    }

    @Test
    fun save_noTeamSelected_setsTeamError() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.setTitle("Training")

        vm.save()
        advanceUntilIdle()

        vm.state.value.teamError shouldNotBe null
        vm.state.value.titleError shouldBe null
        eventRepo.createRequests shouldHaveSize 0
    }

    @Test
    fun save_endBeforeStartSameDay_setsEndTimeError() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        vm.setStartTime(LocalTime(18, 0))
        vm.setEndTime(LocalTime(17, 0))

        vm.save()
        advanceUntilIdle()

        vm.state.value.endTimeError shouldNotBe null
        eventRepo.createRequests shouldHaveSize 0
    }

    @Test
    fun save_endEqualsStart_setsEndTimeError() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        vm.setStartTime(LocalTime(18, 0))
        vm.setEndTime(LocalTime(18, 0))

        vm.save()
        advanceUntilIdle()

        vm.state.value.endTimeError shouldNotBe null
        eventRepo.createRequests shouldHaveSize 0
    }

    @Test
    fun save_endDateBeforeStartDate_setsEndTimeError() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        vm.setStartDate(today.plus(DatePeriod(days = 3)))
        vm.setEndDate(today.plus(DatePeriod(days = 2)))

        vm.save()
        advanceUntilIdle()

        vm.state.value.endTimeError shouldNotBe null
        eventRepo.createRequests shouldHaveSize 0
    }

    @Test
    fun save_allFieldsInvalid_reportsEveryError() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.setEndTime(LocalTime(10, 0))

        vm.save()
        advanceUntilIdle()

        vm.state.value.titleError shouldNotBe null
        vm.state.value.endTimeError shouldNotBe null
        vm.state.value.teamError shouldNotBe null
        vm.state.value.isSaving shouldBe false
    }

    @Test
    fun editingFields_clearsTheirErrors() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.setEndTime(LocalTime(10, 0))
        vm.save()
        advanceUntilIdle()

        vm.setTitle("Training")
        vm.toggleTeam("team1")
        vm.setEndTime(LocalTime(20, 0))

        vm.state.value.titleError shouldBe null
        vm.state.value.teamError shouldBe null
        vm.state.value.endTimeError shouldBe null
    }

    @Test
    fun setStartDate_whenEndFollowedStart_movesEndDateAlong() = runTest(testDispatcher) {
        val vm = makeViewModel()
        val target = today.plus(DatePeriod(days = 5))

        vm.setStartDate(target)

        vm.state.value.endDate shouldBe target
    }

    @Test
    fun setStartDate_whenEndAlreadyLater_keepsEndDate() = runTest(testDispatcher) {
        val vm = makeViewModel()
        val end = today.plus(DatePeriod(days = 4))
        vm.setEndDate(end)

        vm.setStartDate(today.plus(DatePeriod(days = 1)))

        vm.state.value.endDate shouldBe end
    }

    // endregion

    // region save outcome

    @Test
    fun create_success_emitsSaveSuccess() = runTest(testDispatcher) {
        eventRepo.createEventResult = Result.success(savedEvent())
        val vm = makeViewModel()
        vm.fillValidForm()

        vm.events.test {
            vm.save()
            awaitItem() shouldBe FormEvent.SaveSuccess
        }
        vm.state.value.isSaving shouldBe false
    }

    @Test
    fun create_failure_surfacesSaveError() = runTest(testDispatcher) {
        eventRepo.createEventResult = Result.failure(RuntimeException("500"))
        val vm = makeViewModel()
        vm.fillValidForm()

        vm.save()
        advanceUntilIdle()

        vm.state.value.saveError shouldBe "500"
        vm.state.value.isSaving shouldBe false
    }

    @Test
    fun create_blankLocationAndDescription_areSentAsNull() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        vm.setLocation("  ")
        vm.setDescription("")

        vm.save()
        advanceUntilIdle()

        val request = eventRepo.createRequests.single()
        request.location shouldBe null
        request.description shouldBe null
        request.meetupAt shouldBe null
        request.minAttendees shouldBe null
        request.teamIds shouldBe listOf("team1")
    }

    // endregion

    // region recurrence

    @Test
    fun create_withoutRecurringEnabled_sendsNoRecurrence() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        vm.setRecurringPattern(RecurringPatternState(patternType = "weekly", weekdays = setOf(1)))

        vm.save()
        advanceUntilIdle()

        eventRepo.createRequests.single().recurring shouldBe null
    }

    @Test
    fun create_recurringEnabledWithoutPattern_sendsNoRecurrence() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        vm.setRecurringEnabled(true)

        vm.save()
        advanceUntilIdle()

        eventRepo.createRequests.single().recurring shouldBe null
    }

    @Test
    fun create_weeklyPattern_sendsWeekdaysAndNoInterval() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        vm.setRecurringEnabled(true)
        vm.setRecurringPattern(RecurringPatternState(patternType = "weekly", weekdays = setOf(0, 2), intervalDays = 14))

        vm.save()
        advanceUntilIdle()

        val recurring = eventRepo.createRequests.single().recurring!!
        recurring.patternType shouldBe "weekly"
        recurring.weekdays shouldBe listOf(0, 2)
        recurring.intervalDays shouldBe null
        recurring.seriesEndDate shouldBe null
    }

    @Test
    fun create_weeklyPatternWithNoWeekdays_sendsNullWeekdays() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        vm.setRecurringEnabled(true)
        vm.setRecurringPattern(RecurringPatternState(patternType = "weekly", weekdays = emptySet()))

        vm.save()
        advanceUntilIdle()

        eventRepo.createRequests.single().recurring?.weekdays shouldBe null
    }

    @Test
    fun create_dailyPattern_sendsDailyWithoutIntervalOrWeekdays() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        vm.setRecurringEnabled(true)
        vm.setRecurringPattern(RecurringPatternState(patternType = "daily"))

        vm.save()
        advanceUntilIdle()

        eventRepo.createRequests.single().recurring shouldBe RecurringPattern(patternType = "daily")
    }

    @Test
    fun create_customPattern_sendsIntervalDays() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        vm.setRecurringEnabled(true)
        vm.setRecurringPattern(RecurringPatternState(patternType = "custom", intervalDays = 10))

        vm.save()
        advanceUntilIdle()

        val recurring = eventRepo.createRequests.single().recurring!!
        recurring.patternType shouldBe "custom"
        recurring.intervalDays shouldBe 10
        recurring.weekdays shouldBe null
    }

    @Test
    fun create_patternWithEndDate_sendsIsoSeriesEndDate() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        val endDate = today.plus(DatePeriod(months = 2))
        vm.setRecurringEnabled(true)
        vm.setRecurringPattern(
            RecurringPatternState(patternType = "weekly", weekdays = setOf(3), hasEndDate = true, endDate = endDate)
        )

        vm.save()
        advanceUntilIdle()

        eventRepo.createRequests.single().recurring?.seriesEndDate shouldBe endDate.toString()
    }

    @Test
    fun create_patternWithoutEndDate_sendsOpenEndedSeries() = runTest(testDispatcher) {
        val vm = makeViewModel()
        vm.fillValidForm()
        vm.setRecurringEnabled(true)
        vm.setRecurringPattern(RecurringPatternState(patternType = "weekly", weekdays = setOf(3), hasEndDate = false))

        vm.save()
        advanceUntilIdle()

        eventRepo.createRequests.single().recurring?.seriesEndDate shouldBe null
    }

    // endregion

    // region edit scopes

    private suspend fun loadedForEdit(seriesId: String?): CreateEditEventViewModel {
        eventRepo.getEventDetailResult = Result.success(
            EventWithTeams(event = savedEvent(seriesId))
        )
        eventRepo.editEventResult = Result.success(savedEvent(seriesId))
        val vm = makeViewModel()
        vm.loadForEdit("e1")
        return vm
    }

    @Test
    fun loadForEdit_seriesEvent_marksSeries() = runTest(testDispatcher) {
        val vm = loadedForEdit(seriesId = "s1")

        vm.state.value.isEditMode shouldBe true
        vm.state.value.editEventId shouldBe "e1"
        vm.state.value.isSeriesEvent shouldBe true
    }

    @Test
    fun loadForEdit_singleEvent_isNotSeries() = runTest(testDispatcher) {
        val vm = loadedForEdit(seriesId = null)

        vm.state.value.isSeriesEvent shouldBe false
    }

    @Test
    fun loadForDuplicate_seriesEvent_isNewNonSeriesCreate() = runTest(testDispatcher) {
        eventRepo.getEventDetailResult = Result.success(EventWithTeams(event = savedEvent("s1")))
        val vm = makeViewModel()

        vm.loadForDuplicate("e1")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        vm.state.value.isEditMode shouldBe false
        vm.state.value.editEventId shouldBe null
        vm.state.value.isSeriesEvent shouldBe false
        eventRepo.createRequests shouldHaveSize 1
        eventRepo.editCalls shouldHaveSize 0
    }

    @Test
    fun edit_scopeThisOnly_isForwarded() = runTest(testDispatcher) {
        val vm = loadedForEdit("s1")

        vm.save("this_only")
        advanceUntilIdle()

        eventRepo.editCalls.single().first shouldBe "e1"
        eventRepo.editCalls.single().second.scope shouldBe "this_only"
    }

    @Test
    fun edit_scopeThisAndFuture_isForwarded() = runTest(testDispatcher) {
        val vm = loadedForEdit("s1")

        vm.save("this_and_future")
        advanceUntilIdle()

        eventRepo.editCalls.single().second.scope shouldBe "this_and_future"
    }

    @Test
    fun edit_scopeAll_isForwarded() = runTest(testDispatcher) {
        val vm = loadedForEdit("s1")

        vm.save("all")
        advanceUntilIdle()

        eventRepo.editCalls.single().second.scope shouldBe "all"
    }

    @Test
    fun edit_nonSeriesEvent_sendsNoScope() = runTest(testDispatcher) {
        val vm = loadedForEdit(null)

        vm.save()
        advanceUntilIdle()

        eventRepo.editCalls.single().second.scope shouldBe null
    }

    @Test
    fun edit_ignoresRecurringPattern() = runTest(testDispatcher) {
        val vm = loadedForEdit("s1")
        vm.setRecurringEnabled(true)
        vm.setRecurringPattern(RecurringPatternState(patternType = "daily"))

        vm.save("all")
        advanceUntilIdle()

        eventRepo.createRequests shouldHaveSize 0
        eventRepo.editCalls shouldHaveSize 1
    }

    @Test
    fun edit_success_emitsSaveSuccess() = runTest(testDispatcher) {
        val vm = loadedForEdit("s1")

        vm.events.test {
            vm.save("all")
            awaitItem() shouldBe FormEvent.SaveSuccess
        }
    }

    @Test
    fun edit_failure_surfacesSaveError() = runTest(testDispatcher) {
        val vm = loadedForEdit("s1")
        eventRepo.editEventResult = Result.failure(RuntimeException("409"))

        vm.save("all")
        advanceUntilIdle()

        vm.state.value.saveError shouldBe "409"
        vm.state.value.isSaving shouldBe false
    }

    @Test
    fun edit_endBeforeStart_isRejectedBeforeNetwork() = runTest(testDispatcher) {
        val vm = loadedForEdit("s1")
        vm.setEndDate(vm.state.value.startDate.minus(DatePeriod(days = 1)))

        vm.save("all")
        advanceUntilIdle()

        vm.state.value.endTimeError shouldNotBe null
        eventRepo.editCalls shouldHaveSize 0
    }

    @Test
    fun cancelEvent_inEditMode_forwardsScopeAndEmitsCancelSuccess() = runTest(testDispatcher) {
        val vm = loadedForEdit("s1")

        vm.events.test {
            vm.cancelEvent("this_and_future")
            awaitItem() shouldBe FormEvent.CancelSuccess
        }

        eventRepo.cancelCalls shouldBe listOf("e1" to "this_and_future")
    }

    @Test
    fun cancelEvent_notInEditMode_doesNothing() = runTest(testDispatcher) {
        val vm = makeViewModel()

        vm.cancelEvent()

        eventRepo.cancelCalls shouldHaveSize 0
    }

    // endregion
}
