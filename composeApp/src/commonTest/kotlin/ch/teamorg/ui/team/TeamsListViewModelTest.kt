package ch.teamorg.ui.team

import ch.teamorg.domain.Club
import ch.teamorg.domain.ClubRoleEntry
import ch.teamorg.domain.Team
import ch.teamorg.domain.TeamRoleEntry
import ch.teamorg.domain.UserRoles
import ch.teamorg.fake.FakeClubRepository
import ch.teamorg.fake.FakeTeamRepository
import io.kotest.matchers.collections.shouldHaveSize
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

    private fun givenManagerOfClub1(vararg teams: Team) {
        fakeTeamRepo.getMyRolesResult = Result.success(
            UserRoles(clubRoles = listOf(ClubRoleEntry(clubId = "club1", role = "club_manager")))
        )
        fakeClubRepo.getClubTeamsResult = Result.success(teams.toList())
    }

    @Test
    fun init_loadsClubAndTeams() = runTest(testDispatcher) {
        givenManagerOfClub1(Team("t1", "club1", "U17"), Team("t2", "club1", "U19"))

        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)

        val state = viewModel.state.value
        state.clubId shouldBe "club1"
        state.club?.id shouldBe "club1"
        state.teams.map { it.name } shouldBe listOf("U17", "U19")
        state.isClubManager shouldBe true
        state.isLoading shouldBe false
        state.error shouldBe null
    }

    @Test
    fun loadTeams_noRoles_isEmptyAndNotLoading() = runTest(testDispatcher) {
        fakeTeamRepo.getMyRolesResult = Result.success(UserRoles())

        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)

        viewModel.state.value.teams shouldHaveSize 0
        viewModel.state.value.clubId shouldBe null
        viewModel.state.value.club shouldBe null
        viewModel.state.value.isLoading shouldBe false
        viewModel.state.value.error shouldBe null
    }

    @Test
    fun loadTeams_clubWithoutTeams_isEmptyList() = runTest(testDispatcher) {
        givenManagerOfClub1()

        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)

        viewModel.state.value.teams shouldHaveSize 0
        viewModel.state.value.club?.id shouldBe "club1"
        viewModel.state.value.isLoading shouldBe false
    }

    @Test
    fun loadTeams_teamRoleOnly_derivesClubIdAndIsNotManager() = runTest(testDispatcher) {
        fakeTeamRepo.getMyRolesResult = Result.success(
            UserRoles(teamRoles = listOf(TeamRoleEntry(teamId = "t1", clubId = "club9", role = "player")))
        )
        fakeClubRepo.getClubTeamsResult = Result.success(listOf(Team("t1", "club9", "U17")))

        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)

        viewModel.state.value.clubId shouldBe "club9"
        viewModel.state.value.isClubManager shouldBe false
        viewModel.state.value.teams shouldHaveSize 1
    }

    @Test
    fun loadTeams_rolesFailure_surfacesError() = runTest(testDispatcher) {
        fakeTeamRepo.getMyRolesResult = Result.failure(RuntimeException("401"))

        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)

        viewModel.state.value.error shouldBe "401"
        viewModel.state.value.isLoading shouldBe false
    }

    @Test
    fun loadTeams_teamsFailure_surfacesError() = runTest(testDispatcher) {
        givenManagerOfClub1()
        fakeClubRepo.getClubTeamsResult = Result.failure(RuntimeException("500"))

        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)

        viewModel.state.value.error shouldBe "500"
        viewModel.state.value.isLoading shouldBe false
    }

    @Test
    fun refresh_picksUpNewTeams() = runTest(testDispatcher) {
        givenManagerOfClub1(Team("t1", "club1", "U17"))
        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)
        fakeClubRepo.getClubTeamsResult = Result.success(listOf(Team("t1", "club1", "U17"), Team("t2", "club1", "U19")))

        viewModel.loadTeams()

        viewModel.state.value.teams shouldHaveSize 2
    }

    @Test
    fun refresh_failure_keepsCachedTeamsAndSetsError() = runTest(testDispatcher) {
        givenManagerOfClub1(Team("t1", "club1", "U17"))
        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)
        fakeClubRepo.getClubTeamsResult = Result.failure(RuntimeException("offline"))

        viewModel.loadTeams()

        viewModel.state.value.teams shouldHaveSize 1
        viewModel.state.value.error shouldBe "offline"
        viewModel.state.value.isLoading shouldBe false
    }

    @Test
    fun refresh_afterError_clearsError() = runTest(testDispatcher) {
        fakeTeamRepo.getMyRolesResult = Result.failure(RuntimeException("401"))
        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)
        givenManagerOfClub1(Team("t1", "club1", "U17"))

        viewModel.loadTeams()

        viewModel.state.value.error shouldBe null
        viewModel.state.value.teams shouldHaveSize 1
    }

    @Test
    fun createTeam_success_reloadsTeamsAndClosesSheet() = runTest(testDispatcher) {
        givenManagerOfClub1(Team("t1", "club1", "U17"))
        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)
        viewModel.showCreateSheet()
        fakeClubRepo.getClubTeamsResult = Result.success(listOf(Team("t1", "club1", "U17"), Team("t2", "club1", "New")))

        viewModel.createTeam("New", null)

        viewModel.state.value.teams shouldHaveSize 2
        viewModel.state.value.showCreateSheet shouldBe false
        viewModel.state.value.isLoading shouldBe false
    }

    @Test
    fun createTeam_failure_surfacesError() = runTest(testDispatcher) {
        givenManagerOfClub1()
        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)
        fakeClubRepo.createTeamResult = Result.failure(RuntimeException("403"))

        viewModel.createTeam("New", null)

        viewModel.state.value.error shouldBe "403"
        viewModel.state.value.isLoading shouldBe false
    }

    @Test
    fun createTeam_withoutClub_doesNothing() = runTest(testDispatcher) {
        fakeTeamRepo.getMyRolesResult = Result.success(UserRoles())
        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)

        viewModel.createTeam("New", null)

        viewModel.state.value.isLoading shouldBe false
        viewModel.state.value.error shouldBe null
    }

    @Test
    fun updateClub_success_replacesClub() = runTest(testDispatcher) {
        givenManagerOfClub1()
        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)
        viewModel.showEditClubSheet()

        viewModel.updateClub("Updated Club", "Bern")

        viewModel.state.value.club?.name shouldBe "Updated Club"
        viewModel.state.value.showEditClubSheet shouldBe false
        viewModel.state.value.isLoading shouldBe false
    }

    @Test
    fun updateClub_failure_surfacesError() = runTest(testDispatcher) {
        givenManagerOfClub1()
        viewModel = TeamsListViewModel(fakeClubRepo, fakeTeamRepo)
        fakeClubRepo.updateClubResult = Result.failure(RuntimeException("403"))

        viewModel.updateClub("X", null)

        viewModel.state.value.error shouldBe "403"
        viewModel.state.value.isLoading shouldBe false
    }
}
