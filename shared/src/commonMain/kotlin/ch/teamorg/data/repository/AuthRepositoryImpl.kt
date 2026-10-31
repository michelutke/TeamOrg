package ch.teamorg.data.repository

import ch.teamorg.data.PushRegistration
import ch.teamorg.domain.AuthResponse
import ch.teamorg.domain.AuthUser
import ch.teamorg.domain.DeleteAccountConflict
import ch.teamorg.domain.DeleteAccountRequest
import ch.teamorg.domain.DeleteAccountResult
import ch.teamorg.domain.LoginRequest
import ch.teamorg.domain.RegisterRequest
import ch.teamorg.domain.UserRoles
import ch.teamorg.preferences.UserPreferences
import ch.teamorg.repository.AuthRepository
import ch.teamorg.repository.EmailAlreadyRegisteredException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

class AuthRepositoryImpl(
    private val client: HttpClient,
    private val userPreferences: UserPreferences
) : AuthRepository {

    override suspend fun register(request: RegisterRequest): Result<AuthResponse> {
        return try {
            val response = client.post("/auth/register") {
                setBody(request)
            }
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                val authResponse = response.body<AuthResponse>()
                userPreferences.saveToken(authResponse.token)
                userPreferences.saveUserId(authResponse.userId)
                PushRegistration.login(authResponse.userId)
                Result.success(authResponse)
            } else if (response.status == HttpStatusCode.Conflict) {
                Result.failure(EmailAlreadyRegisteredException())
            } else {
                Result.failure(Exception("Registration failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(request: LoginRequest): Result<AuthResponse> {
        return try {
            val response = client.post("/auth/login") {
                setBody(request)
            }
            if (response.status == HttpStatusCode.OK) {
                val authResponse = response.body<AuthResponse>()
                userPreferences.saveToken(authResponse.token)
                userPreferences.saveUserId(authResponse.userId)
                PushRegistration.login(authResponse.userId)
                Result.success(authResponse)
            } else {
                Result.failure(Exception("Login failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun logout() {
        PushRegistration.logout()
        userPreferences.clearToken()
    }

    override fun isLoggedIn(): Boolean {
        return userPreferences.getToken() != null
    }

    override suspend fun hasTeam(): Boolean {
        return try {
            val response = client.get("/auth/me/roles")
            if (response.status == HttpStatusCode.OK) {
                val roles = response.body<UserRoles>()
                roles.teamRoles.isNotEmpty() || roles.clubRoles.isNotEmpty()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getMe(): Result<AuthUser> {
        return try {
            val response = client.get("/auth/me")
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body<AuthUser>())
            } else {
                if (response.status == HttpStatusCode.Unauthorized) {
                    logout()
                }
                Result.failure(Exception("Failed to get user profile: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(password: String): DeleteAccountResult {
        return try {
            val response = client.delete("/auth/me") {
                setBody(DeleteAccountRequest(password))
            }
            when (response.status) {
                HttpStatusCode.NoContent, HttpStatusCode.OK -> {
                    // The token is dead server-side regardless; leaving it on disk would only
                    // produce confusing 401s.
                    logout()
                    DeleteAccountResult.Success
                }
                HttpStatusCode.Unauthorized -> DeleteAccountResult.InvalidPassword
                HttpStatusCode.Conflict -> {
                    val clubs = runCatching { response.body<DeleteAccountConflict>().clubs }
                        .getOrDefault(emptyList())
                    DeleteAccountResult.OwnsClubs(clubs)
                }
                else -> DeleteAccountResult.Error("Delete failed: ${response.status}")
            }
        } catch (e: Exception) {
            DeleteAccountResult.Error(e.message ?: "Delete failed")
        }
    }
}
