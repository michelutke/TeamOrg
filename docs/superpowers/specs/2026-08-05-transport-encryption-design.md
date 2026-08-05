# Transport Encryption — Design

Date: 2026-08-05

Store requirement: both Google Play's Data Safety form and Apple's App Privacy questionnaire
ask whether all user data is encrypted in transit. This spec establishes that the answer is
yes, closes the gaps that make it conditional, and records the evidence so the answer is not
re-derived from scratch at each release.

## Current state

Verified in the repository, not assumed:

- **Client → server is HTTPS in release.** `shared/build.gradle.kts:98` bakes
  `API_BASE_URL` with the default `https://api.teamorg.app` for release builds; the `dev`
  build type defaults to `http://10.0.2.2:8080` (emulator loopback to the developer's own
  machine).
- **TLS is terminated by Coolify's Traefik**, which reverse-proxies to the container ports
  (`docker-compose.yml`, `docs/deployment.md`).
- **HSTS is already emitted by the server** — `server/src/main/kotlin/ch/teamorg/plugins/Security.kt:96`
  sets `Strict-Transport-Security`, and `XForwardedHeaders` (same file, line 65) resolves the
  client-facing scheme behind the proxy.

So the questionnaire answer is already "yes, TLS" for production traffic. Four things keep
that from being unconditional.

## Gaps

1. **Android release builds are permitted to speak cleartext.**
   `composeApp/src/androidMain/res/xml/network_security_config.xml` places a
   `<domain-config cleartextTrafficPermitted="true">` for `10.0.2.2` and `localhost`
   **outside** `<debug-overrides>`, so it applies to release. There is no
   `android:usesCleartextTraffic="false"` in the manifest and no `base-config` denying
   cleartext outside the debug block.
2. **iOS ships an ATS exception.** `iosApp/iosApp/Info.plist:38` declares
   `NSAppTransportSecurity` → `NSExceptionDomains` → `localhost` →
   `NSExceptionAllowsInsecureHTTPLoads`, which is present in the release build.
3. **A misconfigured base URL fails silently.** `API_BASE_URL` is an injected build
   variable (`shared/build.gradle.kts:75-105`). Setting it to an `http://` value produces a
   working app that transmits tokens and personal data in cleartext, with nothing in the code
   objecting.
4. **Server → Postgres has no TLS setting.** `DATABASE_URL` carries no `sslmode`
   (`docs/deployment.md`), so that hop is plaintext. It travels only over Coolify's internal
   Docker network, but "all data encrypted in transit" read strictly includes it.

Neither gap 1 nor gap 2 leaks data to the internet — both target loopback / the emulator's
host alias — but each is a standing permission that a future code change or a store reviewer's
static scan can turn into a finding, and both contradict an unconditional questionnaire answer.

## Goals

- A release build of either mobile app is **incapable** of sending user data over cleartext.
- A wrong `API_BASE_URL` fails loudly at startup instead of shipping cleartext.
- The store answers are documented with the evidence behind them.

## Non-goals

- **Certificate pinning.** It is the "most secure" checkbox and is deliberately rejected:
  a Let's Encrypt chain rotation would brick every installed app with no server-side remedy,
  the deployment already rotates certificates automatically via Coolify, and neither store
  requires pinning. TLS 1.2+ with cleartext disabled is the correct cost/benefit point.
- **End-to-end / application-layer encryption** on top of TLS. Nothing in the data model
  (rosters, attendance, absences) justifies it, and it would break server-side features that
  read the data.
- **Changing how TLS is terminated.** Traefik stays the terminator.
- Debug and emulator development flows keep working over cleartext to `10.0.2.2` / `localhost`.
  That is developer traffic on the developer's own machine, not user data.

## Design

### 1. Android — deny cleartext in release

`composeApp/src/androidMain/res/xml/network_security_config.xml` becomes:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Release: no cleartext, ever. -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- Debug builds only: the emulator host alias and localhost stay reachable over HTTP
         so `dev` builds against a local server keep working. -->
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

`debug-overrides` applies only when `android:debuggable="true"`, which is how the platform
distinguishes the two build types here — the config file itself is shared.

`composeApp/src/androidMain/AndroidManifest.xml` gains `android:usesCleartextTraffic="false"`
on `<application>`, beside the existing `android:networkSecurityConfig` reference. This is
belt-and-braces with `base-config`: the manifest attribute is what several store-side scanners
and audit checklists actually look for.

**Caveat that must be verified, not assumed:** the `dev` build type is not necessarily
`debuggable`. If `composeApp/build.gradle.kts` marks `dev` as non-debuggable, `debug-overrides`
will not apply to it and local development against `10.0.2.2` breaks. The implementer checks
the build type's `isDebuggable` first; if it is false, the fix is to set
`isDebuggable = true` for `dev` (it is a development build type) rather than to weaken the
release config.

### 2. iOS — remove the shipped ATS exception

Delete the `NSAppTransportSecurity` dictionary from `iosApp/iosApp/Info.plist`. The iOS
Simulator permits `http://localhost` without an ATS exception, so local development against a
locally-run server is unaffected. Physical-device development against a Mac over HTTP is not a
supported flow here and does not justify a shipped exception.

