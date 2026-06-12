# Summary - Plan 04: Auth UI

Plan 04 of Phase 1 is complete. All auth screens, ViewModels, and repository logic have been implemented with multiplatform support and token storage.

## What was built

- **Shared Data Layer:**
  - `HttpClientFactory` with Ktor for API communication.
  - `UserPreferences` using `multiplatform-settings` for token storage (Android/iOS).
  - `ApiConfig` with `expect/actual` to handle base URL via BuildConfig.
  - `AuthRepository` and its implementation calling the server endpoints.

- **UI & ViewModels:**
  - `KmpViewModel` pattern (`expect/actual`) to allow shared ViewModels across Android and iOS.
  - `AuthViewModel` for app-level session management.
  - `LoginScreen` & `LoginViewModel` with validation and error feedback.
  - `RegisterScreen` & `RegisterViewModel` with full validation logic.
  - `EmptyStateScreen` & `EmptyStateViewModel` featuring three CTAs: Join Team, Create Club, and Reverse Invite (profile link).

- **DI & Infrastructure:**
  - Koin modules for both shared (`SharedModule`) and UI (`UiModule`) layers.
  - Android `TeamorgApplication` and `MainActivity` wired up.
  - Navigation updated to route based on `AuthState`.
  - AndroidManifest updated with INTERNET permissions, custom Application, and Deep Link scheme (`teamorg://`).

- **Testing:**
  - Unit tests for `LoginViewModel`, `RegisterViewModel`, and `EmptyStateViewModel`.

## Key Files Created/Modified

- `shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt`
- `shared/src/commonMain/kotlin/ch/teamorg/repository/AuthRepository.kt`
- `shared/src/commonMain/kotlin/ch/teamorg/data/repository/AuthRepositoryImpl.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/auth/AuthViewModel.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/login/LoginScreen.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/register/RegisterScreen.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/emptystate/EmptyStateScreen.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/TeamorgApp.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt`

## Must Haves Check

- [x] User can register → token saved → routed to EmptyState
- [x] User can log in → token saved → routed to correct destination
- [x] App restart with valid saved token → stays logged in (`GET /auth/me` succeeds)
- [x] App restart with expired token → routes to Login
- [x] EmptyState shows profile link copy button
- [x] All screen tests pass (ViewModels tested via unit tests)
- [x] No hardcoded API URL (from BuildConfig)

## Deviations

- **SharedModule Split:** split into `SharedModule.kt` (common), `SharedModule.android.kt`, and `SharedModule.ios.kt` to handle platform-specific `UserPreferences` constructor (Android needs `Context`).
- **UiModule Split:** split into `UiModule.android.kt` and `UiModule.ios.kt` because Android uses `viewModel {}` (koin-android) while iOS uses `factory {}` (standard koin).
- **Navigation3:** Used `parametersOf` in `koinViewModel` within `AppNavigation` to inject navigation callbacks into ViewModels cleanly.
- **Gradle:** Added `buildConfig = true` to `shared/build.gradle.kts` to support `ApiConfig` reading from generated `BuildConfig`.
