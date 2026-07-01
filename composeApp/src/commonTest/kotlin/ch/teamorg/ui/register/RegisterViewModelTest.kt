package ch.teamorg.ui.register

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
class RegisterViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeAuth = FakeAuthRepository()
    private lateinit var viewModel: RegisterViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuth.reset()
        viewModel = RegisterViewModel(fakeAuth)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region — initial state

    @Test
    fun state_initially_isEmpty() {
        val state = viewModel.state.value
        state.displayName shouldBe ""
        state.email shouldBe ""
        state.password shouldBe ""
        state.confirmPassword shouldBe ""
        state.isLoading shouldBe false
        state.error shouldBe null
    }

    // region — field updates

    @Test
    fun onDisplayNameChange_updatesDisplayNameAndClearsError() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem()
            viewModel.onDisplayNameChange("Alice")
            awaitItem().displayName shouldBe "Alice"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onEmailChange_updatesEmailAndClearsError() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem()
            viewModel.onEmailChange("alice@example.com")
            awaitItem().email shouldBe "alice@example.com"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onPasswordChange_updatesPasswordAndClearsError() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem()
            viewModel.onPasswordChange("password1")
            awaitItem().password shouldBe "password1"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onConfirmPasswordChange_updatesConfirmPasswordAndClearsError() = runTest(testDispatcher) {
        viewModel.state.test {
            awaitItem()
            viewModel.onConfirmPasswordChange("password1")
            awaitItem().confirmPassword shouldBe "password1"
            cancelAndIgnoreRemainingEvents()
        }
    }

    // region — validation: blank fields

    @Test
    fun onRegisterClick_withAllBlankFields_setsError() {
        viewModel.onRegisterClick()
        viewModel.state.value.error shouldBe "Please fill in all fields"
    }

    @Test
    fun onRegisterClick_withBlankDisplayName_setsError() {
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("password1")
        viewModel.onConfirmPasswordChange("password1")
        viewModel.onRegisterClick()
        viewModel.state.value.error shouldBe "Please fill in all fields"
    }

    @Test
    fun onRegisterClick_withBlankEmail_setsError() {
        viewModel.onDisplayNameChange("Alice")
        viewModel.onPasswordChange("password1")
        viewModel.onConfirmPasswordChange("password1")
        viewModel.onRegisterClick()
        viewModel.state.value.error shouldBe "Please fill in all fields"
    }

    @Test
    fun onRegisterClick_withBlankPassword_setsError() {
        viewModel.onDisplayNameChange("Alice")
        viewModel.onEmailChange("a@b.com")
        viewModel.onConfirmPasswordChange("password1")
        viewModel.onRegisterClick()
        viewModel.state.value.error shouldBe "Please fill in all fields"
    }

    @Test
    fun onRegisterClick_withBlankConfirmPassword_setsError() {
        viewModel.onDisplayNameChange("Alice")
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("password1")
        viewModel.onRegisterClick()
        viewModel.state.value.error shouldBe "Please fill in all fields"
    }

    // region — validation: password length

    @Test
    fun onRegisterClick_withPasswordShorterThan8Chars_setsError() {
        viewModel.onDisplayNameChange("Alice")
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("short")
        viewModel.onConfirmPasswordChange("short")
        viewModel.onRegisterClick()
        viewModel.state.value.error shouldBe "Password must be at least 8 characters"
    }

    @Test
    fun onRegisterClick_withPasswordExactly7Chars_setsError() {
        viewModel.onDisplayNameChange("Alice")
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("1234567")
        viewModel.onConfirmPasswordChange("1234567")
        viewModel.onRegisterClick()
        viewModel.state.value.error shouldBe "Password must be at least 8 characters"
    }

    @Test
    fun onRegisterClick_withPasswordExactly8Chars_doesNotSetLengthError() {
        viewModel.onDisplayNameChange("Alice")
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("12345678")
        viewModel.onConfirmPasswordChange("12345678")
        viewModel.onRegisterClick()
        viewModel.state.value.error shouldBe null
    }

    // region — validation: password mismatch

    @Test
    fun onRegisterClick_withMismatchedPasswords_setsError() {
        viewModel.onDisplayNameChange("Alice")
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("password1")
        viewModel.onConfirmPasswordChange("password2")
        viewModel.onRegisterClick()
        viewModel.state.value.error shouldBe "Passwords do not match"
    }

    // region — happy path

    @Test
    fun onRegisterClick_withValidFields_emitsRegisterSuccess() = runTest(testDispatcher) {
        fillValidFields()
        viewModel.registerSuccess.test {
            viewModel.onRegisterClick()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onRegisterClick_withValidFields_clearsLoadingAndError() = runTest(testDispatcher) {
        fillValidFields()
        viewModel.onRegisterClick()

        val state = viewModel.state.value
        state.isLoading shouldBe false
        state.error shouldBe null
    }

    @Test
    fun onRegisterClick_passesCorrectDataToRepository() = runTest(testDispatcher) {
        fillValidFields()
        viewModel.onRegisterClick()

        fakeAuth.lastRegisterRequest?.email shouldBe "alice@example.com"
        fakeAuth.lastRegisterRequest?.displayName shouldBe "Alice"
        fakeAuth.lastRegisterRequest?.password shouldBe "securePass1"
    }

    // region — error path

    @Test
    fun onRegisterClick_onRepositoryFailure_setsErrorMessage() = runTest(testDispatcher) {
        fakeAuth.registerResult = Result.failure(Exception("Email already exists"))
        fillValidFields()
        viewModel.onRegisterClick()

        viewModel.state.value.error shouldBe "Email already exists"
    }

    @Test
    fun onRegisterClick_onRepositoryFailureWithNullMessage_setsDefaultError() = runTest(testDispatcher) {
        fakeAuth.registerResult = Result.failure(Exception())
        fillValidFields()
        viewModel.onRegisterClick()

        viewModel.state.value.error shouldNotBe null
        viewModel.state.value.error shouldBe "Registration failed"
    }

    @Test
    fun onRegisterClick_onFailure_clearsLoadingState() = runTest(testDispatcher) {
        fakeAuth.registerResult = Result.failure(Exception("Server error"))
        fillValidFields()
        viewModel.onRegisterClick()

        viewModel.state.value.isLoading shouldBe false
    }

    // region — helpers

    private fun fillValidFields() {
        viewModel.onDisplayNameChange("Alice")
        viewModel.onEmailChange("alice@example.com")
        viewModel.onPasswordChange("securePass1")
        viewModel.onConfirmPasswordChange("securePass1")
    }
}
