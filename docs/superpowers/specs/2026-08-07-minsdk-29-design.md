# Lower Android minSdk from 34 to 29

**Date:** 2026-08-07
**Status:** Approved, ready for implementation planning

## Problem

`gradle/libs.versions.toml:4` sets `android-minSdk = "34"`, so the app installs only on
Android 14 and newer — roughly a third of active Android devices. Nothing in the stack
requires that floor; it is almost certainly a leftover from the KMP project template.
`compileSdk` and `targetSdk` are 36 and stay there.

Lowering the floor to API 29 (Android 10) raises reach to roughly 92–95% of devices.

## Investigation results

The change was validated before being specified. `android-minSdk` was temporarily set to
29 and the real Gradle tasks were run, then reverted.

### Library compatibility: no blockers

`:composeApp:processReleaseMainManifest` succeeds at minSdk 29. This is conclusive — the
manifest merger hard-fails when any library declares a `minSdkVersion` above the app's.

Floors read from the AARs in the Gradle module cache:

| Library | minSdk |
|---|---|
| OneSignal 5.7.6 — `otel` module | **26** (highest in the graph) |
| OneSignal 5.7.6 — core, location | 21 |
| Stripe 23.13.1 — payments-core, ui-core, hcaptcha, financial-connections-lite | 23 |
| androidx `activity-compose` 1.12.2 | 23 |
| `androidx.graphics.path` 1.0.1 (the only native `.so`) | 21 |
| Coil3, SQLDelight `android-driver`, Koin, `graphics-shapes` | 21–23 |

The hard floor is **26**. API 29 clears it with margin. No dependency needs a version
bump or replacement.

### Code compatibility: no blockers

`:composeApp:lintDebug` at minSdk 29 reports **zero `NewApi` and zero `InlinedApi`
findings**. Contributing factors:

- Exactly one `SDK_INT` check exists in the codebase
  (`composeApp/src/androidMain/kotlin/ch/teamorg/ui/theme/DynamicColorScheme.android.kt:12`),
  already guarding Material You at API 31.
- Pickers use `GetContent`, `OpenDocument`, and `TakePicturePreview` — API 19-era
  contracts, not the API 33+ photo picker
  (`ImagePicker.android.kt`, `DocumentPicker.android.kt`).
- `java.time` is native from API 26, so no core-library desugaring is needed at 29.
- No `POST_NOTIFICATIONS` request exists in app code; below API 33 the permission does
  not exist at all, and OneSignal owns the 33+ path.
- App code declares no foreground service, so the API 34 `foregroundServiceType`
  requirement is satisfied by OneSignal's own manifest.

The only new lint output at 29 is two `ObsoleteSdkInt` warnings for redundant resource
folders.

### Pre-existing, out of scope

`:composeApp:lintDebug` already fails on `MissingClass: ch.teamorg.ui.TestActivity`
(`composeApp/src/androidMain/AndroidManifest.xml:16`). This is unrelated to minSdk and is
not addressed here.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Target floor | **API 29** | Clears the API 26 library floor with margin; avoids desugaring, adaptive-icon fallbacks, and Android 8/9 quirks that API 26 would reintroduce. |
| Verification | **CI emulator job** | No instrumented tests exist today; a durable regression guard is worth building. |
| Test scope | **Launch + core flows against a live local server** | Stripe and push stay manual (see Out of Scope). |
| Sequencing | **Harness first at API 34, then flip** | Makes PR 2's signal unambiguous. |
| CI trigger | **Path-filtered PRs + all release pushes** | Matches the existing "don't build what cannot have changed" policy (commit `cbc785c`). |

## Architecture

Two PRs. The split exists so that a failure in PR 2 can only mean one thing.

### PR 1 — Instrumented test harness, running at API 34

Built against the *current* minSdk so the harness is proven green on a known-good
configuration before the floor moves.

**Gradle:** `composeApp/build.gradle.kts` `defaultConfig` currently lacks
`testInstrumentationRunner`; add
`testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`. The
`androidInstrumentedTest` source set is already wired with `androidx-test-ext-junit` and
`compose-uiTestJunit4` (`composeApp/build.gradle.kts:66`) but the directory is empty.

**Tests** in `composeApp/src/androidInstrumentedTest/kotlin/ch/teamorg/`:

| Class | Responsibility | Backend |
|---|---|---|
| `AppLaunchTest` | Cold-start `MainActivity`; assert the Koin graph resolves, OneSignal init survives, and the login screen composes | None |
| `AuthFlowTest` | Register → land in app → logout → login | Live server |
| `PickerContractTest` | Assert `GetContent` and `OpenDocument` intents resolve to a handling activity via `PackageManager` | None |

`AppLaunchTest` carries the most value: init-time API breakage is the dominant failure
mode for a minSdk change, and it needs no backend, so it stays fast and non-flaky.

`PickerContractTest` deliberately asserts intent *resolution* rather than driving the
system picker. Automating system file-picker UI requires UiAutomator and is flaky across
API levels, while resolution is the behavior that actually regresses on older APIs.

**Networking is already in place.** `shared/build.gradle.kts:102` defaults the debug
variant's `API_BASE_URL` to `http://10.0.2.2:8080`, `HttpClientFactory.kt:15` whitelists
`10.0.2.2` for cleartext, and commit `e45c32a` moved the cleartext config into the
`androidDebug` variant resource. No new configuration is required for the emulator to
reach a host-side server.

