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
