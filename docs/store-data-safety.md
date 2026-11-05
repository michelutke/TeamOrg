# Store Privacy Answers — Data in Transit

Answers for Google Play's Data Safety form and Apple's App Privacy questionnaire, with the
evidence behind them. Update this file when the transport story changes; do not re-derive it.

## The answer

**Is all user data collected by the app encrypted in transit? — Yes.**

Same answer for both stores.

## Evidence

| Claim | Where to verify |
|---|---|
| Android release builds use an HTTPS base URL | `shared/build.gradle.kts` — release `buildConfigField` defaults to `https://server.teamorg.ch` |
| iOS builds use an HTTPS base URL | `iosApp/Configuration/Config.xcconfig` — `API_BASE_URL` defaults to `https://server.teamorg.ch`, read via `iosApp/iosApp/Info.plist` in `shared/src/iosMain/kotlin/ch/teamorg/data/network/ApiConfig.ios.kt`; the code fallback for a missing plist key is the same HTTPS URL. CI TestFlight builds have the value injected from the `API_BASE_URL` secret (`fastlane/Fastfile` `ios_testflight`); local Xcode builds use the xcconfig default. |
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

## Data collected

Declared in `iosApp/iosApp/PrivacyInfo.xcprivacy`, all linked to the user, none used for
tracking, purpose App Functionality: Name, Email Address, Photos (profile avatar), Other User
Content (absence reasons, event responses), Health (optional injury and body-part details on
absences), User ID, Device ID (OneSignal push token).

### Crash reporting (Sentry)

Release builds send crash logs and diagnostics to Sentry when a `SENTRY_DSN` is configured
(`shared/src/commonMain/kotlin/ch/teamorg/crash/CrashReporting.kt`). Debug, local and test builds
have an empty DSN and never initialize Sentry. Collected: stack traces, device model, OS version,
app version and build, app hang and crash diagnostics, non-HTTP breadcrumbs, and the internal user id
(no email, name or IP: `sendDefaultPii = false`, event user reduced to the id, HTTP breadcrumbs
dropped because request paths contain invite tokens). No screenshots, view hierarchy, session replay or tracing. Not
used for tracking, not shared with third parties other than Sentry as processor. The Sentry
project should be created in the EU data region.

| Store | Form answers for crash reporting |
|---|---|
| Google Play Data Safety | Data type "App info and performance", "Crash logs": collected, not shared, optional = no, purpose "Analytics" (Sentry docs suggest analytics) and "App functionality". Data type "App info and performance", "Diagnostics": same answers. Data type "Personal info", "User IDs" (already declared for push): add purpose "App functionality" for the crash user id. All encrypted in transit (HTTPS). |
| Apple App Privacy | Add "Crash Data" and "Performance Data" and "Other Diagnostic Data" (Diagnostics): linked to the user (user id is attached), not used for tracking, purpose "App Functionality". Mirrored in `iosApp/iosApp/PrivacyInfo.xcprivacy` (`NSPrivacyCollectedDataTypes`). |

Sentry Cocoa ships its own privacy manifest, and Xcode copies the `Sentry.framework`
`PrivacyInfo.xcprivacy` into the app bundle. The app manifest's required-reason API entries
(UserDefaults `CA92.1`, FileTimestamp `C617.1`, SystemBootTime `35F9.1`) are a harmless duplicate
of what Sentry declares.

Symbolication: readable iOS stack traces need dSYMs and readable Android stack traces need the
R8 mapping file uploaded to Sentry. Neither upload is part of this setup (no Sentry Gradle plugin
upload and no `sentry-cli` step in CI). Add them later with a `SENTRY_AUTH_TOKEN`, org and
project, enabled only when the token is present.

## Certificate pinning

Deliberately not implemented. A Let's Encrypt chain rotation would brick every installed app
with no server-side remedy, certificates rotate automatically via Coolify, and neither store
requires pinning. See `docs/superpowers/specs/2026-08-05-transport-encryption-design.md`.

## Account deletion

Both stores require an account-deletion path. Apple guideline 5.1.1(v) requires it to be
initiated **inside the app**; Google Play additionally wants a URL.

| Requirement | Where it is satisfied |
|---|---|
| In-app deletion (iOS + Android) | Profile tab → "Delete account" → password-confirmed dialog (`composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileScreen.kt`). A user with no team never reaches the profile tab, so `EmptyStateScreen` (`composeApp/src/commonMain/kotlin/ch/teamorg/ui/emptystate/EmptyStateScreen.kt`) offers the same dialog (`ui/components/DeleteAccountDialog.kt`). |
| Web URL for the Play Console form | `https://app.teamorg.ch/app/profile/delete` (sign-in required) |
| Endpoint | `DELETE /auth/me`, password in the body, rate-limited with the auth bucket |

**What deletion does:** the account is anonymized, not fully removed. Personal rows are deleted
outright — absence rules, attendance responses, notifications and their settings, notification
reminders, event reminder overrides, subgroup memberships, team roles, club roles. The imported
roster link (`nds_members.user_id`) is detached rather than deleted, so a re-import can re-link
it later. The avatar file is deleted from storage on a best-effort basis (a failed deletion does not block the account deletion). The `users` row itself is retained in
anonymized form (`deleted-<uuid>@deleted.invalid`, display name "Gelöschtes Konto", an unusable
password hash, `deleted_at` set) because event authorship, recorded attendance and the audit log
reference it under `ON DELETE RESTRICT`. The account cannot be logged into (the old address
becomes registerable again), and every existing session token is rejected immediately (checked
in the JWT `validate` block against `deleted_at`).

Two effects beyond the personal-data tables above: the deleted user's address is also cleared
from any `invite_links.invited_email` it appears in, and any impersonation session involving the
user (as actor or target) is marked inactive so a previously minted impersonation token cannot
outlive the deletion.

**Preconditions:** a user who still owns a live club is refused with a 409 — deleting them
silently would leave a billed club without an owner. Ownership transfer and club deletion are not
self-service, so the message tells the user to contact `info@teamorg.ch` to have the club
transferred or closed before their account can be deleted.
