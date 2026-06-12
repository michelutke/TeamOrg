---
plan: "01"
wave: 1
phase: 1
title: "Monorepo scaffold + Gradle setup"
depends_on: []
autonomous: true
files_modified:
  - settings.gradle.kts
  - build.gradle.kts
  - gradle/libs.versions.toml
  - gradle.properties
  - composeApp/build.gradle.kts
  - shared/build.gradle.kts
  - server/build.gradle.kts
  - admin/package.json
  - androidApp/build.gradle.kts
  - iosApp/project.yml
  - iosApp/iosApp/iOSApp.swift
  - iosApp/iosApp/ContentView.swift
  - .github/workflows/ci.yml
  - .gitignore
requirements:
  - AUTH-01
  - AUTH-02
---

# Plan 01 — Monorepo Scaffold + Gradle Setup

## Goal
Create the clean KMP monorepo structure. Everything in subsequent plans depends on this compiling.

## Context
- Fresh start — do NOT copy from `failed example project/`, use it as version reference only
- Kotlin 2.3.10, Compose Multiplatform 1.10.1, AGP 8.13.2, Gradle 8.13
- Navigation3 1.0.0-alpha06, Koin 4.1.0, Ktor 3.1.0
- kotlinx-datetime pinned to 0.6.0 (0.7.x causes Kotlin/Native IR crash — CRITICAL)
- Package name: `ch.teamorg`

## Tasks

<task id="01-01" title="Create version catalog">
Create `gradle/libs.versions.toml` with all pinned versions.

Versions:
```toml
[versions]
kotlin = "2.3.10"
agp = "8.13.2"
compose-multiplatform = "1.10.1"
koin = "4.1.0"
koin-compose = "4.1.0"
ktor = "3.1.0"
kotlinx-datetime = "0.6.0"
kotlinx-serialization = "1.8.1"
navigation3 = "1.0.0-alpha06"
coil3 = "3.4.0"
sqldelight = "2.0.2"
lifecycle = "2.9.1"
material3 = "1.4.0-alpha13"
exposed = "0.54.0"
hikaricp = "5.1.0"
flyway = "10.18.0"
postgresql = "42.7.3"
simple-java-mail = "8.3.4"
multiplatform-settings = "1.3.0"
```

All libraries and plugins defined with aliases.
</task>

<task id="01-02" title="Root build.gradle.kts + settings.gradle.kts">
Configure root build file:
- KMP plugin applied in submodules
- Repository setup (mavenCentral, google, compose.multiplatform)

settings.gradle.kts includes modules:
- `:shared`
- `:composeApp`
- `:androidApp`
- `:server`

(`:admin` is npm — not a Gradle module)
</task>

<task id="01-03" title="shared module setup">
`shared/build.gradle.kts`:
- KMP targets: androidTarget, iosX64, iosArm64, iosSimulatorArm64, jvm (for server tests)
- commonMain dependencies: koin-core, ktor-client-core, kotlinx-datetime, kotlinx-serialization, sqldelight-runtime, multiplatform-settings
- androidMain: ktor-client-okhttp, sqldelight-android-driver
- iosMain: ktor-client-darwin, sqldelight-native-driver
- jvmMain: ktor-client-cio, sqldelight-sqlite-driver (test only)

Create stub `shared/src/commonMain/kotlin/ch/teamorg/` with `.gitkeep`.
Create `shared/src/commonMain/sqldelight/ch/teamorg/db/` (SQLDelight query dir).
</task>

<task id="01-04" title="composeApp module setup">
`composeApp/build.gradle.kts`:
- KMP targets: androidTarget, iosX64, iosArm64, iosSimulatorArm64
- commonMain dependencies: shared, compose-multiplatform, material3, navigation3, koin-compose, coil3, lifecycle-viewmodel-compose
- androidMain: koin-android, lifecycle-process
- compilerOptions: enableExpectActualClasses = true

`android {}` block: compileSdk 36, minSdk 26, targetSdk 36.
</task>

<task id="01-05" title="androidApp thin shell">
`androidApp/build.gradle.kts` — minimal Android application module.
`androidApp/src/main/kotlin/ch/teamorg/MainActivity.kt` — stub, delegates to composeApp.
`androidApp/src/main/AndroidManifest.xml` — internet permission, main activity.
</task>

<task id="01-06" title="server module setup">
`server/build.gradle.kts`:
- JVM application plugin
- Dependencies: ktor-server-netty, ktor-server-content-negotiation, ktor-serialization-kotlinx-json, ktor-server-auth-jwt, koin-ktor, exposed-core, exposed-jdbc, hikaricp, flyway-core, postgresql, simple-java-mail, logback-classic
- Test: ktor-server-tests, kotlin-test, h2 (in-memory for tests)
</task>

<task id="01-07" title="iOS entry point stubs">
Create `iosApp/iosApp/iOSApp.swift` and `ContentView.swift` (stubs — KMP not wired yet, just compiles).
Create `iosApp/project.yml` (XcodeGen config) — configure bundle ID `ch.teamorg.ios`, deployment target iOS 16.
</task>

<task id="01-08" title="GitHub Actions CI">
`.github/workflows/ci.yml`:
- Trigger: push + PR to main
- Jobs:
  - `test-shared`: `./gradlew :shared:allTests`
  - `test-server`: `./gradlew :server:test`
  - `test-android`: `./gradlew :composeApp:testDebugUnitTest`
- All jobs run in parallel
- Fail fast: false (run all, report all failures)
- Use `ubuntu-latest`, Java 21 (temurin)
</task>

<task id="01-09" title=".gitignore">
Standard KMP .gitignore:
- `.gradle/`, `build/`, `*.class`
- `.DS_Store`, `*.iml`, `.idea/`
- `local.properties` (contains API URL, never committed)
- `**/*.keystore`
- `node_modules/`, `admin/.svelte-kit/`
- `failed example project/` — mark for removal but leave until Phase 1 verified
</task>

## Verification

```bash
./gradlew build --dry-run
./gradlew :shared:compileKotlinIosArm64
./gradlew :server:compileKotlin
./gradlew :composeApp:compileDebugKotlin
```

All compile without errors.

## must_haves
- [ ] `./gradlew build` succeeds (no compilation errors)
- [ ] All modules resolve dependencies without version conflicts
- [ ] kotlinx-datetime locked to 0.6.0
- [ ] CI workflow file present and valid YAML
- [ ] No files copied from `failed example project/`
