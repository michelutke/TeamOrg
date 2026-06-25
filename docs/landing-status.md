# TeamOrg Landing (teamorg.ch) — Build & Handoff

_Status snapshot for resuming after conversation compaction. Setup details live in
`docs/landing.md`; this file is the "what we did + current state + open items"._

## What was built

A public marketing site at **teamorg.ch** plus a contact endpoint that emails demo
requests to **info@teamorg.ch**.

- **`landing/`** — new SvelteKit app (`@sveltejs/adapter-node`, Tailwind 4, Svelte 5,
  lucide-svelte), mirrors `admin/`. Served via the existing Coolify Docker-Compose
  stack + Traefik.
  - Sections: Nav (DE/EN toggle), Hero (two overlapping iPhone mockups), Features,
    How-it-works, Pricing (1 CHF/Mitglied/Jahr), Contact, Footer.
  - **i18n** DE (default) + EN via `?lang=` → `lang` cookie (the toggle links use
    `data-sveltekit-reload` + `preload-data="off"` so hover doesn't flip the cookie).
  - **Amber/gold** theme + green/red/yellow pastel highlight accents (the app's
    going/unsure/declined colours). Phone mockups stay purple (the real app).
  - **Fonts self-hosted** (`landing/static/fonts/`, `@font-face` in `app.css`) — no
    requests to Google.
  - **Material 3 Expressive shapes** (`Shape.svelte`: clover/flower/sunny/squircle) for
    the Funktionen icons + How-it-works step badges.
  - **Phone mockups**: `PhoneMockup.svelte`, thin bezel; screen images exported from
    Figma to `static/mockups/`.
  - **Legal**: `impressum` + `datenschutz` (revDSG), bilingual. Operator **Michel Utke**
    (linked to michelutke.com), content managed by him, **postal address on request**
    (not published), hosting stated as **Hetzner Nürnberg (DE/EU)**, fonts self-hosted.
    No UID/Handelsregister (not required for an unregistered private individual under
    CHF 100k turnover).

- **`server/`** (Ktor backend) — `ContactRoutes.kt`: public `POST /contact`, guarded by
  optional `X-Contact-Secret`, sends via `simple-java-mail` over SMTP. `withSessionTimeout(10s)`
  so it fails fast instead of hanging. Port 465 → SMTPS, else STARTTLS. Registered in
  `plugins/Routing.kt`; config in `resources/application.conf` under `contact { ... }`.

- **`docker-compose.yml`** — new `landing` service (port 3000, on `default`+`coolify`
  networks, `traefik.docker.network=coolify`) + SMTP/contact env on `server`.

- **`docs/landing.md`** — setup reference.

## Git / merge state

- All work merged into **`main`** via PRs **#23, #25, #27, #28**. `main` is current.
- All feature branches **deleted**; only `main` remains (local + remote).
- (Mid-session the SSH agent broke — worked around with `gh` HTTPS creds — then fixed.)

## Deployment (Coolify on Hetzner, Nürnberg)

- Coolify builds `docker-compose.yml` (services: `server`, `admin`, `landing`); Traefik
  terminates TLS + reverse-proxies. DNS for `teamorg.ch`/`www` already points at the host.
- **Domains** (Coolify UI, on the `landing` service): `https://teamorg.ch:3000` +
  `https://www.teamorg.ch:3000`. ⚠️ The `:3000` belongs **only** in Coolify's Domains
  field (tells Traefik the container port) — **never** type it in the browser (causes
  `SSL_ERROR_RX_RECORD_TOO_LONG`). Public URL is just `https://teamorg.ch`.
- www → apex redirect: do at Infomaniak DNS (URL redirect) or Coolify's www toggle.

## Required env (Coolify)

**server:** `DATABASE_URL`, `JWT_SECRET`, `ONESIGNAL_APP_ID/API_KEY`,
`SMTP_HOST=mail.infomaniak.com`, `SMTP_PORT=587`, `SMTP_USER=info@teamorg.ch`,
`SMTP_PASS=<mailbox device password>`, `CONTACT_FROM=info@teamorg.ch`,
`CONTACT_TO=info@teamorg.ch`, `CONTACT_SHARED_SECRET=<random>`.

**landing:** `LANDING_ORIGIN=https://teamorg.ch`, `API_URL=http://server:8080`
(hardcoded in compose), `CONTACT_SHARED_SECRET=<same as server>`,
`TURNSTILE_SITEKEY`, `TURNSTILE_SECRET` (Cloudflare Turnstile, anti-spam).

## Contact-email troubleshooting log (chronological)

1. **Form stuck on "Wird gesendet"** → action `fetch` had no timeout. Fixed: timeouts
   in `+page.server.ts` (Turnstile 8s, backend 15s) + `Contact.svelte` enhance resets
   state in `finally`, resets Turnstile, clears form on success.
2. **`Connect timed out` to mail.infomaniak.com:465** → **Hetzner blocks outbound SMTP
   ports by default**. User had them unblocked. (Diagnosed via `withSessionTimeout` log.)
3. **`535 5.7.0 Invalid login or password`** (current) → connection works, auth fails.
   Fix is credentials only:
   - `SMTP_USER` = full address `info@teamorg.ch`.
   - `SMTP_PASS` = the **mailbox-level device/application password** from **Infomaniak
     Mail settings** (the per-mailbox "device password", works for any SMTP client incl.
     a backend — the "for Android/iOS/macOS" label is just examples). **NOT** the
     account-profile Anwendungskennwort (that's for the Infomaniak manager/API).
   - Use **port 587** (STARTTLS) — Infomaniak's setting. IMAP (993) is not needed
     (we only send).
   - After changing env, **restart/redeploy `server`** to pick it up, then test.
   - If 535 persists with the device password: the address may be an **alias** (can't
     auth) — point `SMTP_USER/PASS` at the real mailbox, keep `CONTACT_FROM/TO`=info@teamorg.ch.

## Open items

- [ ] Set correct `SMTP_USER`/`SMTP_PASS` (mailbox device password) + `SMTP_PORT=587`,
      restart `server`, submit a test → confirm mail arrives at info@teamorg.ch.
- [ ] www → apex redirect (Infomaniak DNS or Coolify toggle).
- [ ] Optional: when revenue crosses CHF 100k → register Einzelunternehmen, add UID/MwSt
      to Impressum.

## Console-log note

All remaining browser-console output is **third-party Cloudflare Turnstile** widget
noise (Feature-Policy hints, partitioned cookie, Quirks-Mode/WebGL/`postMessage`/`cmg/1`
lines inside the `normal` challenge iframe). Harmless, not ours. Our own font-preload
warning was removed (preload tags dropped; fonts load via `@font-face`/`swap`).
`[404] GET /wp-admin/install.php` in landing logs = a bot scan, correctly 404s.
