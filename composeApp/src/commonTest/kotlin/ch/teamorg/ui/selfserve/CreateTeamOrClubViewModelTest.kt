package ch.teamorg.ui.selfserve

import app.cash.turbine.test
import ch.teamorg.domain.AuthUser
import ch.teamorg.fake.FakeAuthRepository
import ch.teamorg.fake.FakeBillingRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTeamOrClubViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeBilling = FakeBillingRepository()
    private val fakeAuth = FakeAuthRepository()
    private lateinit var viewModel: CreateTeamOrClubViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeBilling.reset()
        fakeAuth.reset()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = CreateTeamOrClubViewModel(fakeBilling, fakeAuth).also { viewModel = it }

    // region — init: billing email prefill

    @Test
    fun init_withGetMeSuccess_prefillsBillingEmail() = runTest(testDispatcher) {
        fakeAuth.getMeResult = Result.success(
            AuthUser(userId = "user1", email = "coach@example.com", displayName = "Coach", avatarUrl = null)
        )
        createViewModel()

        viewModel.state.value.billingEmail shouldBe "coach@example.com"
    }

    @Test
    fun init_withGetMeFailure_billingEmailRemainsBlank() = runTest(testDispatcher) {
        fakeAuth.getMeResult = Result.failure(Exception("Not authenticated"))
        createViewModel()

        viewModel.state.value.billingEmail shouldBe ""
    }

    // region — field updates

    @Test
    fun onNameChange_updatesNameAndClearsError() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onNameChange("")
        viewModel.submit()

        viewModel.onNameChange("My Team")

        viewModel.state.value.name shouldBe "My Team"
        viewModel.state.value.error shouldBe null
    }

    // region — submit validation

    @Test
    fun submit_withBlankName_setsErrorAndEmitsNoEvent() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onBillingEmailChange("a@b.com")

        viewModel.events.test {
            viewModel.submit()
            expectNoEvents()
        }
        viewModel.state.value.error shouldBe "Name must not be empty"
    }

    @Test
    fun submit_withInvalidBillingEmail_setsErrorAndEmitsNoEvent() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onNameChange("My Team")
        viewModel.onBillingEmailChange("not-an-email")

        viewModel.events.test {
            viewModel.submit()
            expectNoEvents()
        }
        viewModel.state.value.error shouldBe "Invalid email address"
    }

    // region — submit success

    @Test
    fun submit_withValidInput_emitsProceedToCardAndTogglesLoading() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onNameChange("My Team")
        viewModel.onBillingEmailChange("a@b.com")

        viewModel.events.test {
            viewModel.submit()
            awaitItem() shouldBe CreateTeamOrClubEvent.ProceedToCard(fakeBilling.createSelfServeResult.getOrThrow())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.state.value.isLoading shouldBe false
    }

    @Test
    fun submit_withValidInput_passesFieldsToBillingRepository() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onKindChange("club")
        viewModel.onNameChange("My Club")
        viewModel.onSportTypeChange("soccer")
        viewModel.onLocationChange("Zurich")
        viewModel.onBillingEmailChange("a@b.com")

        viewModel.events.test {
            viewModel.submit()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        fakeBilling.lastCreateSelfServeKind shouldBe "club"
        fakeBilling.lastCreateSelfServeName shouldBe "My Club"
        fakeBilling.lastCreateSelfServeSportType shouldBe "soccer"
        fakeBilling.lastCreateSelfServeLocation shouldBe "Zurich"
        fakeBilling.lastCreateSelfServeBillingEmail shouldBe "a@b.com"
    }

    // region — submit failure

    @Test
    fun submit_withBillingRepositoryFailure_setsErrorAndEmitsNoEvent() = runTest(testDispatcher) {
        fakeBilling.createSelfServeResult = Result.failure(Exception("Something went wrong"))
        createViewModel()
        viewModel.onNameChange("My Team")
        viewModel.onBillingEmailChange("a@b.com")

        viewModel.events.test {
            viewModel.submit()
            expectNoEvents()
        }
        viewModel.state.value.error shouldBe "Something went wrong"
        viewModel.state.value.isLoading shouldBe false
    }
}
