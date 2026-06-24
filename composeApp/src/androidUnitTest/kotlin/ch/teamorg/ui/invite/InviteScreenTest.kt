package ch.teamorg.ui.invite

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import ch.teamorg.ui.TestActivity
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import ch.teamorg.domain.InviteDetails
import ch.teamorg.ui.MainDispatcherRule
import ch.teamorg.ui.fakes.FakeAuthRepository
import ch.teamorg.ui.fakes.FakeInviteRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InviteScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    private val testToken = "test-token-123"

    private val fakeInviteDetails = InviteDetails(
        token = testToken,
        scope = "team",
        teamName = "FC Zurich U21",
        clubName = "FC Zurich",
        role = "player",
        invitedBy = "Coach Hans",
        expiresAt = "2026-12-31",
        alreadyRedeemed = false,
    )

    private fun launchScreen(
        fakeRepo: FakeInviteRepository = FakeInviteRepository(),
        isLoggedIn: Boolean = false,
        onNavigateToLogin: (String) -> Unit = {},
        onNavigateToRegister: (InviteDetails) -> Unit = {},
        onJoinSuccess: () -> Unit = {},
        onLogout: () -> Unit = {},
    ): InviteViewModel {
        val viewModel = InviteViewModel(
            inviteRepository = fakeRepo,
            authRepository = FakeAuthRepository(),
        )
        composeTestRule.setContent {
            InviteScreen(
                token = testToken,
                viewModel = viewModel,
                isLoggedIn = isLoggedIn,
                onNavigateToLogin = onNavigateToLogin,
                onNavigateToRegister = onNavigateToRegister,
                onJoinSuccess = onJoinSuccess,
                onLogout = onLogout,
            )
        }
        return viewModel
    }

    @Test
    fun inviteScreen_failedLoad_showsRetryButton() {
        val fakeRepo = FakeInviteRepository().apply {
            getInviteDetailsResult = Result.failure(RuntimeException("Network error"))
        }
        launchScreen(fakeRepo = fakeRepo)

        composeTestRule.onNodeWithTag("btn_retry").assertIsDisplayed()
    }

    @Test
    fun inviteScreen_loadedState_showsJoinButtonWithTeamName() {
        val fakeRepo = FakeInviteRepository().apply {
            getInviteDetailsResult = Result.success(fakeInviteDetails)
        }
        launchScreen(fakeRepo = fakeRepo, isLoggedIn = true)

        composeTestRule.onNodeWithText("FC Zurich U21").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_join_team").assertIsDisplayed()
    }

    @Test
    fun inviteScreen_unauthenticatedState_showsCreateAccountAndLoginButtons() {
        val fakeRepo = FakeInviteRepository().apply {
            getInviteDetailsResult = Result.success(fakeInviteDetails)
        }
        launchScreen(fakeRepo = fakeRepo, isLoggedIn = false)

        composeTestRule.onNodeWithTag("btn_create_account_to_join").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_login_to_join").assertIsDisplayed()
    }

    @Test
    fun inviteScreen_authenticatedUser_clickJoin_triggersOnJoinSuccess() {
        var joinSuccessCalled = false
        val fakeRepo = FakeInviteRepository().apply {
            getInviteDetailsResult = Result.success(fakeInviteDetails)
            redeemInviteResult = ch.teamorg.domain.RedeemResult.Success
        }
        launchScreen(
            fakeRepo = fakeRepo,
            isLoggedIn = true,
            onJoinSuccess = { joinSuccessCalled = true }
        )

        composeTestRule.onNodeWithTag("btn_join_team").performClick()
        composeTestRule.waitForIdle()

        assert(joinSuccessCalled) { "onJoinSuccess must be called after successful redeem" }
    }
}
