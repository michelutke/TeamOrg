package ch.teamorg.ui.login

import ch.teamorg.fake.FakeAuthRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeAuth = FakeAuthRepository()
    private lateinit var viewModel: ForgotPasswordViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuth.reset()
        viewModel = ForgotPasswordViewModel(fakeAuth)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onSubmitClick_validEmail_callsRepositoryAndShowsConfirmation() {
        viewModel.onEmailChange("user@example.com")

        viewModel.onSubmitClick()

        fakeAuth.forgotPasswordEmails shouldBe listOf("user@example.com")
        viewModel.state.value.confirmationShown shouldBe true
        viewModel.state.value.isLoading shouldBe false
        viewModel.state.value.error shouldBe null
    }

    @Test
    fun onSubmitClick_repositoryFails_stillShowsConfirmation() {
        fakeAuth.forgotPasswordResult = Result.failure(RuntimeException("boom"))
        viewModel.onEmailChange("user@example.com")

        viewModel.onSubmitClick()

        fakeAuth.forgotPasswordEmails shouldBe listOf("user@example.com")
        viewModel.state.value.confirmationShown shouldBe true
        viewModel.state.value.error shouldBe null
    }

    @Test
    fun onSubmitClick_emptyEmail_showsErrorAndSkipsRepository() {
        viewModel.onSubmitClick()

        viewModel.state.value.error shouldNotBe null
        viewModel.state.value.confirmationShown shouldBe false
        fakeAuth.forgotPasswordEmails shouldBe emptyList()
    }

    @Test
    fun onSubmitClick_invalidEmail_showsErrorAndSkipsRepository() {
        viewModel.onEmailChange("not-an-email")

        viewModel.onSubmitClick()

        viewModel.state.value.error shouldNotBe null
        viewModel.state.value.confirmationShown shouldBe false
        fakeAuth.forgotPasswordEmails shouldBe emptyList()
    }

    @Test
    fun onEmailChange_clearsError() {
        viewModel.onSubmitClick()
        viewModel.state.value.error shouldNotBe null

        viewModel.onEmailChange("a")

        viewModel.state.value.error shouldBe null
    }
}
