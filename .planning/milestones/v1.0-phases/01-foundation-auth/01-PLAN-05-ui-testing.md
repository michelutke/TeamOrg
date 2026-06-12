---
plan: "05"
wave: 4
phase: 1
title: "Compose UI Testing — Auth Screens"
depends_on: ["04"]
autonomous: true
files_modified:
  - composeApp/src/androidTest/kotlin/ch/teamorg/ui/login/LoginScreenUiTest.kt
  - composeApp/src/androidTest/kotlin/ch/teamorg/ui/register/RegisterScreenUiTest.kt
  - composeApp/src/androidTest/kotlin/ch/teamorg/ui/emptystate/EmptyStateScreenUiTest.kt
requirements:
  - AUTH-01
  - AUTH-02
  - AUTH-04
---

# Plan 05 — Compose UI Testing: Auth Screens

## Goal
Verify that the UI renders correctly and handles user interaction (clicks, text input, validation display) as expected using `compose-ui-test`.

## Context
- Use `createComposeRule()` or `createAndroidComposeRule<MainActivity>()`.
- Focus on interaction logic: do buttons disable when loading? do errors show up?
- Target: Android (via `androidTest` source set).

## Tasks

<task id="05-01" title="LoginScreen UI Tests">
- `test login screen has all fields`
- `test clicking login with empty fields shows validation errors`
- `test login button disables during loading state`
- `test clicking create account navigates to register`
</task>

<task id="05-02" title="RegisterScreen UI Tests">
- `test password mismatch shows error`
- `test invalid email format shows error`
- `test register button disables during loading`
</task>

<task id="05-03" title="EmptyState UI Tests">
- `test sections render correctly (Join, Create, Share)`
- `test clicking copy link shows toast/snackbar confirmation`
</task>

## Verification
```bash
./gradlew :composeApp:connectedDebugAndroidTest
```
