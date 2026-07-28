package ch.teamorg.repository

import ch.teamorg.data.repository.BillingInfo
import ch.teamorg.data.repository.CardUpdateStart
import ch.teamorg.data.repository.SelfServeCreated

interface BillingRepository {
    suspend fun createSelfServe(kind: String, name: String, sportType: String?, location: String?, billingEmail: String): Result<SelfServeCreated>
    suspend fun confirmBilling(clubId: String, setupIntentId: String): Result<Unit>
    suspend fun getBilling(clubId: String): Result<BillingInfo>
    suspend fun startCardUpdate(clubId: String): Result<CardUpdateStart>
    suspend fun convert(clubId: String, targetKind: String): Result<String>
}
