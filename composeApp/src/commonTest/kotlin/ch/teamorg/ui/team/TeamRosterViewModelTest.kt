package ch.teamorg.ui.team

import app.cash.turbine.test
import ch.teamorg.domain.TeamMember
import ch.teamorg.fake.FakeClubRepository
import ch.teamorg.fake.FakeTeamRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
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
class TeamRosterViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeTeamRepo = FakeTeamRepository()
    private val fakeClubRepo = FakeClubRepository()
    private lateinit var viewModel: TeamRosterViewModel

    private val memberAlice = TeamMember(
        userId = "u1", displayName = "Alice", avatarUrl = null,
        role = "player", jerseyNumber = 7, position = "setter"
    )
    private val memberBob = TeamMember(
        userId = "u2", displayName = "Bob", avatarUrl = null,
        role = "coach", jerseyNumber = null, position = null
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeTeamRepo.reset()
        viewModel = TeamRosterViewModel(fakeTeamRepo, fakeClubRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region — initial state

    @Test
    fun state_initially_isEmpty() {
        val state = viewModel.state.value
        state.members.shouldBeEmpty()
        state.isLoading shouldBe false
        state.isRefreshing shouldBe false
        state.error shouldBe null
        state.inviteUrl shouldBe null
    }

    // region — loadRoster happy path

    @Test
    fun loadRoster_withSuccess_populatesMembers() = runTest(testDispatcher) {
        fakeTeamRepo.getRosterResult = Result.success(listOf(memberAlice, memberBob))
        viewModel.loadRoster("team1")

        viewModel.state.value.members shouldContainExactly listOf(memberAlice, memberBob)
    }

    @Test
    fun loadRoster_withSuccess_clearsLoadingAndError() = runTest(testDispatcher) {
        fakeTeamRepo.getRosterResult = Result.success(listOf(memberAlice))
        viewModel.loadRoster("team1")

        val state = viewModel.state.value
        state.isLoading shouldBe false
        state.error shouldBe null
    }

    @Test
    fun loadRoster_passesTeamIdToRepository() = runTest(testDispatcher) {
        viewModel.loadRoster("team42")

        fakeTeamRepo.lastRosterTeamId shouldBe "team42"
    }

    // region — loadRoster: loading state

    @Test
    fun loadRoster_afterSuccess_isLoadingIsFalse() = runTest(testDispatcher) {
        fakeTeamRepo.getRosterResult = Result.success(listOf(memberAlice))
        viewModel.loadRoster("team1")

        viewModel.state.value.isLoading shouldBe false
    }

    // region — loadRoster: pull-to-refresh

    @Test
    fun loadRoster_withIsRefreshTrue_populatesMembersAndClearsRefreshing() = runTest(testDispatcher) {
        fakeTeamRepo.getRosterResult = Result.success(listOf(memberAlice))
        viewModel.loadRoster("team1", isRefresh = true)

        val state = viewModel.state.value
        state.isRefreshing shouldBe false
        state.members shouldContainExactly listOf(memberAlice)
    }

    @Test
    fun loadRoster_withIsRefreshTrue_isLoadingRemainsFlase() = runTest(testDispatcher) {
        fakeTeamRepo.getRosterResult = Result.success(listOf(memberAlice))
        viewModel.loadRoster("team1", isRefresh = true)

        viewModel.state.value.isLoading shouldBe false
    }

    // region — loadRoster error path

    @Test
    fun loadRoster_onFailure_setsErrorMessage() = runTest(testDispatcher) {
        fakeTeamRepo.getRosterResult = Result.failure(Exception("Network error"))
        viewModel.loadRoster("team1")

        viewModel.state.value.error shouldBe "Network error"
    }

    @Test
    fun loadRoster_onFailureWithNullMessage_setsDefaultError() = runTest(testDispatcher) {
        fakeTeamRepo.getRosterResult = Result.failure(Exception())
        viewModel.loadRoster("team1")

        viewModel.state.value.error shouldBe "Failed to fetch roster"
    }

    @Test
    fun loadRoster_onFailure_clearsLoadingAndRefreshing() = runTest(testDispatcher) {
        fakeTeamRepo.getRosterResult = Result.failure(Exception("Error"))
        viewModel.loadRoster("team1")

        val state = viewModel.state.value
        state.isLoading shouldBe false
        state.isRefreshing shouldBe false
    }

    // region — removeMember

    @Test
    fun removeMember_withSuccess_removesMemberFromState() = runTest(testDispatcher) {
        fakeTeamRepo.getRosterResult = Result.success(listOf(memberAlice, memberBob))
        viewModel.loadRoster("team1")

        viewModel.removeMember("team1", "u1")

        viewModel.state.value.members shouldContainExactly listOf(memberBob)
    }

    @Test
    fun removeMember_onFailure_setsErrorMessage() = runTest(testDispatcher) {
        fakeTeamRepo.removeMemberResult = Result.failure(Exception("Not authorized"))
        viewModel.removeMember("team1", "u1")

        viewModel.state.value.error shouldBe "Not authorized"
    }

    @Test
    fun removeMember_onFailureWithNullMessage_setsDefaultError() = runTest(testDispatcher) {
        fakeTeamRepo.removeMemberResult = Result.failure(Exception())
        viewModel.removeMember("team1", "u1")

        viewModel.state.value.error shouldBe "Failed to remove member"
    }

    @Test
    fun removeMember_passesCorrectIdsToRepository() = runTest(testDispatcher) {
        viewModel.removeMember("team99", "userXYZ")

        fakeTeamRepo.lastRemovedTeamId shouldBe "team99"
        fakeTeamRepo.lastRemovedUserId shouldBe "userXYZ"
    }

    // region — createInvite

    @Test
    fun createInvite_withSuccess_setsInviteUrl() = runTest(testDispatcher) {
        fakeTeamRepo.createInviteResult = Result.success("https://teamorg.app/invite/xyz")
        viewModel.createInvite("team1", "player")

        viewModel.state.value.inviteUrl shouldBe "https://teamorg.app/invite/xyz"
    }

    @Test
    fun createInvite_onFailure_setsErrorMessage() = runTest(testDispatcher) {
        fakeTeamRepo.createInviteResult = Result.failure(Exception("Invite limit reached"))
        viewModel.createInvite("team1", "player")

        viewModel.state.value.error shouldBe "Invite limit reached"
    }

    @Test
    fun createInvite_onFailureWithNullMessage_setsDefaultError() = runTest(testDispatcher) {
        fakeTeamRepo.createInviteResult = Result.failure(Exception())
        viewModel.createInvite("team1", "player")

        viewModel.state.value.error shouldBe "Failed to create invite"
    }

    @Test
    fun createInvite_passesRoleToRepository() = runTest(testDispatcher) {
        viewModel.createInvite("team1", "coach")

        fakeTeamRepo.lastInviteRole shouldBe "coach"
        fakeTeamRepo.lastInviteTeamId shouldBe "team1"
    }

    // region — resetInvite

    @Test
    fun resetInvite_clearsInviteUrl() = runTest(testDispatcher) {
        fakeTeamRepo.createInviteResult = Result.success("https://teamorg.app/invite/xyz")
        viewModel.createInvite("team1", "player")
        viewModel.resetInvite()

        viewModel.state.value.inviteUrl shouldBe null
    }
}
