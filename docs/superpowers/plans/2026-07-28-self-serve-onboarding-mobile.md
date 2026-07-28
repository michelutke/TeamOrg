# Self-Serve Onboarding — Mobile (Plan 3 of 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mobile (Compose Multiplatform) self-serve onboarding: first-time users choose join-with-code or create-your-own team/club with native Stripe PaymentSheet card capture, plus an in-app billing screen (card on file, update card, team↔club convert) and frozen-club surfacing.

**Architecture:** Backend endpoints exist (main). One additive backend change: self-serve/update-card responses gain `publishableKey` so clients never need build-time Stripe config. Shared layer gains a `BillingRepository` + invite-code lookup following the existing repository pattern. Card capture is an expect/actual `rememberCardSetupSheet` composable modeled on the existing `ImagePicker` pattern: Android uses stripe-android's `rememberPaymentSheet` directly; iOS registers a Swift presenter (`StripeSetupBridge`) at launch because stripe-ios is Swift-only and invisible to Kotlin/Native. UI: EmptyState becomes the join-or-create chooser; a new create wizard (details → card) and a Billing screen slot into the existing Screen/AppNavigation/Koin structure.

**Tech Stack:** Kotlin CMP, Ktor client, Koin, kotlinx.serialization; stripe-android 23.13.1 (androidMain), stripe-ios 26.0.0 via SPM `stripe-ios-spm` product `StripePaymentSheet` (first SPM package in iosApp); tests: kotlin.test + turbine + kotest matchers + fakes (commonTest), server tests testcontainers.

**Spec:** `docs/superpowers/specs/2026-07-24-self-serve-onboarding-billing-design.md`
**Research (authoritative for Stripe integration details):** the Stripe-CMP research is inlined into Tasks 3–4 below.

## Global Constraints

