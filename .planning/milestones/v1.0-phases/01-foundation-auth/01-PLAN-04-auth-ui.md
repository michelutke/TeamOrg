---
plan: "04"
wave: 3
phase: 1
title: "Auth UI — Login, Register, Empty State screens + ViewModels"
depends_on: ["02", "03"]
autonomous: true
files_modified:
  - shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt
  - shared/src/commonMain/kotlin/ch/teamorg/data/network/ApiConfig.kt
  - shared/src/commonMain/kotlin/ch/teamorg/data/repository/AuthRepositoryImpl.kt
  - shared/src/commonMain/kotlin/ch/teamorg/repository/AuthRepository.kt
  - shared/src/commonMain/kotlin/ch/teamorg/domain/Auth.kt
  - shared/src/commonMain/kotlin/ch/teamorg/auth/AuthState.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/auth/AuthViewModel.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/login/LoginScreen.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/login/LoginViewModel.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/register/RegisterScreen.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/register/RegisterViewModel.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/emptystate/EmptyStateScreen.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/emptystate/EmptyStateViewModel.kt
  - composeApp/src/androidMain/kotlin/ch/teamorg/di/KmpViewModel.android.kt
  - composeApp/src/iosMain/kotlin/ch/teamorg/di/KmpViewModel.ios.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/di/KmpViewModel.kt
  - shared/src/androidMain/kotlin/ch/teamorg/preferences/UserPreferences.android.kt
  - shared/src/iosMain/kotlin/ch/teamorg/preferences/UserPreferences.ios.kt
  - shared/src/commonMain/kotlin/ch/teamorg/preferences/UserPreferences.kt
  - shared/src/commonMain/kotlin/ch/teamorg/di/SharedModule.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt
  - composeApp/src/androidMain/kotlin/ch/teamorg/di/AndroidComposeModule.kt
  - composeApp/src/androidMain/kotlin/ch/teamorg/TeamorgApplication.kt
  - composeApp/src/androidMain/kotlin/ch/teamorg/MainActivity.kt
  - composeApp/src/androidMain/AndroidManifest.xml
  - composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/login/LoginScreenTest.kt
  - composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/register/RegisterScreenTest.kt
  - composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/emptystate/EmptyStateScreenTest.kt
  - local.properties.example
requirements:
  - AUTH-01
  - AUTH-02
  - AUTH-03
  - AUTH-04
  - AUTH-05
  - AUTH-06
---

# Plan 04 — Auth UI: Login, Register, Empty State + ViewModels

## Goal
All auth screens implemented, connected to the server via Ktor client, token stored with multiplatform-settings, and ViewModel architecture in place. Full test coverage.

## Context
- KmpViewModel pattern: `expect/actual` — Android uses Koin+Lifecycle VM; iOS bypasses KoinViewModelFactory
- Token stored via `multiplatform-settings` (UserPreferences wrapper)
- API base URL from `local.properties` → `BuildConfig` (debug/release variants)
- Empty state shows: Join CTA + Create club CTA + "Share your link" (reverse invite)
- No Material You

## Tasks

<task id="04-01" title="ApiConfig.kt + HttpClientFactory.kt">
`ApiConfig.kt` — `expect/actual` for base URL from BuildConfig:
```kotlin
expect object ApiConfig {
    val baseUrl: String
}
```
Android actual reads `BuildConfig.API_BASE_URL` (from `local.properties` gradle injection).
iOS actual reads from bundle info plist (set via XcodeGen Config).

`HttpClientFactory.kt` — Ktor `HttpClient`:
- ContentNegotiation (kotlinx-json)
- Logging (DEBUG in debug builds)
- DefaultRequest with baseUrl + `Content-Type: application/json`
- Auth plugin: reads token from UserPreferences, injects `Authorization: Bearer {token}`
- Refresh not implemented (401 → clear token → emit logged-out event)
</task>

<task id="04-02" title="UserPreferences.kt — token storage">
`expect class UserPreferences`:
- `fun saveToken(token: String)`
- `fun getToken(): String?`
- `fun clearToken()`
- `fun saveUserId(id: String)`
- `fun getUserId(): String?`

Uses `multiplatform-settings` under the hood.
Android actual: SharedPreferences-backed Settings.
iOS actual: NSUserDefaults-backed Settings.
</task>

<task id="04-03" title="Auth domain + repository">
`Auth.kt` domain models:
```kotlin
data class AuthUser(
    val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val isSuperAdmin: Boolean
)
data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, val displayName: String)
data class AuthResponse(val token: String, val user: AuthUser)
```

`AuthRepository` interface:
- `suspend fun register(request: RegisterRequest): Result<AuthResponse>`
- `suspend fun login(request: LoginRequest): Result<AuthResponse>`
- `fun logout()`
- `fun isLoggedIn(): Boolean`
- `suspend fun getMe(): Result<AuthUser>`

