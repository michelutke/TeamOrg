# Transport Encryption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make it impossible for a release build of either mobile app to send user data over cleartext, fail loudly on a non-HTTPS API base URL, and record the store-questionnaire answers with their evidence.

**Architecture:** Production TLS already exists (Traefik terminates, the server emits HSTS, release builds point at `https://api.teamorg.app`). This plan removes the three ways that guarantee is currently conditional — an Android cleartext permission that leaks into release, a shipped iOS ATS exception, and an unvalidated `API_BASE_URL` — then documents the result.

**Tech Stack:** Kotlin Multiplatform (`shared`), Compose Multiplatform (`composeApp`), Android network security config, iOS `Info.plist` / App Transport Security, Ktor client, `kotlin.test`.

**Spec:** `docs/superpowers/specs/2026-08-05-transport-encryption-design.md`

## Global Constraints

- **No certificate pinning.** Explicitly rejected in the spec: a Let's Encrypt chain rotation would brick installed apps with no server-side remedy.
- Debug / emulator development over cleartext to `10.0.2.2`, `localhost` and `127.0.0.1` must keep working. Only release must be locked down.
- `shared` jvmTest uses **`kotlin.test` only — no Kotest** in that source set.
- Do not change how TLS is terminated. Traefik stays the terminator.
- Do not modify server code. This plan is client configuration plus one guard in `shared`, plus docs.
- Compile gates that must stay green: `./gradlew :composeApp:compileDebugKotlinAndroid` and `./gradlew :shared:compileKotlinIosSimulatorArm64`.
- Commit messages must not contain `Co-Authored-By` or any AI-authorship hint.
- `commit.gpgsign=true` in this repo. If a 1Password/GPG prompt stalls, use `git -c commit.gpgsign=false commit` rather than waiting.

## File Structure

| File | Responsibility |
|---|---|
| `shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt` | Rejects a base URL that is neither HTTPS nor a local dev host, before the client is built |
| `shared/src/jvmTest/kotlin/ch/teamorg/network/HttpClientBaseUrlTest.kt` | New. Pins the accept/reject matrix for that guard |
| `composeApp/src/androidMain/res/xml/network_security_config.xml` | Denies cleartext outside debug builds |
| `composeApp/src/androidMain/AndroidManifest.xml` | `usesCleartextTraffic="false"` — the attribute store-side scanners look for |
| `iosApp/iosApp/Info.plist` | Loses its shipped ATS exception |
| `docs/store-data-safety.md` | New. The recorded questionnaire answers plus evidence |
| `docs/security-runbook.md` | Gains the two infrastructure items that cannot be applied from the repo |

---

### Task 1: HTTPS guard in the shared HTTP client

**Files:**
- Modify: `shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt`
- Test: `shared/src/jvmTest/kotlin/ch/teamorg/network/HttpClientBaseUrlTest.kt` (create)

**Interfaces:**
- Consumes: `ApiConfig.baseUrl` (existing `expect val`, actuals in `androidMain` / `iosMain` / `jvmMain`).
- Produces: `internal fun requireSecureBaseUrl(baseUrl: String): String` in package `ch.teamorg.data.network` — returns the URL unchanged when acceptable, throws `IllegalArgumentException` otherwise. Nothing later in this plan consumes it.

**Context:** `HttpClientFactory.create` currently does `url(ApiConfig.baseUrl)` inside `install(DefaultRequest)` with no validation. `API_BASE_URL` is injected at build time from an env var, a Gradle property, or `local.properties` (`shared/build.gradle.kts:75-105`), so a typo or a misconfigured CI variable can ship an `http://` production build silently.

- [ ] **Step 1: Write the failing test**

Create `shared/src/jvmTest/kotlin/ch/teamorg/network/HttpClientBaseUrlTest.kt`:

