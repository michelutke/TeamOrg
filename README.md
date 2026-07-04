# teamorg

Sports team management platform. Create clubs, manage teams, invite members, and assign roles, jerseys, and positions — across Android, iOS, and the web.

## Tech Stack

| Layer | Technology |
|---|---|
| Server | Ktor 3.3.3 · Netty · JWT · Exposed ORM · PostgreSQL · Flyway · Koin |
| Shared | Kotlin Multiplatform · Ktor Client · SQLDelight 2 · Kotlinx Serialization |
| Android | Compose Multiplatform 1.10.1 · Navigation3 · Coil3 · Koin |
| iOS | SwiftUI · shared KMP framework |
| Web admin | SvelteKit 2 · Svelte 5 · Tailwind 4 (`admin/`) · Playwright E2E |

Kotlin 2.3.10 · JVM 21 · Android API 34–36

## Architecture

```
Server (Ktor + PostgreSQL)
        │  HTTP/REST + JWT
   ┌────┴─────────────┐
   ▼                  ▼
Shared (KMP)        admin (SvelteKit)
domain, repos,      web app — SSR proxy
Ktor client,        to the server API
SQLDelight cache
        │
   ┌────┴────┐
   ▼         ▼
composeApp  iosApp
(Android/   (SwiftUI,
 Desktop)    native)
```

Feature docs live in [docs/](docs/) — e.g. [unified attendance](docs/unified-attendance.md),
[NDS import/export](docs/nds-import-export-design.md), [deployment](docs/deployment.md).

## Local Development

### Prerequisites

- JDK 21
- Android SDK (for Android target)
- Xcode 16+ (for iOS target)
- PostgreSQL running locally

### Environment Variables

| Variable | Description |
|---|---|
| `DATABASE_URL` | PostgreSQL JDBC URL, e.g. `jdbc:postgresql://localhost:5432/teamorg` |
| `JWT_SECRET` | Secret string for signing JWT tokens |
| `API_BASE_URL` | Server base URL used by clients (default: `https://api.teamorg.app`) |
| `API_URL` | Server base URL the **admin web app** proxies to (default: `http://localhost:8080`) |
| `invite.base-url` (server config) | Base URL for generated invite links (default: `https://teamorg.ch`, whose `/i/*` landing forwards to the app) |

E2E test variables (`E2E_BASE_URL`, `E2E_EMAIL`, …) are documented in
[admin/e2e/README.md](admin/e2e/README.md).

### Run

```bash
# Server
./gradlew :server:run

# Android app (debug APK)
./gradlew :composeApp:assembleDebug

# Desktop (JVM)
./gradlew :composeApp:run

# iOS — open in Xcode
open iosApp/iosApp.xcworkspace

# Web admin (SvelteKit dev server on :5173, proxies to API_URL)
cd admin && npm install && npm run dev
```

## Running Tests

```bash
# Server integration tests (testcontainers — Docker must be running)
./gradlew :server:test

# Shared KMP
./gradlew :shared:allTests

# Android unit tests
./gradlew :composeApp:testDebugUnitTest

# Mobile ViewModel tests (iOS simulator target)
./gradlew :composeApp:iosSimulatorArm64Test

# Web admin type/lint check
cd admin && npm run check

# Web admin E2E (Playwright, needs a running instance — see admin/e2e/README.md)
cd admin && npm run test:e2e

# iOS UI tests (requires a simulator UDID)
xcodebuild test \
  -workspace iosApp/iosApp.xcworkspace \
  -scheme iosApp-Workspace \
  -destination "id=$SIMULATOR_UDID"
```

## License

See [LICENSE](./LICENSE).
