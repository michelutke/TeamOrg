---
status: awaiting_human_verify
trigger: "User can't log in or register in the app. Stuck on the signin/register screen."
created: 2026-03-20T00:00:00Z
updated: 2026-03-20T00:30:00Z
---

## Current Focus

hypothesis: CONFIRMED - Two bugs: (1) HttpClient auth token captured at creation time, (2) AuthUser.userId vs server User.id field mismatch
test: Integration + unit tests all pass
expecting: User can login/register and navigate past auth screen
next_action: Await human verification

## Symptoms

expected: User should be able to register a new account or login with existing credentials and navigate away from the auth screens
actual: User is stuck on the signin/register screen - can't proceed past it
errors: Silent 401 on /auth/me (wrong token) + deserialization error (field name mismatch)
reproduction: Open app, try to register or login - stays on auth screen
started: After commit a411689 which fixed invite/join flow bugs

## Eliminated

## Evidence

- timestamp: 2026-03-20T00:05:00Z
  checked: LoginViewModel + RegisterViewModel
  found: Both correctly call authRepository.login/register which saves token to UserPreferences, then emit success via Channel.
  implication: Login/register flow up to token save is correct.

- timestamp: 2026-03-20T00:06:00Z
  checked: AuthViewModel.checkAuthState()
  found: Calls authRepository.isLoggedIn() then authRepository.getMe(). If getMe fails, emits Unauthenticated.
  implication: If /auth/me fails after login, user goes back to Login screen.

- timestamp: 2026-03-20T00:07:00Z
  checked: HttpClientFactory.create() DefaultRequest block
  found: `val token = userPreferences.getToken()` captured ONCE at HttpClient creation. App starts with null token. After login saves token, HttpClient still sends null.
  implication: BUG 1 - post-login API calls have no Authorization header.

- timestamp: 2026-03-20T00:20:00Z
  checked: Server User model (id) vs client AuthUser model (userId)
  found: Server /auth/me returns `User(id=...)` but client AuthUser expects `userId`. Deserialization fails: "Field 'userId' is required but it was missing."
  implication: BUG 2 - even with correct auth token, getMe() fails due to field name mismatch.

- timestamp: 2026-03-20T00:22:00Z
  checked: Previous fix commit a411689
  found: Changed login/register callbacks from direct navigation to onAuthSuccess() -> checkAuthState(). Before a411689, login navigated directly to EmptyState/Events, bypassing checkAuthState() entirely. The fix exposed both bugs by routing through getMe() for the first time.
  implication: Both bugs existed before but were latent. The previous fix made them manifest.

## Resolution

root_cause: |
  Two bugs causing login/register to loop back to auth screen:
  1) HttpClientFactory.DefaultRequest captured userPreferences.getToken() once at HttpClient creation time (app startup = null token). Post-login checkAuthState() called /auth/me with no auth header -> 401 -> logout -> Unauthenticated -> back to Login.
  2) Server /auth/me returns User(id=...) but client AuthUser expects field named `userId`. Deserialization fails: "Field 'userId' is required but it was missing." Even with correct auth, getMe() would fail.
  Both bugs were latent until commit a411689 changed login/register callbacks from direct navigation to checkAuthState(), which calls getMe() for the first time.
fix: |
  1) HttpClientFactory: Replaced DefaultRequest block token capture with requestPipeline.intercept(Before) that reads userPreferences.getToken() on every request.
  2) AuthUser: Added @SerialName("id") to the userId field so it correctly deserializes from server's "id" JSON key.
verification: |
  - compileCommonMainKotlinMetadata: PASS
  - compileDebugKotlinAndroid: PASS
  - testDebugUnitTest (124 tests): PASS
  - jvmTest (17 tests, 0 failed): PASS
  New tests added:
  - AuthViewModelTest: 7 tests covering init state, checkAuthState after login, logout
  - ClientRepositoryFlowTest: 2 integration tests verifying single HttpClient picks up token saved after creation
  - FakeAuthRepository: Updated to simulate real login/register behavior (sets loggedIn=true on success)
files_changed:
  - shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt
  - shared/src/commonMain/kotlin/ch/teamorg/domain/Auth.kt
  - composeApp/src/commonTest/kotlin/ch/teamorg/auth/AuthViewModelTest.kt
  - composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeAuthRepository.kt
  - shared/src/jvmTest/kotlin/ch/teamorg/repository/ClientRepositoryFlowTest.kt
