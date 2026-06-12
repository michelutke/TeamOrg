# CI Pipeline Fixes Summary

Fixed several CI and build-related issues in the Teamorg KMP project to ensure stable builds and deployments.

## Changes Made

### 1. Duplicate MainActivity Removed
- Deleted the stub `MainActivity.kt` in `androidApp`.
- The `androidApp` module now correctly uses the implementation in `:composeApp` (as referenced in `androidApp/src/main/AndroidManifest.xml`).

### 2. API Config & CI Environment
- Added `API_BASE_URL` build configuration field to `shared/build.gradle.kts`.
- It defaults to `https://api.teamorg.app` but can be overridden via Gradle properties.
- Updated `local.properties.example` with the new property.
- Modified `.github/workflows/ci.yml` and `deploy-android.yml` to create `local.properties` with the default API URL before running any Gradle tasks.

### 3. Missing Repositories
- Added JetBrains dev repositories (`compose/dev` and `compose/interactive`) to `settings.gradle.kts` to allow resolution of alpha artifacts like Navigation3 and Lifecycle.

### 4. Build File & Dependency Cleanup
- Fixed `server/build.gradle.kts` to use the version catalog for the Kotlin JVM plugin.
- Simplified `androidApp/build.gradle.kts` by removing the `kotlin.multiplatform` plugin and using the standard `kotlin.android` plugin.
- Added missing plugin definitions (`kotlin-jvm`, `kotlin-android`) and library (`activity-compose`) to `gradle/libs.versions.toml`.
- Cleaned up `composeApp/build.gradle.kts` source sets, explicitly defining `androidUnitTest` and using the version catalog for `activity-compose`.

## Verification
- Build files are syntactically valid Kotlin DSL.
- Logical separation of modules is preserved.
- CI workflows are updated to handle the new `local.properties` requirement.
