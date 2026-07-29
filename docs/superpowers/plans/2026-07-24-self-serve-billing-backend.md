# Self-Serve Onboarding + Billing — Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backend for self-serve club/team creation with Stripe card capture, CHF 2/member/year subscription billing, anti-gaming member sampling, frozen-club enforcement, and team↔club conversion.

**Architecture:** Wrapper-club model (`kind = club|team`; a "team" is a club with one team, UI hides club layer). Stripe Subscription per club (yearly, anchored Jan 1 Europe/Zurich, per-seat CHF 2); cron sets seat quantity from `max(Dec 31 snapshot, median of Oct–Dec samples)`. Webhooks drive `billingStatus`; `frozen` blocks writes via middleware. All Stripe access behind a `StripeService` interface so tests swap in a fake via the existing Koin-override pattern.

**Tech Stack:** Kotlin/Ktor, Exposed, Flyway (Postgres), Koin, stripe-java 29.x, kotlin-test + testcontainers (existing `IntegrationTestBase`).

**Spec:** `docs/superpowers/specs/2026-07-24-self-serve-onboarding-billing-design.md`

## Global Constraints

- JVM 21, Kotlin, existing code style (route extension functions, repository interface + `Impl`, Koin `single`).
- Migrations live in `server/src/main/resources/db/migrations/`, next free number is **V16**.
- Existing clubs must migrate to `kind='club'`, `billing_mode='manual'`, `billing_status='active'`, `owner_user_id=NULL` — zero behavior change for them.
- Only `billing_mode='stripe'` clubs are ever sampled, quantity-updated, or frozen.
- Billed member count = distinct non-provisional users holding any ClubRole in the club or any TeamRole in a non-archived team of the club.
- All id-bearing endpoints verify caller role/membership (no-IDOR rule). Owner-only billing/convert = caller id equals `clubs.owner_user_id`.
- Timezone for all billing dates: `Europe/Zurich`.
- Config keys (env-injected, never committed): `stripe.secret-key`, `stripe.webhook-secret`, `stripe.price-id`. Document in `.env.example` / SETUP.md.
- Run tests with `./gradlew :server:test`. Commit after each task. No Co-Authored-By lines in commits.
- New Stripe Price is created once, manually, in the Stripe dashboard: product "teamorg membership", CHF 2.00 / year, per-unit licensed pricing → its `price_...` id goes into `stripe.price-id`.

---

### Task 1: Schema — migration V16 + Exposed tables

**Files:**
- Create: `server/src/main/resources/db/migrations/V16__self_serve_billing.sql`
- Modify: `server/src/main/kotlin/ch/teamorg/db/tables/ClubsTable.kt`
- Create: `server/src/main/kotlin/ch/teamorg/db/tables/BillingTables.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/infra/DatabaseFactory.kt` (H2 SchemaUtils list — add new tables)
- Test: `server/src/test/kotlin/ch/teamorg/billing/BillingSchemaTest.kt`

**Interfaces:**
- Consumes: existing `ClubsTable`, `UsersTable`.
- Produces: `ClubsTable.kind/ownerUserId/billingMode/billingStatus` columns; `ClubBillingTable`, `MemberCountSamplesTable`, `BillingEventsTable` Exposed objects (exact columns below) used by every later task.

- [ ] **Step 1: Write the failing test**

```kotlin
package ch.teamorg.billing

import ch.teamorg.db.tables.ClubBillingTable
import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.test.IntegrationTestBase
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class BillingSchemaTest : IntegrationTestBase() {

    @Test
    fun `new billing columns and tables exist with correct defaults`() = withTeamorgTestApplication {
        val clubId = transaction {
            ClubsTable.insert {
                it[name] = "Schema Test Club"
            } get ClubsTable.id
        }
        transaction {
            val row = ClubsTable.selectAll().where { ClubsTable.id eq clubId }.single()
            assertEquals("club", row[ClubsTable.kind])
            assertEquals("manual", row[ClubsTable.billingMode])
            assertEquals("active", row[ClubsTable.billingStatus])
            assertEquals(null, row[ClubsTable.ownerUserId])

            ClubBillingTable.insert {
                it[ClubBillingTable.clubId] = clubId
                it[stripeCustomerId] = "cus_test"
                it[billingEmail] = "treasurer@example.com"
            }
            val billing = ClubBillingTable.selectAll().where { ClubBillingTable.clubId eq clubId }.single()
            assertEquals("cus_test", billing[ClubBillingTable.stripeCustomerId])
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.BillingSchemaTest"`
Expected: compile FAIL — `kind`, `billingMode`, `ClubBillingTable` unresolved.

- [ ] **Step 3: Write migration V16**

```sql
-- V16__self_serve_billing.sql
ALTER TABLE clubs ADD COLUMN kind TEXT NOT NULL DEFAULT 'club';           -- club | team
ALTER TABLE clubs ADD COLUMN owner_user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE clubs ADD COLUMN billing_mode TEXT NOT NULL DEFAULT 'manual'; -- stripe | manual | free
ALTER TABLE clubs ADD COLUMN billing_status TEXT NOT NULL DEFAULT 'active'; -- active | past_due | frozen
-- clubs.status additionally allows 'pending' (self-serve club before card setup succeeds)

CREATE TABLE club_billing (
    club_id UUID PRIMARY KEY REFERENCES clubs(id) ON DELETE CASCADE,
    stripe_customer_id TEXT NOT NULL UNIQUE,
    stripe_subscription_id TEXT NULL,
    setup_intent_id TEXT NULL,
    billing_email TEXT NOT NULL,
    card_brand TEXT NULL,
    card_last4 TEXT NULL,
    card_exp_month INT NULL,
    card_exp_year INT NULL,
    next_sample_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE member_count_samples (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id UUID NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    sampled_at TIMESTAMP NOT NULL,
    member_count INT NOT NULL
);
CREATE INDEX idx_member_count_samples_club_time ON member_count_samples(club_id, sampled_at);

CREATE TABLE billing_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stripe_event_id TEXT NOT NULL UNIQUE,
    club_id UUID NULL REFERENCES clubs(id) ON DELETE SET NULL,
    type TEXT NOT NULL,
    payload TEXT NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT now()
);
```

- [ ] **Step 4: Extend `ClubsTable` and create `BillingTables.kt`**

In `ClubsTable.kt`, after `val status = ...` add:

```kotlin
    val kind = text("kind").default("club") // club | team
    val ownerUserId = uuid("owner_user_id").references(UsersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val billingMode = text("billing_mode").default("manual") // stripe | manual | free
    val billingStatus = text("billing_status").default("active") // active | past_due | frozen
```

New file `BillingTables.kt`:

```kotlin
package ch.teamorg.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object ClubBillingTable : Table("club_billing") {
    val clubId = uuid("club_id").references(ClubsTable.id, onDelete = ReferenceOption.CASCADE)
    val stripeCustomerId = text("stripe_customer_id").uniqueIndex()
    val stripeSubscriptionId = text("stripe_subscription_id").nullable()
    val setupIntentId = text("setup_intent_id").nullable()
    val billingEmail = text("billing_email")
    val cardBrand = text("card_brand").nullable()
    val cardLast4 = text("card_last4").nullable()
    val cardExpMonth = integer("card_exp_month").nullable()
    val cardExpYear = integer("card_exp_year").nullable()
    val nextSampleAt = timestamp("next_sample_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(clubId)
}

object MemberCountSamplesTable : Table("member_count_samples") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val clubId = uuid("club_id").references(ClubsTable.id, onDelete = ReferenceOption.CASCADE)
    val sampledAt = timestamp("sampled_at")
    val memberCount = integer("member_count")

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_member_count_samples_club_time", false, clubId, sampledAt)
    }
}

object BillingEventsTable : Table("billing_events") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val stripeEventId = text("stripe_event_id").uniqueIndex()
    val clubId = uuid("club_id").references(ClubsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val type = text("type")
    val payload = text("payload")
    val processedAt = timestamp("processed_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}
```

In `DatabaseFactory.kt`, add `ClubBillingTable, MemberCountSamplesTable, BillingEventsTable` to the H2 `SchemaUtils.create(...)` list (imports too).

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.BillingSchemaTest"`
Expected: PASS

- [ ] **Step 6: Run full server tests, then commit**

Run: `./gradlew :server:test`
Expected: all PASS (migration must not break existing flow tests).

```bash
git add server/src/main/resources/db/migrations/V16__self_serve_billing.sql \
  server/src/main/kotlin/ch/teamorg/db/tables/ \
  server/src/main/kotlin/ch/teamorg/infra/DatabaseFactory.kt \
  server/src/test/kotlin/ch/teamorg/billing/BillingSchemaTest.kt
git commit -m "feat(billing): schema for self-serve billing (V16)"
```

---

### Task 2: Billed-count math — pure function, TDD

**Files:**
- Create: `server/src/main/kotlin/ch/teamorg/domain/BilledCount.kt`
- Test: `server/src/test/kotlin/ch/teamorg/billing/BilledCountTest.kt`

