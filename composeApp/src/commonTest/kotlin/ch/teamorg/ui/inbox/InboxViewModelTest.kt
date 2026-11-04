package ch.teamorg.ui.inbox

import ch.teamorg.domain.Notification
import ch.teamorg.fake.FakeNotificationRepository
import ch.teamorg.navigation.Screen
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
class InboxViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = FakeNotificationRepository()

    private fun notification(
        id: String,
        isRead: Boolean = false,
        type: String = "event_new",
        entityType: String? = "event",
        entityId: String? = "e-$id"
    ) = Notification(
        id = id,
        type = type,
        title = "Title $id",
        body = "Body $id",
        entityId = entityId,
        entityType = entityType,
        isRead = isRead,
        createdAt = "2026-01-01T10:00:00Z"
    )

    private fun seed(vararg items: Notification) {
        repo.getNotificationsResult = Result.success(items.toList())
        repo.getUnreadCountResult = Result.success(items.count { !it.isRead }.toLong())
    }

    private fun makeViewModel() = InboxViewModel(repo)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region load

    @Test
    fun init_loadsNotificationsAndUnreadCount() = runTest(testDispatcher) {
        seed(notification("n1"), notification("n2", isRead = true), notification("n3"))

        val vm = makeViewModel()

        val state = vm.state.value
        state.notifications shouldHaveSize 3
        state.isLoading shouldBe false
        state.unreadCount shouldBe 2L
        state.hasUnread shouldBe true
        state.error shouldBe null
    }

    @Test
    fun init_emptyInbox_hasNoUnread() = runTest(testDispatcher) {
        seed()

        val vm = makeViewModel()

        vm.state.value.notifications shouldHaveSize 0
        vm.state.value.hasUnread shouldBe false
        vm.state.value.isLoading shouldBe false
    }

    @Test
    fun init_loadFailure_surfacesErrorAndStopsLoading() = runTest(testDispatcher) {
        repo.getNotificationsResult = Result.failure(RuntimeException("offline"))

        val vm = makeViewModel()

        vm.state.value.error shouldBe "offline"
        vm.state.value.isLoading shouldBe false
        vm.state.value.notifications shouldHaveSize 0
    }

    @Test
    fun refresh_reloadsAndClearsRefreshingFlag() = runTest(testDispatcher) {
        seed(notification("n1"))
        val vm = makeViewModel()
        seed(notification("n1"), notification("n2"))

        vm.refresh()

        vm.state.value.notifications shouldHaveSize 2
        vm.state.value.isRefreshing shouldBe false
        repo.getNotificationsCallCount shouldBe 2
    }

    @Test
    fun refresh_failureKeepsCachedNotificationsAndSetsError() = runTest(testDispatcher) {
        seed(notification("n1"))
        val vm = makeViewModel()
        repo.getNotificationsResult = Result.failure(RuntimeException("timeout"))

        vm.refresh()

        vm.state.value.error shouldBe "timeout"
        vm.state.value.notifications shouldHaveSize 1
        vm.state.value.isRefreshing shouldBe false
    }

    // endregion

    // region mark read

    @Test
    fun markRead_marksOnlyThatNotificationAndDecrementsUnread() = runTest(testDispatcher) {
        seed(notification("n1"), notification("n2"))
        val vm = makeViewModel()

        vm.markRead("n1")

        val byId = vm.state.value.notifications.associateBy { it.id }
        byId.getValue("n1").isRead shouldBe true
        byId.getValue("n2").isRead shouldBe false
        vm.state.value.unreadCount shouldBe 1L
        vm.state.value.hasUnread shouldBe true
        repo.markReadCalls shouldBe listOf("n1")
    }

    @Test
    fun markRead_lastUnread_clearsHasUnread() = runTest(testDispatcher) {
        seed(notification("n1"))
        val vm = makeViewModel()

        vm.markRead("n1")

        vm.state.value.unreadCount shouldBe 0L
        vm.state.value.hasUnread shouldBe false
    }

    @Test
    fun markRead_failure_revertsOptimisticUpdate() = runTest(testDispatcher) {
        seed(notification("n1"), notification("n2"))
        val vm = makeViewModel()
        repo.markReadResult = Result.failure(RuntimeException("500"))

        vm.markRead("n1")

        vm.state.value.notifications.first { it.id == "n1" }.isRead shouldBe false
        vm.state.value.unreadCount shouldBe 2L
        vm.state.value.hasUnread shouldBe true
    }

    @Test
    fun markAllRead_marksEverythingRead() = runTest(testDispatcher) {
        seed(notification("n1"), notification("n2"), notification("n3", isRead = true))
        val vm = makeViewModel()

        vm.markAllRead()

        vm.state.value.notifications.all { it.isRead } shouldBe true
        vm.state.value.unreadCount shouldBe 0L
        vm.state.value.hasUnread shouldBe false
        vm.state.value.error shouldBe null
        repo.markAllReadCallCount shouldBe 1
    }

    @Test
    fun markAllRead_failure_revertsAndSurfacesError() = runTest(testDispatcher) {
        seed(notification("n1"), notification("n2"))
        val vm = makeViewModel()
        repo.markAllReadResult = Result.failure(RuntimeException("500"))

        vm.markAllRead()

        vm.state.value.notifications.count { !it.isRead } shouldBe 2
        vm.state.value.unreadCount shouldBe 2L
        vm.state.value.hasUnread shouldBe true
        vm.state.value.error shouldBe "Could not mark read. Try again."
    }

    // endregion

    // region delete

    @Test
    fun deleteAll_clearsNotificationsAndUnread() = runTest(testDispatcher) {
        seed(notification("n1"), notification("n2"))
        val vm = makeViewModel()

        vm.deleteAll()

        vm.state.value.notifications shouldHaveSize 0
        vm.state.value.unreadCount shouldBe 0L
        vm.state.value.hasUnread shouldBe false
        repo.deleteAllCallCount shouldBe 1
    }

    @Test
    fun deleteAll_failure_restoresNotificationsAndSurfacesError() = runTest(testDispatcher) {
        seed(notification("n1"), notification("n2", isRead = true))
        val vm = makeViewModel()
        repo.deleteAllResult = Result.failure(RuntimeException("500"))

        vm.deleteAll()

        vm.state.value.notifications shouldHaveSize 2
        vm.state.value.unreadCount shouldBe 1L
        vm.state.value.hasUnread shouldBe true
        vm.state.value.error shouldBe "Could not delete. Try again."
    }

    // endregion

    // region tap routing

    @Test
    fun getDeepLinkScreen_eventTypes_routeToEventDetail() = runTest(testDispatcher) {
        val vm = makeViewModel()

        listOf("event_new", "event_edit", "event_cancel", "reminder", "response").forEach { type ->
            vm.getDeepLinkScreen(notification("n1", type = type, entityType = "event", entityId = "evt-42")) shouldBe
                Screen.EventDetail("evt-42")
        }
    }

    @Test
    fun getDeepLinkScreen_eventWithoutEntityId_doesNotNavigate() = runTest(testDispatcher) {
        val vm = makeViewModel()

        vm.getDeepLinkScreen(notification("n1", entityType = "event", entityId = null)) shouldBe null
    }

    @Test
    fun getDeepLinkScreen_abwesenheit_doesNotNavigate() = runTest(testDispatcher) {
        val vm = makeViewModel()

        vm.getDeepLinkScreen(notification("n1", type = "absence", entityType = "abwesenheit", entityId = "rule-1")) shouldBe null
    }

    @Test
    fun getDeepLinkScreen_noEntity_doesNotNavigate() = runTest(testDispatcher) {
        val vm = makeViewModel()

        vm.getDeepLinkScreen(notification("n1", entityType = null, entityId = null)) shouldBe null
    }

    // endregion
}
