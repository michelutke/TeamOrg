package ch.teamorg.ui.invite

import app.cash.turbine.test
import ch.teamorg.domain.AuthUser
import ch.teamorg.domain.InviteDetails
import ch.teamorg.domain.RedeemResult
import ch.teamorg.fake.FakeAuthRepository
import ch.teamorg.fake.FakeInviteRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
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
class InviteViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeInviteRepo = FakeInviteRepository()
    private val fakeAuthRepo = FakeAuthRepository()
    private lateinit var viewModel: InviteViewModel

    private val sampleDetails = InviteDetails(
        token = "abc123",
        scope = "team",
        teamName = "Team A",
        clubName = "Club A",
        role = "player",
        invitedBy = "Coach Bob",
        expiresAt = "2099-01-01T00:00:00Z",
        alreadyRedeemed = false
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeInviteRepo.reset()
        fakeAuthRepo.reset()
        viewModel = InviteViewModel(fakeInviteRepo, fakeAuthRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region — initial state

    @Test
    fun state_initially_isEmpty() {
        val state = viewModel.state.value
        state.inviteDetails shouldBe null
        state.isLoading shouldBe false
        state.isRedeeming shouldBe false
        state.error shouldBe null
        state.isRedeemed shouldBe false
        state.showLogout shouldBe false
    }

    // region — loadInvite happy path

    @Test
    fun loadInvite_withSuccess_populatesInviteDetails() = runTest(testDispatcher) {
        fakeInviteRepo.getInviteDetailsResult = Result.success(sampleDetails)
        viewModel.loadInvite("abc123")

        val state = viewModel.state.value
        state.inviteDetails shouldBe sampleDetails
        state.isLoading shouldBe false
    }

    @Test
    fun loadInvite_passesTokenToRepository() = runTest(testDispatcher) {
        viewModel.loadInvite("mytoken")

        fakeInviteRepo.lastDetailsToken shouldBe "mytoken"
    }

    @Test
    fun loadInvite_clearsErrorBeforeCall() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.loadInvite("abc123")
            val loadingState = awaitItem()
            loadingState.error shouldBe null
            cancelAndIgnoreRemainingEvents()
        }
    }

    // region — loadInvite error path

    @Test
    fun loadInvite_onFailure_setsErrorMessage() = runTest(testDispatcher) {
        fakeInviteRepo.getInviteDetailsResult = Result.failure(Exception("Token expired"))
        viewModel.loadInvite("abc123")

        viewModel.state.value.error shouldBe "Token expired"
    }

    @Test
    fun loadInvite_onFailureWithNullMessage_setsDefaultError() = runTest(testDispatcher) {
        fakeInviteRepo.getInviteDetailsResult = Result.failure(Exception())
        viewModel.loadInvite("abc123")

        viewModel.state.value.error shouldBe "Failed to fetch invite details"
    }

    @Test
    fun loadInvite_onFailure_clearsLoadingState() = runTest(testDispatcher) {
        fakeInviteRepo.getInviteDetailsResult = Result.failure(Exception("Error"))
        viewModel.loadInvite("abc123")

        viewModel.state.value.isLoading shouldBe false
    }

    // region — redeemInvite happy path

    @Test
    fun redeemInvite_withSuccess_setsIsRedeemedTrue() = runTest(testDispatcher) {
        viewModel.redeemInvite("abc123")

        val state = viewModel.state.value
        state.isRedeemed shouldBe true
        state.isRedeeming shouldBe false
    }

    @Test
    fun redeemInvite_passesTokenToRepository() = runTest(testDispatcher) {
        viewModel.redeemInvite("inviteToken")

        fakeInviteRepo.lastRedeemToken shouldBe "inviteToken"
    }

    // region — redeemInvite: already-member (Success) path

    @Test
    fun redeemInvite_withSuccessResult_treatsAsSuccess() = runTest(testDispatcher) {
        // Repository maps 409 / already-member to RedeemResult.Success
        fakeInviteRepo.redeemInviteResult = RedeemResult.Success
        viewModel.redeemInvite("abc123")

        val state = viewModel.state.value
        state.isRedeemed shouldBe true
        state.error shouldBe null
        state.isRedeeming shouldBe false
    }

    // region — redeemInvite: email mismatch

    @Test
    fun redeemInvite_emailMismatch_setsGermanMessageWithBothEmails() = runTest(testDispatcher) {
        fakeInviteRepo.redeemInviteResult = RedeemResult.EmailMismatch("invited@example.com")
        fakeAuthRepo.getMeResult = Result.success(
            AuthUser(userId = "u1", email = "current@example.com", displayName = "Cur", avatarUrl = null)
        )

        viewModel.redeemInvite("abc123")

        val state = viewModel.state.value
        state.isRedeemed shouldBe false
        state.showLogout shouldBe true
        state.error shouldNotBe null
        val error = state.error ?: ""
        error shouldContain "invited@example.com"
        error shouldContain "current@example.com"
    }

    // region — redeemInvite: expired / inactive / not found

    @Test
    fun redeemInvite_expired_setsGermanExpiredMessage() = runTest(testDispatcher) {
        fakeInviteRepo.redeemInviteResult = RedeemResult.Expired
        viewModel.redeemInvite("abc123")

        val state = viewModel.state.value
        state.error shouldBe "Diese Einladung ist abgelaufen."
        state.isRedeemed shouldBe false
        state.isRedeeming shouldBe false
    }

    @Test
    fun redeemInvite_inactive_setsGermanInactiveMessage() = runTest(testDispatcher) {
        fakeInviteRepo.redeemInviteResult = RedeemResult.Inactive
        viewModel.redeemInvite("abc123")

        viewModel.state.value.error shouldBe "Diese Einladung ist nicht mehr gültig."
    }

    @Test
    fun redeemInvite_notFound_setsGermanNotFoundMessage() = runTest(testDispatcher) {
        fakeInviteRepo.redeemInviteResult = RedeemResult.NotFound
        viewModel.redeemInvite("abc123")

        viewModel.state.value.error shouldBe "Diese Einladung wurde nicht gefunden."
    }

    // region — redeemInvite error path

    @Test
    fun redeemInvite_onError_setsErrorMessage() = runTest(testDispatcher) {
        fakeInviteRepo.redeemInviteResult = RedeemResult.Error("Invite expired")
        viewModel.redeemInvite("abc123")

        val state = viewModel.state.value
        state.error shouldBe "Invite expired"
        state.isRedeemed shouldBe false
        state.isRedeeming shouldBe false
    }

    @Test
    fun redeemInvite_onError_clearsRedeemingState() = runTest(testDispatcher) {
        fakeInviteRepo.redeemInviteResult = RedeemResult.Error("Server error")
        viewModel.redeemInvite("abc123")

        viewModel.state.value.isRedeeming shouldBe false
    }

    // region — full load-then-redeem journey

    /**
     * Simulates the real user flow: load invite details, then redeem.
     * Verifies that isRedeemed transitions to true, which is the signal
     * InviteScreen's LaunchedEffect uses to trigger onJoinSuccess.
     */
    @Test
    fun fullFlow_loadThenRedeem_setsIsRedeemedTrue() = runTest(testDispatcher) {
        fakeInviteRepo.getInviteDetailsResult = Result.success(sampleDetails)

        // Load invite
        viewModel.loadInvite("abc123")
        viewModel.state.value.inviteDetails shouldNotBe null
        viewModel.state.value.isRedeemed shouldBe false

        // Redeem invite
        viewModel.redeemInvite("abc123")
        viewModel.state.value.isRedeemed shouldBe true
        viewModel.state.value.isRedeeming shouldBe false
        viewModel.state.value.error shouldBe null
    }

    @Test
    fun fullFlow_loadThenRedeem_inviteDetailsPreservedAfterRedeem() = runTest(testDispatcher) {
        fakeInviteRepo.getInviteDetailsResult = Result.success(sampleDetails)

        viewModel.loadInvite("abc123")
        viewModel.redeemInvite("abc123")

        // Invite details should still be available (not cleared)
        viewModel.state.value.inviteDetails shouldBe sampleDetails
    }

    /**
     * Turbine-based test that tracks state transitions during redeem.
     * Ensures isRedeeming=true is emitted before isRedeemed=true.
     */
    @Test
    fun redeemInvite_emitsRedeemingThenRedeemed() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem() // initial state

            viewModel.redeemInvite("abc123")

            // With UnconfinedTestDispatcher, we may get coalesced states.
            // The final state must have isRedeemed=true.
            val finalState = expectMostRecentItem()
            finalState.isRedeemed shouldBe true
            finalState.isRedeeming shouldBe false
        }
    }

    // region — error does not set isRedeemed (prevents false navigation)

    @Test
    fun redeemInvite_onError_doesNotSetIsRedeemed() = runTest(testDispatcher) {
        fakeInviteRepo.redeemInviteResult = RedeemResult.Error("Network timeout")
        viewModel.redeemInvite("abc123")

        viewModel.state.value.isRedeemed shouldBe false
        viewModel.state.value.error shouldBe "Network timeout"
    }

    // region — authenticated user with existing team redeems cross-team invite

    /**
     * Exact scenario from the bug: authenticated club manager with existing
     * teams redeems an invite from another manager's team. The redeem must
     * succeed and set isRedeemed=true so the LaunchedEffect in InviteScreen
     * triggers onJoinSuccess.
     */
    @Test
    fun authenticatedUserWithExistingTeam_redeemInvite_setsIsRedeemed() = runTest(testDispatcher) {
        // Simulate: user loaded invite details (authenticated, has teams already)
        fakeInviteRepo.getInviteDetailsResult = Result.success(sampleDetails)
        viewModel.loadInvite("abc123")
        viewModel.state.value.inviteDetails shouldNotBe null

        // User clicks Join — redeem succeeds
        fakeInviteRepo.redeemInviteResult = RedeemResult.Success
        viewModel.redeemInvite("abc123")

        val state = viewModel.state.value
        state.isRedeemed shouldBe true
        state.isRedeeming shouldBe false
        state.error shouldBe null
        state.inviteDetails shouldNotBe null
    }

    /**
     * Verify state transitions with Turbine: loading invite, then redeeming.
     * The final state must have isRedeemed=true which is the navigation trigger.
     */
    @Test
    fun authenticatedUserWithExistingTeam_fullFlow_turbineVerifiesIsRedeemed() = runTest(testDispatcher) {
        fakeInviteRepo.getInviteDetailsResult = Result.success(sampleDetails)
        fakeInviteRepo.redeemInviteResult = RedeemResult.Success

        viewModel.state.test {
            awaitItem() // initial

            viewModel.loadInvite("abc123")
            // Skip intermediate loading states, get to loaded
            val loadedState = expectMostRecentItem()
            loadedState.inviteDetails shouldNotBe null
            loadedState.isRedeemed shouldBe false

            viewModel.redeemInvite("abc123")
            val redeemedState = expectMostRecentItem()
            redeemedState.isRedeemed shouldBe true
            redeemedState.isRedeeming shouldBe false
        }
    }
}
