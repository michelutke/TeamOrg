package ch.teamorg.ui.team

import app.cash.turbine.test
import ch.teamorg.domain.DuplicateCandidate
import ch.teamorg.domain.DuplicateSuggestion
import ch.teamorg.domain.LinkMemberResult
import ch.teamorg.domain.MovableCounts
import ch.teamorg.domain.TeamMember
import ch.teamorg.domain.TeamRoleEntry
import ch.teamorg.domain.UserRoles
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

    // region — duplicate suggestions

    private val suggestionLara = DuplicateSuggestion(
        memberId = "m1",
        lastName = "Müller",
        firstName = "Lara",
        birthDate = "2008-04-01",
        personNumber = "123456789",
        funktion = "Teilnehmer/in",
        candidates = listOf(DuplicateCandidate("u9", "Lara Müller", "HIGH")),
        willMove = MovableCounts(attendance = 12, subgroups = 1, rules = 0)
    )

    @Test
    fun loadRoster_asCoach_exposesDuplicateSuggestions() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.duplicateSuggestionsResult = Result.success(listOf(suggestionLara))

        viewModel.loadRoster("t1")

        viewModel.state.value.duplicates shouldContainExactly listOf(suggestionLara)
    }

    @Test
    fun loadRoster_asPlainPlayer_doesNotFetchSuggestions() = runTest {
        givenCallerIsPlainPlayerOfTeam("t1")
        fakeTeamRepo.duplicateSuggestionsResult = Result.success(listOf(suggestionLara))

        viewModel.loadRoster("t1")

        viewModel.state.value.duplicates.shouldBeEmpty()
    }

    @Test
    fun loadRoster_whenSuggestionsFetchFails_leavesDuplicatesEmptyAndDoesNotSurfaceAnError() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.duplicateSuggestionsResult = Result.failure(RuntimeException("boom"))

        viewModel.loadRoster("t1")

        viewModel.state.value.duplicates.shouldBeEmpty()
        viewModel.state.value.error shouldBe null
    }

    @Test
    fun mergeDuplicate_onSuccess_dropsThatSuggestionAndCountsTheMerge() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.duplicateSuggestionsResult = Result.success(listOf(suggestionLara))
        viewModel.loadRoster("t1")
        fakeTeamRepo.linkNdsMemberResult = LinkMemberResult.Success
        // The post-merge refresh must not resurrect the suggestion we just resolved.
        fakeTeamRepo.duplicateSuggestionsResult = Result.success(emptyList())

        viewModel.mergeDuplicate("t1", "m1", "u9")

        val state = viewModel.state.value
        state.duplicates.shouldBeEmpty()
        state.mergedCount shouldBe 1
        state.mergeError shouldBe null
        state.mergeInProgress shouldBe false
        fakeTeamRepo.linkNdsMemberCalls shouldContainExactly listOf(Triple("t1", "m1", "u9"))
    }

    @Test
    fun mergeDuplicate_onConflict_showsTheAlreadyLinkedMessageAndKeepsTheSheetOpen() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.duplicateSuggestionsResult = Result.success(listOf(suggestionLara))
        viewModel.loadRoster("t1")
        viewModel.openDuplicatesSheet()
        fakeTeamRepo.linkNdsMemberResult = LinkMemberResult.Conflict

        viewModel.mergeDuplicate("t1", "m1", "u9")

        val state = viewModel.state.value
        state.mergeError shouldBe "This account is already linked to another member of this team."
        state.showDuplicatesSheet shouldBe true
        state.mergedCount shouldBe 0
        state.mergeInProgress shouldBe false
    }

    @Test
    fun mergeDuplicate_onNotLinkable_showsTheCannotBeLinkedMessage() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.linkNdsMemberResult = LinkMemberResult.NotLinkable

        viewModel.mergeDuplicate("t1", "m1", "u9")

        viewModel.state.value.mergeError shouldBe "This account can't be linked."
    }

    @Test
    fun mergeDuplicate_onError_showsTheGenericRetryMessage() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.linkNdsMemberResult = LinkMemberResult.Error("linkNdsMember: 404 Not Found")

        viewModel.mergeDuplicate("t1", "m1", "u9")

        // Never leak the raw status string to a coach.
        viewModel.state.value.mergeError shouldBe "Couldn't merge. Please try again."
    }

    @Test
    fun clearMergeError_removesTheMessage() = runTest {
        givenCallerIsCoachOfTeam("t1")
        fakeTeamRepo.linkNdsMemberResult = LinkMemberResult.Conflict
        viewModel.mergeDuplicate("t1", "m1", "u9")

        viewModel.clearMergeError()

        viewModel.state.value.mergeError shouldBe null
    }

    // endregion

    private fun givenCallerIsCoachOfTeam(teamId: String) {
        fakeTeamRepo.getMyRolesResult = Result.success(
            UserRoles(teamRoles = listOf(TeamRoleEntry(teamId = teamId, clubId = "c1", role = "coach")))
        )
    }

    private fun givenCallerIsPlainPlayerOfTeam(teamId: String) {
        fakeTeamRepo.getMyRolesResult = Result.success(
            UserRoles(teamRoles = listOf(TeamRoleEntry(teamId = teamId, clubId = "c1", role = "player")))
        )
    }
}
