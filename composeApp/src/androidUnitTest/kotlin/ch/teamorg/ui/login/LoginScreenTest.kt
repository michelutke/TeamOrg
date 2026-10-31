package ch.teamorg.ui.login

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import ch.teamorg.ui.TestActivity
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ch.teamorg.ui.MainDispatcherRule
import ch.teamorg.ui.fakes.FakeAuthRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LoginScreenTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    private fun launchScreen(
        onLoginSuccess: () -> Unit = {},
        onNavigateToRegister: () -> Unit = {},
        onNavigateToForgotPassword: () -> Unit = {},
    ): LoginViewModel {
        val viewModel = LoginViewModel(authRepository = FakeAuthRepository())
        composeTestRule.setContent {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = onLoginSuccess,
                onNavigateToRegister = onNavigateToRegister,
                onNavigateToForgotPassword = onNavigateToForgotPassword,
            )
        }
        return viewModel
    }

    @Test
    fun loginScreen_showsEmailPasswordFieldsAndSignInButton() {
        launchScreen()

        composeTestRule.onNodeWithTag("tf_email").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tf_password").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_sign_in").assertIsDisplayed()
    }

    @Test
    fun loginScreen_enteringEmailUpdatesField() {
        launchScreen()

        composeTestRule.onNodeWithTag("tf_email").performTextInput("user@example.com")

        composeTestRule.onNodeWithTag("tf_email").assertIsDisplayed()
    }

    @Test
    fun loginScreen_enteringPasswordUpdatesField() {
        launchScreen()

        composeTestRule.onNodeWithTag("tf_password").performTextInput("password123")

        composeTestRule.onNodeWithTag("tf_password").assertIsDisplayed()
    }

    @Test
    fun loginScreen_emptyEmail_showsValidationError() {
        launchScreen()

        composeTestRule.onNodeWithTag("tf_password").performTextInput("password123")
        composeTestRule.onNodeWithTag("btn_sign_in").performClick()

        composeTestRule.onNodeWithText("Please fill in all fields").assertIsDisplayed()
    }

    @Test
    fun loginScreen_emptyPassword_showsValidationError() {
        launchScreen()

        composeTestRule.onNodeWithTag("tf_email").performTextInput("user@example.com")
        composeTestRule.onNodeWithTag("btn_sign_in").performClick()

        composeTestRule.onNodeWithText("Please fill in all fields").assertIsDisplayed()
    }

    @Test
    fun loginScreen_tappingNavigateToRegister_callsCallback() {
        var navigated = false
        launchScreen(onNavigateToRegister = { navigated = true })

        composeTestRule.onNodeWithTag("btn_navigate_register").performClick()

        assertTrue(navigated)
    }
}
