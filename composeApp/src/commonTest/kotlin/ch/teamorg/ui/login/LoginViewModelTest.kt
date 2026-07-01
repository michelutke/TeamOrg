package ch.teamorg.ui.login

import app.cash.turbine.test
import ch.teamorg.fake.FakeAuthRepository
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
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeAuth = FakeAuthRepository()
    private lateinit var viewModel: LoginViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuth.reset()
        viewModel = LoginViewModel(fakeAuth)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region — initial state

    @Test
    fun state_initially_hasEmptyFieldsAndNoError() {
        val state = viewModel.state.value
        state.email shouldBe ""
        state.password shouldBe ""
        state.isLoading shouldBe false
        state.error shouldBe null
    }

    // region — field updates

    @Test
    fun onEmailChange_updatesEmailAndClearsError() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.onEmailChange("alice@example.com")
            val state = awaitItem()
            state.email shouldBe "alice@example.com"
            state.error shouldBe null

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onPasswordChange_updatesPasswordAndClearsError() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.onPasswordChange("secret123")
            val state = awaitItem()
            state.password shouldBe "secret123"
            state.error shouldBe null

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onEmailChange_whenErrorPresent_clearsError() = runTest(testDispatcher) {
        // put ViewModel into error state first
        viewModel.onLoginClick() // blank fields -> error
        viewModel.state.test {
            awaitItem() // error state

            viewModel.onEmailChange("new@example.com")
            val state = awaitItem()
            state.error shouldBe null

            cancelAndIgnoreRemainingEvents()
        }
    }

    // region — validation

    @Test
    fun onLoginClick_withBlankEmail_setsError() {
        viewModel.onPasswordChange("password123")
        viewModel.onLoginClick()
        viewModel.state.value.error shouldBe "Please fill in all fields"
    }

    @Test
    fun onLoginClick_withBlankPassword_setsError() {
        viewModel.onEmailChange("alice@example.com")
        viewModel.onLoginClick()
        viewModel.state.value.error shouldBe "Please fill in all fields"
    }

    @Test
    fun onLoginClick_withBothBlank_setsError() {
        viewModel.onLoginClick()
        viewModel.state.value.error shouldBe "Please fill in all fields"
    }

    @Test
    fun onLoginClick_withWhitespaceOnlyEmail_setsError() {
        viewModel.onEmailChange("   ")
        viewModel.onPasswordChange("password123")
        viewModel.onLoginClick()
        viewModel.state.value.error shouldBe "Please fill in all fields"
    }

    // region — happy path

    @Test
    fun onLoginClick_withValidCredentials_emitsLoginSuccess() = runTest(testDispatcher) {
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("password123")

        viewModel.loginSuccess.test {
            viewModel.onLoginClick()
            awaitItem() // Unit success event
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onLoginClick_withValidCredentials_clearsLoadingAndError() = runTest(testDispatcher) {
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.onLoginClick()

        val state = viewModel.state.value
        state.isLoading shouldBe false
        state.error shouldBe null
    }

    @Test
    fun onLoginClick_passesBothFieldsToRepository() = runTest(testDispatcher) {
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("secret123")
        viewModel.onLoginClick()

        fakeAuth.lastLoginRequest?.email shouldBe "alice@example.com"
        fakeAuth.lastLoginRequest?.password shouldBe "secret123"
    }

    // region — error path

    @Test
    fun onLoginClick_onRepositoryFailure_setsErrorMessage() = runTest(testDispatcher) {
        fakeAuth.loginResult = Result.failure(Exception("Invalid credentials"))
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("wrongpass")
        viewModel.onLoginClick()

        viewModel.state.value.error shouldBe "Invalid credentials"
    }

    @Test
    fun onLoginClick_onRepositoryFailureWithNullMessage_setsDefaultError() = runTest(testDispatcher) {
        fakeAuth.loginResult = Result.failure(Exception())
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("pass")
        viewModel.onLoginClick()

        viewModel.state.value.error shouldNotBe null
        viewModel.state.value.error shouldBe "Login failed"
    }

    @Test
    fun onLoginClick_onFailure_clearsLoadingState() = runTest(testDispatcher) {
        fakeAuth.loginResult = Result.failure(Exception("Server error"))
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("pass")
        viewModel.onLoginClick()

        viewModel.state.value.isLoading shouldBe false
    }
}