**Interfaces:**
- Produces: `fun computeBilledCount(yearEndCount: Int, q4Samples: List<Int>): Int` — `max(yearEndCount, median(q4Samples))`; empty samples → `yearEndCount`; even-sized median rounds up (ceil of the two middle values' average). Used by Task 7 (year-end job) and Task 6 (billing info preview).

- [ ] **Step 1: Write the failing test**

```kotlin
package ch.teamorg.billing

import ch.teamorg.domain.computeBilledCount
import kotlin.test.Test
import kotlin.test.assertEquals

class BilledCountTest {
    @Test fun `no samples returns year-end count`() =
        assertEquals(12, computeBilledCount(12, emptyList()))

    @Test fun `year-end higher than median wins`() =
        assertEquals(20, computeBilledCount(20, listOf(15, 16, 15)))

    @Test fun `median beats gamed year-end drop`() =
        // 30 members all season, treasurer removes 25 members on Dec 30
        assertEquals(30, computeBilledCount(5, listOf(30, 30, 29, 30, 31)))

    @Test fun `median forgives one-off spike`() =
        // guest import spiked one sample to 80
        assertEquals(31, computeBilledCount(30, listOf(30, 31, 80, 31, 30)))

    @Test fun `even sample count rounds median up`() =
        assertEquals(16, computeBilledCount(0, listOf(15, 16)))

    @Test fun `zero members zero samples bills zero`() =
        assertEquals(0, computeBilledCount(0, emptyList()))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.BilledCountTest"`
Expected: compile FAIL — `computeBilledCount` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ch.teamorg.domain

import kotlin.math.ceil
import kotlin.math.max

/**
 * Billed member count for a year: max of the Dec 31 snapshot and the median of the
 * Oct 1–Dec 31 samples. Median resists remove-members-before-Dec-31 gaming while
 * forgiving one-off spikes. Even-length median rounds up.
 */
fun computeBilledCount(yearEndCount: Int, q4Samples: List<Int>): Int {
    if (q4Samples.isEmpty()) return yearEndCount
    val sorted = q4Samples.sorted()
    val median = if (sorted.size % 2 == 1) {
        sorted[sorted.size / 2]
    } else {
        ceil((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0).toInt()
    }
    return max(yearEndCount, median)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.BilledCountTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/domain/BilledCount.kt \
  server/src/test/kotlin/ch/teamorg/billing/BilledCountTest.kt
git commit -m "feat(billing): billed-count formula max(dec31, q4 median)"
```

---

### Task 3: StripeService interface + real impl + fake

**Files:**
- Modify: `gradle/libs.versions.toml` (add stripe), `server/build.gradle.kts`
- Create: `server/src/main/kotlin/ch/teamorg/infra/StripeService.kt`
- Create: `server/src/main/kotlin/ch/teamorg/infra/StripeServiceImpl.kt`
- Create: `server/src/test/kotlin/ch/teamorg/billing/FakeStripeService.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/plugins/Koin.kt`

**Interfaces:**
- Produces (used by Tasks 4–8):

```kotlin
data class SetupIntentResult(val id: String, val clientSecret: String)
data class SetupIntentStatus(val status: String, val paymentMethodId: String?) // status: succeeded | processing | requires_payment_method | ...
data class CardInfo(val brand: String, val last4: String, val expMonth: Int, val expYear: Int)
data class StripeWebhookEvent(val id: String, val type: String, val customerId: String?, val subscriptionStatus: String?, val rawJson: String)

interface StripeService {
    fun createCustomer(email: String, name: String, clubId: UUID): String
    fun createSetupIntent(customerId: String): SetupIntentResult
    fun getSetupIntent(setupIntentId: String): SetupIntentStatus
    fun getCard(paymentMethodId: String): CardInfo?
    fun setDefaultPaymentMethod(customerId: String, paymentMethodId: String)
    fun createYearlySubscription(customerId: String, quantity: Int, anchorEpochSeconds: Long): String
    fun updateSubscriptionQuantity(subscriptionId: String, quantity: Int)
    /** Verifies the Stripe-Signature header; throws IllegalArgumentException on bad signature. */
    fun constructWebhookEvent(payload: String, signatureHeader: String): StripeWebhookEvent
}
```

- Test double: `FakeStripeService` records calls in public lists (`createdSubscriptions`, `quantityUpdates`), returns `seti_fake`/`sub_fake_1` ids, `getSetupIntent` returns whatever `nextSetupIntentStatus` is set to (default `succeeded` with `pm_fake`), `constructWebhookEvent` parses a minimal JSON without signature checking. Loaded via the existing `withTeamorgTestApplication(koinOverride = ...)` mechanism.

- [ ] **Step 1: Add dependency**

`gradle/libs.versions.toml` — under `[versions]`: `stripe = "29.2.0"` (bump to latest stable 29.x at implementation time); under `[libraries]`: `stripe-java = { module = "com.stripe:stripe-java", version.ref = "stripe" }`.
`server/build.gradle.kts` dependencies: `implementation(libs.stripe.java)`.

Run: `./gradlew :server:compileKotlin` — Expected: PASS (dependency resolves).

- [ ] **Step 2: Write `StripeService.kt`** (interface + data classes exactly as in Interfaces block above, package `ch.teamorg.infra`, plus `import java.util.UUID`).

- [ ] **Step 3: Write `StripeServiceImpl.kt`**

```kotlin
package ch.teamorg.infra

import com.stripe.StripeClient
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import com.stripe.param.CustomerUpdateParams
import com.stripe.param.SetupIntentCreateParams
import com.stripe.param.SubscriptionCreateParams
import com.stripe.param.SubscriptionItemUpdateParams
import java.util.UUID

class StripeServiceImpl(
    secretKey: String,
    private val webhookSecret: String,
    private val priceId: String,
) : StripeService {
    private val client = StripeClient(secretKey)

    override fun createCustomer(email: String, name: String, clubId: UUID): String =
        client.customers().create(
            CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .putMetadata("club_id", clubId.toString())
                .build()
        ).id

    override fun createSetupIntent(customerId: String): SetupIntentResult {
        val si = client.setupIntents().create(
            SetupIntentCreateParams.builder()
                .setCustomer(customerId)
                .addPaymentMethodType("card")
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                .build()
        )
        return SetupIntentResult(si.id, si.clientSecret)
    }

    override fun getSetupIntent(setupIntentId: String): SetupIntentStatus {
        val si = client.setupIntents().retrieve(setupIntentId)
        return SetupIntentStatus(si.status, si.paymentMethod)
    }

    override fun getCard(paymentMethodId: String): CardInfo? {
        val pm = client.paymentMethods().retrieve(paymentMethodId)
        val card = pm.card ?: return null
        return CardInfo(card.brand, card.last4, card.expMonth.toInt(), card.expYear.toInt())
    }

    override fun setDefaultPaymentMethod(customerId: String, paymentMethodId: String) {
        client.customers().update(
            customerId,
            CustomerUpdateParams.builder()
                .setInvoiceSettings(
                    CustomerUpdateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(paymentMethodId)
                        .build()
                )
                .build()
        )
    }

    override fun createYearlySubscription(customerId: String, quantity: Int, anchorEpochSeconds: Long): String =
        client.subscriptions().create(
            SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(
                    SubscriptionCreateParams.Item.builder()
                        .setPrice(priceId)
                        .setQuantity(quantity.toLong().coerceAtLeast(1))
                        .build()
                )
                .setBillingCycleAnchor(anchorEpochSeconds)
                .setProrationBehavior(SubscriptionCreateParams.ProrationBehavior.NONE)
                .build()
        ).id

    override fun updateSubscriptionQuantity(subscriptionId: String, quantity: Int) {
        val sub = client.subscriptions().retrieve(subscriptionId)
        val itemId = sub.items.data.first().id
        client.subscriptionItems().update(
            itemId,
            SubscriptionItemUpdateParams.builder()
                .setQuantity(quantity.toLong().coerceAtLeast(1))
                .setProrationBehavior(SubscriptionItemUpdateParams.ProrationBehavior.NONE)
                .build()
        )
    }

    override fun constructWebhookEvent(payload: String, signatureHeader: String): StripeWebhookEvent {
        val event = try {
            Webhook.constructEvent(payload, signatureHeader, webhookSecret)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Stripe webhook signature", e)
        }
        val obj = event.dataObjectDeserializer.`object`.orElse(null)
        val customerId = when (obj) {
            is com.stripe.model.Invoice -> obj.customer
            is com.stripe.model.Subscription -> obj.customer
            else -> null
        }
        val subscriptionStatus = (obj as? com.stripe.model.Subscription)?.status
        return StripeWebhookEvent(event.id, event.type, customerId, subscriptionStatus, payload)
    }
}
```

Note for implementer: stripe-java API surface moves between majors — if `client.subscriptionItems()` etc. differ in the resolved version, adapt mechanically (the interface is the contract; consult the version's own javadoc). NEVER log full card/PM objects.

- [ ] **Step 4: Write `FakeStripeService.kt`** (test sources)

```kotlin
package ch.teamorg.billing

import ch.teamorg.infra.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class FakeStripeService : StripeService {
    val quantityUpdates = mutableListOf<Pair<String, Int>>()
    val createdSubscriptions = mutableListOf<Triple<String, Int, Long>>()
    var nextSetupIntentStatus = SetupIntentStatus("succeeded", "pm_fake")
    private val counter = AtomicInteger(0)

    override fun createCustomer(email: String, name: String, clubId: UUID) = "cus_fake_${counter.incrementAndGet()}"
    override fun createSetupIntent(customerId: String) = SetupIntentResult("seti_fake_${counter.incrementAndGet()}", "seti_secret_fake")
    override fun getSetupIntent(setupIntentId: String) = nextSetupIntentStatus
    override fun getCard(paymentMethodId: String) = CardInfo("visa", "4242", 12, 2030)
    override fun setDefaultPaymentMethod(customerId: String, paymentMethodId: String) {}
    override fun createYearlySubscription(customerId: String, quantity: Int, anchorEpochSeconds: Long): String {
        val id = "sub_fake_${counter.incrementAndGet()}"
        createdSubscriptions += Triple(customerId, quantity, anchorEpochSeconds)
        return id
    }
    override fun updateSubscriptionQuantity(subscriptionId: String, quantity: Int) {
        quantityUpdates += subscriptionId to quantity
    }
    /** Parses {"id":..,"type":..,"customerId":..,"subscriptionStatus":..} — no signature check. */
    override fun constructWebhookEvent(payload: String, signatureHeader: String): StripeWebhookEvent {
        val json = Json.parseToJsonElement(payload).jsonObject
        return StripeWebhookEvent(
            id = json["id"]!!.jsonPrimitive.content,
            type = json["type"]!!.jsonPrimitive.content,
            customerId = json["customerId"]?.jsonPrimitive?.content,
            subscriptionStatus = json["subscriptionStatus"]?.jsonPrimitive?.content,
            rawJson = payload
        )
    }
}
```

- [ ] **Step 5: Register in Koin**

In `Koin.kt` `appModule`, add:

```kotlin
    single<StripeService> {
        val config = environment.config
        StripeServiceImpl(
            secretKey = config.propertyOrNull("stripe.secret-key")?.getString() ?: "",
            webhookSecret = config.propertyOrNull("stripe.webhook-secret")?.getString() ?: "",
            priceId = config.propertyOrNull("stripe.price-id")?.getString() ?: ""
        )
    }
```

(Imports: `ch.teamorg.infra.StripeService`, `ch.teamorg.infra.StripeServiceImpl`.)

- [ ] **Step 6: Compile + full tests, commit**

Run: `./gradlew :server:test`
Expected: PASS (nothing consumes StripeService yet).

```bash
git add gradle/libs.versions.toml server/build.gradle.kts \
  server/src/main/kotlin/ch/teamorg/infra/StripeService.kt \
  server/src/main/kotlin/ch/teamorg/infra/StripeServiceImpl.kt \
  server/src/test/kotlin/ch/teamorg/billing/FakeStripeService.kt \
  server/src/main/kotlin/ch/teamorg/plugins/Koin.kt
git commit -m "feat(billing): StripeService abstraction + stripe-java impl + test fake"
```

---

### Task 4: BillingRepository

**Files:**
- Create: `server/src/main/kotlin/ch/teamorg/domain/repositories/BillingRepository.kt`
- Create: `server/src/main/kotlin/ch/teamorg/domain/repositories/BillingRepositoryImpl.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/plugins/Koin.kt`
- Test: `server/src/test/kotlin/ch/teamorg/billing/BillingRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1 tables.
- Produces (used by Tasks 5–8):

```kotlin
@kotlinx.serialization.Serializable
data class ClubBilling(
    val clubId: String,
    val stripeCustomerId: String,
    val stripeSubscriptionId: String?,
    val setupIntentId: String?,
    val billingEmail: String,
    val cardBrand: String?,
    val cardLast4: String?,
    val cardExpMonth: Int?,
    val cardExpYear: Int?,
)

interface BillingRepository {
    fun createBilling(clubId: UUID, stripeCustomerId: String, billingEmail: String, setupIntentId: String)
    fun setSetupIntent(clubId: UUID, setupIntentId: String)
    fun activate(clubId: UUID, subscriptionId: String?, card: CardInfo?) // sets card meta; subscriptionId only when newly created
    fun findByClubId(clubId: UUID): ClubBilling?
    fun findClubIdByCustomerId(customerId: String): UUID?
    fun setBillingStatus(clubId: UUID, status: String) // clubs.billing_status
    /** Insert webhook/job marker; returns false when stripeEventId already processed (idempotency). */
    fun recordEvent(stripeEventId: String, clubId: UUID?, type: String, payload: String): Boolean
    /** Distinct non-provisional users with any ClubRole in club or TeamRole in a non-archived team of club. */
    fun countActiveMembers(clubId: UUID): Int
    fun insertSample(clubId: UUID, count: Int, sampledAt: Instant)
    fun sampleCountsBetween(clubId: UUID, from: Instant, to: Instant): List<Int>
    fun clubsDueForSampling(now: Instant): List<UUID>   // billing_mode='stripe', status='active', next_sample_at null or <= now
    fun setNextSampleAt(clubId: UUID, at: Instant)
    fun activeStripeClubs(): List<UUID>                  // billing_mode='stripe', clubs.status='active', has subscription
    fun deleteAbandonedPendingClubs(cutoff: Instant): Int // clubs.status='pending' AND created_at < cutoff
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package ch.teamorg.billing

import ch.teamorg.db.tables.*
import ch.teamorg.domain.repositories.BillingRepository
import ch.teamorg.test.IntegrationTestBase
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.getKoin
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class BillingRepositoryTest : IntegrationTestBase() {

    private fun makeUser(email: String, provisional: Boolean = false): UUID = transaction {
        UsersTable.insert {
            it[UsersTable.email] = email
            it[passwordHash] = "x"
            it[displayName] = email
            it[UsersTable.provisional] = provisional
        } get UsersTable.id
    }

    @Test
    fun `countActiveMembers dedupes and excludes provisional and archived teams`() = withTeamorgTestApplication {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        val clubId = transaction { ClubsTable.insert { it[name] = "Count Club" } get ClubsTable.id }
        val teamA = transaction { TeamsTable.insert { it[TeamsTable.clubId] = clubId; it[name] = "A" } get TeamsTable.id }
        val teamArchived = transaction {
            TeamsTable.insert { it[TeamsTable.clubId] = clubId; it[name] = "Old"; it[archivedAt] = Instant.now() } get TeamsTable.id
        }
        val manager = makeUser("m@x.ch")        // club_manager AND player in teamA -> counts once
        val player = makeUser("p@x.ch")          // player in teamA
        val ghost = makeUser("g@x.ch", provisional = true) // provisional -> excluded
        val oldie = makeUser("o@x.ch")           // only in archived team -> excluded
        transaction {
            ClubRolesTable.insert { it[userId] = manager; it[ClubRolesTable.clubId] = clubId; it[role] = "club_manager" }
            TeamRolesTable.insert { it[userId] = manager; it[teamId] = teamA; it[role] = "player" }
            TeamRolesTable.insert { it[userId] = player; it[teamId] = teamA; it[role] = "player" }
            TeamRolesTable.insert { it[userId] = ghost; it[teamId] = teamA; it[role] = "player" }
            TeamRolesTable.insert { it[userId] = oldie; it[teamId] = teamArchived; it[role] = "player" }
        }
        assertEquals(2, repo.countActiveMembers(clubId))
    }

    @Test
    fun `recordEvent is idempotent per stripeEventId`() = withTeamorgTestApplication {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        assertTrue(repo.recordEvent("evt_1", null, "invoice.paid", "{}"))
        assertFalse(repo.recordEvent("evt_1", null, "invoice.paid", "{}"))
    }

    @Test
    fun `sampling due list and samples roundtrip`() = withTeamorgTestApplication {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        val clubId = transaction {
            ClubsTable.insert { it[name] = "S"; it[billingMode] = "stripe"; it[status] = "active" } get ClubsTable.id
        }
        repo.createBilling(clubId, "cus_s", "t@x.ch", "seti_1")
        val now = Instant.parse("2026-11-15T10:00:00Z")
        assertTrue(clubId in repo.clubsDueForSampling(now)) // nextSampleAt null -> due
        repo.insertSample(clubId, 25, now)
        repo.setNextSampleAt(clubId, now.plusSeconds(7 * 86400))
        assertTrue(clubId !in repo.clubsDueForSampling(now))
        assertEquals(listOf(25), repo.sampleCountsBetween(clubId, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.BillingRepositoryTest"`
Expected: compile FAIL — `BillingRepository` unresolved.

- [ ] **Step 3: Implement**

`BillingRepository.kt`: interface + `ClubBilling` data class exactly as the Interfaces block (package `ch.teamorg.domain.repositories`, imports `ch.teamorg.infra.CardInfo`, `java.time.Instant`, `java.util.UUID`).

`BillingRepositoryImpl.kt` (every method inside `transaction { }`, matching existing `*RepositoryImpl` style):

```kotlin
package ch.teamorg.domain.repositories

import ch.teamorg.db.tables.*
import ch.teamorg.infra.CardInfo
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class BillingRepositoryImpl : BillingRepository {

    override fun createBilling(clubId: UUID, stripeCustomerId: String, billingEmail: String, setupIntentId: String) {
        transaction {
            ClubBillingTable.insert {
                it[ClubBillingTable.clubId] = clubId
                it[ClubBillingTable.stripeCustomerId] = stripeCustomerId
                it[ClubBillingTable.billingEmail] = billingEmail
                it[ClubBillingTable.setupIntentId] = setupIntentId
            }
        }
    }

    override fun setSetupIntent(clubId: UUID, setupIntentId: String) {
        transaction {
            ClubBillingTable.update({ ClubBillingTable.clubId eq clubId }) {
                it[ClubBillingTable.setupIntentId] = setupIntentId
                it[updatedAt] = Instant.now()
            }
        }
    }

    override fun activate(clubId: UUID, subscriptionId: String?, card: CardInfo?) {
        transaction {
            ClubBillingTable.update({ ClubBillingTable.clubId eq clubId }) {
                if (subscriptionId != null) it[stripeSubscriptionId] = subscriptionId
                if (card != null) {
                    it[cardBrand] = card.brand
                    it[cardLast4] = card.last4
                    it[cardExpMonth] = card.expMonth
                    it[cardExpYear] = card.expYear
                }
                it[updatedAt] = Instant.now()
            }
        }
    }

    override fun findByClubId(clubId: UUID): ClubBilling? = transaction {
        ClubBillingTable.selectAll().where { ClubBillingTable.clubId eq clubId }.singleOrNull()?.toClubBilling()
    }

    override fun findClubIdByCustomerId(customerId: String): UUID? = transaction {
        ClubBillingTable.selectAll().where { ClubBillingTable.stripeCustomerId eq customerId }
            .singleOrNull()?.get(ClubBillingTable.clubId)
    }

    override fun setBillingStatus(clubId: UUID, status: String) {
        transaction {
            ClubsTable.update({ ClubsTable.id eq clubId }) {
                it[billingStatus] = status
                it[updatedAt] = Instant.now()
            }
        }
    }

    override fun recordEvent(stripeEventId: String, clubId: UUID?, type: String, payload: String): Boolean = transaction {
        val existing = BillingEventsTable.selectAll()
            .where { BillingEventsTable.stripeEventId eq stripeEventId }.count()
        if (existing > 0) return@transaction false
        BillingEventsTable.insert {
            it[BillingEventsTable.stripeEventId] = stripeEventId
            it[BillingEventsTable.clubId] = clubId
            it[BillingEventsTable.type] = type
            it[BillingEventsTable.payload] = payload
        }
        true
    }

    override fun countActiveMembers(clubId: UUID): Int = transaction {
        val clubRoleUsers = ClubRolesTable
            .join(UsersTable, JoinType.INNER, ClubRolesTable.userId, UsersTable.id)
            .select(ClubRolesTable.userId)
            .where { (ClubRolesTable.clubId eq clubId) and (UsersTable.provisional eq false) }
            .map { it[ClubRolesTable.userId] }
        val teamRoleUsers = TeamRolesTable
            .join(TeamsTable, JoinType.INNER, TeamRolesTable.teamId, TeamsTable.id)
            .join(UsersTable, JoinType.INNER, TeamRolesTable.userId, UsersTable.id)
            .select(TeamRolesTable.userId)
            .where {
                (TeamsTable.clubId eq clubId) and TeamsTable.archivedAt.isNull() and (UsersTable.provisional eq false)
            }
            .mapNotNull { it[TeamRolesTable.userId] }
        (clubRoleUsers + teamRoleUsers).distinct().size
    }

    override fun insertSample(clubId: UUID, count: Int, sampledAt: Instant) {
        transaction {
            MemberCountSamplesTable.insert {
                it[MemberCountSamplesTable.clubId] = clubId
                it[MemberCountSamplesTable.sampledAt] = sampledAt
                it[memberCount] = count
            }
        }
    }

    override fun sampleCountsBetween(clubId: UUID, from: Instant, to: Instant): List<Int> = transaction {
        MemberCountSamplesTable.selectAll()
            .where {
                (MemberCountSamplesTable.clubId eq clubId) and
                    (MemberCountSamplesTable.sampledAt greaterEq from) and
                    (MemberCountSamplesTable.sampledAt lessEq to)
            }
            .map { it[MemberCountSamplesTable.memberCount] }
    }

    override fun clubsDueForSampling(now: Instant): List<UUID> = transaction {
        ClubsTable.join(ClubBillingTable, JoinType.INNER, ClubsTable.id, ClubBillingTable.clubId)
            .select(ClubsTable.id)
            .where {
                (ClubsTable.billingMode eq "stripe") and (ClubsTable.status eq "active") and
                    (ClubBillingTable.nextSampleAt.isNull() or (ClubBillingTable.nextSampleAt lessEq now))
            }
            .map { it[ClubsTable.id] }
    }

    override fun setNextSampleAt(clubId: UUID, at: Instant) {
        transaction {
            ClubBillingTable.update({ ClubBillingTable.clubId eq clubId }) {
                it[nextSampleAt] = at
                it[updatedAt] = Instant.now()
            }
        }
    }

    override fun activeStripeClubs(): List<UUID> = transaction {
        ClubsTable.join(ClubBillingTable, JoinType.INNER, ClubsTable.id, ClubBillingTable.clubId)
            .select(ClubsTable.id)
            .where {
                (ClubsTable.billingMode eq "stripe") and (ClubsTable.status eq "active") and
                    ClubBillingTable.stripeSubscriptionId.isNotNull()
            }
            .map { it[ClubsTable.id] }
    }

    override fun deleteAbandonedPendingClubs(cutoff: Instant): Int = transaction {
        ClubsTable.deleteWhere { (status eq "pending") and (createdAt less cutoff) }
    }

    private fun ResultRow.toClubBilling() = ClubBilling(
        clubId = this[ClubBillingTable.clubId].toString(),
        stripeCustomerId = this[ClubBillingTable.stripeCustomerId],
        stripeSubscriptionId = this[ClubBillingTable.stripeSubscriptionId],
        setupIntentId = this[ClubBillingTable.setupIntentId],
        billingEmail = this[ClubBillingTable.billingEmail],
        cardBrand = this[ClubBillingTable.cardBrand],
        cardLast4 = this[ClubBillingTable.cardLast4],
        cardExpMonth = this[ClubBillingTable.cardExpMonth],
        cardExpYear = this[ClubBillingTable.cardExpYear],
    )
}
```

Koin: `single<BillingRepository> { BillingRepositoryImpl() }`.

(Exposed API note: this codebase's Exposed version may use `select { }` instead of `selectAll().where { }` — match whatever `ClubRepositoryImpl.kt` uses.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.BillingRepositoryTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/domain/repositories/BillingRepository.kt \
  server/src/main/kotlin/ch/teamorg/domain/repositories/BillingRepositoryImpl.kt \
  server/src/main/kotlin/ch/teamorg/plugins/Koin.kt \
  server/src/test/kotlin/ch/teamorg/billing/BillingRepositoryTest.kt
git commit -m "feat(billing): BillingRepository with member counting and sampling"
```

---

### Task 5: Self-serve creation + billing confirm endpoints

**Files:**
- Create: `server/src/main/kotlin/ch/teamorg/routes/SelfServeRoutes.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/plugins/Routing.kt` (register `selfServeRoutes()`)
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/ClubRepository.kt` + `ClubRepositoryImpl.kt` (add `createSelfServe`, `setStatus`, `findOwnerId`, `setKind`, `countActiveTeams`)
- Test: `server/src/test/kotlin/ch/teamorg/billing/SelfServeFlowTest.kt`

**Interfaces:**
- Consumes: `StripeService` (Task 3), `BillingRepository` (Task 4), existing `TeamRepository.create`, `UserRepository`.
- Produces:
  - `POST /clubs/self-serve` (jwt) body `{kind: "club"|"team", name, sportType?, location?, billingEmail}` → 201 `{clubId, teamId?, setupIntentClientSecret}`. Club `status='pending'`, `billingMode='stripe'`, `ownerUserId=caller`; caller gets ClubRole `club_manager`; `kind=="team"` also creates team named `name` + TeamRole `coach`.
  - `POST /clubs/{clubId}/billing/confirm` (jwt, owner) body `{setupIntentId}` → verifies SetupIntent `succeeded`, sets default PM, creates subscription (quantity = `countActiveMembers`, anchor = next Jan 1 00:00 Europe/Zurich), sets club `status='active'`, stores card meta → 200 `{status: "active"}`. If already has subscription (card update path): only swaps default PM + card meta.
  - ClubRepository additions used later: `fun findOwnerId(clubId: UUID): UUID?`, `fun setStatus(clubId: UUID, status: String)`, `fun setKind(clubId: UUID, kind: String)`, `fun countActiveTeams(clubId: UUID): Int`, `fun createSelfServe(name: String, sportType: String, location: String?, kind: String, ownerUserId: UUID): UUID` (inserts club with `status='pending'`, `billingMode='stripe'`).
  - Helper produced for Tasks 5/7: `fun nextJanuaryFirstEpochSeconds(nowZurich: java.time.ZonedDateTime): Long` in `SelfServeRoutes.kt` companion — implement as top-level fun in `ch.teamorg.domain` file `BillingDates.kt`: `ZonedDateTime.of(now.year + 1, 1, 1, 0, 0, 0, 0, ZoneId.of("Europe/Zurich")).toEpochSecond()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ch.teamorg.billing

import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.koin.dsl.module
import kotlin.test.*

class SelfServeFlowTest : IntegrationTestBase() {

    private val fake = FakeStripeService()
    private val stripeOverride = module { single<StripeService> { fake } }

    /** Registers user via POST /auth/register, returns JWT. */
    private suspend fun io.ktor.client.HttpClient.register(email: String): String {
        val res = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"$email"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        return Json.parseToJsonElement(res.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    @Test
    fun `create team self-serve then confirm billing activates club`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("owner@x.ch")

        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"team","name":"Volley Crew","billingEmail":"owner@x.ch"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val body = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val clubId = body["clubId"]!!.jsonPrimitive.content
        assertNotNull(body["teamId"], "team kind must auto-create its single team")
        assertEquals("seti_secret_fake", body["setupIntentClientSecret"]!!.jsonPrimitive.content)

        val confirmRes = client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_fake_1"}""")
        }
        assertEquals(HttpStatusCode.OK, confirmRes.status)
        assertEquals(1, fake.createdSubscriptions.size)
        assertEquals(1, fake.createdSubscriptions[0].second) // quantity = 1 member (the owner)
    }

    @Test
    fun `confirm rejected for non-owner`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val ownerToken = client.register("own2@x.ch")
        val strangerToken = client.register("stranger@x.ch")
        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(ownerToken)
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"club","name":"VC Bern","billingEmail":"own2@x.ch"}""")
        }
        val clubId = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["clubId"]!!.jsonPrimitive.content
        val res = client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(strangerToken)
            contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_fake_1"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, res.status)
    }

    @Test
    fun `confirm with unsucceeded setup intent returns 402`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("own3@x.ch")
        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"club","name":"VC Thun","billingEmail":"own3@x.ch"}""")
        }
        val clubId = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["clubId"]!!.jsonPrimitive.content
        fake.nextSetupIntentStatus = ch.teamorg.infra.SetupIntentStatus("requires_payment_method", null)
        val res = client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_fake_1"}""")
        }
        assertEquals(HttpStatusCode.PaymentRequired, res.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.SelfServeFlowTest"`
Expected: FAIL — 404 on `/clubs/self-serve` (route missing).

- [ ] **Step 3: Implement**

`ch.teamorg.domain.BillingDates.kt`:

```kotlin
package ch.teamorg.domain

import java.time.ZoneId
import java.time.ZonedDateTime

val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")

fun nextJanuaryFirstEpochSeconds(now: ZonedDateTime): Long =
    ZonedDateTime.of(now.year + 1, 1, 1, 0, 0, 0, 0, ZURICH).toEpochSecond()
```

ClubRepository additions (interface + Impl, same style as existing methods):

```kotlin
fun createSelfServe(name: String, sportType: String, location: String?, kind: String, ownerUserId: UUID): UUID
fun findOwnerId(clubId: UUID): UUID?
fun setStatus(clubId: UUID, status: String)
fun setKind(clubId: UUID, kind: String)
fun countActiveTeams(clubId: UUID): Int  // teams of club with archivedAt == null
```

`createSelfServe` impl: single `transaction` inserting into `ClubsTable` (`status="pending"`, `billingMode="stripe"`, `billingStatus="active"`, `kind`, `ownerUserId`) and `ClubRolesTable` (`role="club_manager"`, userId=ownerUserId), returns club id.

`SelfServeRoutes.kt`:

```kotlin
package ch.teamorg.routes

import ch.teamorg.domain.ZURICH
import ch.teamorg.domain.nextJanuaryFirstEpochSeconds
import ch.teamorg.domain.repositories.BillingRepository
import ch.teamorg.domain.repositories.ClubRepository
import ch.teamorg.domain.repositories.TeamRepository
import ch.teamorg.infra.StripeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.time.ZonedDateTime
import java.util.UUID

@Serializable
data class SelfServeCreateRequest(
    val kind: String, // club | team
    val name: String,
    val sportType: String = "volleyball",
    val location: String? = null,
    val billingEmail: String,
)

@Serializable
data class SelfServeCreateResponse(val clubId: String, val teamId: String? = null, val setupIntentClientSecret: String)

@Serializable
data class BillingConfirmRequest(val setupIntentId: String)

fun Route.selfServeRoutes() {
    val clubRepository by inject<ClubRepository>()
    val teamRepository by inject<TeamRepository>()
    val billingRepository by inject<BillingRepository>()
    val stripeService by inject<StripeService>()

    authenticate("jwt") {
        post("/clubs/self-serve") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<SelfServeCreateRequest>()
            if (request.kind !in setOf("club", "team")) {
                return@post call.respond(HttpStatusCode.BadRequest, "kind must be club or team")
            }
            if (request.name.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "Name is required")
            if (!request.billingEmail.contains("@")) return@post call.respond(HttpStatusCode.BadRequest, "Valid billing email required")

            val clubId = clubRepository.createSelfServe(request.name, request.sportType, request.location, request.kind, userId)
            var teamId: UUID? = null
            if (request.kind == "team") {
                val team = teamRepository.create(clubId, request.name, null)
                teamId = UUID.fromString(team.id)
                transaction {
                    ch.teamorg.db.tables.TeamRolesTable.insert {
                        it[ch.teamorg.db.tables.TeamRolesTable.userId] = userId
                        it[ch.teamorg.db.tables.TeamRolesTable.teamId] = teamId!!
                        it[role] = "coach"
                    }
                }
            }
            val customerId = stripeService.createCustomer(request.billingEmail, request.name, clubId)
            val setupIntent = stripeService.createSetupIntent(customerId)
            billingRepository.createBilling(clubId, customerId, request.billingEmail, setupIntent.id)
            call.respond(
                HttpStatusCode.Created,
                SelfServeCreateResponse(clubId.toString(), teamId?.toString(), setupIntent.clientSecret)
            )
        }

        post("/clubs/{clubId}/billing/confirm") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val clubId = UUID.fromString(call.parameters["clubId"])
            if (clubRepository.findOwnerId(clubId) != userId) {
                return@post call.respond(HttpStatusCode.Forbidden, "Only the club owner can manage billing")
            }
            val request = call.receive<BillingConfirmRequest>()
            val billing = billingRepository.findByClubId(clubId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "No billing record for club")

            val si = stripeService.getSetupIntent(request.setupIntentId)
            if (si.status != "succeeded" || si.paymentMethodId == null) {
                return@post call.respond(HttpStatusCode.PaymentRequired, "Card setup not completed")
            }
            stripeService.setDefaultPaymentMethod(billing.stripeCustomerId, si.paymentMethodId)
            val card = stripeService.getCard(si.paymentMethodId)

            if (billing.stripeSubscriptionId == null) {
                val quantity = billingRepository.countActiveMembers(clubId)
                val anchor = nextJanuaryFirstEpochSeconds(ZonedDateTime.now(ZURICH))
                val subscriptionId = stripeService.createYearlySubscription(billing.stripeCustomerId, quantity, anchor)
                billingRepository.activate(clubId, subscriptionId, card)
                clubRepository.setStatus(clubId, "active")
            } else {
                billingRepository.activate(clubId, null, card) // card update only
            }
            billingRepository.setBillingStatus(clubId, "active")
            call.respond(HttpStatusCode.OK, mapOf("status" to "active"))
        }
    }
}
```

Register in `Routing.kt`: import + `selfServeRoutes()` after `clubRoutes()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.SelfServeFlowTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Full tests + commit**

Run: `./gradlew :server:test` — Expected: PASS.

```bash
git add server/src/main/kotlin/ch/teamorg/routes/SelfServeRoutes.kt \
  server/src/main/kotlin/ch/teamorg/plugins/Routing.kt \
  server/src/main/kotlin/ch/teamorg/domain/BillingDates.kt \
  server/src/main/kotlin/ch/teamorg/domain/repositories/ClubRepository.kt \
  server/src/main/kotlin/ch/teamorg/domain/repositories/ClubRepositoryImpl.kt \
  server/src/test/kotlin/ch/teamorg/billing/SelfServeFlowTest.kt
git commit -m "feat(billing): self-serve club/team creation with Stripe card setup"
```

---

### Task 6: Billing info, card update, conversion endpoints

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/routes/SelfServeRoutes.kt`
- Test: `server/src/test/kotlin/ch/teamorg/billing/BillingManagementTest.kt`

**Interfaces:**
- Consumes: Tasks 2–5 (`computeBilledCount`, `BillingRepository`, `ClubRepository.setKind/countActiveTeams/findOwnerId`, `FakeStripeService`).
- Produces:
  - `GET /clubs/{clubId}/billing` (owner) → 200 `{billingEmail, cardBrand?, cardLast4?, cardExpMonth?, cardExpYear?, currentMemberCount, projectedBilledCount, billingStatus, billingMode}` — `projectedBilledCount = computeBilledCount(currentMemberCount, samples Oct1–now)`.
  - `POST /clubs/{clubId}/billing/update-card` (owner) → 200 `{setupIntentClientSecret}` (new SetupIntent; completed via existing `/billing/confirm`).
  - `POST /clubs/{clubId}/convert` (owner) body `{targetKind: "club"|"team"}` → team→club always OK; club→team requires exactly 1 active team else 409; same-kind → 200 no-op. Response `{kind}`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ch.teamorg.billing

import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.koin.dsl.module
import kotlin.test.*

class BillingManagementTest : IntegrationTestBase() {

    private val fake = FakeStripeService()
    private val stripeOverride = module { single<StripeService> { fake } }

    private suspend fun io.ktor.client.HttpClient.register(email: String): String {
        val res = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"$email"}""")
        }
        return Json.parseToJsonElement(res.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    private suspend fun io.ktor.client.HttpClient.createAndConfirm(token: String, kind: String, name: String): String {
        val res = post("/clubs/self-serve") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"kind":"$kind","name":"$name","billingEmail":"b@x.ch"}""")
        }
        val clubId = Json.parseToJsonElement(res.bodyAsText()).jsonObject["clubId"]!!.jsonPrimitive.content
        post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_x"}""")
        }
        return clubId
    }

    @Test
    fun `billing info returns card meta and projected count for owner only`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("bi@x.ch")
        val clubId = client.createAndConfirm(token, "team", "Crew")

        val res = client.get("/clubs/$clubId/billing") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, res.status)
        val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        assertEquals("4242", body["cardLast4"]!!.jsonPrimitive.content)
        assertEquals(1, body["currentMemberCount"]!!.jsonPrimitive.int)

        val stranger = client.register("nosy@x.ch")
        assertEquals(HttpStatusCode.Forbidden, client.get("/clubs/$clubId/billing") { bearerAuth(stranger) }.status)
    }

    @Test
    fun `team converts to club and back, club with 2 teams refuses downgrade`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("cv@x.ch")
        val clubId = client.createAndConfirm(token, "team", "Solo")

        var res = client.post("/clubs/$clubId/convert") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"targetKind":"club"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("club", Json.parseToJsonElement(res.bodyAsText()).jsonObject["kind"]!!.jsonPrimitive.content)

        res = client.post("/clubs/$clubId/convert") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"targetKind":"team"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)

        // back to club, add second team, downgrade must 409
        client.post("/clubs/$clubId/convert") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"targetKind":"club"}""")
        }
        client.post("/clubs/$clubId/teams") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"name":"Second"}""")
        }
        res = client.post("/clubs/$clubId/convert") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"targetKind":"team"}""")
        }
        assertEquals(HttpStatusCode.Conflict, res.status)
    }

    @Test
    fun `update-card returns fresh setup intent secret`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("uc@x.ch")
        val clubId = client.createAndConfirm(token, "club", "VC Aare")
        val res = client.post("/clubs/$clubId/billing/update-card") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("seti_secret_fake", Json.parseToJsonElement(res.bodyAsText()).jsonObject["setupIntentClientSecret"]!!.jsonPrimitive.content)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.BillingManagementTest"`
Expected: FAIL — 404 on `/billing` GET / `/convert`.

- [ ] **Step 3: Implement — add to `selfServeRoutes()` inside `authenticate("jwt")`**

```kotlin
        get("/clubs/{clubId}/billing") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val clubId = UUID.fromString(call.parameters["clubId"])
            if (clubRepository.findOwnerId(clubId) != userId) {
                return@get call.respond(HttpStatusCode.Forbidden, "Only the club owner can view billing")
            }
            val billing = billingRepository.findByClubId(clubId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "No billing record for club")
            val club = clubRepository.findById(clubId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Club not found")
            val current = billingRepository.countActiveMembers(clubId)
            val now = ZonedDateTime.now(ZURICH)
            val q4From = ZonedDateTime.of(now.year, 10, 1, 0, 0, 0, 0, ZURICH).toInstant()
            val samples = billingRepository.sampleCountsBetween(clubId, q4From, now.toInstant())
            call.respond(
                BillingInfoResponse(
                    billingEmail = billing.billingEmail,
                    cardBrand = billing.cardBrand,
                    cardLast4 = billing.cardLast4,
                    cardExpMonth = billing.cardExpMonth,
                    cardExpYear = billing.cardExpYear,
                    currentMemberCount = current,
                    projectedBilledCount = ch.teamorg.domain.computeBilledCount(current, samples),
                    billingStatus = club.billingStatus,
                    billingMode = club.billingMode,
                )
            )
        }

        post("/clubs/{clubId}/billing/update-card") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val clubId = UUID.fromString(call.parameters["clubId"])
            if (clubRepository.findOwnerId(clubId) != userId) {
                return@post call.respond(HttpStatusCode.Forbidden, "Only the club owner can manage billing")
            }
            val billing = billingRepository.findByClubId(clubId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "No billing record for club")
            val setupIntent = stripeService.createSetupIntent(billing.stripeCustomerId)
            billingRepository.setSetupIntent(clubId, setupIntent.id)
            call.respond(mapOf("setupIntentClientSecret" to setupIntent.clientSecret))
        }

        post("/clubs/{clubId}/convert") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val clubId = UUID.fromString(call.parameters["clubId"])
            if (clubRepository.findOwnerId(clubId) != userId) {
                return@post call.respond(HttpStatusCode.Forbidden, "Only the club owner can convert")
            }
            val request = call.receive<ConvertRequest>()
            if (request.targetKind !in setOf("club", "team")) {
                return@post call.respond(HttpStatusCode.BadRequest, "targetKind must be club or team")
            }
            if (request.targetKind == "team" && clubRepository.countActiveTeams(clubId) != 1) {
                return@post call.respond(HttpStatusCode.Conflict, "Club must have exactly one active team to become a team")
            }
            clubRepository.setKind(clubId, request.targetKind)
            call.respond(mapOf("kind" to request.targetKind))
        }
```

DTOs (top of file):

```kotlin
@Serializable
data class ConvertRequest(val targetKind: String)

@Serializable
data class BillingInfoResponse(
    val billingEmail: String,
    val cardBrand: String?,
    val cardLast4: String?,
    val cardExpMonth: Int?,
    val cardExpYear: Int?,
    val currentMemberCount: Int,
    val projectedBilledCount: Int,
    val billingStatus: String,
    val billingMode: String,
)
```

`ClubRepository.findById` DTO must expose `billingStatus` and `billingMode` — extend the existing club response data class with these two String fields (default-populated in the row mapper).

Note: test's second scenario needs a club_manager to add a team — the owner already holds `club_manager` (Task 5), and existing `POST /clubs/{clubId}/teams` requires that role, so it works. But the club is `status='pending'`→ set active by confirm — done via `createAndConfirm`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.BillingManagementTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Full tests + commit**

Run: `./gradlew :server:test` — Expected: PASS (WireContractTest may need the two new club DTO fields — update contract fixtures if it fails).

```bash
git add server/src/main/kotlin/ch/teamorg/routes/SelfServeRoutes.kt \
  server/src/main/kotlin/ch/teamorg/domain/repositories/ \
  server/src/test/kotlin/ch/teamorg/billing/BillingManagementTest.kt
git commit -m "feat(billing): billing info, card update, team-club conversion"
```

---

### Task 7: Stripe webhook endpoint

**Files:**
- Create: `server/src/main/kotlin/ch/teamorg/routes/StripeWebhookRoutes.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/plugins/Routing.kt`
- Test: `server/src/test/kotlin/ch/teamorg/billing/StripeWebhookTest.kt`

**Interfaces:**
- Consumes: `StripeService.constructWebhookEvent`, `BillingRepository.recordEvent/findClubIdByCustomerId/setBillingStatus`.
- Produces: `POST /stripe/webhook` (NO jwt — signature is the auth). Status transitions:
  - `invoice.paid` → `active`
  - `invoice.payment_failed` → `past_due`
  - `customer.subscription.updated` with `subscriptionStatus` in `{unpaid, canceled}` → `frozen`
  - Unknown types → 200 ignored. Bad signature → 400. Duplicate event id → 200 (already processed).

- [ ] **Step 1: Write the failing test**

```kotlin
package ch.teamorg.billing

import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import java.util.UUID
import kotlin.test.*

class StripeWebhookTest : IntegrationTestBase() {

    private val fake = FakeStripeService()
    private val stripeOverride = module { single<StripeService> { fake } }

    private suspend fun io.ktor.client.HttpClient.register(email: String): String {
        val res = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"$email"}""")
        }
        return Json.parseToJsonElement(res.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    private fun billingStatus(clubId: String): String = transaction {
        ClubsTable.selectAll().where { ClubsTable.id eq UUID.fromString(clubId) }.single()[ClubsTable.billingStatus]
    }

    @Test
    fun `payment_failed then subscription unpaid freezes club, invoice paid recovers`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("wh@x.ch")
        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"kind":"club","name":"VC Hook","billingEmail":"wh@x.ch"}""")
        }
        val clubId = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["clubId"]!!.jsonPrimitive.content
        client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_x"}""")
        }
        val customerId = "cus_fake_1" // first customer the fake created

        suspend fun hook(id: String, type: String, subStatus: String? = null): HttpStatusCode =
            client.post("/stripe/webhook") {
                contentType(ContentType.Application.Json)
                header("Stripe-Signature", "t=1,v1=fake")
                setBody(buildString {
                    append("""{"id":"$id","type":"$type","customerId":"$customerId"""")
                    if (subStatus != null) append(""","subscriptionStatus":"$subStatus"""")
                    append("}")
                })
            }.status

        assertEquals(HttpStatusCode.OK, hook("evt_a", "invoice.payment_failed"))
        assertEquals("past_due", billingStatus(clubId))
        assertEquals(HttpStatusCode.OK, hook("evt_b", "customer.subscription.updated", "unpaid"))
        assertEquals("frozen", billingStatus(clubId))
        assertEquals(HttpStatusCode.OK, hook("evt_c", "invoice.paid"))
        assertEquals("active", billingStatus(clubId))
        // duplicate replay: no state change, still 200
        assertEquals(HttpStatusCode.OK, hook("evt_b", "customer.subscription.updated", "unpaid"))
        assertEquals("active", billingStatus(clubId))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.StripeWebhookTest"`
Expected: FAIL — 404 `/stripe/webhook`.

- [ ] **Step 3: Implement `StripeWebhookRoutes.kt`**

```kotlin
package ch.teamorg.routes

import ch.teamorg.domain.repositories.BillingRepository
import ch.teamorg.infra.StripeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StripeWebhook")

fun Route.stripeWebhookRoutes() {
    val billingRepository by inject<BillingRepository>()
    val stripeService by inject<StripeService>()

    post("/stripe/webhook") {
        val payload = call.receiveText()
        val signature = call.request.header("Stripe-Signature")
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing signature")
        val event = try {
            stripeService.constructWebhookEvent(payload, signature)
        } catch (e: IllegalArgumentException) {
            logger.warn("Rejected Stripe webhook with invalid signature")
            return@post call.respond(HttpStatusCode.BadRequest, "Invalid signature")
        }

        val clubId = event.customerId?.let { billingRepository.findClubIdByCustomerId(it) }
        if (!billingRepository.recordEvent(event.id, clubId, event.type, event.rawJson)) {
            return@post call.respond(HttpStatusCode.OK, "Already processed")
        }
        if (clubId == null) {
            logger.info("Stripe event ${event.type} without matching club, ignoring")
            return@post call.respond(HttpStatusCode.OK, "No club")
        }

        when (event.type) {
            "invoice.paid" -> billingRepository.setBillingStatus(clubId, "active")
            "invoice.payment_failed" -> billingRepository.setBillingStatus(clubId, "past_due")
            "customer.subscription.updated" ->
                if (event.subscriptionStatus in setOf("unpaid", "canceled")) {
                    billingRepository.setBillingStatus(clubId, "frozen")
                }
            else -> logger.info("Ignoring Stripe event type ${event.type}")
        }
        call.respond(HttpStatusCode.OK, "OK")
    }
}
```

Register in `Routing.kt` (outside any auth block — it's already top-level there): `stripeWebhookRoutes()`.

Ops note (document in SETUP.md in this step): Stripe dashboard → webhook endpoint `https://<api-host>/stripe/webhook`, events `invoice.paid`, `invoice.payment_failed`, `customer.subscription.updated`; dunning: Settings → Billing → Automatic collection: Smart Retries ~3 weeks, then "mark subscription unpaid" (this is what triggers `frozen`).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.StripeWebhookTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/routes/StripeWebhookRoutes.kt \
  server/src/main/kotlin/ch/teamorg/plugins/Routing.kt SETUP.md \
  server/src/test/kotlin/ch/teamorg/billing/StripeWebhookTest.kt
git commit -m "feat(billing): Stripe webhook with idempotency and status transitions"
```

---

### Task 8: Frozen-club write enforcement

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/middleware/RoleMiddleware.kt` (add `requireClubWritable`)
- Modify: `server/src/main/kotlin/ch/teamorg/routes/ClubRoutes.kt`, `TeamRoutes.kt`, `EventRoutes.kt`, `InviteRoutes.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/ClubRepository.kt` + Impl (add `isFrozen(clubId): Boolean`, `findClubIdForTeam(teamId): UUID?` if not present on TeamRepository)
- Test: `server/src/test/kotlin/ch/teamorg/billing/FrozenClubEnforcementTest.kt`

**Interfaces:**
- Consumes: `ClubsTable.billingStatus`.
- Produces: `suspend fun ApplicationCall.requireClubWritable(clubId: UUID, clubRepository: ClubRepository): Boolean` — responds `402 PaymentRequired` "Club is frozen due to unpaid invoice" and returns false when `billing_status='frozen'`. Reads stay allowed everywhere.
- Applied to (club-mutating endpoints; resolve teamId→clubId via team lookup): `PATCH /clubs/{id}`, `POST /clubs/{id}/logo`, `POST /clubs/{id}/teams`, `POST /clubs/{id}/invites`, `POST /teams/{id}/invites`, team PATCH/archive endpoints in `TeamRoutes.kt`, event create/update/delete in `EventRoutes.kt`. NOT applied to `/billing/*` (owner must be able to fix the card) or invite `redeem`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ch.teamorg.billing

import ch.teamorg.db.tables.ClubsTable
import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.koin.dsl.module
import java.util.UUID
import kotlin.test.*

class FrozenClubEnforcementTest : IntegrationTestBase() {

    private val fake = FakeStripeService()
    private val stripeOverride = module { single<StripeService> { fake } }

    private suspend fun io.ktor.client.HttpClient.register(email: String): String {
        val res = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"$email"}""")
        }
        return Json.parseToJsonElement(res.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    @Test
    fun `frozen club blocks writes but allows reads and billing fix`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val token = client.register("fr@x.ch")
        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"kind":"club","name":"VC Ice","billingEmail":"fr@x.ch"}""")
        }
        val clubId = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["clubId"]!!.jsonPrimitive.content
        client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_x"}""")
        }
        transaction {
            ClubsTable.update({ ClubsTable.id eq UUID.fromString(clubId) }) { it[billingStatus] = "frozen" }
        }

        // write blocked with 402
        val teamRes = client.post("/clubs/$clubId/teams") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"name":"Nope"}""")
        }
        assertEquals(HttpStatusCode.PaymentRequired, teamRes.status)

        // read still works
        assertEquals(HttpStatusCode.OK, client.get("/clubs/$clubId") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/clubs/$clubId/teams") { bearerAuth(token) }.status)

        // billing endpoints must stay open to fix the card
        assertEquals(HttpStatusCode.OK, client.post("/clubs/$clubId/billing/update-card") { bearerAuth(token) }.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.FrozenClubEnforcementTest"`
Expected: FAIL — team creation returns 201, not 402.

- [ ] **Step 3: Implement**

`RoleMiddleware.kt` addition:

```kotlin
/**
 * Block mutating operations on clubs frozen for non-payment (billing_status = 'frozen').
 * Reads and the billing endpoints themselves stay available so the owner can recover.
 */
suspend fun ApplicationCall.requireClubWritable(clubId: UUID, clubRepository: ClubRepository): Boolean {
    if (clubRepository.isFrozen(clubId)) {
        respond(HttpStatusCode.PaymentRequired, "Club is frozen due to unpaid invoice")
        return false
    }
    return true
}
```

`ClubRepository`: `fun isFrozen(clubId: UUID): Boolean` — Impl: `transaction { ClubsTable.selectAll().where { ClubsTable.id eq clubId }.singleOrNull()?.get(ClubsTable.billingStatus) == "frozen" }`.

Wire into each mutating route listed in Interfaces, directly after the existing role check, e.g. in `ClubRoutes.kt` `post("/teams")`:

```kotlin
                    if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@post
                    if (!call.requireClubWritable(clubId, clubRepository)) return@post
```

For team-scoped mutations (TeamRoutes, EventRoutes, team invites): resolve clubId first — `TeamRepository.findById(teamId)` already returns `clubId` (String) → `UUID.fromString(team.clubId)` → `requireClubWritable`. Events attached to multiple teams: check the first team's club (self-serve clubs have all teams in one club; cross-club events don't exist in this model).

Implementer must enumerate mutating endpoints in the four route files (`grep -n "post(\|patch(\|put(\|delete(" server/src/main/kotlin/ch/teamorg/routes/{Club,Team,Event,Invite}Routes.kt`) and add the guard to each club/team-scoped mutation, skipping `/billing/`, `/convert` (owner recovery actions) and `POST /invites/{token}/redeem`.

- [ ] **Step 4: Run test to verify it passes, full suite green**

Run: `./gradlew :server:test`
Expected: PASS — existing flow tests exercise the guarded routes on non-frozen clubs and must stay green.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/middleware/RoleMiddleware.kt \
  server/src/main/kotlin/ch/teamorg/routes/ server/src/main/kotlin/ch/teamorg/domain/repositories/ \
  server/src/test/kotlin/ch/teamorg/billing/FrozenClubEnforcementTest.kt
git commit -m "feat(billing): 402 write-enforcement for frozen clubs"
```

---

### Task 9: Sampling + year-end + cleanup jobs

**Files:**
- Create: `server/src/main/kotlin/ch/teamorg/infra/BillingJobs.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/Application.kt` (call `startBillingJobs()`)
- Test: `server/src/test/kotlin/ch/teamorg/billing/BillingJobsTest.kt`

**Interfaces:**
- Consumes: `BillingRepository` (Task 4), `StripeService` (Task 3), `computeBilledCount` (Task 2), `ZURICH` (Task 5).
- Produces — testable core functions (called by an hourly `Application.startBillingJobs()` loop shaped like `ReminderSchedulerJob`):

```kotlin
/** Samples every due stripe club; schedules next sample 21–35 days out (Jan–Sep) or 5–9 days out (Oct–Dec), random. */
fun runSampling(billingRepository: BillingRepository, now: ZonedDateTime, random: kotlin.random.Random)

/** Dec 31 only (Zurich date): per active stripe club, set subscription quantity to computeBilledCount(now, Q4 samples).
 *  Idempotent via recordEvent("yearend-<year>-<clubId>", ...). Safe to call every hour. */
fun runYearEnd(billingRepository: BillingRepository, stripeService: StripeService, now: ZonedDateTime)

/** Deletes clubs stuck in status='pending' older than 48h. */
fun runPendingCleanup(billingRepository: BillingRepository, now: ZonedDateTime)
```

- [ ] **Step 1: Write the failing test**

```kotlin
package ch.teamorg.billing

import ch.teamorg.db.tables.*
import ch.teamorg.domain.repositories.BillingRepository
import ch.teamorg.domain.ZURICH
import ch.teamorg.infra.runPendingCleanup
import ch.teamorg.infra.runSampling
import ch.teamorg.infra.runYearEnd
import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.random.Random
import kotlin.test.*

class BillingJobsTest : IntegrationTestBase() {

    private val fake = FakeStripeService()
    private val stripeOverride = module { single<StripeService> { fake } }

    private fun makeStripeClub(repo: BillingRepository, name: String, memberCount: Int, subId: String? = "sub_x_$name"): UUID {
        val clubId = transaction {
            ClubsTable.insert {
                it[ClubsTable.name] = name
                it[billingMode] = "stripe"
                it[status] = "active"
            } get ClubsTable.id
        }
        repo.createBilling(clubId, "cus_$name", "t@x.ch", "seti")
        if (subId != null) repo.activate(clubId, subId, null)
        val teamId = transaction { TeamsTable.insert { it[TeamsTable.clubId] = clubId; it[TeamsTable.name] = name } get TeamsTable.id }
        repeat(memberCount) { i ->
            val u = transaction {
                UsersTable.insert {
                    it[email] = "$name$i@x.ch"; it[passwordHash] = "x"; it[displayName] = "u$i"
                } get UsersTable.id
            }
            transaction { TeamRolesTable.insert { it[userId] = u; it[TeamRolesTable.teamId] = teamId; it[role] = "player" } }
        }
        return clubId
    }

    @Test
    fun `sampling records count and schedules q4 window 5-9 days`() = withTeamorgTestApplication(stripeOverride) {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        val clubId = makeStripeClub(repo, "smp", memberCount = 4)
        val now = ZonedDateTime.of(2026, 11, 10, 12, 0, 0, 0, ZURICH)
        runSampling(repo, now, Random(42))
        assertEquals(listOf(4), repo.sampleCountsBetween(clubId, now.minusDays(1).toInstant(), now.plusDays(1).toInstant()))
        assertTrue(clubId !in repo.clubsDueForSampling(now.toInstant()))
        assertTrue(clubId in repo.clubsDueForSampling(now.plusDays(10).toInstant())) // next sample within 5-9 days
    }

    @Test
    fun `year end uses max of snapshot and q4 median, runs once`() = withTeamorgTestApplication(stripeOverride) {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        val clubId = makeStripeClub(repo, "ye", memberCount = 2) // gamed-down snapshot of 2
        // season samples say 10
        listOf(10, 10, 11).forEachIndexed { i, c ->
            repo.insertSample(clubId, c, ZonedDateTime.of(2026, 10 + i, 5, 12, 0, 0, 0, ZURICH).toInstant())
        }
        val dec31 = ZonedDateTime.of(2026, 12, 31, 21, 0, 0, 0, ZURICH)
        runYearEnd(repo, fake, dec31)
        assertEquals(listOf("sub_x_ye" to 10), fake.quantityUpdates)
        runYearEnd(repo, fake, dec31.plusHours(1)) // idempotent
        assertEquals(1, fake.quantityUpdates.size)
        // not Dec 31 -> no-op
        runYearEnd(repo, fake, ZonedDateTime.of(2026, 12, 30, 21, 0, 0, 0, ZURICH))
        assertEquals(1, fake.quantityUpdates.size)
    }

    @Test
    fun `pending cleanup removes only stale pending clubs`() = withTeamorgTestApplication(stripeOverride) {
        lateinit var repo: BillingRepository
        application { repo = getKoin().get() }
        val stale = transaction {
            ClubsTable.insert { it[name] = "stale"; it[status] = "pending" } get ClubsTable.id
        }
        transaction {
            ClubsTable.update({ ClubsTable.id eq stale }) { it[createdAt] = Instant.now().minusSeconds(3 * 86400) }
        }
        val fresh = transaction { ClubsTable.insert { it[name] = "fresh"; it[status] = "pending" } get ClubsTable.id }
        runPendingCleanup(repo, ZonedDateTime.now(ZURICH))
        val remaining = transaction { ClubsTable.selectAll().map { it[ClubsTable.id] } }
        assertTrue(stale !in remaining)
        assertTrue(fresh in remaining)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.BillingJobsTest"`
Expected: compile FAIL — `runSampling` etc. unresolved.

- [ ] **Step 3: Implement `BillingJobs.kt`**

```kotlin
package ch.teamorg.infra

import ch.teamorg.domain.ZURICH
import ch.teamorg.domain.computeBilledCount
import ch.teamorg.domain.repositories.BillingRepository
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

private val logger = LoggerFactory.getLogger("BillingJobs")

fun Application.startBillingJobs() {
    val billingRepository by inject<BillingRepository>()
    val stripeService by inject<StripeService>()

    launch(Dispatchers.IO) {
        while (isActive) {
            delay(1.hours)
            try {
                val now = ZonedDateTime.now(ZURICH)
                runSampling(billingRepository, now, Random)
                runYearEnd(billingRepository, stripeService, now)
                runPendingCleanup(billingRepository, now)
            } catch (e: Exception) {
                logger.error("Billing jobs error", e)
            }
        }
    }
}

fun runSampling(billingRepository: BillingRepository, now: ZonedDateTime, random: Random) {
    for (clubId in billingRepository.clubsDueForSampling(now.toInstant())) {
        val count = billingRepository.countActiveMembers(clubId)
        billingRepository.insertSample(clubId, count, now.toInstant())
        val nextDays = if (now.monthValue >= 10) random.nextInt(5, 10) else random.nextInt(21, 36)
        val nextHour = random.nextInt(6, 23)
        billingRepository.setNextSampleAt(clubId, now.plusDays(nextDays.toLong()).withHour(nextHour).toInstant())
    }
}

fun runYearEnd(billingRepository: BillingRepository, stripeService: StripeService, now: ZonedDateTime) {
    if (now.monthValue != 12 || now.dayOfMonth != 31) return
    val q4From = ZonedDateTime.of(now.year, 10, 1, 0, 0, 0, 0, ZURICH).toInstant()
    for (clubId in billingRepository.activeStripeClubs()) {
        val marker = "yearend-${now.year}-$clubId"
        if (!billingRepository.recordEvent(marker, clubId, "yearend", "{}")) continue
        val snapshot = billingRepository.countActiveMembers(clubId)
        val samples = billingRepository.sampleCountsBetween(clubId, q4From, now.toInstant())
        val billed = computeBilledCount(snapshot, samples)
        val subscriptionId = billingRepository.findByClubId(clubId)?.stripeSubscriptionId ?: continue
        try {
            stripeService.updateSubscriptionQuantity(subscriptionId, billed)
            logger.info("Year-end quantity for club $clubId set to $billed (snapshot=$snapshot, samples=${samples.size})")
        } catch (e: Exception) {
            logger.error("Failed year-end quantity update for club $clubId", e)
        }
    }
}

fun runPendingCleanup(billingRepository: BillingRepository, now: ZonedDateTime) {
    val deleted = billingRepository.deleteAbandonedPendingClubs(now.minusHours(48).toInstant())
    if (deleted > 0) logger.info("Deleted $deleted abandoned pending clubs")
}
```

`Application.kt`: add `startBillingJobs()` after `startSwissVolleySyncJob()`.

Known limitation (accepted): a failed `updateSubscriptionQuantity` after the idempotency marker is recorded is not retried automatically — it is logged as ERROR; super-admin fixes in Stripe dashboard. Year-end job runs hourly on Dec 31, so a transient outage earlier in the day retries only for clubs whose marker wasn't recorded.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.BillingJobsTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/infra/BillingJobs.kt \
  server/src/main/kotlin/ch/teamorg/Application.kt \
  server/src/test/kotlin/ch/teamorg/billing/BillingJobsTest.kt
git commit -m "feat(billing): sampling, year-end quantity, pending-cleanup jobs"
```

---

### Task 10: Invite short codes

**Files:**
- Create: `server/src/main/resources/db/migrations/V17__invite_short_codes.sql`
- Modify: `server/src/main/kotlin/ch/teamorg/db/tables/InviteLinksTable.kt`
- Modify: `server/src/main/kotlin/ch/teamorg/domain/repositories/InviteRepository.kt` + Impl
- Modify: `server/src/main/kotlin/ch/teamorg/routes/InviteRoutes.kt`
- Test: `server/src/test/kotlin/ch/teamorg/billing/InviteShortCodeTest.kt`

**Interfaces:**
- Consumes: existing invite create/redeem flow.
- Produces:
  - Migration: `ALTER TABLE invite_links ADD COLUMN short_code TEXT NULL UNIQUE;`
  - Reusable invites get an 8-char code from alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (no 0/O/1/I/L) generated with `java.security.SecureRandom`, retry on unique-violation (max 5 attempts).
  - `GET /invites/code/{shortCode}` (public) → same response as existing `GET /invites/{token}` plus `"token"` field, so clients resolve code → token → existing redeem. 404 when unknown/expired.
  - Create-invite responses for reusable invites include `shortCode`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ch.teamorg.billing

import ch.teamorg.infra.StripeService
import ch.teamorg.test.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.koin.dsl.module
import kotlin.test.*

class InviteShortCodeTest : IntegrationTestBase() {

    private val fake = FakeStripeService()
    private val stripeOverride = module { single<StripeService> { fake } }

    private suspend fun io.ktor.client.HttpClient.register(email: String): String {
        val res = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"$email"}""")
        }
        return Json.parseToJsonElement(res.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    @Test
    fun `reusable team invite has short code resolvable to token and redeemable`() = withTeamorgTestApplication(stripeOverride) {
        val client = createJsonClient()
        val ownerToken = client.register("sc@x.ch")
        val createRes = client.post("/clubs/self-serve") {
            bearerAuth(ownerToken); contentType(ContentType.Application.Json)
            setBody("""{"kind":"team","name":"Coders","billingEmail":"sc@x.ch"}""")
        }
        val body = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val clubId = body["clubId"]!!.jsonPrimitive.content
        val teamId = body["teamId"]!!.jsonPrimitive.content
        client.post("/clubs/$clubId/billing/confirm") {
            bearerAuth(ownerToken); contentType(ContentType.Application.Json)
            setBody("""{"setupIntentId":"seti_x"}""")
        }

        val inviteRes = client.post("/teams/$teamId/invites") {
            bearerAuth(ownerToken); contentType(ContentType.Application.Json)
            setBody("""{"reusable":true,"role":"player"}""")
        }
        assertEquals(HttpStatusCode.Created, inviteRes.status)
        val shortCode = Json.parseToJsonElement(inviteRes.bodyAsText()).jsonObject["shortCode"]!!.jsonPrimitive.content
        assertEquals(8, shortCode.length)
        assertFalse(shortCode.any { it in "0O1IL" })

        val lookupRes = client.get("/invites/code/$shortCode")
        assertEquals(HttpStatusCode.OK, lookupRes.status)
        val token = Json.parseToJsonElement(lookupRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val playerToken = client.register("newplayer@x.ch")
        val redeemRes = client.post("/invites/$token/redeem") { bearerAuth(playerToken) }
        assertEquals(HttpStatusCode.OK, redeemRes.status)

        assertEquals(HttpStatusCode.NotFound, client.get("/invites/code/ZZZZZZZZ").status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "ch.teamorg.billing.InviteShortCodeTest"`
Expected: FAIL — no `shortCode` in create response.

- [ ] **Step 3: Implement**

Migration V17 as above. `InviteLinksTable`: `val shortCode = text("short_code").nullable().uniqueIndex()`.

Code generator (put in `InviteRepositoryImpl.kt`):

```kotlin
private const val SHORT_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
private val secureRandom = java.security.SecureRandom()

fun generateShortCode(): String =
    (1..8).map { SHORT_CODE_ALPHABET[secureRandom.nextInt(SHORT_CODE_ALPHABET.length)] }.joinToString("")
```

`InviteRepository` additions: `fun findByShortCode(shortCode: String): /* existing invite detail type */?`. On reusable-invite creation, set `shortCode = generateShortCode()` retrying up to 5 times on `ExposedSQLException` unique violation. Include `shortCode` in the reusable-invite create response DTO (nullable field, null for personal invites — check `WireContractTest` fixtures).

`InviteRoutes.kt` (public section, next to existing `get("/invites/{token}")`):

```kotlin
        get("/invites/code/{shortCode}") {
            val shortCode = call.parameters["shortCode"]?.uppercase()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val invite = inviteRepository.findByShortCode(shortCode)
            if (invite == null || invite.isExpiredOrInactive()) {  // reuse whatever expiry check get /invites/{token} uses
                return@get call.respond(HttpStatusCode.NotFound, "Invite not found")
            }
            call.respond(invite) // same DTO as GET /invites/{token}, must include token
        }
```

(Adapt names to the actual DTO/checks in `InviteRoutes.kt:77–209` — mirror the existing token lookup handler exactly, only the lookup key differs.)

- [ ] **Step 4: Run test to verify it passes, full suite green**

Run: `./gradlew :server:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/resources/db/migrations/V17__invite_short_codes.sql \
  server/src/main/kotlin/ch/teamorg/db/tables/InviteLinksTable.kt \
  server/src/main/kotlin/ch/teamorg/domain/repositories/InviteRepository.kt \
  server/src/main/kotlin/ch/teamorg/domain/repositories/InviteRepositoryImpl.kt \
  server/src/main/kotlin/ch/teamorg/routes/InviteRoutes.kt \
  server/src/test/kotlin/ch/teamorg/billing/InviteShortCodeTest.kt
git commit -m "feat(invites): human-friendly short codes for reusable invites"
```

---

### Task 11: Config, docs, deployment notes

**Files:**
- Modify: `server/src/main/resources/application.yaml` (or the config file `jwt.*` lives in — add `stripe.*` keys reading env vars)
- Modify: `SETUP.md`, `.env.example` if present, `docker-compose.yml` (pass `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_ID`)

**Interfaces:** none new — closes the loop on Global Constraints config keys.

- [ ] **Step 1: Wire config**

In the server config file, following the existing `jwt`/`onesignal` pattern:

```yaml
stripe:
  secret-key: "$STRIPE_SECRET_KEY"
  webhook-secret: "$STRIPE_WEBHOOK_SECRET"
  price-id: "$STRIPE_PRICE_ID"
```

- [ ] **Step 2: Document in SETUP.md**

Add a "Stripe billing" section: the three env vars; dashboard setup (CHF 2/year per-unit price → price id; webhook endpoint + 3 event types; dunning settings: Smart Retries, then mark subscription **unpaid**); test mode uses `sk_test_...` keys; local webhook testing via `stripe listen --forward-to localhost:8080/stripe/webhook`.

- [ ] **Step 3: Verify boot without Stripe env vars**

Run: `./gradlew :server:test`
Expected: PASS — empty-string fallbacks in Koin keep dev/test boot working with no Stripe env.

- [ ] **Step 4: Commit**

```bash
git add server/src/main/resources/ SETUP.md docker-compose.yml
git commit -m "chore(billing): stripe config wiring and setup docs"
```

---

## Deferred to follow-up plans

- **Plan 2 (web):** SvelteKit onboarding (join/create wizard, Stripe Elements, billing settings page, frozen-club banner).
- **Plan 3 (mobile):** CMP welcome flow, short-code join, creation wizard, expect/actual Stripe PaymentSheet, deep links.
- Stripe test-clock exploratory testing against a real test-mode account (manual QA, not CI).
- Super-admin billing overview UI (list stripe clubs, statuses, manual freeze/unfreeze).
