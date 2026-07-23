# Store Deployment — Google Play & TestFlight

Both stores deploy automatically from CI via fastlane. Proven end-to-end (first fully green run: 2026-07-23).

## Branch → destination

| Branch | Workflow | Lanes | Destination |
|---|---|---|---|
| `main` | Deploy Android to Updraft / Deploy iOS | `android_updraft`, `ios_check` | Updraft APK + iOS compile check |
| `production` | Release to Stores | `android_play`, `ios_testflight` | Play **internal testing** track + **TestFlight** |

Lanes live in `fastlane/Fastfile` and run identically locally (`bundle exec fastlane <lane>` with a filled `fastlane/.env`, see `fastlane/env.example`). Path convention: fastlane *actions* resolve paths from the repo root; plain Ruby in lane bodies runs from `fastlane/`.

## Versioning

`versionCode` / iOS build number = GitHub run number (`ANDROID_VERSION_CODE`), `versionName` = `1.0.<run>`. Play requires strictly increasing versionCodes.

## Secrets

| Where | What |
|---|---|
| Repo-level Actions secrets | `ANDROID_KEYSTORE_BASE64/-_PASSWORD`, `ANDROID_KEY_ALIAS/_PASSWORD`, `API_BASE_URL`, `ONESIGNAL_APP_ID`, `GOOGLE_SERVICES_JSON_BASE64` |
| Environment `development` | `UPDRAFT_APP_KEY`, `UPDRAFT_API_KEY` |
| Environment `production` (branch-restricted) | `PLAY_SERVICE_ACCOUNT_JSON`, `APPLE_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_BASE64`, `IOS_DIST_CERT_P12_BASE64`, `IOS_DIST_CERT_PASSWORD` |

Notes: keystore is PKCS12 — store and key password are the same value. The ASC API key needs the **App Manager** role (Admin is not required: certificates are imported, profiles are created by `sigh` through the API). With Play App Signing enrolled, `release.jks` is only the upload key and can be rotated via Google if lost.

## iOS signing model

Manual signing, no cloud signing: CI imports the Apple Distribution certificate (`.p12` secret) into a temp keychain, `sigh` fetches/creates the App Store provisioning profile via the ASC API key, gym archives with explicit `PROVISIONING_PROFILE_SPECIFIER`. Renewal duty: distribution certificate expires yearly — export a new `.p12`, update the secret.

## Release notes

`distribution/whatsnew/whatsnew-<locale>` (`de-DE`, `en-US`) upload with every Play release (mapped to supply's changelog format by the lane). Edit as part of a release commit.

## Hard-won gotchas (kept so nobody re-learns them)

1. **`versionCode`/`versionName`** come from env in `composeApp/build.gradle.kts` — hardcoding them breaks store uploads.
2. **ASC API key role**: App Manager cannot *cloud-sign* (`xcodebuild -allowProvisioningUpdates` export fails with "Cloud signing permission error"); the manual-signing lane avoids this entirely.
3. **KMP wizard `Info.plist`** shipped `CFBundlePackageType = $(PACKAGE_TYPE)` which never expands → altool rejects the ipa with error `-21017`. It is hardcoded to `APPL`.
4. **SDK floor**: since 2026-04-28 App Store Connect only accepts builds made with the iOS 26 SDK — Xcode pin (`.xcode-version` + the two workflow `xcode-version:` fields, keep in sync) must stay ≥ 26.
5. **Export compliance**: `ITSAppUsesNonExemptEncryption=false` in `Info.plist` (only OS TLS is used) — pre-answers the ASC encryption question per build.
6. **First-release quirk**: repo/environment *variable* `PLAY_RELEASE_STATUS=draft` if Play rejects a completed release before the app's first manual rollout.
7. Runs stuck in `queued` with zero jobs never recover — cancel and re-dispatch (`gh workflow run release-stores.yml --ref production`).

## One-time console setup (already done, for reference)

- Play Console: app `ch.teamorg`, Play App Signing enrolled, service account invited with *Release to testing tracks*.
- Apple: bundle ID `ch.teamorg` (+ Push Notifications capability), ASC app record, App Manager API key, Apple Distribution certificate.
- Testers: Play → Internal testing → tester list; ASC → TestFlight → Internal Testing group (no review, up to 100).
- Push: APNs Auth Key (`.p8`) uploaded to OneSignal (token-based), Key ID + Team ID + bundle ID.
