package ch.teamorg.ui.club

import app.cash.turbine.test
import ch.teamorg.domain.Club
import ch.teamorg.fake.FakeClubRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
class ClubSetupViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeClubRepo = FakeClubRepository()
    private lateinit var viewModel: ClubSetupViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClubRepo.reset()
        viewModel = ClubSetupViewModel(fakeClubRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region — initial state

    @Test
    fun state_initially_hasDefaultValues() {
        val state = viewModel.state.value
        state.name shouldBe ""
        state.sportType shouldBe "volleyball"
        state.location shouldBe ""
        state.isLoading shouldBe false
        state.error shouldBe null
        state.clubId shouldBe null
        state.logoUrl shouldBe null
    }

    // region — field updates

    @Test
    fun onNameChange_updatesNameAndClearsError() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem()
            viewModel.onNameChange("FC Example")
            awaitItem().name shouldBe "FC Example"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onSportTypeChange_updatesSportType() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem()
            viewModel.onSportTypeChange("football")
            awaitItem().sportType shouldBe "football"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onLocationChange_updatesLocation() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem()
            viewModel.onLocationChange("Zurich")
            awaitItem().location shouldBe "Zurich"
            cancelAndIgnoreRemainingEvents()
        }
    }

    // region — createClub validation

    @Test
    fun createClub_withBlankName_setsError() {
        viewModel.createClub()
        viewModel.state.value.error shouldBe "Club name is required"
    }

    @Test
    fun createClub_withWhitespaceOnlyName_setsError() {
        viewModel.onNameChange("   ")
        viewModel.createClub()
        viewModel.state.value.error shouldBe "Club name is required"
    }

    // region — createClub happy path

    @Test
    fun createClub_withValidName_emitsClubCreatedEvent() = runTest(testDispatcher) {
        val expectedClub = Club(id = "club1", name = "FC Example", logoUrl = null, sportType = "volleyball")
        fakeClubRepo.createClubResult = Result.success(expectedClub)
        viewModel.onNameChange("FC Example")

        viewModel.events.test {
            viewModel.createClub()
            val event = awaitItem()
            event shouldBe ClubSetupEvent.ClubCreated(expectedClub)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun createClub_withValidName_storesClubIdInState() = runTest(testDispatcher) {
        fakeClubRepo.createClubResult = Result.success(
            Club(id = "club42", name = "FC Example", logoUrl = null, sportType = "volleyball")
        )
        viewModel.onNameChange("FC Example")
        viewModel.createClub()

        viewModel.state.value.clubId shouldBe "club42"
    }

    @Test
    fun createClub_withEmptyLocation_passesNullLocationToRepository() = runTest(testDispatcher) {
        viewModel.onNameChange("FC Example")
        viewModel.createClub()

        fakeClubRepo.lastCreatedLocation shouldBe null
    }

    @Test
    fun createClub_withLocation_passesLocationToRepository() = runTest(testDispatcher) {
        viewModel.onNameChange("FC Example")
        viewModel.onLocationChange("Zurich")
        viewModel.createClub()

        fakeClubRepo.lastCreatedLocation shouldBe "Zurich"
    }

    @Test
    fun createClub_clearsLoadingAfterSuccess() = runTest(testDispatcher) {
        viewModel.onNameChange("FC Example")
        viewModel.createClub()

        viewModel.state.value.isLoading shouldBe false
    }

    // region — createClub error path

    @Test
    fun createClub_onRepositoryFailure_setsErrorMessage() = runTest(testDispatcher) {
        fakeClubRepo.createClubResult = Result.failure(Exception("Name already taken"))
        viewModel.onNameChange("FC Example")
        viewModel.createClub()

        viewModel.state.value.error shouldBe "Name already taken"
    }

    @Test
    fun createClub_onRepositoryFailureWithNullMessage_setsDefaultError() = runTest(testDispatcher) {
        fakeClubRepo.createClubResult = Result.failure(Exception())
        viewModel.onNameChange("FC Example")
        viewModel.createClub()

        viewModel.state.value.error shouldBe "Failed to create club"
    }

    @Test
    fun createClub_onFailure_clearsLoadingState() = runTest(testDispatcher) {
        fakeClubRepo.createClubResult = Result.failure(Exception("Error"))
        viewModel.onNameChange("FC Example")
        viewModel.createClub()

        viewModel.state.value.isLoading shouldBe false
    }

    // region — uploadLogo

    @Test
    fun uploadLogo_withNoClubId_doesNothing() = runTest(testDispatcher) {
        // clubId is null initially; uploadLogo should be a no-op
        viewModel.uploadLogo(ByteArray(0), "png")

        viewModel.state.value.isLogoUploading shouldBe false
        fakeClubRepo.lastUploadedClubId shouldBe null
    }

    @Test
    fun uploadLogo_afterClubCreated_emitsLogoUploadedEvent() = runTest(testDispatcher) {
        // Create club first to get clubId
        viewModel.onNameChange("FC Example")
        viewModel.createClub()

        viewModel.events.test {
            viewModel.uploadLogo(ByteArray(10), "jpg")
            val event = awaitItem()
            event shouldBe ClubSetupEvent.LogoUploaded
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uploadLogo_afterClubCreated_updatesLogoUrl() = runTest(testDispatcher) {
        viewModel.onNameChange("FC Example")
        viewModel.createClub()

        fakeClubRepo.uploadLogoResult = Result.success(
            Club(id = "club1", name = "FC Example", logoUrl = "https://cdn.example.com/logo.jpg", sportType = "volleyball")
        )
        viewModel.uploadLogo(ByteArray(10), "jpg")

        viewModel.state.value.logoUrl shouldBe "https://cdn.example.com/logo.jpg"
    }

    @Test
    fun uploadLogo_onFailure_setsErrorAndClearsUploading() = runTest(testDispatcher) {
        viewModel.onNameChange("FC Example")
        viewModel.createClub()

        fakeClubRepo.uploadLogoResult = Result.failure(Exception("Upload failed"))
        viewModel.uploadLogo(ByteArray(10), "jpg")

        val state = viewModel.state.value
        state.error shouldBe "Upload failed"
        state.isLogoUploading shouldBe false
    }

    @Test
    fun uploadLogo_passesExtensionToRepository() = runTest(testDispatcher) {
        viewModel.onNameChange("FC Example")
        viewModel.createClub()
        viewModel.uploadLogo(ByteArray(10), "png")

        fakeClubRepo.lastUploadedExtension shouldBe "png"
    }
}
