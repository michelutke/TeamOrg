package ch.teamorg.ui.team

import ch.teamorg.domain.AbwesenheitRule
import ch.teamorg.domain.BackfillStatus
import ch.teamorg.domain.CreateAbwesenheitRequest
import ch.teamorg.domain.DeleteAccountResult
import ch.teamorg.domain.Event
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.SubGroup
import ch.teamorg.domain.UpdateAbwesenheitRequest
import ch.teamorg.fake.FakeAttendanceRepository
import ch.teamorg.fake.FakeAuthRepository
import ch.teamorg.fake.FakeTeamRepository
import ch.teamorg.preferences.UserPreferences
import ch.teamorg.repository.AbwesenheitRepository
import ch.teamorg.repository.EventRepository
import com.russhwolf.settings.MapSettings
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

// Minimal fake for AbwesenheitRepository — nothing exercised here
private class FakeAbwesenheitRepository : AbwesenheitRepository {
    override suspend fun listRules() = Result.success(emptyList<AbwesenheitRule>())
    override suspend fun createRule(request: CreateAbwesenheitRequest) =
        Result.failure<AbwesenheitRule>(NotImplementedError())
    override suspend fun updateRule(ruleId: String, request: UpdateAbwesenheitRequest) =
        Result.failure<AbwesenheitRule>(NotImplementedError())
    override suspend fun deleteRule(ruleId: String) = Result.success(Unit)
    override suspend fun getBackfillStatus() = Result.success(BackfillStatus(status = "done"))
}

// Minimal fake for EventRepository — nothing exercised here
private class FakeEventRepository : EventRepository {
    override suspend fun getMyEvents(from: String?, to: String?, type: String?, teamId: String?) =
        Result.success(emptyList<EventWithTeams>())
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

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerProfileViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var viewModel: PlayerProfileViewModel

    private fun buildViewModel(authRepository: FakeAuthRepository) = PlayerProfileViewModel(
        teamRepository = FakeTeamRepository(),
        userPreferences = UserPreferences(MapSettings()),
        abwesenheitRepository = FakeAbwesenheitRepository(),
        attendanceRepository = FakeAttendanceRepository(),
        eventRepository = FakeEventRepository(),
        authRepository = authRepository
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
        viewModel = buildViewModel(authRepository)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `successful deletion sets accountDeleted and clears progress`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.Success

        viewModel.deleteAccount("password123")

        viewModel.state.value.accountDeleted.shouldBeTrue()
        viewModel.state.value.deleteInProgress.shouldBeFalse()
        viewModel.state.value.deleteError shouldBe null
        authRepository.deleteAccountPasswords shouldBe listOf("password123")
    }

    @Test
    fun `invalid password maps to a sentence and keeps the dialog open`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.InvalidPassword
        viewModel.openDeleteDialog()

        viewModel.deleteAccount("wrong")

        viewModel.state.value.deleteError shouldBe "That password is incorrect."
        viewModel.state.value.showDeleteDialog.shouldBeTrue()
        viewModel.state.value.accountDeleted.shouldBeFalse()
    }

    @Test
    fun `owning a club maps to a sentence naming the club`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.OwnsClubs(listOf("Owner Club"))
        viewModel.openDeleteDialog()

        viewModel.deleteAccount("password123")

        viewModel.state.value.deleteError shouldBe
            "You own Owner Club. Contact info@teamorg.ch so we can transfer or close the club before you delete your account."
        viewModel.state.value.showDeleteDialog.shouldBeTrue()
    }

    @Test
    fun `owning several clubs lists them all`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.OwnsClubs(listOf("A", "B"))

        viewModel.deleteAccount("password123")

        viewModel.state.value.deleteError shouldBe
            "You own A, B. Contact info@teamorg.ch so we can transfer or close the club before you delete your account."
    }

    @Test
    fun `a conflict with no club names still gives an actionable sentence`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.OwnsClubs(emptyList())

        viewModel.deleteAccount("password123")

        viewModel.state.value.deleteError shouldBe
            "You still own a club. Contact info@teamorg.ch so we can transfer or close the club before you delete your account."
    }

    @Test
    fun `a generic error maps to the retry sentence`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.Error("boom")

        viewModel.deleteAccount("password123")

        viewModel.state.value.deleteError shouldBe "Couldn't delete your account. Please try again."
    }

    @Test
    fun `a second call while in flight is ignored`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.Success
        authRepository.deleteAccountGate = CompletableDeferred()

        viewModel.deleteAccount("first")
        viewModel.state.value.deleteInProgress.shouldBeTrue()
        viewModel.deleteAccount("second")
        authRepository.deleteAccountGate!!.complete(Unit)

        authRepository.deleteAccountPasswords shouldBe listOf("first")
        viewModel.state.value.accountDeleted.shouldBeTrue()
    }

    @Test
    fun `opening the dialog clears a stale error`() = runTest {
        authRepository.deleteAccountResult = DeleteAccountResult.InvalidPassword
        viewModel.deleteAccount("wrong")
        viewModel.state.value.deleteError shouldBe "That password is incorrect."

        viewModel.closeDeleteDialog()
        viewModel.openDeleteDialog()

        viewModel.state.value.deleteError shouldBe null
        viewModel.state.value.showDeleteDialog.shouldBeTrue()
    }
}
