package ch.teamorg

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Register and login driven through the real UI against a live backend on
 * http://10.0.2.2:8080. Requires the server to be running.
 */
@RunWith(AndroidJUnit4::class)
class AuthFlowTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val password = "instrumented-pw-1" // gitleaks:allow (disposable test-fixture password, not a real credential)

    /** Unique per run so repeated runs never collide on the email-uniqueness constraint. */
    private fun uniqueEmail(prefix: String) = "$prefix-${System.currentTimeMillis()}@example.com"

    private fun awaitTag(tag: String, timeoutMillis: Long = 30_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun register_newAccount_landsOnEmptyState() {
        TestPrefs.clear()
        val email = uniqueEmail("register")

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitTag("btn_navigate_register")
            composeRule.onNodeWithTag("btn_navigate_register").performClick()

            awaitTag("tf_display_name")
            composeRule.onNodeWithTag("tf_display_name").performTextInput("Instrumented Coach")
            composeRule.onNodeWithTag("tf_email").performTextInput(email)
            composeRule.onNodeWithTag("tf_password").performTextInput(password)
            composeRule.onNodeWithTag("tf_confirm_password").performTextInput(password)
            composeRule.onNodeWithTag("btn_create_account").performClick()

            // A brand-new user has no team, so navigation lands on EmptyState.
            awaitTag("btn_create_team_or_club")
            composeRule.onNodeWithTag("btn_create_team_or_club").assertIsDisplayed()
        }
    }

    @Test
    fun login_withSeededAccount_landsOnEmptyState() {
        TestPrefs.clear()
        val email = uniqueEmail("login")
        BackendSeed.register(email, password, "Seeded Coach")

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitTag("btn_sign_in")
            composeRule.onNodeWithTag("tf_email").performTextInput(email)
            composeRule.onNodeWithTag("tf_password").performTextInput(password)
            composeRule.onNodeWithTag("btn_sign_in").performClick()

            awaitTag("btn_create_team_or_club")
            composeRule.onNodeWithTag("btn_create_team_or_club").assertIsDisplayed()
        }
    }
}