`AuthRepositoryImpl` calls Ktor client endpoints from Plan 02.
</task>

<task id="04-04" title="AuthState.kt + AuthViewModel (app-level)">
`AuthState.kt`:
```kotlin
sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: AuthUser, val hasTeam: Boolean) : AuthState()
}
```

`AuthViewModel` (KmpViewModel):
- Holds `StateFlow<AuthState>`
- On init: checks stored token → calls `GET /auth/me` → emits Authenticated or Unauthenticated
- `hasTeam` = false in Phase 1 (team membership check added in Phase 2)
- Consumed by `TeamorgApp.kt` for navigation routing
</task>

<task id="04-05" title="KmpViewModel expect/actual">
`KmpViewModel.kt` (commonMain): `expect abstract class KmpViewModel`
`KmpViewModel.android.kt`: `actual abstract class KmpViewModel : ViewModel()` (uses AndroidX lifecycle)
`KmpViewModel.ios.kt`: `actual abstract class KmpViewModel` (plain class, manual lifecycle)

Koin `viewModel {}` on Android; direct constructor injection on iOS.
</task>

<task id="04-06" title="LoginScreen.kt + LoginViewModel">
`LoginViewModel`:
- State: `{ email, password, isLoading, error: String? }`
- Actions: `onEmailChange`, `onPasswordChange`, `onLoginClick`
- On login: calls `AuthRepository.login()` → success: save token → update AuthState → navigate; error: show snackbar

`LoginScreen.kt` (M3 themed):
- Email field (M3 `OutlinedTextField`)
- Password field with show/hide toggle
- "Sign in" button (M3 `Button` with primary color)
- "Create account" link navigates to Register
- Error shown via Snackbar (top of screen, red) — from UX patterns doc
- Loading state: button shows progress indicator, fields disabled
- Background: `--background #090912`
</task>

<task id="04-07" title="RegisterScreen.kt + RegisterViewModel">
`RegisterViewModel`:
- State: `{ displayName, email, password, confirmPassword, isLoading, error: String? }`
- Validation: email format, password >= 8 chars, passwords match
- On register: calls `AuthRepository.register()` → success → navigate to EmptyState

`RegisterScreen.kt`:
- Display name field
- Email field
- Password field (show/hide)
- Confirm password field
- "Create account" button
- "Already have an account?" → Login
- Client-side validation shown inline (below field)
- Server errors via Snackbar
</task>

<task id="04-08" title="EmptyStateScreen.kt">
Shown when user is authenticated but has no team yet.

Three sections:
1. **Welcome** — "Welcome to Teamorg" + illustration/icon placeholder
2. **Join a team** — "Got an invite link?" → text field to paste link + "Join" button
3. **Create a club** — "Starting a club?" → "Set up your club" button (navigates to Phase 2 club setup)
4. **Share your profile** — "Let a coach add you:" → generates `teamorg://invite/player/{userId}` → copy to clipboard button + share sheet

Copy button shows Toast "Copied to clipboard" (bottom, info, 3s — from UX patterns doc).

Note: The deep link URI scheme is registered in AndroidManifest and iOS Info.plist, handled in Phase 2.
</task>

<task id="04-09" title="Koin DI modules">
`SharedModule.kt`:
- `single { UserPreferences() }`
- `single { HttpClientFactory.create(get()) }`
- `single<AuthRepository> { AuthRepositoryImpl(get(), get()) }`

`UiModule.kt`:
- `viewModel { AuthViewModel(get()) }`
- `viewModel { LoginViewModel(get()) }`
- `viewModel { RegisterViewModel(get()) }`
- `viewModel { EmptyStateViewModel(get()) }`

`TeamorgApplication.kt` (Android): `startKoin { modules(SharedModule, UiModule, AndroidComposeModule) }`
</task>

<task id="04-10" title="Tests — auth UI">
`LoginScreenTest.kt`:
- `test login screen renders correctly`
- `test login with valid credentials navigates to main`
- `test login with invalid credentials shows error snackbar`
- `test login loading state disables inputs`

`RegisterScreenTest.kt`:
- `test register screen renders correctly`
- `test register validates email format`
- `test register validates password length`
- `test register validates password match`
- `test register success navigates to empty state`

`EmptyStateScreenTest.kt`:
- `test empty state shows all three sections`
- `test copy profile link copies to clipboard`
- `test join team button visible`
</task>

## Verification

```bash
./gradlew :composeApp:testDebugUnitTest
# All LoginScreen, RegisterScreen, EmptyStateScreen tests pass
```

## must_haves
- [ ] User can register → token saved → routed to EmptyState
- [ ] User can log in → token saved → routed to correct destination
- [ ] App restart with valid saved token → stays logged in (`GET /auth/me` succeeds)
- [ ] App restart with expired token → routes to Login
- [ ] EmptyState shows profile link copy button
- [ ] All screen tests pass
- [ ] No hardcoded API URL (from BuildConfig)
