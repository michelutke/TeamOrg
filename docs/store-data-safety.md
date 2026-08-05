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
it later. The avatar file is deleted from storage. The `users` row itself is retained in
anonymized form (`deleted-<uuid>@deleted.invalid`, display name "Gelöschtes Konto", an unusable
password hash, `deleted_at` set) because event authorship, recorded attendance and the audit log
reference it under `ON DELETE RESTRICT`. The account cannot be logged into (the old address
becomes registerable again), and every existing session token is rejected immediately (checked
in the JWT `validate` block against `deleted_at`).

Two effects beyond the personal-data tables above: the deleted user's address is also cleared
from any `invite_links.invited_email` it appears in, and any impersonation session involving the
user (as actor or target) is marked inactive so a previously minted impersonation token cannot
outlive the deletion.

**Preconditions:** a user who still owns a live club is refused with a 409 and told to transfer
ownership or delete the club first — deleting them silently would leave a billed club without an
owner.
