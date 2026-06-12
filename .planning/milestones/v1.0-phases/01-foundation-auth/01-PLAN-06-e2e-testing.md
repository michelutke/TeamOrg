---
plan: "06"
wave: 4
phase: 1
title: "E2E Auth Flow Testing"
depends_on: ["04"]
autonomous: true
files_modified:
  - composeApp/src/androidTest/kotlin/ch/teamorg/e2e/AuthE2ETest.kt
requirements:
  - AUTH-01
  - AUTH-02
  - AUTH-05
  - AUTH-06
---

# Plan 06 — E2E Auth Flow Testing

## Goal
Validate the full stack: UI -> ViewModel -> Repository -> Network -> Ktor Server (test instance).

## Context
- Use Ktor's `testApplication` or a local mock server that mimics the real server's behavior.
- Preferred: Run a local Ktor server instance during the test or use `MockEngine` for the Ktor client to simulate the full network roundtrip.

## Tasks

<task id="06-01" title="Happy Path: Registration to Empty State">
1. Start at Login
2. Navigate to Register
3. Fill valid details
4. Click Register
5. Verify redirect to Empty State screen
6. Verify token is stored in UserPreferences
</task>

<task id="06-02" title="Happy Path: Login to Main App">
1. Start at Login
2. Fill valid credentials (pre-seeded in test DB)
3. Click Login
4. Verify redirect to Dashboard (main app nav)
</task>

<task id="06-03" title="Edge Case: Expired Token">
1. App starts with an expired token in storage
2. Verify it automatically redirects/stays at Login screen
</task>

## Verification
```bash
./gradlew :composeApp:connectedDebugAndroidTest
```