- Follow existing patterns exactly: repository interface in `shared/src/commonMain/kotlin/ch/teamorg/repository/`, impl in `.../data/repository/` returning `Result<T>`, `@Serializable` DTOs beside the impl, Koin `singleOf(...) bind ...` in SharedModule (all platform variants); Screen sealed class + AppNavigation case + UiModule `factory {}` for new screens/VMs; VM pattern = `MutableStateFlow` state + `MutableSharedFlow` events.
- Version catalog: NEW key `stripeAndroid = "23.13.1"` — do NOT touch the existing `stripe = "29.2.0"` (server SDK).
- iOS: SPM repo `https://github.com/stripe/stripe-ios-spm` exact 26.0.0, product **StripePaymentSheet** only. Commit `project.pbxproj` + `Package.resolved`.
- Publishable key comes from the SERVER response (`publishableKey` field), never from build config. Never log it together with the client secret; never persist the client secret.
- Stripe 3DS return URL: `teamorg://stripe-redirect` (custom scheme only — PaymentSheet does not support universal links). iOS `onOpenURL` must route Stripe callbacks BEFORE the invite deep-link handler.
- German-first UI copy matching existing screens' style (check how existing screens do strings — hardcoded German or resource pattern; follow it). Pricing copy: "CHF 2 pro Mitglied und Jahr, jeweils im Januar abgerechnet." Kind-switch copy: "Du kannst später jederzeit zwischen Team und Verein wechseln."
- Theme: MaterialTheme.colorScheme tokens only (primary #0E6577 light / #64D8E8 dark already defined).
- Verify commands: `./gradlew :shared:compileCommonMainKotlinMetadata :composeApp:compileCommonMainKotlinMetadata` (fast compile gate), `./gradlew :shared:jvmTest`, `./gradlew :composeApp:iosSimulatorArm64Test` (VM tests), server: `./gradlew :server:test --tests "..."` focused. iOS build check: `bundle exec fastlane ios_check` equivalent (unsigned sim build) or `xcodebuild -workspace iosApp/iosApp.xcworkspace -scheme iosApp-Workspace -sdk iphonesimulator -configuration Debug build` — pick what the repo's CI lane does (see fastlane/Fastfile).
- Commit after each task. No Co-Authored-By/AI attribution ever.

## File Structure (new/modified)

- `server/.../routes/SelfServeRoutes.kt` — +`publishableKey` in create + update-card responses; `application.conf` + Koin: `stripe.publishable-key`
- `shared/.../repository/BillingRepository.kt` (new iface), `shared/.../data/repository/BillingRepositoryImpl.kt` (new), `shared/.../repository/InviteRepository.kt` (+getInviteByCode), SharedModule regs
- `composeApp/.../payments/CardSetupSheet.kt` (+.android.kt, +.ios.kt)
- `iosApp/iosApp/StripeSetupBridge.swift` (new), `iosApp/iosApp/iOSApp.swift` (register + onOpenURL order), `project.pbxproj`/`Package.resolved` (SPM)
- `composeApp/.../ui/emptystate/*` (rework chooser), `composeApp/.../ui/selfserve/CreateTeamOrClubScreen.kt` + VM (new), `.../ui/selfserve/CardSetupScreen.kt` + VM (new), `.../ui/billing/BillingScreen.kt` + VM (new)
- `composeApp/.../navigation/Screen.kt` + `AppNavigation.kt` + `di/UiModule.kt`
- `composeApp/src/commonTest/.../fake/FakeBillingRepository.kt` + VM tests

---

### Task 1: Backend — publishableKey in self-serve responses

**Files:**
- Modify: `server/src/main/kotlin/ch/teamorg/routes/SelfServeRoutes.kt`, `server/src/main/resources/application.conf`, `server/src/main/kotlin/ch/teamorg/plugins/Koin.kt` (only if key plumbing needs it — prefer reading config where the routes get other config)
- Modify: `server/src/test/kotlin/ch/teamorg/billing/SelfServeFlowTest.kt`, `BillingManagementTest.kt`
- Modify: `SETUP.md` (STRIPE_PUBLISHABLE_KEY env var server-side), `docker-compose.yml`

**Interfaces:**
- Produces: `SelfServeCreateResponse` gains `publishableKey: String`; update-card response gains `publishableKey: String` (alongside setupIntentClientSecret). Config key `stripe.publishable-key` ← env `STRIPE_PUBLISHABLE_KEY`, empty-string default. Follow exactly how `stripe.secret-key` is wired (application.conf substitution + wherever it's read).

- [ ] **Step 1:** Extend the two failing assertions first: in SelfServeFlowTest assert create response contains `publishableKey` (fake env: config in tests has no stripe key → expect empty string — assert field EXISTS in JSON); same for update-card in BillingManagementTest. Run focused tests → FAIL.
- [ ] **Step 2:** Implement: config key, pass into route (mirror how StripeService gets its config — the route layer may need the value via a small Koin-provided holder or read from `environment.config`; match existing style), add field to both response DTOs.
- [ ] **Step 3:** Focused tests green → full `./gradlew :server:test` green.
- [ ] **Step 4:** SETUP.md + docker-compose env passthrough. Commit: `feat(billing): expose publishable key in self-serve responses`

---

### Task 2: Shared — BillingRepository + invite-code lookup

**Files:**
- Create: `shared/src/commonMain/kotlin/ch/teamorg/repository/BillingRepository.kt`, `shared/src/commonMain/kotlin/ch/teamorg/data/repository/BillingRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/ch/teamorg/repository/InviteRepository.kt` + its Impl (add `getInviteByCode(code: String): Result<InviteDetails>` hitting `GET /invites/code/{code}` — same DTO as token lookup, includes token)
- Modify: SharedModule (all platform variants that register repos)
- Create: `composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeBillingRepository.kt`
- Test: shared test for DTO serialization if the repo has a serialization test precedent (check `EventSerializationTest` pattern); otherwise compile + fake is enough.

**Interfaces (produced — later tasks compile against these):**
```kotlin
@Serializable data class SelfServeCreated(val clubId: String, val teamId: String? = null, val setupIntentClientSecret: String, val publishableKey: String)
@Serializable data class BillingInfo(
    val billingEmail: String, val cardBrand: String?, val cardLast4: String?,
    val cardExpMonth: Int?, val cardExpYear: Int?,
    val currentMemberCount: Int, val projectedBilledCount: Int,
    val billingStatus: String, val billingMode: String, val kind: String
)
@Serializable data class CardUpdateStart(val setupIntentClientSecret: String, val publishableKey: String)

interface BillingRepository {
    suspend fun createSelfServe(kind: String, name: String, sportType: String?, location: String?, billingEmail: String): Result<SelfServeCreated>
    suspend fun confirmBilling(clubId: String, setupIntentId: String): Result<Unit>
    suspend fun getBilling(clubId: String): Result<BillingInfo>
    suspend fun startCardUpdate(clubId: String): Result<CardUpdateStart>
    suspend fun convert(clubId: String, targetKind: String): Result<String> // returns new kind
}
```
NOTE for implementer: confirm the backend's confirm response shape (`{status}`) and update-card response field name against `SelfServeRoutes.kt` — adapt DTOs to the real wire shapes, the sketch above is the contract for the UI side.

- [ ] Steps: fake-first where a VM test needs it later; implement iface+impl+DTOs+Koin regs; `./gradlew :shared:compileCommonMainKotlinMetadata :shared:jvmTest` green; commit `feat(mobile): shared billing repository and invite-code lookup`.

---

### Task 3: CardSetupSheet expect/actual (common + Android)

**Files:**
- Modify: `gradle/libs.versions.toml` (stripeAndroid), `composeApp/build.gradle.kts` (androidMain dep)
- Create: `composeApp/src/commonMain/kotlin/ch/teamorg/payments/CardSetupSheet.kt`, `composeApp/src/androidMain/kotlin/ch/teamorg/payments/CardSetupSheet.android.kt`
- Create: `composeApp/src/iosMain/kotlin/ch/teamorg/payments/CardSetupSheet.ios.kt` (Kotlin hook side only — Swift half is Task 4)

**Interfaces (produced):**
```kotlin
// commonMain
enum class SetupResult { Completed, Canceled, Failed }
@Composable expect fun rememberCardSetupSheet(onResult: (SetupResult) -> Unit): (publishableKey: String, setupIntentClientSecret: String) -> Unit
```
Android actual: `rememberPaymentSheet { result -> ... }`, launcher does `PaymentConfiguration.init(context, pk)` then `paymentSheet.presentWithSetupIntent(secret, PaymentSheet.Configuration.Builder("Teamorg").build())`. Map `PaymentSheetResult.Completed/Canceled/Failed`.
iOS actual: module-level `setSetupPresenter(presenter: (String, String, (String) -> Unit) -> Unit)` registration fn + actual that invokes it mapping "completed"/"canceled"/else. (Full sketches in the research — the implementer gets them verbatim below.)

```kotlin
// iosMain — CardSetupSheet.ios.kt
private var setupPresenter: ((String, String, (String) -> Unit) -> Unit)? = null
fun setSetupPresenter(presenter: (String, String, (String) -> Unit) -> Unit) { setupPresenter = presenter }
@Composable actual fun rememberCardSetupSheet(onResult: (SetupResult) -> Unit): (String, String) -> Unit = { pk, secret ->
    setupPresenter?.invoke(pk, secret) { code ->
        onResult(when (code) { "completed" -> SetupResult.Completed; "canceled" -> SetupResult.Canceled; else -> SetupResult.Failed })
    } ?: onResult(SetupResult.Failed)
}
```
```kotlin
// androidMain — CardSetupSheet.android.kt
@Composable actual fun rememberCardSetupSheet(onResult: (SetupResult) -> Unit): (String, String) -> Unit {
    val context = LocalContext.current
    val paymentSheet = rememberPaymentSheet { result ->
        onResult(when (result) {
            is PaymentSheetResult.Completed -> SetupResult.Completed
            is PaymentSheetResult.Canceled -> SetupResult.Canceled
            is PaymentSheetResult.Failed -> SetupResult.Failed
        })
    }
    return { pk, secret ->
        PaymentConfiguration.init(context, pk)
        paymentSheet.presentWithSetupIntent(secret, PaymentSheet.Configuration.Builder("Teamorg").build())
    }
}
```
- [ ] Steps: dep + three files; verify `./gradlew :composeApp:compileCommonMainKotlinMetadata :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64` (exact task names may differ — use the compile tasks CI/test lanes use) all green; commit `feat(mobile): stripe card-setup sheet expect/actual`.

---

### Task 4: iOS — SPM package + Swift bridge + deep-link ordering

**Files:**
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj` (XCRemoteSwiftPackageReference `https://github.com/stripe/stripe-ios-spm`, exact 26.0.0, product dep StripePaymentSheet on iosApp target, Frameworks phase)
- Create: `iosApp/iosApp/Package.resolved` location as Xcode generates (commit it)
- Create: `iosApp/iosApp/StripeSetupBridge.swift` (verbatim from research: static `current: PaymentSheet?` strong ref; `register()` sets `CardSetupSheet_iosKt.setSetupPresenter`; DispatchQueue.main.async; STPAPIClient.shared.publishableKey = pk; PaymentSheet(setupIntentClientSecret:configuration:) with merchantDisplayName "Teamorg" + returnURL "teamorg://stripe-redirect"; top-VC via connectedScenes walking presentedViewController)
- Modify: `iosApp/iosApp/iOSApp.swift` — call `StripeSetupBridge.register()` in didFinishLaunching after doInitKoin; `onOpenURL` gains `if StripeAPI.handleURLCallback(with: url) { return }` BEFORE handleDeepLink. Wrap Stripe imports/calls in `#if canImport(StripePaymentSheet)` ONLY if the repo's OneSignal precedent requires it — since the package IS committed here, prefer unconditional import.
- Verify generated Kotlin symbol name (`CardSetupSheet_iosKt.setSetupPresenter`) against the built framework header; adapt.

- [ ] Steps: pbxproj edits (careful, text-based — mirror structure from a reference pbxproj with one SPM package; validate with `plutil -lint` NO (pbxproj isn't plist-lintable) → validate by resolving); `xcodebuild -resolvePackageDependencies -workspace iosApp/iosApp.xcworkspace -scheme iosApp-Workspace`; full unsigned sim build (the ios_check lane's xcodebuild invocation from fastlane/Fastfile) green; commit `feat(ios): stripe payment sheet bridge and package`.
- [ ] KNOWN RISK: text-editing pbxproj for the first SPM package. If the build fails after two fix attempts, STOP and report BLOCKED — the controller/user can add the package via Xcode UI in 2 minutes and commit; don't burn cycles.

---

### Task 5: EmptyState chooser rework + create wizard (details step)

**Files:**
- Modify: `composeApp/.../ui/emptystate/EmptyStateScreen.kt` + `EmptyStateViewModel.kt`
- Create: `composeApp/.../ui/selfserve/CreateTeamOrClubScreen.kt` + `CreateTeamOrClubViewModel.kt`
- Modify: `navigation/Screen.kt` (+`CreateTeamOrClub`, keep `ClubSetup` for super-admin? CHECK: ClubSetup posts to super-admin-only POST /clubs — for normal users it fails today. DECISION: EmptyState's "Create" now goes to the new self-serve wizard for everyone; keep ClubSetup screen only if super-admin path still reachable elsewhere, else leave file but unroute it and note), `AppNavigation.kt`, `di/UiModule.kt`
- Test: `composeApp/src/commonTest/.../selfserve/CreateTeamOrClubViewModelTest.kt` (+ EmptyStateViewModel test updates if its events change)

**Interfaces:**
- EmptyState: headline + two primary actions — "Team oder Verein erstellen" → `Screen.CreateTeamOrClub`; join affordance keeps the existing link/code input (it already accepts raw tokens; ALSO accept 8-char short codes: if input matches `[A-Za-z0-9]{8}` after trim, resolve via `InviteRepository.getInviteByCode` → navigate `Screen.Invite(token)`; keep existing /i/-link parsing).
- CreateTeamOrClubViewModel(billingRepository): state {kind='team', name, sportType='volleyball', location, billingEmail (prefill: authRepository current user email — check how other VMs get it), isLoading, error}; `submit()` validates (kind, name non-blank, email contains @) then `createSelfServe` → event `ProceedToCard(SelfServeCreated)`.
- Screen: kind cards (Team/Verein + hints + switch-note), fields, pricing note prominent, submit "Weiter zur Zahlung".
- Navigation: `Screen.CardSetup(clubId, clientSecret, publishableKey)` — serializable args like `Screen.Invite(token)`; created in Task 6, so this task routes ProceedToCard to it (compile order: define Screen.CardSetup here with a placeholder composable if needed, or implement Tasks 5+6 against the same nav change — the brief for Task 6 owns the screen body).
- VM tests: submit validation failures, success emits event with server data, error surfaces Result failure (use FakeBillingRepository).

- [ ] Steps: TDD on the VM (fake-backed tests first), then screens/nav; `:composeApp:iosSimulatorArm64Test` + metadata compile green; commit `feat(mobile): join-or-create chooser and create wizard`.

---

### Task 6: Card setup screen + confirm flow

**Files:**
- Create: `composeApp/.../ui/selfserve/CardSetupScreen.kt` + `CardSetupViewModel.kt`
- Modify: `AppNavigation.kt`, `di/UiModule.kt`
- Test: `CardSetupViewModelTest.kt`

**Interfaces:**
- Screen shows pricing note + "Karte hinterlegen" button → `rememberCardSetupSheet(onResult)` launcher with (publishableKey, clientSecret) from nav args. On `SetupResult.Completed` → VM `confirm(clubId, setupIntentId)`. PROBLEM the implementer must solve: PaymentSheet result doesn't return the SetupIntent id — the client already knows it? The client secret embeds the id: `seti_xxx_secret_yyy` → id = substringBefore("_secret_"). Use that (document with a comment; backend verifies against Stripe anyway).
- VM: confirm → billingRepository.confirmBilling → success event `Done` → navigation clears backstack to `Screen.Teams` (mirrors ClubSetup's onClubCreated). Failure → error state, retry allowed (button re-presents sheet). Canceled → stay, no error. Failed → error copy.
- Tests: confirm success/failure paths; setupIntentId extraction from secret.

- [ ] Steps: TDD VM, screen, nav; sim tests + metadata compile green; commit `feat(mobile): card setup screen with native payment sheet`.

---

### Task 7: Billing screen + convert + frozen surfacing

**Files:**
- Create: `composeApp/.../ui/billing/BillingScreen.kt` + `BillingViewModel.kt`
- Modify: `ui/team/TeamsListScreen.kt` (entry point: in the club header card area, visible action "Abrechnung" — shown always for club managers; backend 403s non-owners → show friendly "nur für Inhaber" state), `Screen.kt`, `AppNavigation.kt`, `UiModule.kt`
- Test: `BillingViewModelTest.kt`

**Interfaces:**
- BillingViewModel(billingRepository): load(clubId) → BillingInfo state; 403 → `notOwner` state; `updateCard()` → startCardUpdate → event `PresentCardSheet(pk, secret)` → screen uses rememberCardSetupSheet; Completed → confirmBilling (same id-from-secret trick) → reload. `convert()` → target = opposite of state kind → convert() → reload; conflict error (409) → message "Nur mit genau einem aktiven Team möglich."
- Screen: card-on-file row (brand •••• last4, exp), member counts (current + projected w/ one-line basis note), status chip (aktiv/überfällig/eingefroren), manual-mode note when billingMode != stripe, convert button with kind label, update-card button.
- Frozen surfacing: where the club object is already loaded for Teams (billingStatus now in DTO — verify shared Club DTO includes it; if not, add fields to shared Club model — additive), show a banner card on TeamsListScreen when frozen/past_due linking to Billing screen.
- Tests: load owner/not-owner, convert success + 409, update-card event, frozen state exposure.

- [ ] Steps: TDD VM, screen, entry + banner; sim tests + metadata compile green; commit `feat(mobile): billing screen, convert, frozen banner`.

---

### Task 8: Final gate

- [ ] `./gradlew :shared:compileCommonMainKotlinMetadata :composeApp:compileCommonMainKotlinMetadata` green
- [ ] `./gradlew :shared:jvmTest` + `./gradlew :composeApp:iosSimulatorArm64Test` + `./gradlew :composeApp:testDebugUnitTest --rerun-tasks` green (the --rerun guard against UP-TO-DATE masking is a known repo gotcha)
- [ ] `./gradlew :server:test` green (Task 1 touched backend)
- [ ] iOS unsigned sim build via the ios_check lane invocation green
- [ ] Manual QA checklist appended to this plan: Android + iOS device runs — create team w/ test card 4242, decline card, 3DS card (verify teamorg://stripe-redirect returns into PaymentSheet), join via 8-char code, billing screen update-card + convert, frozen club shows banner + blocked writes show friendly message.
- [ ] Commit any gate fixes: `chore(mobile): final gate fixes`

## Out of scope
- Web parity changes (web PR #66 handles web).
- Push-notification for billing events; super-admin mobile tooling; reusable-invite creation UI (exists in mobile already? — TeamRoster invite sheet creates reusable links per Figma; verify during Task 5, don't build new).

## Manual QA checklist (device runs, Stripe sandbox — do before release)

Android + iOS, backend with STRIPE_* test env:
- [ ] Create team with test card 4242 4242 4242 4242 → lands on Teams, club active, subscription in Stripe test dashboard
- [ ] Decline card 4000 0000 0000 0002 → error shown, retry works
- [ ] 3DS card 4000 0027 6000 3184 → challenge completes; on iOS verify teamorg://stripe-redirect returns into PaymentSheet (not the invite handler)
- [ ] Join via 8-char short code from EmptyState
- [ ] Billing screen: card meta correct, update card (new test card reflected), convert team→club and back, convert blocked with 2 teams
- [ ] Frozen club (set billing_status='frozen' in DB): banner on Teams, writes blocked with friendly message, billing screen reachable
- [ ] Xcode: open project once, confirm Package Dependencies UI shows stripe-ios 26.0.0 (hand-authored pbxproj)