**CI job** `test-android-emulator` in `.github/workflows/test.yml`:

- `runs-on: ubuntu-latest`, `timeout-minutes: 30`, joins the existing `concurrency` group
- `services: postgres` — copy the block from the existing `test-backend` job rather than
  introducing docker-compose; `docker-compose.yml` is a Coolify deployment file and does
  not define Postgres
- KVM enable step via `udev` rule — the emulator will not boot without it
- Boot the backend with `./gradlew :server:run &`, then poll `GET /health` until it
  returns 200 (`server/src/main/kotlin/ch/teamorg/plugins/Routing.kt:26`, unauthenticated)
- `reactivecircus/android-emulator-runner@v2` with `api-level: 34`, `target: default`,
  `arch: x86_64`, running `./gradlew :composeApp:connectedDebugAndroidTest`
- Reuse the dummy `google-services.json` step from the existing `test-android` job
- Triggers: PRs touching `composeApp/**`, `shared/**`, `gradle/**`, or
  `.github/workflows/test.yml`; plus all pushes to `main` and release branches

**Required server environment in CI:**

- `DATABASE_URL` → the Postgres service
- `JWT_SECRET` → any dummy value
- `SWISSVOLLEY_SYNC_ENABLED=false` — **required**. `SwissVolleySyncJob.kt:25` disables the
  job only on an explicit `false`, so the default would start a background poller against
  the real `https://api.volleyball.ch` from the CI runner.

`PushService.kt:37` already no-ops on blank OneSignal credentials, so those can stay unset.

**First-run verification item:** `StripeServiceImpl.kt:18` constructs
`StripeClient(secretKey)` eagerly during DI wiring, with an empty key in CI. Whether
stripe-java validates the key at construction or only at request time is unverified. If
construction throws, supply a dummy `sk_test_…` value via `STRIPE_SECRET_KEY`.

### PR 2 — The bump

1. `gradle/libs.versions.toml:4` → `android-minSdk = "29"`. It propagates automatically to
   `composeApp/build.gradle.kts:106` and `shared/build.gradle.kts:97`; no other Gradle edits.
2. `.github/workflows/test.yml` — emulator `api-level: 34` → `29`.
3. `composeApp/src/androidMain/res/` — merge `drawable-v24/` into `drawable/` and
   `mipmap-anydpi-v26/` into `mipmap-anydpi/`, then delete the qualified folders. Every
   API 29 device is ≥26, so the qualifiers are dead weight. Cosmetic; skipping it leaves
   two `ObsoleteSdkInt` warnings.
4. `README.md` and `SETUP.md` — update any stated Android version floor.

The diff outside the resource move is two numbers, so a red emulator run means Android 10
breakage rather than harness trouble.

## Out of scope

Deliberately excluded, because the investigation showed they are unnecessary:

- Core-library desugaring — `java.time` is native from API 26
- New `SDK_INT` guards — `NewApi` was clean at 29
- Dependency version changes — highest floor in the graph is 26
- Notification-permission handling — `POST_NOTIFICATIONS` does not exist below API 33
- `foregroundServiceType` work — app code declares no service
- Fixing the pre-existing `MissingClass: TestActivity` lint error
- Broadening instrumented coverage beyond the three classes in PR 1

## Manual pre-release checks

Two paths cannot run in CI. Both involve the SDKs with the highest floors in the graph, so
they are exactly where an Android 10 surprise would live. One pass on a physical Android 10
device, not per release:

| Path | Why CI cannot cover it | Check |
|---|---|---|
| OneSignal push delivery | Needs FCM and Play Services; the `default` AOSP image has neither, and `google_apis` images do not receive real pushes reliably in CI | Install, log in, trigger a notification, confirm receipt and tap-through |
| Stripe PaymentSheet | Real card-entry UI; UiAutomator automation is flaky across API levels | Self-serve club creation → PaymentSheet → test card `4242 4242 4242 4242` → confirm the subscription activates |

## Risks

**Residual risk.** Lint's `NewApi` proves the absence of statically detectable API misuse.
It cannot catch behavioral differences: an OEM Android 10 skin, a library taking a
different internal path below API 31, or a TLS/cert-chain difference on a 2019 device. The
emulator job plus one real-device pass reduces this to "unlikely and quickly visible in
Play vitals," not zero.

**Play Console.** A lower floor widens the device catalogue, so the first pre-launch report
after this ships covers devices it has never touched. Expect new, probably cosmetic
findings. No existing install breaks.

**Rollback.** Revert two numbers. Nothing structural changes. Users who installed at
minSdk 29 keep a working app if the floor is later raised — Play stops offering them
updates rather than uninstalling.

## Definition of done

1. `test-android-emulator` green at API 29
2. `:composeApp:testDebugUnitTest`, `:shared:allTests`, and `:server:test` still green
3. `./gradlew :composeApp:lintDebug` shows no new `NewApi` or `InlinedApi` findings
   (the pre-existing `MissingClass` error remains)
4. Manual push and Stripe checks pass on one physical Android 10 device
5. Internal-track release, 48 hours of Play vitals watched for API 29–30 crash clusters
   before wider rollout
