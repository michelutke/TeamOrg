package ch.teamorg.ui.emptystate

import app.cash.turbine.test
import ch.teamorg.domain.AuthUser
import ch.teamorg.fake.FakeAuthRepository
import ch.teamorg.fake.FakeInviteRepository
import io.kotest.matchers.shouldBe
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
class EmptyStateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeAuth = FakeAuthRepository()
    private val fakeInvite = FakeInviteRepository()
    private lateinit var viewModel: EmptyStateViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuth.reset()
        fakeInvite.reset()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = EmptyStateViewModel(fakeAuth, fakeInvite).also { viewModel = it }

    // region — init: profile link loading

    @Test
    fun init_withGetMeSuccess_setsProfileLink() = runTest(testDispatcher) {
        fakeAuth.getMeResult = Result.success(
            AuthUser(userId = "user42", email = "a@b.com", displayName = "Alice", avatarUrl = null)
        )
        createViewModel()

        viewModel.state.value.profileLink shouldBe "teamorg://invite/player/user42"
    }

    @Test
    fun init_profileLink_containsUserId() = runTest(testDispatcher) {
        fakeAuth.getMeResult = Result.success(
            AuthUser(userId = "abc-xyz", email = "a@b.com", displayName = "Alice", avatarUrl = null)
        )
        createViewModel()

        viewModel.state.value.profileLink shouldContain "abc-xyz"
    }

    @Test
    fun init_withGetMeFailure_profileLinkRemainsEmpty() = runTest(testDispatcher) {
        fakeAuth.getMeResult = Result.failure(Exception("Not authenticated"))
        createViewModel()

        viewModel.state.value.profileLink shouldBe ""
    }

    // region — field updates

    @Test
    fun onInviteLinkChange_updatesInviteLinkAndClearsError() = runTest(testDispatcher) {
        createViewModel()
        viewModel.state.test {
            awaitItem() // initial (may include profile link emission)

            viewModel.onInviteLinkChange("teamorg://invite/team/abc")
            val state = awaitItem()
            state.inviteLink shouldBe "teamorg://invite/team/abc"
            state.error shouldBe null

            cancelAndIgnoreRemainingEvents()
        }
    }

    // region — onJoinTeamClick

    @Test
    fun onJoinTeamClick_withBlankLink_setsError() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onJoinTeamClick()

        viewModel.state.value.error shouldBe "Please paste an invite link"
    }

    @Test
    fun onJoinTeamClick_withValidLink_emitsNavigateToInvite() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onInviteLinkChange("teamorg://invite/team/abc123")
        viewModel.events.test {
            viewModel.onJoinTeamClick()
            awaitItem() shouldBe EmptyStateEvent.NavigateToInvite("abc123")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onJoinTeamClick_withPlainToken_emitsNavigateToInvite() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onInviteLinkChange("abc123")
        viewModel.events.test {
            viewModel.onJoinTeamClick()
            awaitItem() shouldBe EmptyStateEvent.NavigateToInvite("abc123")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onJoinTeamClick_withEightCharCode_resolvesViaGetInviteByCodeAndEmitsNavigateToInvite() = runTest(testDispatcher) {
        fakeInvite.getInviteByCodeResult = Result.success(
            ch.teamorg.domain.InviteDetails(
                token = "resolved-token",
                scope = "team",
                teamName = "Team A",
                clubName = "Club A",
                role = "player",
                invitedBy = "Coach",
                expiresAt = "2099-01-01T00:00:00Z",
                alreadyRedeemed = false
            )
        )
        createViewModel()
        viewModel.onInviteLinkChange("ABCD1234")
        viewModel.events.test {
            viewModel.onJoinTeamClick()
            awaitItem() shouldBe EmptyStateEvent.NavigateToInvite("resolved-token")
            cancelAndIgnoreRemainingEvents()
        }
        fakeInvite.lastCodeLookup shouldBe "ABCD1234"
    }

    @Test
    fun onJoinTeamClick_withEightCharCode_onFailure_setsErrorAndEmitsNoEvent() = runTest(testDispatcher) {
        fakeInvite.getInviteByCodeResult = Result.failure(Exception("not found"))
        createViewModel()
        viewModel.onInviteLinkChange("ABCD1234")
        viewModel.events.test {
            viewModel.onJoinTeamClick()
            expectNoEvents()
        }
        viewModel.state.value.error shouldBe "Invalid invite code"
    }

    // region — onCreateClubClick

    @Test
    fun onCreateClubClick_emitsNavigateToCreateTeamOrClubEvent() = runTest(testDispatcher) {
        createViewModel()
        viewModel.events.test {
            viewModel.onCreateClubClick()
            awaitItem() shouldBe EmptyStateEvent.NavigateToCreateTeamOrClub
            cancelAndIgnoreRemainingEvents()
        }
    }

    // region — onProfileLinkCopied

    @Test
    fun onProfileLinkCopied_setsInfoMessage() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onProfileLinkCopied()

        viewModel.state.value.infoMessage shouldBe "Link copied to clipboard"
    }

    // region — dismissMessages

    @Test
    fun dismissMessages_clearsErrorAndInfoMessage() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onProfileLinkCopied() // sets infoMessage
        viewModel.dismissMessages()

        val state = viewModel.state.value
        state.error shouldBe null
        state.infoMessage shouldBe null
    }

    @Test
    fun dismissMessages_withOnlyError_clearsError() = runTest(testDispatcher) {
        createViewModel()
        // manually trigger an error-like scenario via field update + check dismissal
        viewModel.onInviteLinkChange("link")
        viewModel.dismissMessages()

        viewModel.state.value.error shouldBe null
    }
}
