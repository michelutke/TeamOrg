# Local Development Setup

## Prerequisites

```bash
brew bundle          # gh, gitleaks
bundle install       # fastlane (Ruby version: see .ruby-version)
```

- JDK 21, Android SDK (API 36)
- Xcode (see `.xcode-version`) for iOS work
- Node 20+ for `admin/` and `landing/`

## App configuration

Create `local.properties` in the repo root:

```properties
API_BASE_URL=<server url>
onesignal.appId=<OneSignal app id>
```

## Running

| Target | Command |
|---|---|
| Server | `./gradlew :server:run` (PostgreSQL via `docker-compose up -d`) |
| Android | `./gradlew :composeApp:installDebug` or run from Android Studio |
| iOS | open `iosApp/iosApp.xcworkspace`, scheme `iosApp-Workspace` |
| Web admin | `cd admin && npm i && npm run dev` |
| Landing | `cd landing && npm i && npm run dev` |

## Tests

```bash
./gradlew :server:test :shared:testDebugUnitTest :composeApp:testDebugUnitTest
cd admin && npm run check && npm run test
```

## Stripe billing

Env vars (server): `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_ID`, `STRIPE_PUBLISHABLE_KEY`. All optional — empty-string fallbacks keep dev/test boot working without Stripe configured.

- Dashboard → Products: create a per-unit price of CHF 2/year, copy its price id into `STRIPE_PRICE_ID`.
- Dashboard → Developers → API keys: use `sk_test_...` in test mode, copy into `STRIPE_SECRET_KEY`.
- Dashboard → Developers → Webhooks: add endpoint `https://<api-host>/stripe/webhook`, subscribe to `invoice.paid`, `invoice.payment_failed`, `customer.subscription.updated`, `customer.subscription.deleted`. Copy the signing secret into `STRIPE_WEBHOOK_SECRET`. Pin the endpoint's API version (Webhooks → endpoint → API version) to match the SDK's pinned version (`com.stripe.Stripe.API_VERSION` in stripe-java) — a mismatch can cause event deserialization to silently fail.
- Dashboard → Settings → Billing → Automatic collection: enable Smart Retries, then set the failed-payment outcome to "mark subscription unpaid" — this triggers the `frozen` billing status transition in the app (a canceled/deleted subscription also freezes the club).
- Local webhook testing: `stripe listen --forward-to localhost:8080/stripe/webhook` (prints a `whsec_...` secret to use as `STRIPE_WEBHOOK_SECRET` locally).

## Deploys (fastlane)

CI deploys automatically: `main` → Updraft (Android APK) + iOS build check; `production` → Play internal track + TestFlight.

For local deploys, copy `fastlane/env.example` to the gitignored local env file `fastlane/` expects, fill in the values, then:

```bash
bundle exec fastlane check_secrets     # verify everything is set (names only)
bundle exec fastlane android_updraft
bundle exec fastlane android_play
bundle exec fastlane ios_testflight
```

Store setup details: [docs/play-store-deployment.md](docs/play-store-deployment.md).
