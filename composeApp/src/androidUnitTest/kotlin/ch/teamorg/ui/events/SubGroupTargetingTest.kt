package ch.teamorg.ui.events

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ch.teamorg.domain.SubGroup
import ch.teamorg.domain.Team
import ch.teamorg.domain.UserRoles
import ch.teamorg.ui.MainDispatcherRule
import ch.teamorg.ui.TestActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubGroupTargetingTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    private fun launchScreen(): CreateEditEventViewModel {
        val viewModel = CreateEditEventViewModel(
            eventRepository = FakeEventListRepository(
                events = emptyList(),
                subGroupsByTeam = mapOf(
                    "team-a" to listOf(
                        SubGroup(id = "sg1", teamId = "team-a", name = "Starters", memberCount = 5),
                    ),
                ),
            ),
            clubRepository = FakeEventListClubRepo(
                teamsByClub = mapOf(
                    "club1" to listOf(Team(id = "team-a", clubId = "club1", name = "Team A")),
                ),
            ),
            teamRepository = FakeEventListTeamRepo(
                roles = UserRoles(teamRoles = listOf(teamRole("team-a", "club1"))),
            ),
        )
        composeTestRule.setContent {
            CreateEditEventScreen(
                viewModel = viewModel,
                onBack = {},
                onSaved = {},
            )
        }
        return viewModel
    }

    @Test
    fun createForm_selectingTeam_surfacesTeamSubgroupsInSheet() {
        launchScreen()

        // Team loaded from club roles.
        composeTestRule.onNodeWithText("Team A").assertIsDisplayed()

        // Select the team → its subgroups become available.
        composeTestRule.onNodeWithText("Team A").performClick()

        // Open the sub-groups sheet.
        composeTestRule.onNodeWithTag("field_subgroups").performClick()

        composeTestRule.onNodeWithTag("subgroup_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Starters").assertIsDisplayed()
    }

    @Test
    fun createForm_selectingSubgroup_reflectsInFieldLabel() {
        launchScreen()

        composeTestRule.onNodeWithText("Team A").performClick()
        composeTestRule.onNodeWithTag("field_subgroups").performClick()

        // Select the subgroup, then close the sheet.
        composeTestRule.onNodeWithText("Starters").performClick()
        composeTestRule.onNodeWithText("Done").performClick()

        // The sub-groups field label now reflects the selection.
        composeTestRule.onNodeWithText("Starters").assertIsDisplayed()
    }
}
