package ch.teamorg

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start smoke test. Proves the Koin graph resolves, OneSignal init in
 * TeamorgApplication.onCreate survives, and the first screen composes.
 *
 * Deliberately backend-free: with no stored token AuthViewModel short-circuits to
 * Unauthenticated without a network call, so this test stays fast and non-flaky.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    // Empty rule (not createAndroidComposeRule) so prefs can be cleared before launch.
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun coldStart_withNoStoredToken_showsLoginScreen() {
        TestPrefs.clear()

        ActivityScenario.launch(MainActivity::class.java).use {
            // The splash animates before the login screen appears.
            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.onAllNodesWithTag("btn_sign_in").fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithTag("tf_email").assertIsDisplayed()
            composeRule.onNodeWithTag("tf_password").assertIsDisplayed()
            composeRule.onNodeWithTag("btn_sign_in").assertIsDisplayed()
            composeRule.onNodeWithTag("btn_navigate_register").assertIsDisplayed()
        }
    }
}