```kotlin
package ch.teamorg.network

import ch.teamorg.data.network.requireSecureBaseUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpClientBaseUrlTest {

    @Test
    fun `https url is accepted and returned unchanged`() {
        assertEquals("https://api.teamorg.app", requireSecureBaseUrl("https://api.teamorg.app"))
    }

    @Test
    fun `emulator host over http is accepted`() {
        assertEquals("http://10.0.2.2:8080", requireSecureBaseUrl("http://10.0.2.2:8080"))
    }

    @Test
    fun `localhost over http is accepted`() {
        assertEquals("http://localhost:8080", requireSecureBaseUrl("http://localhost:8080"))
    }

    @Test
    fun `loopback ip over http is accepted`() {
        assertEquals("http://127.0.0.1:8080", requireSecureBaseUrl("http://127.0.0.1:8080"))
    }

    @Test
    fun `remote host over http is rejected`() {
        assertFailsWith<IllegalArgumentException> { requireSecureBaseUrl("http://api.teamorg.app") }
    }

    @Test
    fun `attacker host that merely contains localhost is rejected`() {
        assertFailsWith<IllegalArgumentException> { requireSecureBaseUrl("http://localhost.evil.example") }
    }
}
```

The last test is the one that matters: a naive `contains("localhost")` check would pass
`http://localhost.evil.example`. The implementation must anchor on the host boundary.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :shared:jvmTest --tests '*HttpClientBaseUrlTest'`
Expected: FAIL — compilation error, `requireSecureBaseUrl` is unresolved.

- [ ] **Step 3: Implement the guard**

In `shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt`, add above the
`object HttpClientFactory` declaration:

```kotlin
private val LOCAL_DEV_HOSTS = listOf("10.0.2.2", "localhost", "127.0.0.1")

/**
 * A release build must never transmit user data over cleartext. `API_BASE_URL` is injected at
 * build time, so a wrong value would otherwise ship silently — this turns that into a crash at
 * client construction. Local development hosts stay allowed over http.
 */
internal fun requireSecureBaseUrl(baseUrl: String): String {
    if (baseUrl.startsWith("https://")) return baseUrl
    // Anchor on the host boundary so "localhost.evil.example" is not mistaken for localhost.
    val isLocalDev = LOCAL_DEV_HOSTS.any { host ->
        baseUrl == "http://$host" ||
            baseUrl.startsWith("http://$host:") ||
            baseUrl.startsWith("http://$host/")
    }
    require(isLocalDev) { "API_BASE_URL must use https:// (got: $baseUrl)" }
    return baseUrl
}
```

Then change the `install(DefaultRequest)` block's URL line from:

```kotlin
                url(ApiConfig.baseUrl)
```

to:

```kotlin
                url(requireSecureBaseUrl(ApiConfig.baseUrl))
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :shared:jvmTest --tests '*HttpClientBaseUrlTest'`
Expected: PASS, 6 tests.

- [ ] **Step 5: Confirm nothing else broke**

Run, in the foreground, one at a time:

```bash
./gradlew :shared:jvmTest
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: all three succeed. `ApiConfig.jvm.kt` defaults to `http://localhost:8080`, which the
allow-list covers, so existing `shared` tests that build a client are unaffected.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt \
        shared/src/jvmTest/kotlin/ch/teamorg/network/HttpClientBaseUrlTest.kt
git commit -m "security(mobile): reject a non-HTTPS API base URL at client construction"
```

---

### Task 2: Deny cleartext in release builds (Android + iOS)

**Files:**
- Modify: `composeApp/src/androidMain/res/xml/network_security_config.xml`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml:4-13`
- Modify: `iosApp/iosApp/Info.plist:38-46`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: nothing consumed by later tasks. This task is platform configuration only.

**Context you need:** `composeApp` declares only a `release` build type override
(`composeApp/build.gradle.kts:129-134`); the Android plugin's default `debug` build type is
`debuggable`, so `<debug-overrides>` applies to debug builds and not to release. This has been
verified — do not add a `dev` build type or change `isDebuggable`.

The current config permits cleartext to `10.0.2.2` and `localhost` from a `<domain-config>` that
sits **outside** `<debug-overrides>`, so it applies to release too. There is no base-config
denying cleartext and no `usesCleartextTraffic` attribute.

- [ ] **Step 1: Rewrite the network security config**

