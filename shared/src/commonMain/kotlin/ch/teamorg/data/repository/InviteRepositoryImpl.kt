package ch.teamorg.data.repository

import ch.teamorg.domain.InviteDetails
import ch.teamorg.domain.RedeemResult
import ch.teamorg.repository.InviteRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RedeemErrorBody(
    val error: String? = null,
    val invitedEmail: String? = null
)

class InviteRepositoryImpl(private val client: HttpClient) : InviteRepository {

    private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun getInviteDetails(token: String): Result<InviteDetails> {
        return try {
            val response = client.get("/invites/$token")
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch invite details: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun redeemInvite(token: String): RedeemResult {
        return try {
            val response = client.post("/invites/$token/redeem")
            when (response.status) {
                HttpStatusCode.OK,
                HttpStatusCode.Created -> RedeemResult.Success
                HttpStatusCode.Conflict -> RedeemResult.Success // already a member
                HttpStatusCode.Forbidden -> {
                    val body = parseError(response.bodyAsText())
                    RedeemResult.EmailMismatch(body?.invitedEmail)
                }
                HttpStatusCode.Gone -> {
                    val body = parseError(response.bodyAsText())
                    if (body?.error == "inactive") RedeemResult.Inactive else RedeemResult.Expired
                }
                HttpStatusCode.NotFound -> RedeemResult.NotFound
                else -> RedeemResult.Error("Failed to redeem invite: ${response.status}")
            }
        } catch (e: Exception) {
            RedeemResult.Error(e.message ?: "Failed to redeem invite")
        }
    }

    private fun parseError(text: String): RedeemErrorBody? {
        return try {
            errorJson.decodeFromString<RedeemErrorBody>(text)
        } catch (_: Exception) {
            null
        }
    }
}