If removing it turns out to break the simulator flow, the exception moves into a
debug-configuration-only plist value — it does not go back into the shipped `Info.plist`.

### 3. Fail loudly on a non-HTTPS base URL

`shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt` currently does
`url(ApiConfig.baseUrl)` (line 33) with no validation. Add a guard that rejects a base URL
which is neither HTTPS nor a recognised local development host:

```kotlin
internal fun requireSecureBaseUrl(baseUrl: String): String {
    if (baseUrl.startsWith("https://")) return baseUrl
    val isLocalDev = LOCAL_DEV_HOSTS.any { baseUrl.startsWith("http://$it") }
    require(isLocalDev) { "API_BASE_URL must use https:// (got: $baseUrl)" }
    return baseUrl
}

private val LOCAL_DEV_HOSTS = listOf("10.0.2.2", "localhost", "127.0.0.1")
```

Called from `HttpClientFactory` before `url(...)`. A build pointed at
`http://api.example.com` then throws on first client construction instead of silently
transmitting cleartext. The allow-list is host-prefix matching on the configured base URL —
not a general URL parser — because the only values that reach it come from the build
configuration.

`shared/src/jvmMain/.../ApiConfig.jvm.kt` defaults to `http://localhost:8080`, which the
allow-list covers, so JVM tests are unaffected.

### 4. Infrastructure items (runbook, not code)

Appended to `docs/security-runbook.md` §6, since they cannot be applied from the repository:

- **Postgres TLS.** Check whether the Coolify managed Postgres presents a certificate. If it
  does, append `&sslmode=require` to `DATABASE_URL`. If it does not, record that decision and
  its justification (traffic confined to the internal Docker network, no public port per §5)
  rather than leaving the question open — the store answer needs a defensible position on it.
- **HTTP → HTTPS redirect** on all three domains in Traefik. Listed as an open item in §6
  today; it must be confirmed applied, because HSTS only protects a client that has already
  completed one HTTPS request.

### 5. Documented store answers

New `docs/store-data-safety.md` recording, for each questionnaire:

- **Google Play Data Safety** — "Is all of the user data collected by your app encrypted in
  transit?" → **Yes**. Evidence: HTTPS-only base URL, cleartext denied by network security
  config and manifest, HSTS emitted by the server, TLS terminated by Traefik.
- **Apple App Privacy** — same answer, same evidence, plus the note that no ATS exception
  ships.
- The exceptions and why they are not answers-changing: debug builds reach the emulator host
  over HTTP; the server↔Postgres hop is internal-network-only (with whatever §4 concludes).

This file is the thing to update when the transport story changes — it exists so the next
release does not re-investigate from scratch.

## Testing

The mobile changes are configuration; the guard is code.

- `shared` jvmTest, new `HttpClientBaseUrlTest`: `https://api.teamorg.app` is accepted;
  `http://10.0.2.2:8080` and `http://localhost:8080` are accepted; `http://api.teamorg.app`
  and `http://evil.example.com` throw `IllegalArgumentException`. `shared` jvmTest uses
  `kotlin.test` — no Kotest in that source set.
- Compile gates that must stay green: `./gradlew :composeApp:compileDebugKotlinAndroid`,
  `./gradlew :shared:compileKotlinIosSimulatorArm64`, `./gradlew :shared:jvmTest`.
- **Manual, owed and not automatable here:** a release-configuration Android build must be
  installed and confirmed to load data (proving the HTTPS path works under
  `usesCleartextTraffic="false"`), and a `dev` build must still reach a locally-run server
  (proving `debug-overrides` covers it). An iOS build must be run in the simulator after the
  ATS removal.

There is no automated test that a release APK refuses cleartext — that is a platform
behaviour, asserted by the configuration itself.

## Affected files

- `composeApp/src/androidMain/res/xml/network_security_config.xml` — deny cleartext outside debug
- `composeApp/src/androidMain/AndroidManifest.xml` — `usesCleartextTraffic="false"`
- `composeApp/build.gradle.kts` — only if the `dev` build type is not debuggable (verify first)
- `iosApp/iosApp/Info.plist` — remove `NSAppTransportSecurity`
- `shared/src/commonMain/kotlin/ch/teamorg/data/network/HttpClientFactory.kt` — HTTPS guard
- `shared/src/jvmTest/kotlin/ch/teamorg/network/HttpClientBaseUrlTest.kt` — new test
- `docs/security-runbook.md` — Postgres TLS + redirect items
- `docs/store-data-safety.md` — new, the recorded answers

## Risks

- **Breaking local development** is the real risk, not breaking production: if `dev` is not
  debuggable, cleartext to `10.0.2.2` stops working. Mitigated by checking the build type
  before changing the config, and by the manual `dev`-build verification above.
- **Removing the iOS ATS exception** could break a simulator flow that turns out to depend on
  it. Mitigated by running the simulator build as part of the task; the fallback is a
  debug-only plist value, never a shipped one.
- The HTTPS guard throws at client-construction time, which on mobile means at app start. A
  release build with a bad `API_BASE_URL` will crash instead of working insecurely. That is the
  intent, and it is caught by the release-build verification before any store submission.
