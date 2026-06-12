---
phase: 03
slug: event-scheduling
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-19
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotest (shared/Android unit), Compose UI test (Android), XCTest (iOS), Testcontainers + RestAssured (server E2E) |
| **Config file** | `shared/src/commonTest/`, `composeApp/src/androidTest/`, `server/src/test/` |
| **Quick run command** | `./gradlew :shared:testDebugUnitTest :server:test --tests "*.EventTest*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~90 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :shared:testDebugUnitTest :server:test --tests "*.EventTest*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 0 | ES-01 | unit | `./gradlew :shared:testDebugUnitTest --tests "*.EventModelTest*"` | W0 | pending |
| 03-01-02 | 01 | 0 | ES-08 | unit | `./gradlew :shared:testDebugUnitTest --tests "*.RecurringExpansionTest*"` | W0 | pending |
| 03-01-03 | 01 | 1 | ES-01-04 | integration | `./gradlew :server:test --tests "*.EventCrudTest*"` | W0 | pending |
| 03-01-04 | 01 | 1 | ES-08-10 | integration | `./gradlew :server:test --tests "*.RecurringEventTest*"` | W0 | pending |
| 03-01-05 | 01 | 2 | ES-09-10 | integration | `./gradlew :server:test --tests "*.EditCancelScopeTest*"` | W0 | pending |
| 03-02-01 | 02 | 1 | ES-11, ES-13 | UI | `./gradlew :composeApp:connectedAndroidTest --tests "*.EventListScreenTest*"` | W0 | pending |
| 03-02-02 | 02 | 1 | ES-07 | integration | `./gradlew :server:test --tests "*.EventRoutesTest*"` | W0 | pending |
| 03-02-03 | 02 | 2 | ES-05 | UI | `./gradlew :composeApp:connectedAndroidTest --tests "*.SubGroupTargetingTest*"` | W0 | pending |
| 03-02-04 | 02 | 2 | ES-12 | unit | `./gradlew :shared:testDebugUnitTest --tests "*.TimezoneDisplayTest*"` | W0 | pending |
| 03-02-05 | 02 | 2 | ES-14 | UI | `./gradlew :composeApp:connectedAndroidTest --tests "*.CalendarViewTest*"` | W0 | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `shared/src/commonTest/kotlin/event/EventModelTest.kt` — stubs for ES-01-04
- [ ] `shared/src/commonTest/kotlin/event/RecurringExpansionTest.kt` — stubs for ES-08
- [ ] `shared/src/commonTest/kotlin/event/TimezoneDisplayTest.kt` — stubs for ES-12
- [ ] `server/src/test/kotlin/event/EventCrudTest.kt` — stubs for ES-01-04 server routes
- [ ] `server/src/test/kotlin/event/RecurringEventTest.kt` — stubs for ES-08-10
- [ ] `server/src/test/kotlin/event/EditCancelScopeTest.kt` — stubs for ES-09-10
- [ ] `composeApp/src/androidTest/kotlin/event/EventListScreenTest.kt` — stubs for ES-11, ES-13 (event list + filtering)
- [ ] `composeApp/src/androidTest/kotlin/event/CalendarViewTest.kt` — stub for ES-14 (calendar rendering)
- [ ] `composeApp/src/androidTest/kotlin/event/SubGroupTargetingTest.kt` — stub for ES-05 (sub-group targeting)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Calendar view renders correctly on iOS | ES-14 | No compose-multiplatform UI test runner for iOS in CI | Launch iOS sim, navigate to calendar, verify month + week render |
| Timezone offset display (CET/CEST) | ES-12 | Device locale/TZ dependency | Set device TZ to CET, create event, verify displayed time is UTC+1 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
