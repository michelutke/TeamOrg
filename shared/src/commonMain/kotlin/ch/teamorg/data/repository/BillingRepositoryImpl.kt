package ch.teamorg.data.repository

import ch.teamorg.repository.BillingRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class SelfServeCreated(
    val clubId: String,
    val teamId: String? = null,
    val setupIntentClientSecret: String,
    val publishableKey: String
)

@Serializable
data class BillingInfo(
    val billingEmail: String,
    val cardBrand: String?,
    val cardLast4: String?,
    val cardExpMonth: Int?,
    val cardExpYear: Int?,
    val currentMemberCount: Int,
    val projectedBilledCount: Int,
    val billingStatus: String,
    val billingMode: String,
    val kind: String
)

@Serializable
data class CardUpdateStart(val setupIntentClientSecret: String, val publishableKey: String)

@Serializable
private data class SelfServeCreateRequest(
    val kind: String,
    val name: String,
    // Server declares sportType non-nullable with a "volleyball" default that only
    // applies when the key is ABSENT — an explicit "sportType": null would 400.
    // kotlinx.serialization's explicitNulls default is true, so coerce client-side.
    val sportType: String,
    val location: String?,
    val billingEmail: String
)

@Serializable
private data class BillingConfirmRequest(val setupIntentId: String)

@Serializable
private data class ConvertRequest(val targetKind: String)

@Serializable
private data class ConvertResponse(val kind: String)

class BillingRepositoryImpl(private val client: HttpClient) : BillingRepository {
    override suspend fun createSelfServe(
        kind: String,
        name: String,
        sportType: String?,
        location: String?,
        billingEmail: String
    ): Result<SelfServeCreated> {
        return try {
            val response = client.post("/clubs/self-serve") {
                setBody(SelfServeCreateRequest(kind, name, sportType ?: "volleyball", location, billingEmail))
            }
            if (response.status == HttpStatusCode.Created) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to create self-serve club: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun confirmBilling(clubId: String, setupIntentId: String): Result<Unit> {
        return try {
            val response = client.post("/clubs/$clubId/billing/confirm") {
                setBody(BillingConfirmRequest(setupIntentId))
            }
            if (response.status == HttpStatusCode.OK) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to confirm billing: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBilling(clubId: String): Result<BillingInfo> {
        return try {
            val response = client.get("/clubs/$clubId/billing")
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch billing: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun startCardUpdate(clubId: String): Result<CardUpdateStart> {
        return try {
            val response = client.post("/clubs/$clubId/billing/update-card")
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to start card update: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun convert(clubId: String, targetKind: String): Result<String> {
        return try {
            val response = client.post("/clubs/$clubId/convert") {
                setBody(ConvertRequest(targetKind))
            }
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body<ConvertResponse>().kind)
            } else {
                Result.failure(Exception("Failed to convert club: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
