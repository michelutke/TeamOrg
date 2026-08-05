package ch.teamorg.ui.fakes

import ch.teamorg.domain.AuthResponse
import ch.teamorg.domain.AuthUser
import ch.teamorg.domain.DeleteAccountResult
import ch.teamorg.domain.LoginRequest
import ch.teamorg.domain.RegisterRequest
import ch.teamorg.repository.AuthRepository

class FakeAuthRepository : AuthRepository {
    var loginResult: Result<AuthResponse> = Result.failure(RuntimeException("Not configured"))
    var registerResult: Result<AuthResponse> = Result.failure(RuntimeException("Not configured"))
    var getMeResult: Result<AuthUser> = Result.success(
        AuthUser(
            userId = "user-123",
            email = "test@test.com",
            displayName = "Test User",
            avatarUrl = null
        )
    )

    override suspend fun login(request: LoginRequest): Result<AuthResponse> = loginResult
    override suspend fun register(request: RegisterRequest): Result<AuthResponse> = registerResult
    override fun logout() {}
    override fun isLoggedIn(): Boolean = false
    override suspend fun getMe(): Result<AuthUser> = getMeResult
    override suspend fun hasTeam(): Boolean = false
    override suspend fun deleteAccount(password: String): DeleteAccountResult = DeleteAccountResult.Success
}
