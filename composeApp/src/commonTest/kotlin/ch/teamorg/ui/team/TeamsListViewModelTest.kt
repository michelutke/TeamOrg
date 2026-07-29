package ch.teamorg.ui.team

import ch.teamorg.domain.Club
import ch.teamorg.domain.ClubRoleEntry
import ch.teamorg.domain.UserRoles
import ch.teamorg.fake.FakeClubRepository
import ch.teamorg.fake.FakeTeamRepository
import io.kotest.matchers.shouldBe
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
class TeamsListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeClubRepo = FakeClubRepository()
    private val fakeTeamRepo = FakeTeamRepository()
    private lateinit var viewModel: TeamsListViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClubRepo.reset()
        fakeTeamRepo.reset()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadTeams_clubFrozen_exposesFrozenBillingStatusOnState() = runTest(testDispatcher) {
        fakeTeamRepo.getMyRolesResult = Result.success(
            UserRoles(clubRoles = listOf(ClubRoleEntry(clubId = "club1", role = "club_manager")))
        )
        fakeClubRepo.getClubResult = Result.success(
            Club(id = "club1", name = "X", logoUrl = null, sportType = "volleyball", billingStatus = "frozen")
        )

        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)
        viewModel.loadTeams()

        viewModel.state.value.club?.billingStatus shouldBe "frozen"
    }
}
