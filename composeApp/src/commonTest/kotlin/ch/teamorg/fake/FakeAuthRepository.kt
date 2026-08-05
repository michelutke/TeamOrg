package ch.teamorg.fake

import ch.teamorg.domain.AuthResponse
import ch.teamorg.domain.AuthUser
import ch.teamorg.domain.DeleteAccountResult
import ch.teamorg.domain.LoginRequest
import ch.teamorg.domain.RegisterRequest
import ch.teamorg.repository.AuthRepository

class FakeAuthRepository : AuthRepository {

    var loginResult: Result<AuthResponse> = Result.success(
        AuthResponse(token = "token123", userId = "user1", displayName = "Test User", avatarUrl = null)
    )
    var registerResult: Result<AuthResponse> = Result.success(
        AuthResponse(token = "token123", userId = "user1", displayName = "Test User", avatarUrl = null)
    )
    var getMeResult: Result<AuthUser> = Result.success(
        AuthUser(userId = "user1", email = "test@example.com", displayName = "Test User", avatarUrl = null)
    )
    var loggedIn: Boolean = false
    var hasTeamResult: Boolean = false
    var deleteAccountResult: DeleteAccountResult = DeleteAccountResult.Success
    var deleteAccountPasswords: MutableList<String> = mutableListOf()

    var lastLoginRequest: LoginRequest? = null
    var lastRegisterRequest: RegisterRequest? = null
    var logoutCalled: Boolean = false

    fun reset() {
        loginResult = Result.success(
            AuthResponse(token = "token123", userId = "user1", displayName = "Test User", avatarUrl = null)
        )
        registerResult = Result.success(
            AuthResponse(token = "token123", userId = "user1", displayName = "Test User", avatarUrl = null)
        )
        getMeResult = Result.success(
            AuthUser(userId = "user1", email = "test@example.com", displayName = "Test User", avatarUrl = null)
        )
        loggedIn = false
        hasTeamResult = false
        deleteAccountResult = DeleteAccountResult.Success
        deleteAccountPasswords = mutableListOf()
        lastLoginRequest = null
        lastRegisterRequest = null
        logoutCalled = false
    }

    override suspend fun login(request: LoginRequest): Result<AuthResponse> {
        lastLoginRequest = request
        loginResult.onSuccess { loggedIn = true }
        return loginResult
    }

    override suspend fun register(request: RegisterRequest): Result<AuthResponse> {
        lastRegisterRequest = request
        registerResult.onSuccess { loggedIn = true }
        return registerResult
    }

    override fun logout() {
        logoutCalled = true
        loggedIn = false
    }

    override fun isLoggedIn(): Boolean = loggedIn

    override suspend fun getMe(): Result<AuthUser> = getMeResult

    override suspend fun hasTeam(): Boolean = hasTeamResult

    override suspend fun deleteAccount(password: String): DeleteAccountResult {
        deleteAccountPasswords.add(password)
        return deleteAccountResult
    }
}
