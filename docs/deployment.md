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

Deployment is driven by **Coolify's native Git integration** — link this repo to a
Coolify resource and enable auto-deploy on push. Coolify pulls the repo and builds
the images itself; no GitHub Actions workflow is involved.

Coolify builds from:

- `server/Dockerfile` — Ktor backend (build context = repo root). `:server` has no
  dependency on `:shared`/`:composeApp`, so it builds with configuration-on-demand
  and needs **no Android SDK**.
- `admin/Dockerfile` — SvelteKit admin (`@sveltejs/adapter-node`, build context = `admin/`).
- `docker-compose.yml` — wires both services together.

### Coolify setup

1. Provision a **PostgreSQL** managed database in Coolify. Flyway migrations are
   applied by the server at startup.
2. Create a resource from this repo, set the build pack to **Docker Compose** pointing
   at `docker-compose.yml`, and enable **auto-deploy** on your release branch.
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

Pushes to the configured branch trigger an automatic rebuild + redeploy.

Uploaded files persist in the `uploads` named volume (`/app/uploads` in the server
container).

### Local image build (sanity check)

```bash
# Server (from repo root)
docker build -f server/Dockerfile -t teamorg-server .
# Admin
docker build -f admin/Dockerfile -t teamorg-admin ./admin
```
