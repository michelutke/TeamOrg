---
phase: 05-notifications
plan: "04"
subsystem: push-notifications
tags: [onesignal, android, ios, push, auth]
dependency_graph:
  requires: [05-03]
  provides: [NO-10, onesignal-init, push-registration-hooks]
  affects: [AuthRepositoryImpl, TeamorgApplication, iOSApp]
tech_stack:
  added: [onesignal-android-5.7.6, OneSignalFramework-ios-spm]
  patterns: [expect-actual-push-registration, buildconfig-field, onesignal-external-user-id]
key_files:
  created:
    - shared/src/commonMain/kotlin/ch/teamorg/data/PushRegistration.kt
    - shared/src/androidMain/kotlin/ch/teamorg/data/PushRegistration.android.kt
    - shared/src/iosMain/kotlin/ch/teamorg/data/PushRegistration.ios.kt
    - shared/src/jvmMain/kotlin/ch/teamorg/data/PushRegistration.jvm.kt
  modified:
    - gradle/libs.versions.toml
    - composeApp/build.gradle.kts
    - shared/build.gradle.kts
    - composeApp/src/androidMain/kotlin/ch/teamorg/TeamorgApplication.kt
    - iosApp/iosApp/iOSApp.swift
    - shared/src/commonMain/kotlin/ch/teamorg/data/repository/AuthRepositoryImpl.kt
decisions:
  - "PushRegistration expect/actual: Android calls OneSignal.login/logout; iOS+JVM no-op (iOS OneSignal SDK is native Swift, not accessible from KMM)"
  - "ONESIGNAL_APP_ID read from onesignal.appId local.properties via findProperty() in BuildConfig"
  - "iOS SPM package (OneSignalFramework) requires manual Xcode setup — documented as user setup step"
  - "iOS OneSignal.login TODO comment in AppDelegate — userId flow to Swift layer deferred"
metrics:
  duration_seconds: 142
  completed_date: "2026-03-26"
  tasks_completed: 2
  tasks_total: 2
  files_created: 4
  files_modified: 6
---

# Phase 05 Plan 04: OneSignal SDK Integration Summary

**One-liner:** OneSignal SDK 5.7.6 wired on Android (auto Gradle) + iOS (SPM placeholder) with PushRegistration expect/actual calling login/logout from AuthRepositoryImpl.

## What Was Built

### Task 1: Android OneSignal Setup

- Added `onesignal = "5.7.6"` to `libs.versions.toml` versions and libraries sections.
- Added `libs.onesignal` to both `composeApp` and `shared` `androidMain` dependencies.
- Added `ONESIGNAL_APP_ID` BuildConfig field in `composeApp/build.gradle.kts` defaultConfig, reading from `onesignal.appId` local property.
- `TeamorgApplication.kt`: calls `OneSignal.Debug.logLevel = LogLevel.NONE` then `OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)` after Koin startup.
- `PushRegistration` expect/actual: common interface with `login(userId)` and `logout()`; Android actual delegates to `OneSignal.login/logout`; iOS and JVM actuals are no-ops.
- `AuthRepositoryImpl`: calls `PushRegistration.login(authResponse.userId)` after successful register and login; calls `PushRegistration.logout()` before clearing preferences.

### Task 2: iOS OneSignal Setup

- `iOSApp.swift`: imports `OneSignalFramework`, calls `OneSignal.Debug.setLogLevel(.LL_NONE)`, `OneSignal.initialize("ONESIGNAL_APP_ID_PLACEHOLDER", withLaunchOptions:)`, and `OneSignal.Notifications.requestPermission(...)` in `AppDelegate.didFinishLaunchingWithOptions`.
- Placeholder app ID string requires user substitution from OneSignal Dashboard.
- TODO comment marks where `OneSignal.login(userId)` should be called from Swift once the auth state is exposed natively.
- iOS SPM package `OneSignalFramework` must be added manually in Xcode: File → Add Package Dependencies → `https://github.com/OneSignal/OneSignal-XCFramework`.

## Verification

- `./gradlew :composeApp:compileDebugKotlin` — BUILD SUCCESSFUL (26s)
- iOS: requires manual Xcode SPM + placeholder replacement before iOS build succeeds

## Checkpoint: human-verify

**Status:** Auto-approved (auto_advance=true)
Android build verified clean. iOS requires user to add OneSignal SPM package in Xcode and replace `ONESIGNAL_APP_ID_PLACEHOLDER`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing] Added jvmMain PushRegistration actual**
- **Found during:** Task 1
- **Issue:** shared module has a jvm() target; expect without jvmMain actual would cause compile error
- **Fix:** Created `PushRegistration.jvm.kt` with no-op implementations
- **Files modified:** shared/src/jvmMain/kotlin/ch/teamorg/data/PushRegistration.jvm.kt
- **Commit:** 78f5133

## User Action Required

To complete iOS OneSignal setup:

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. File → Add Package Dependencies → URL: `https://github.com/OneSignal/OneSignal-XCFramework`
3. Add `OneSignalFramework` target to `iosApp` app target.
4. Optionally add a Notification Service Extension target and assign `OneSignalExtension` package to it (required for rich notifications and accurate delivery counts).
5. Replace `"ONESIGNAL_APP_ID_PLACEHOLDER"` in `iOSApp.swift` with your actual OneSignal App ID.
6. Add `onesignal.appId=YOUR_APP_ID` to `local.properties` for Android BuildConfig.

## Self-Check: PASSED

- shared/src/commonMain/kotlin/ch/teamorg/data/PushRegistration.kt — FOUND
- shared/src/androidMain/kotlin/ch/teamorg/data/PushRegistration.android.kt — FOUND
- shared/src/iosMain/kotlin/ch/teamorg/data/PushRegistration.ios.kt — FOUND
- shared/src/jvmMain/kotlin/ch/teamorg/data/PushRegistration.jvm.kt — FOUND
- composeApp/src/androidMain/kotlin/ch/teamorg/TeamorgApplication.kt — FOUND (contains OneSignal.initWithContext)
- iosApp/iosApp/iOSApp.swift — FOUND (contains import OneSignalFramework, OneSignal.initialize)
- shared/src/commonMain/kotlin/ch/teamorg/data/repository/AuthRepositoryImpl.kt — FOUND (contains PushRegistration.login, PushRegistration.logout)
- Commit 78f5133 — FOUND
- Commit b541b48 — FOUND
