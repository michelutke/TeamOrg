package ch.teamorg.ui.events

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class CalendarViewTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    private val now = Clock.System.now()

    private fun launchScreen(): EventListViewModel {
        val events = listOf(
            EventWithTeams(
                event = Event(
                    id = "e1",
                    title = "Training Monday",
                    type = "training",
                    startAt = now.plus(1, DateTimeUnit.HOUR),
                    endAt = now.plus(2, DateTimeUnit.HOUR),
                    location = "Gym A",
                    status = "active",
                    createdBy = "u-coach",
                    createdAt = now,
                    updatedAt = now,
                ),
                matchedTeams = listOf(MatchedTeam(id = "team-a", name = "Team A")),
            ),
        )
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

    @Test
    fun viewMode_defaultsToList_calendarNavigationHidden() {
        launchScreen()

        composeTestRule.onNodeWithText("Training Monday").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next month").assertDoesNotExist()
    }

    @Test
    fun viewMode_tapCalendar_showsCalendarView() {
        launchScreen()

        composeTestRule.onNodeWithTag("btn_view_calendar").performClick()

        // Calendar month navigation is only present in calendar mode.
        composeTestRule.onNodeWithContentDescription("Next month").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Previous month").assertIsDisplayed()
    }

    @Test
    fun viewMode_tapCalendarThenList_switchesBack() {
        launchScreen()

        composeTestRule.onNodeWithTag("btn_view_calendar").performClick()
        composeTestRule.onNodeWithContentDescription("Next month").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_view_list").performClick()

        composeTestRule.onNodeWithContentDescription("Next month").assertDoesNotExist()
        composeTestRule.onNodeWithText("Training Monday").assertIsDisplayed()
    }
}
