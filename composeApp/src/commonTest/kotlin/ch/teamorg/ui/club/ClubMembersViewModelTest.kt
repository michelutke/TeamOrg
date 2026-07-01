package ch.teamorg.ui.club

import ch.teamorg.domain.ClubUser
import ch.teamorg.fake.FakeClubRepository
import ch.teamorg.fake.FakeTeamRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
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
class ClubMembersViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeClubRepo = FakeClubRepository()
    private val fakeTeamRepo = FakeTeamRepository()
    private lateinit var viewModel: ClubMembersViewModel

    private fun makeUser(id: String) = ClubUser(
        userId = id, displayName = "User $id", email = "$id@test.com"
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClubRepo.reset()
        viewModel = ClubMembersViewModel(fakeClubRepo, fakeTeamRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region — initial state

    @Test
    fun state_initially_isEmpty() {
        val state = viewModel.state.value
        state.users.shouldBeEmpty()
        state.loading shouldBe false
        state.endReached shouldBe false
        state.error shouldBe null
    }

    // region — loadMore appends pages

    @Test
    fun init_withFullPage_appendsUsersAndEndReachedIsFalse() = runTest(testDispatcher) {
        val page = List(25) { makeUser("u$it") }
        fakeClubRepo.listClubUsersResult = Result.success(page)

        viewModel.init("club1")

        viewModel.state.value.users shouldHaveSize 25
        viewModel.state.value.endReached shouldBe false
        viewModel.state.value.loading shouldBe false
    }

    @Test
    fun loadMore_withShortPage_setsEndReached() = runTest(testDispatcher) {
        val shortPage = List(10) { makeUser("u$it") }
        fakeClubRepo.listClubUsersResult = Result.success(shortPage)

        viewModel.init("club1")

        viewModel.state.value.endReached shouldBe true
    }

    @Test
    fun loadMore_appendsSubsequentPage() = runTest(testDispatcher) {
        val firstPage = List(25) { makeUser("a$it") }
        val secondPage = List(5) { makeUser("b$it") }
        fakeClubRepo.listClubUsersResult = Result.success(firstPage)

        viewModel.init("club1")

        fakeClubRepo.listClubUsersResult = Result.success(secondPage)
        viewModel.loadMore()

        viewModel.state.value.users shouldHaveSize 30
        viewModel.state.value.endReached shouldBe true
    }

    @Test
    fun loadMore_whenEndReached_doesNotCallRepo() = runTest(testDispatcher) {
        fakeClubRepo.listClubUsersResult = Result.success(List(5) { makeUser("u$it") })
        viewModel.init("club1")
        // endReached = true now; capture call count after init
        val callsAfterInit = fakeClubRepo.listClubUsersCallCount

        viewModel.loadMore()

        // Repo must not have been invoked again
        fakeClubRepo.listClubUsersCallCount shouldBe callsAfterInit
        viewModel.state.value.error shouldBe null
    }

    // region — filter

    @Test
    fun setFilter_doesNotClearUsers() = runTest(testDispatcher) {
        fakeClubRepo.listClubUsersResult = Result.success(List(5) { makeUser("u$it") })
        viewModel.init("club1")

        viewModel.setFilter("u0")

        viewModel.state.value.users shouldHaveSize 5
        viewModel.state.value.filter shouldBe "u0"
    }

    // region — error handling

    @Test
    fun init_onFailure_setsError() = runTest(testDispatcher) {
        fakeClubRepo.listClubUsersResult = Result.failure(Exception("Network error"))

        viewModel.init("club1")

        viewModel.state.value.error shouldBe "Network error"
        viewModel.state.value.loading shouldBe false
    }

    @Test
    fun clearError_resetsError() = runTest(testDispatcher) {
        fakeClubRepo.listClubUsersResult = Result.failure(Exception("err"))
        viewModel.init("club1")

        viewModel.clearError()

        viewModel.state.value.error shouldBe null
    }

    // region — init idempotent for same clubId

    @Test
    fun init_calledTwiceWithSameId_doesNotResetState() = runTest(testDispatcher) {
        val page = List(5) { makeUser("u$it") }
        fakeClubRepo.listClubUsersResult = Result.success(page)
        viewModel.init("club1")

        // Second init with same id should no-op
        fakeClubRepo.listClubUsersResult = Result.success(emptyList())
        viewModel.init("club1")

        viewModel.state.value.users shouldHaveSize 5
    }
}
