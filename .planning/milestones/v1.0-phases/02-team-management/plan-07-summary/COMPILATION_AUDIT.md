# Compilation Audit Report

## Issues Found & Fixed

### 1. Navigation3 API Mismatch
- **Issue:** The project was using a mix of manual backstack management and outdated Navigation3 API calls (e.g., `rememberNavWrapper`, `NavBackStackEntry` without `NavController`).
- **Fix:** Switched to the correct `rememberNavController` and `NavHost(navController)` API from Navigation3 `alpha06`.
- **Files Changed:** 
    - `composeApp/src/commonMain/kotlin/ch/teamorg/TeamorgApp.kt`
    - `composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt`

### 2. Screen Model Missing Loading State
- **Issue:** `TeamorgApp` needed a `Loading` state for initial `AuthState` check, but it was missing from the `Screen` sealed class.
- **Fix:** Added `data object Loading` to `Screen`.
- **Files Changed:**
    - `composeApp/src/commonMain/kotlin/ch/teamorg/navigation/Screen.kt`

### 3. ViewModel Base Class Inconsistency
- **Issue:** `expect class KmpViewModel` in `commonMain` did not inherit from `androidx.lifecycle.ViewModel`, which is required for Koin's `viewModel {}` DSL and proper lifecycle management in Compose. The `iosMain` implementation was also missing the `ViewModel` inheritance.
- **Fix:** Updated `KmpViewModel` to inherit from `ViewModel` in all source sets.
- **Files Changed:**
    - `composeApp/src/commonMain/kotlin/ch/teamorg/di/KmpViewModel.kt`
    - `composeApp/src/androidMain/kotlin/ch/teamorg/di/KmpViewModel.android.kt`
    - `composeApp/src/iosMain/kotlin/ch/teamorg/di/KmpViewModel.ios.kt`

### 4. Koin UI Module Platform Inconsistency
- **Issue:** `uiModule.ios.kt` was using `factory {}` while `uiModule.android.kt` used `viewModel {}`. Navigation3 and Compose Multiplatform work best when both use `viewModel {}` for lifecycle persistence.
- **Fix:** Changed `factory {}` to `viewModel {}` in `uiModule.ios.kt`.
- **Files Changed:**
    - `composeApp/src/iosMain/kotlin/ch/teamorg/di/UiModule.ios.kt`

### 5. Missing Kotlin Serialization Plugin
- **Issue:** `composeApp` was using `@Serializable` for its navigation screens but didn't have the `kotlinx-serialization` plugin applied in its `build.gradle.kts`.
- **Fix:** Added the serialization plugin to `composeApp/build.gradle.kts`.
- **Files Changed:**
    - `composeApp/build.gradle.kts`

### 6. CI/Environment Variable Support for API_BASE_URL
- **Issue:** `BuildConfig.API_BASE_URL` in `shared` only looked at Gradle properties, which can be tricky to pass in CI without `local.properties`.
- **Fix:** Updated `shared/build.gradle.kts` to check for `System.getenv("API_BASE_URL")` as well.
- **Files Changed:**
    - `shared/build.gradle.kts`

## Status
All identified compilation and architectural issues have been resolved. The project should now build correctly in CI.