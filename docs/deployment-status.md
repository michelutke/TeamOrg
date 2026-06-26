# Deployment Status — Handoff

_Last updated: 2026-06-26. Continue from "Next steps". Sensitive values are in
`docs/deploy-secrets.local.md` (gitignored, on this machine only)._

## Live URLs (verified 2026-06-26)
- **Landing (marketing):** `https://teamorg.ch`
- **Web app (manager/admin SvelteKit):** `https://app.teamorg.ch`  (`/login`, `/app`, `/i/{token}`)
- **Server / API (Ktor):** `https://server.teamorg.ch`  (root 404 by design — API routes only)
- The old `*.teamorg.michelutke.com` domains are RETIRED — do not use them.

## Where things stand

### ✅ Backend + Admin — LIVE on Coolify
- Deployed via Coolify **native Git integration** (auto-deploy on push to the release branch). No GitHub Actions involved for Coolify.
- Builds from `server/Dockerfile`, `admin/Dockerfile`, `docker-compose.yml`.
- **Server:** canonical domain `https://server.teamorg.ch` (the Android app's `API_BASE_URL` secret points here). Ktor, container port 8080. Root path returns 404 by design — only API routes exist. Verified live 2026-06-26.
- **Web app:** `https://app.teamorg.ch` (SvelteKit adapter-node, container port 3000). Login verified working 2026-06-26.
- **Postgres:** Coolify-managed, image `postgres:18-alpine`, name `postgres`, user `admin`, initial DB `postgres`.
- **Super-admin account created:** `teamorg@michelutke.com` (registered via `/auth/register`, then `is_super_admin` flipped in the DB).

### Hard-won fixes already applied (don't regress)
1. **DB URL must be JDBC form** with creds as query params:
   `jdbc:postgresql://<internal-host>:5432/postgres?user=admin&password=...`
   (Coolify's "Postgres URL (internal)" gives `postgres://...` — that does NOT work directly.)
2. **`server` joins Coolify's predefined `coolify` network** (in `docker-compose.yml`) so it can resolve the managed Postgres hostname. Without it: `UnknownHostException`.
3. **DB password must match what Postgres was initialized with** (changing it in the UI after first init does nothing — `ALTER USER` or recreate).
4. **Public URLs carry no port** (`:8080`/`:3000` belong only in Coolify's Domains field, which maps the domain to the container port). Hitting `:3000` in a browser gives the "SSL record too long" error.
5. iOS inbox crash fixed earlier (cache hardening) — already merged to main.

### Coolify env vars currently set
- server: `DATABASE_URL`, `JWT_SECRET`, `ONESIGNAL_APP_ID`, `ONESIGNAL_API_KEY`
- admin: `API_URL` = `http://server:8080` (internal), `ORIGIN` = `https://app.teamorg.ch`

### Coolify Domains field
- server → `https://server.teamorg.ch:8080`
- admin (web app) → `https://app.teamorg.ch:3000`

## Git state
- Branch **`ci/release-pipelines`**, PR **#16** open against `main` (not yet merged).
- Merging #16 pushes to `main` → triggers Coolify auto-deploy AND the Android workflow.
- Commits include: Coolify Dockerfiles/compose, adapter-node switch, `coolify` network attach, and the Android workflow fix (google-services.json + onesignal.appId).

## Next steps (resume here)

### 1. Android → Updraft (ready, just needs config)
Set in GitHub → Settings → Secrets and variables → Actions:

**Secrets:** `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`, `GOOGLE_SERVICES_JSON_BASE64`, `UPDRAFT_APP_KEY`, `UPDRAFT_API_KEY`
(values in `docs/deploy-secrets.local.md`; Updraft keys still need to be fetched from Updraft).

**Variables:** `API_BASE_URL` = `https://server.teamorg.ch`,
`ONESIGNAL_APP_ID` = `2281f6c6-e979-49e3-a16b-d7b7628b67ea`.

Then either run the **"Deploy Android to Updraft"** workflow manually on branch
`ci/release-pipelines` (Actions → Run workflow), or merge PR #16 to fire it from `main`.

### 2. iOS (build-only pipeline scaffolded)
- Workflow `.github/workflows/deploy-ios.yml` compiles the app for the iOS
  Simulator with `CODE_SIGNING_ALLOWED=NO` — verifies it builds, no signing/distribution.
  Runs on `macos-15`, JDK 21 (gradle build phase compiles the KMP framework).
  Optional: its failure blocks nothing (no branch protection requires it).
- Apple-gated for distribution: needs Apple Developer team ID + App Store Connect API key.
  - Set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig` (empty now).
  - Then swap the build step for an archive + signed `.ipa` export → TestFlight or Updraft
    (TODO block at bottom of the workflow file lists the steps).

### 3. Merge PR #16
Once Android secrets are in and verified, merge to make everything live on `main`.

## Optional follow-ups discussed
- Server change to auto-promote a `SUPER_ADMIN_EMAIL` on startup (so no manual SQL on fresh DBs).
- Server change to auto-convert `postgres://` URLs to JDBC form.
