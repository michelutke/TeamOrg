package ch.teamorg.repository

import ch.teamorg.domain.AuthResponse
import ch.teamorg.domain.AuthUser
import ch.teamorg.domain.DeleteAccountResult
import ch.teamorg.domain.LoginRequest
import ch.teamorg.domain.RegisterRequest

interface AuthRepository {
    suspend fun register(request: RegisterRequest): Result<AuthResponse>
    suspend fun login(request: LoginRequest): Result<AuthResponse>
    fun logout()
    fun isLoggedIn(): Boolean
    suspend fun getMe(): Result<AuthUser>
    suspend fun hasTeam(): Boolean
    suspend fun deleteAccount(password: String): DeleteAccountResult
}
