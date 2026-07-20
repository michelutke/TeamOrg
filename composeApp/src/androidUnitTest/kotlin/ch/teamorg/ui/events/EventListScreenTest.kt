package ch.teamorg.ui.events

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ch.teamorg.domain.Event
import ch.teamorg.domain.EventWithTeams
import ch.teamorg.domain.MatchedTeam
import ch.teamorg.fake.FakeAttendanceRepository
import ch.teamorg.preferences.UserPreferences
import ch.teamorg.ui.MainDispatcherRule
import ch.teamorg.ui.TestActivity
import com.russhwolf.settings.MapSettings
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EventListScreenTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    private val now = Clock.System.now()

    private fun event(
        id: String,
        title: String,
        type: String,
        team: MatchedTeam,
        location: String,
        status: String = "active",
    ): EventWithTeams = EventWithTeams(
        event = Event(
            id = id,
            title = title,
            type = type,
            startAt = now.plus(1, DateTimeUnit.HOUR),
            endAt = now.plus(2, DateTimeUnit.HOUR),
            location = location,
            status = status,
            createdBy = "u-coach",
            createdAt = now,
            updatedAt = now,
        ),
        matchedTeams = listOf(team),
    )

    private val teamA = MatchedTeam(id = "team-a", name = "Team A")
    private val teamB = MatchedTeam(id = "team-b", name = "Team B")

    private fun launchScreen(events: List<EventWithTeams>): EventListViewModel {
        val viewModel = EventListViewModel(
            eventRepository = FakeEventListRepository(events),
            teamRepository = FakeEventListTeamRepo(),
            userPreferences = UserPreferences(MapSettings()),
            attendanceRepository = FakeAttendanceRepository(),
        )
        composeTestRule.setContent {
            EventListScreen(
                viewModel = viewModel,
                onEventClick = {},
                onCreateClick = {},
            )
        }
        return viewModel
    }

    private fun sampleEvents() = listOf(
        event("e1", "Training Monday", "training", teamA, "Gym A"),
        event("e2", "Match Tuesday", "match", teamB, "Stadium"),
        event("e3", "Cancelled Session", "training", teamA, "Gym A", status = "cancelled"),
    )

    @Test
    fun eventList_showsUpcomingEvents() {
        launchScreen(sampleEvents())

        composeTestRule.onNodeWithText("Training Monday").assertIsDisplayed()
        composeTestRule.onNodeWithText("Match Tuesday").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancelled Session").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("event_card").assertCountEquals(3)
    }

    @Test
    fun eventList_filterByTeam_narrowsList() {
        launchScreen(sampleEvents())

        composeTestRule.onNodeWithTag("filter_team_team-b").performClick()

        composeTestRule.onAllNodesWithTag("event_card").assertCountEquals(1)
        composeTestRule.onNodeWithText("Match Tuesday").assertIsDisplayed()
    }

    @Test
    fun eventList_filterByType_narrowsList() {
        launchScreen(sampleEvents())

        composeTestRule.onNodeWithTag("filter_type_match").performClick()

        composeTestRule.onAllNodesWithTag("event_card").assertCountEquals(1)
        composeTestRule.onNodeWithText("Match Tuesday").assertIsDisplayed()
    }

    @Test
    fun eventList_cancelledEvent_showsCancelledChip() {
        launchScreen(sampleEvents())

        composeTestRule.onNodeWithText("Cancelled").assertIsDisplayed()
    }
}
