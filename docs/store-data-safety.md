# Store Privacy Answers — Data in Transit

Answers for Google Play's Data Safety form and Apple's App Privacy questionnaire, with the
evidence behind them. Update this file when the transport story changes; do not re-derive it.

## The answer

**Is all user data collected by the app encrypted in transit? — Yes.**

Same answer for both stores.

## Evidence

| Claim | Where to verify |
|---|---|
| Android release builds use an HTTPS base URL | `shared/build.gradle.kts` — release `buildConfigField` defaults to `https://api.teamorg.app` |
| iOS builds use an HTTPS base URL | `iosApp/Configuration/Config.xcconfig` — `API_BASE_URL` defaults to `https://api.teamorg.app`, read via `iosApp/iosApp/Info.plist` in `shared/src/iosMain/kotlin/ch/teamorg/data/network/ApiConfig.ios.kt`; the code fallback for a missing plist key is the same HTTPS URL. CI TestFlight builds have the value injected from the `API_BASE_URL` secret (`fastlane/Fastfile` `ios_testflight`); local Xcode builds use the xcconfig default. |
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
