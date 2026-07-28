package ch.teamorg.fake

import ch.teamorg.data.repository.BillingInfo
import ch.teamorg.data.repository.CardUpdateStart
import ch.teamorg.data.repository.SelfServeCreated
import ch.teamorg.repository.BillingRepository

class FakeBillingRepository : BillingRepository {

    private fun defaultSelfServeCreated() = SelfServeCreated(
        clubId = "club1",
        teamId = null,
        setupIntentClientSecret = "seti_secret",
        publishableKey = "pk_test"
    )

    private fun defaultBillingInfo() = BillingInfo(
        billingEmail = "billing@example.com",
        cardBrand = "visa",
        cardLast4 = "4242",
        cardExpMonth = 12,
        cardExpYear = 2030,
        currentMemberCount = 1,
        projectedBilledCount = 1,
        billingStatus = "active",
        billingMode = "stripe",
        kind = "team"
    )

    private fun defaultCardUpdateStart() = CardUpdateStart(
        setupIntentClientSecret = "seti_secret",
        publishableKey = "pk_test"
    )

    var createSelfServeResult: Result<SelfServeCreated> = Result.success(defaultSelfServeCreated())
    var confirmBillingResult: Result<Unit> = Result.success(Unit)
    var getBillingResult: Result<BillingInfo> = Result.success(defaultBillingInfo())
    var startCardUpdateResult: Result<CardUpdateStart> = Result.success(defaultCardUpdateStart())
    var convertResult: Result<String> = Result.success("club")

    var lastCreateSelfServeKind: String? = null
    var lastCreateSelfServeName: String? = null
    var lastCreateSelfServeSportType: String? = null
    var lastCreateSelfServeLocation: String? = null
    var lastCreateSelfServeBillingEmail: String? = null
    var lastConfirmBillingClubId: String? = null
    var lastConfirmBillingSetupIntentId: String? = null
    var lastGetBillingClubId: String? = null
    var lastStartCardUpdateClubId: String? = null
    var lastConvertClubId: String? = null
    var lastConvertTargetKind: String? = null

    fun reset() {
        createSelfServeResult = Result.success(defaultSelfServeCreated())
        confirmBillingResult = Result.success(Unit)
        getBillingResult = Result.success(defaultBillingInfo())
        startCardUpdateResult = Result.success(defaultCardUpdateStart())
        convertResult = Result.success("club")
        lastCreateSelfServeKind = null
        lastCreateSelfServeName = null
        lastCreateSelfServeSportType = null
        lastCreateSelfServeLocation = null
        lastCreateSelfServeBillingEmail = null
        lastConfirmBillingClubId = null
        lastConfirmBillingSetupIntentId = null
        lastGetBillingClubId = null
        lastStartCardUpdateClubId = null
        lastConvertClubId = null
        lastConvertTargetKind = null
    }

    override suspend fun createSelfServe(
        kind: String,
        name: String,
        sportType: String?,
        location: String?,
        billingEmail: String
    ): Result<SelfServeCreated> {
        lastCreateSelfServeKind = kind
        lastCreateSelfServeName = name
        lastCreateSelfServeSportType = sportType
        lastCreateSelfServeLocation = location
        lastCreateSelfServeBillingEmail = billingEmail
        return createSelfServeResult
    }

    override suspend fun confirmBilling(clubId: String, setupIntentId: String): Result<Unit> {
        lastConfirmBillingClubId = clubId
        lastConfirmBillingSetupIntentId = setupIntentId
        return confirmBillingResult
    }

    override suspend fun getBilling(clubId: String): Result<BillingInfo> {
        lastGetBillingClubId = clubId
        return getBillingResult
    }

    override suspend fun startCardUpdate(clubId: String): Result<CardUpdateStart> {
        lastStartCardUpdateClubId = clubId
        return startCardUpdateResult
    }

    override suspend fun convert(clubId: String, targetKind: String): Result<String> {
        lastConvertClubId = clubId
        lastConvertTargetKind = targetKind
        return convertResult
    }
}