Replace the entire contents of `composeApp/src/androidMain/res/xml/network_security_config.xml`
with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Release: cleartext is impossible. The app talks HTTPS or it fails. -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- Debug builds only (android:debuggable=true): the emulator host alias and localhost stay
         reachable over HTTP so a debug build against a locally-run server keeps working. -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
        <domain-config cleartextTrafficPermitted="true">
            <domain includeSubdomains="true">10.0.2.2</domain>
            <domain includeSubdomains="true">localhost</domain>
        </domain-config>
    </debug-overrides>
</network-security-config>
```

- [ ] **Step 2: Add the manifest attribute**

In `composeApp/src/androidMain/AndroidManifest.xml`, add `android:usesCleartextTraffic="false"`
to the `<application>` element, on the line after `android:networkSecurityConfig`:

```xml
        android:networkSecurityConfig="@xml/network_security_config"
        android:usesCleartextTraffic="false">
```

(The existing line ends with `>` — move it to the new attribute.)

This is intentionally redundant with `base-config`: the manifest attribute is what several
store-side and third-party scanners actually read.

- [ ] **Step 3: Remove the shipped iOS ATS exception**

In `iosApp/iosApp/Info.plist`, delete this entire block:

```xml
	<key>NSAppTransportSecurity</key>
	<dict>
		<key>NSExceptionDomains</key>
		<dict>
			<key>localhost</key>
			<dict>
				<key>NSExceptionAllowsInsecureHTTPLoads</key>
				<true/>
			</dict>
		</dict>
	</dict>
```

The iOS Simulator permits `http://localhost` without an ATS exception, so simulator development
against a locally-run server is unaffected.

- [ ] **Step 4: Verify the debug build still resolves the config**

Run, in the foreground:

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. Then confirm the merged debug manifest carries both attributes:

```bash
grep -o 'usesCleartextTraffic="[^"]*"' composeApp/build/intermediates/merged_manifest/debug/*/AndroidManifest.xml
```

Expected: `usesCleartextTraffic="false"`. The path glob covers the task-name directory, which
varies by AGP version; if it does not match, find the file with
`find composeApp/build/intermediates -name AndroidManifest.xml -path '*merged_manifest/debug*'`.

- [ ] **Step 5: Verify the iOS shared framework still compiles**

Run: `./gradlew :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (The plist change cannot break Kotlin compilation; this is the gate
the repo has, and a full Xcode build is part of the manual verification below.)

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/androidMain/res/xml/network_security_config.xml \
        composeApp/src/androidMain/AndroidManifest.xml \
        iosApp/iosApp/Info.plist
git commit -m "security(mobile): deny cleartext traffic in release builds"
```

- [ ] **Step 7: Record what still needs a human**

Nothing further to run. Report in your task report, verbatim, that these remain unverified and
require a device / Xcode:

1. A **release-configuration Android build** installed and confirmed to load data — proves the
   HTTPS path works under `usesCleartextTraffic="false"`.
2. A **debug build** confirmed to still reach a locally-run server at `10.0.2.2:8080` — proves
   `debug-overrides` covers it.
3. An **iOS simulator run** after the ATS removal — proves local development is unaffected. If
   it is not, the fallback is a debug-configuration-only plist value, never a shipped one.

There is no automated test that a release APK refuses cleartext; that is a platform behaviour
asserted by the configuration itself.

---

### Task 3: Document the store answers and the infrastructure items

**Files:**
- Create: `docs/store-data-safety.md`
- Modify: `docs/security-runbook.md` (append to section 6, "TLS and proxy headers")

**Interfaces:**
- Consumes: the changes made in Tasks 1 and 2, which are the evidence this document cites.
- Produces: nothing code-level.

**Context:** The point of this task is that the next release must not re-investigate the
transport story from scratch. Cite file paths and line references so a reader can verify each
claim rather than trust it.

- [ ] **Step 1: Write `docs/store-data-safety.md`**

