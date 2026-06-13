# Deployment & Release Pipelines

Two release targets, each driven by a GitHub Actions workflow on push to `main`
(development) or `production`.

## 1. Android app → Updraft

Workflow: `.github/workflows/deploy-android.yml`

Builds a signed release APK and uploads it to [Updraft](https://getupdraft.com).

**Required secrets** (per GitHub environment `development` / `production`):

| Secret | Purpose |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | base64 of the release keystore (`.jks`) |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | signing key alias |
| `ANDROID_KEY_PASSWORD` | signing key password |
| `UPDRAFT_APP_KEY` | Updraft app identifier |
| `UPDRAFT_API_KEY` | Updraft API key |

**Required variables:** `API_BASE_URL` (backend URL baked into the build).

## 2. Backend + Admin → Coolify (Docker)

Workflow: `.github/workflows/deploy-coolify.yml`

Coolify pulls this repo and builds the images itself from:

- `server/Dockerfile` — Ktor backend (build context = repo root). `:server` has no
  dependency on `:shared`/`:composeApp`, so it builds with configuration-on-demand
  and needs **no Android SDK**.
- `admin/Dockerfile` — SvelteKit admin (`@sveltejs/adapter-node`, build context = `admin/`).
- `docker-compose.yml` — wires both services together.

The workflow only **triggers** the deploy via Coolify's webhook; the build happens
on the Coolify host.

**Required secrets:**

| Secret | Purpose |
|---|---|
| `COOLIFY_WEBHOOK` | Coolify resource deploy webhook URL |
| `COOLIFY_TOKEN` | Coolify API token (Bearer) |

### Coolify setup

1. Provision a **PostgreSQL** managed database in Coolify. Run Flyway migrations on
   boot (the server applies them at startup).
2. Create a **Docker Compose** resource pointing at this repo / `docker-compose.yml`.
3. Set environment variables on the resource:

   | Variable | Service | Notes |
   |---|---|---|
   | `DATABASE_URL` | server | `jdbc:postgresql://host:5432/teamorg?user=…&password=…` |
   | `JWT_SECRET` | server | strong random secret |
   | `ONESIGNAL_APP_ID` | server | optional (push) |
   | `ONESIGNAL_API_KEY` | server | optional (push) |
   | `API_URL` | admin | backend base URL (e.g. `http://server:8080` on the compose network) |
   | `ORIGIN` | admin | public admin URL (e.g. `https://admin.teamorg.app`) — required by SvelteKit form actions |

4. Point domains at the `server` (8080) and `admin` (3000) services; Coolify
   terminates TLS and reverse-proxies.
5. Copy the resource's **deploy webhook URL** and an **API token** into the GitHub
   secrets above.

Uploaded files persist in the `uploads` named volume (`/app/uploads` in the server
container).

### Local image build (sanity check)

```bash
# Server (from repo root)
docker build -f server/Dockerfile -t teamorg-server .
# Admin
docker build -f admin/Dockerfile -t teamorg-admin ./admin
```
