# Security Runbook — Infrastructure Steps

Changes that cannot be made from the repository. Apply these in Coolify, the Stripe
dashboard, or on the host. Ordered by risk; each step says what breaks if it is skipped
and what breaks if it is applied carelessly.

Code-side hardening lives in the `security/*` branches — see the PR descriptions.

## 1. JWT secret (blocks deployment)

The server now refuses to boot with the placeholder secret or anything shorter than 32
characters. **Check the current value before promoting**, otherwise the deploy fails.

```bash
openssl rand -base64 48
```

Coolify → `server` resource → Environment Variables → `JWT_SECRET`.

- Already ≥32 random characters → nothing to do.
- Shorter, or still `dev-secret-change-in-production` → set a new one. **Rotating logs
  every user out**, mobile included, because all tokens are signed with it. Pick a quiet
  window.
- Local development only: `ALLOW_WEAK_JWT_SECRET=true` bypasses the guard.

## 2. Stripe: switch to live keys before real customers pay

Production is still on `pk_test`/`sk_test` (confirmed during the 2026-07-31 QA run).
Before launch, in Coolify → `server`:

- `STRIPE_SECRET_KEY` → live key
- `STRIPE_WEBHOOK_SECRET` → the **live-mode** endpoint's signing secret (a different
  value from the test endpoint — reusing the test one makes every webhook fail signature
  verification, which silently breaks freeze/unfreeze)
- `STRIPE_PRICE_ID` → the live CHF 2/member/year price
- `PUBLIC_STRIPE_PUBLISHABLE_KEY` → live publishable key

In the Stripe dashboard, recreate for live mode: the webhook endpoint with the four
handled events, pinned to API version `2025-05-28.basil`, and dunning set to Smart
Retries → mark subscription unpaid.

## 3. Run the server container as a non-root user

`admin` and `landing` now run as the image's `node` user. The **server was deliberately
left as root** because it writes to the `uploads` volume, which already exists in
production owned by root — switching users without fixing ownership breaks avatar and
club-logo uploads.

To complete it, on the host:

```bash
# 1. Find the volume path
docker volume inspect <stack>_uploads --format '{{ .Mountpoint }}'
# 2. Chown to the uid the container will use (1000)
sudo chown -R 1000:1000 <mountpoint>
```

Then add to `server/Dockerfile` before the entrypoint:

```dockerfile
RUN useradd --system --uid 1000 --create-home appuser && chown -R appuser /app
USER appuser
```

Redeploy and immediately verify an avatar upload succeeds. Roll back the Dockerfile if it
does not.

## 4. Review the published container ports

All three services use `ports: - "8080"` / `- "3000"`, which publishes them on a random
host port. Traefik routes over the `coolify` Docker network and does not need this, so the
containers are likely reachable on the host IP without TLS, bypassing the proxy.

Check from outside the host:

```bash
docker ps --format '{{.Names}}\t{{.Ports}}'   # look for 0.0.0.0:<random>-><container port>
nmap -p- <host-ip>                            # confirm reachability from the internet
```

If they are exposed, replace `ports:` with `expose:` in `docker-compose.yml` and redeploy
**one service first** to confirm Traefik still routes it. This was not changed in the repo
because a wrong guess here takes the whole site down; verify, then apply.

If the host has a firewall, the equivalent quick win is to allow only 80/443 inbound.

## 5. Postgres exposure and backups

- Coolify → Postgres resource: confirm **no public port** is mapped. It should be
  reachable only over the `coolify` network.
- The DB password lives in `DATABASE_URL` as a query parameter. Rotate it with
  `ALTER USER` and update the variable (changing it in the Coolify UI alone does nothing —
  see `docs/deployment-status.md`).
- Confirm automated backups are enabled and that a restore has been tested at least once.
  An untested backup is not a backup.

## 6. TLS and proxy headers

The apps now emit HSTS themselves. Additionally, in Coolify/Traefik:

- Force HTTP → HTTPS redirect on all three domains.
- Confirm `www.teamorg.ch` → apex redirect (still an open item from `landing-status.md`).
- Only submit the domain to the HSTS preload list once you are certain every subdomain
  will stay HTTPS-only — preload is effectively irreversible.

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

## 7. Secret hygiene

- `docs/deploy-secrets.local.md` is gitignored and excluded from the Docker build context.
  Keep it that way; it is the single most valuable file on the machine.
- Rotate `CONTACT_SHARED_SECRET` and the Infomaniak `SMTP_PASS` if they have ever been
  pasted into a chat, ticket, or terminal recording.
- OneSignal `ONESIGNAL_API_KEY` grants push to the whole app — treat it like a password.

## 8. Dependency and image updates

- Rebuild images monthly: `eclipse-temurin:21-jre` and `node:22-alpine` ship security
  fixes and the tags move.
- Run `npm audit --omit=dev` in `admin/` and `landing/`, and check Gradle dependencies for
  advisories before each release window.

## 9. Cleanup owed from the QA run

- Delete club "QA Prod 0731" via `/admin/login` → Clubs, and the Stripe **test** customer
  for `utke.michel+prodqa0731@gmail.com`.
- Delete `utke.michel+ccdebug@gmail.com` and the pending club "Claude Debug Club DELETE ME".