```markdown
# Store Privacy Answers — Data in Transit

Answers for Google Play's Data Safety form and Apple's App Privacy questionnaire, with the
evidence behind them. Update this file when the transport story changes; do not re-derive it.

## The answer

**Is all user data collected by the app encrypted in transit? — Yes.**

Same answer for both stores.

## Evidence

| Claim | Where to verify |
|---|---|
| Release builds use an HTTPS base URL | `shared/build.gradle.kts` — release `buildConfigField` defaults to `https://api.teamorg.app` |
| A non-HTTPS base URL cannot ship silently | `shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt` — `requireSecureBaseUrl` throws unless the URL is HTTPS or a local dev host |
| Android release builds cannot speak cleartext | `composeApp/src/androidMain/res/xml/network_security_config.xml` — `base-config cleartextTrafficPermitted="false"`; `AndroidManifest.xml` — `android:usesCleartextTraffic="false"` |
| iOS ships no ATS exception | `iosApp/iosApp/Info.plist` — no `NSAppTransportSecurity` key |
| The server pins clients to HTTPS | `server/src/main/kotlin/ch/teamorg/plugins/Security.kt` — `Strict-Transport-Security`, with `XForwardedHeaders` resolving the client-facing scheme behind the proxy |
| TLS is terminated at the edge | Coolify / Traefik, per `docs/deployment.md` |

## Exceptions, and why they do not change the answer

- **Debug builds** reach `10.0.2.2` (the Android emulator's alias for the developer's own
  machine) and `localhost` over HTTP. This is developer traffic on the developer's machine, never
  user data, and it is confined to `debug-overrides` in the network security config, which the
  platform applies only when `android:debuggable="true"`.
- **Server → Postgres** travels the internal Docker network. See `docs/security-runbook.md` §6
  for the `sslmode` decision and its justification.

## Certificate pinning

Deliberately not implemented. A Let's Encrypt chain rotation would brick every installed app
with no server-side remedy, certificates rotate automatically via Coolify, and neither store
requires pinning. See `docs/superpowers/specs/2026-08-05-transport-encryption-design.md`.
```

- [ ] **Step 2: Append the infrastructure items to the runbook**

In `docs/security-runbook.md`, inside section `## 6. TLS and proxy headers`, append after the
existing bullet list:

```markdown
### Postgres connection TLS

`DATABASE_URL` carries no `sslmode`, so the server → Postgres hop is plaintext. It travels only
over Coolify's internal Docker network and the database has no public port (§5), but the store
privacy answer needs a defensible position rather than an open question.

Check whether the managed Postgres presents a certificate:

```bash
docker exec -it <postgres-container> psql -U <user> -c "SHOW ssl;"
```

- `on` → append `&sslmode=require` to `DATABASE_URL` in Coolify and redeploy the server.
- `off` → record that here, with the justification (internal network only, no public port), and
  do not leave it as a pending item. `docs/store-data-safety.md` references this decision.

### HTTP → HTTPS redirect (must be confirmed, not assumed)

HSTS only protects a client that has already completed one successful HTTPS request. Until the
redirect is enforced in Traefik for all three domains, a first-ever plain-HTTP request is
answered over cleartext. Confirm with:

```bash
curl -sSI http://api.teamorg.app | head -1   # expect 301/308
```
```

- [ ] **Step 3: Commit**

```bash
git add docs/store-data-safety.md docs/security-runbook.md
git commit -m "docs(security): record store transport answers and Postgres TLS decision"
```

---

## Self-Review

**Spec coverage:** §1 Android → Task 2 steps 1-2. §2 iOS → Task 2 step 3. §3 base-URL guard →
Task 1. §4 infrastructure items → Task 3 step 2. §5 documented answers → Task 3 step 1. Testing
section → Task 1 steps 1-5, Task 2 steps 4-5, manual items recorded in Task 2 step 7.

**Build-type caveat resolved:** the spec told the implementer to verify whether `dev` is
debuggable. There is no `dev` build type in `composeApp` — only the default `debug` and an
overridden `release` — so Task 2 states this as settled fact and forbids the gradle change the
spec left conditional.

**Placeholder scan:** no TBDs; every code step carries the literal content to write.

**Type consistency:** `requireSecureBaseUrl(String): String` and `LOCAL_DEV_HOSTS` are named
identically in the test, the implementation, and the docs table.
