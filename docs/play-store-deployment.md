# Play Store Deployment (internal track)

Push to `production` → CI builds a signed AAB and uploads it to the Play **internal testing** track automatically (`deploy-play-store` job in `.github/workflows/deploy-android.yml`). Pushes to `main` keep going to Updraft only.

## Versioning

- `versionCode` = GitHub Actions run number (`ANDROID_VERSION_CODE` env, falls back to 1 locally)
- `versionName` = `1.0.<run number>` (`ANDROID_VERSION_NAME` env)

Play requires strictly increasing versionCodes — the run number guarantees that as long as the workflow file stays in the same repo.

## One-time setup

### 1. Create the app in Play Console (manual, API cannot do this)

Play Console → All apps → **Create app** → name "TeamOrg", default language German (Switzerland), App, Free.
Complete the mandatory declarations under **App content** (privacy policy URL, data safety, content rating, target audience) — uploads work before store listing is complete, but the release cannot be rolled out to testers until these are done.

### 2. Service account

1. Google Cloud Console → create/reuse a project → IAM & Admin → **Service Accounts** → Create (`play-publisher`). No project roles needed.
2. Create key → JSON → download.
3. Play Console → **Users and permissions** → Invite new users → the service account e-mail.
   Grant app-level permissions on TeamOrg: *Release to testing tracks*, *Manage testing tracks and edit tester lists*, *View app information*.
4. GitHub repo → Settings → Secrets → Actions (environment **production**): add `PLAY_SERVICE_ACCOUNT_JSON` = the full JSON file content (plain text).

### 3. First upload quirk

A brand-new app has no releases; some Play API operations fail until the first bundle exists. If the very first CI run fails on the upload step with a "package not found"-style error, either:

- upload the AAB from that run's artifacts manually once in Play Console (Internal testing → Create release), or
- set repo/environment **variable** `PLAY_RELEASE_STATUS=draft` for the first run (the workflow reads it; default is `completed`), then remove it.

### 4. Testers

Play Console → Internal testing → Testers → create an e-mail list (max 100). Testers install via the opt-in link.

## Release notes

`distribution/whatsnew/whatsnew-de-DE` and `whatsnew-en-US` are uploaded with every release. Edit them as part of a release commit when something noteworthy ships.

## Required secrets (production environment)

| Secret | Purpose |
|---|---|
| `PLAY_SERVICE_ACCOUNT_JSON` | Play publisher service account key (JSON) |
| `ANDROID_KEYSTORE_BASE64` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD` | release signing (already set for Updraft) |
| `GOOGLE_SERVICES_JSON_BASE64` | required (no placeholder fallback) — push notifications must work in store builds |
| `API_BASE_URL`, `ONESIGNAL_APP_ID` | app config (already set) |

Recommendation: enroll in **Play App Signing** during the first manual release creation and upload `release.jks` as the upload key — Google then holds the app signing key and the current CI signing setup keeps working unchanged.
