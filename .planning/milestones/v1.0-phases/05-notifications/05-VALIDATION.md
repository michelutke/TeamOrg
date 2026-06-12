---
phase: 05
slug: notifications
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-26
---

# Phase 05 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotlin Test + Ktor TestApplication (server), Kotlin Test (shared) |
| **Config file** | `server/src/test/resources/application-test.conf` |
| **Quick run command** | `./gradlew :server:test --tests "ch.teamorg.notification.*"` |
| **Full suite command** | `./gradlew :server:test :shared:allTests` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :server:test --tests "ch.teamorg.notification.*"`
- **After every plan wave:** Run `./gradlew :server:test :shared:allTests`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| *Populated after planning* | | | | | | | |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `server/src/test/kotlin/ch/teamorg/notification/` — test directory for notification tests
- [ ] Notification test fixtures — DB setup for notification tables
- [ ] OneSignal mock — `FakePushService` implementing `PushService` interface

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Push received on device | NO-10 | Requires real device + OneSignal sandbox | Send test notification via OneSignal dashboard, verify on device |
| iOS Notification Service Extension | NO-10 | Xcode build configuration | Verify extension target exists in Xcode project |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
