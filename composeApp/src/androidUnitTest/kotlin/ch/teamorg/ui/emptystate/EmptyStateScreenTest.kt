package ch.teamorg.ui.emptystate

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import ch.teamorg.ui.TestActivity
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import ch.teamorg.domain.AuthUser
import ch.teamorg.repository.AuthRepository
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
class EmptyStateScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    private fun launchScreen(
        authRepository: AuthRepository = FakeAuthRepository(),
        onNavigateToCreateTeamOrClub: () -> Unit = {},
        onNavigateToInvite: (String) -> Unit = {},
    ): EmptyStateViewModel {
        val viewModel = EmptyStateViewModel(authRepository = authRepository, inviteRepository = FakeInviteRepository())
        composeTestRule.setContent {
            EmptyStateScreen(
                viewModel = viewModel,
                onNavigateToCreateTeamOrClub = onNavigateToCreateTeamOrClub,
                onNavigateToInvite = onNavigateToInvite,
            )
        }
        return viewModel
    }

    @Test
    fun emptyStateScreen_showsWelcomeText() {
        launchScreen()

        composeTestRule.onNodeWithText("Welcome to Teamorg").assertIsDisplayed()
    }

    @Test
    fun emptyStateScreen_showsInviteLinkField() {
        launchScreen()

        composeTestRule.onNodeWithTag("tf_invite_link").assertIsDisplayed()
    }

    @Test
    fun emptyStateScreen_showsJoinTeamButton() {
        launchScreen()

        composeTestRule.onNodeWithTag("btn_join_team").assertIsDisplayed()
    }

    @Test
    fun emptyStateScreen_showsCreateTeamOrClubButton_forSuperAdmin() {
        val superAdmin = FakeAuthRepository().apply {
            getMeResult = Result.success(
                AuthUser(
                    userId = "admin-1",
                    email = "admin@test.com",
                    displayName = "Admin",
                    avatarUrl = null,
                    isSuperAdmin = true
                )
            )
        }
        launchScreen(authRepository = superAdmin)

        composeTestRule.onNodeWithTag("btn_create_team_or_club").assertIsDisplayed()
    }

    @Test
    fun emptyStateScreen_showsCreateTeamOrClubButton_forNonSuperAdmin() {
        launchScreen()

        composeTestRule.onNodeWithTag("btn_create_team_or_club").assertIsDisplayed()
    }

    @Test
    fun emptyStateScreen_showsCopyProfileLinkButton() {
        launchScreen()

        composeTestRule.onNodeWithTag("btn_copy_profile_link").assertIsDisplayed()
    }
}
