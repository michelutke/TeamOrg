package ch.teamorg.ui.selfserve

import app.cash.turbine.test
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
class CardSetupViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeBillingRepo = FakeBillingRepository()
    private lateinit var viewModel: CardSetupViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeBillingRepo.reset()
        viewModel = CardSetupViewModel(fakeBillingRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun confirm_success_emitsDoneEvent() = runTest(testDispatcher) {
        viewModel.events.test {
            viewModel.confirm("club1", "seti_1AbC2dEf_secret_XyZ789")
            val event = awaitItem()
            event shouldBe CardSetupEvent.Done
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirm_success_clearsLoading() = runTest(testDispatcher) {
        viewModel.confirm("club1", "seti_1AbC2dEf_secret_XyZ789")

        viewModel.state.value.isLoading shouldBe false
    }

    @Test
    fun confirm_failure_setsErrorMessage() = runTest(testDispatcher) {
        fakeBillingRepo.confirmBillingResult = Result.failure(Exception("Card declined"))
        viewModel.confirm("club1", "seti_1AbC2dEf_secret_XyZ789")

        val state = viewModel.state.value
        state.error shouldBe "Card declined"
        state.isLoading shouldBe false
    }

    @Test
    fun confirm_failureWithNullMessage_setsDefaultError() = runTest(testDispatcher) {
        fakeBillingRepo.confirmBillingResult = Result.failure(Exception())
        viewModel.confirm("club1", "seti_1AbC2dEf_secret_XyZ789")

        viewModel.state.value.error shouldBe "Failed to confirm card setup"
    }

    @Test
    fun confirm_derivesSetupIntentIdFromClientSecret() = runTest(testDispatcher) {
        viewModel.confirm("club1", "seti_1AbC2dEf_secret_XyZ789")

        fakeBillingRepo.lastConfirmBillingSetupIntentId shouldBe "seti_1AbC2dEf"
    }

    @Test
    fun confirm_passesClubIdToRepository() = runTest(testDispatcher) {
        viewModel.confirm("club42", "seti_1AbC2dEf_secret_XyZ789")

        fakeBillingRepo.lastConfirmBillingClubId shouldBe "club42"
    }
}
