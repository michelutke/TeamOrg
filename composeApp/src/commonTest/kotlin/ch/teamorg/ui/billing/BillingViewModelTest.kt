package ch.teamorg.ui.billing

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
class BillingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeBillingRepo = FakeBillingRepository()
    private lateinit var viewModel: BillingViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeBillingRepo.reset()
        viewModel = BillingViewModel(fakeBillingRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_ownerSuccess_setsBillingInfo() = runTest(testDispatcher) {
        viewModel.load("club1")

        val state = viewModel.state.value
        state.billingInfo shouldBe fakeBillingRepo.getBillingResult.getOrNull()
        state.notOwner shouldBe false
        state.isLoading shouldBe false
    }

    @Test
    fun load_forbiddenFailure_setsNotOwnerState() = runTest(testDispatcher) {
        fakeBillingRepo.getBillingResult = Result.failure(Exception("Failed to fetch billing: 403 Forbidden"))
        viewModel.load("club1")

        viewModel.state.value.notOwner shouldBe true
    }

    @Test
    fun load_otherFailure_setsErrorMessage() = runTest(testDispatcher) {
        fakeBillingRepo.getBillingResult = Result.failure(Exception("Failed to fetch billing: 500 Internal Server Error"))
        viewModel.load("club1")

        val state = viewModel.state.value
        state.error shouldBe "Failed to fetch billing: 500 Internal Server Error"
        state.notOwner shouldBe false
    }

    @Test
    fun convert_clubKind_targetsTeam() = runTest(testDispatcher) {
        fakeBillingRepo.getBillingResult = Result.success(
            fakeBillingRepo.getBillingResult.getOrThrow().copy(kind = "club")
        )
        viewModel.load("club1")
        viewModel.convert()

        fakeBillingRepo.lastConvertTargetKind shouldBe "team"
    }

    @Test
    fun convert_teamKind_targetsClub() = runTest(testDispatcher) {
        fakeBillingRepo.getBillingResult = Result.success(
            fakeBillingRepo.getBillingResult.getOrThrow().copy(kind = "team")
        )
        viewModel.load("club1")
        viewModel.convert()

        fakeBillingRepo.lastConvertTargetKind shouldBe "club"
    }

    @Test
    fun convert_success_reloadsBillingInfo() = runTest(testDispatcher) {
        viewModel.load("club1")
        viewModel.convert()

        fakeBillingRepo.lastConvertClubId shouldBe "club1"
        viewModel.state.value.error shouldBe null
        viewModel.state.value.billingInfo shouldBe fakeBillingRepo.getBillingResult.getOrNull()
    }

    @Test
    fun convert_conflictFailure_setsConvertBlockedMessage() = runTest(testDispatcher) {
        viewModel.load("club1")
        fakeBillingRepo.convertResult = Result.failure(Exception("Failed to convert club: 409 Conflict"))
        viewModel.convert()

        viewModel.state.value.error shouldBe "Only possible with exactly one active team."
    }

    @Test
    fun updateCard_success_emitsPresentCardSheetEvent() = runTest(testDispatcher) {
        viewModel.load("club1")

        viewModel.events.test {
            viewModel.updateCard()
            val event = awaitItem()
            event shouldBe BillingEvent.PresentCardSheet(
                publishableKey = fakeBillingRepo.startCardUpdateResult.getOrThrow().publishableKey,
                clientSecret = fakeBillingRepo.startCardUpdateResult.getOrThrow().setupIntentClientSecret
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateCard_failure_setsErrorMessage() = runTest(testDispatcher) {
        viewModel.load("club1")
        fakeBillingRepo.startCardUpdateResult = Result.failure(Exception("Failed to start card update: 500"))
        viewModel.updateCard()

        viewModel.state.value.error shouldBe "Failed to start card update: 500"
    }

    @Test
    fun onCardSetupCompleted_derivesSetupIntentIdAndReloads() = runTest(testDispatcher) {
        viewModel.load("club1")
        viewModel.onCardSetupCompleted("seti_1AbC2dEf_secret_XyZ789")

        fakeBillingRepo.lastConfirmBillingSetupIntentId shouldBe "seti_1AbC2dEf"
        fakeBillingRepo.lastConfirmBillingClubId shouldBe "club1"
        viewModel.state.value.billingInfo shouldBe fakeBillingRepo.getBillingResult.getOrNull()
    }

    @Test
    fun updateCard_success_clearsIsUpdatingCardFlag() = runTest(testDispatcher) {
        viewModel.load("club1")
        viewModel.updateCard()

        viewModel.state.value.isUpdatingCard shouldBe false
    }

    @Test
    fun updateCard_failure_clearsIsUpdatingCardFlag() = runTest(testDispatcher) {
        viewModel.load("club1")
        fakeBillingRepo.startCardUpdateResult = Result.failure(Exception("Failed to start card update: 500"))
        viewModel.updateCard()

        viewModel.state.value.isUpdatingCard shouldBe false
    }

    @Test
    fun convert_success_clearsIsConvertingFlag() = runTest(testDispatcher) {
        viewModel.load("club1")
        viewModel.convert()

        viewModel.state.value.isConverting shouldBe false
    }

    @Test
    fun convert_conflictFailure_clearsIsConvertingFlag() = runTest(testDispatcher) {
        viewModel.load("club1")
        fakeBillingRepo.convertResult = Result.failure(Exception("Failed to convert club: 409 Conflict"))
        viewModel.convert()

        viewModel.state.value.isConverting shouldBe false
    }
}
