# teamorg.ch — Marketing landing site

SvelteKit (adapter-node) app in `landing/`, deployed via the same Coolify
Docker-Compose resource as `server` + `admin`. The "Demo anfragen" form posts to
the backend `POST /contact` endpoint, which emails **info@teamorg.ch** via Proton SMTP.

## Architecture

```
Browser ──> teamorg.ch (Traefik) ──> landing:3000 (SvelteKit)
                                        │  form action: validate + Turnstile verify
                                        ▼
                                  server:8080  POST /contact  (X-Contact-Secret)
                                        │  simple-java-mail
                                        ▼
                                  Proton SMTP ──> info@teamorg.ch
```

- DNS: `teamorg.ch` and `www.teamorg.ch` already resolve to the Coolify host
  (`46.224.221.8`) — **no DNS change needed**.
- Coolify auto-detects the new `landing` service in `docker-compose.yml` on the next
  deploy. Add the public domains in the Coolify UI.

## Coolify setup

1. **Domains** (on the `landing` service): `https://teamorg.ch` and
   `https://www.teamorg.ch` → container port **3000**. Set `www` → apex redirect.
2. **Environment variables** on the resource:

   | Variable | Service | Value / notes |
   |---|---|---|
   | `LANDING_ORIGIN` | landing | `https://teamorg.ch` (required by SvelteKit form actions) |
   | `TURNSTILE_SITEKEY` | landing | Cloudflare Turnstile **site** key (sent to browser) |
   | `TURNSTILE_SECRET` | landing | Cloudflare Turnstile **secret** key |
   | `CONTACT_SHARED_SECRET` | landing + server | same random string in both |
   | `SMTP_HOST` | server | `mail.infomaniak.com` (primary) — Proton backup: `smtp.protonmail.ch` |
   | `SMTP_PORT` | server | `465` (SSL) or `587` (STARTTLS) |
   | `SMTP_USER` | server | full mailbox address, e.g. `info@teamorg.ch` |
   | `SMTP_PASS` | server | mailbox password (or an **app password** if 2FA is on) |
   | `CONTACT_FROM` | server | sender address, must be owned on the SMTP account (e.g. `info@teamorg.ch`); defaults to `SMTP_USER` |
   | `CONTACT_TO` | server | `info@teamorg.ch` |

   `API_URL` for landing is hardcoded to `http://server:8080` in compose.

   The backend picks the transport automatically: port `465` → SMTPS, anything else →
   STARTTLS. So the same code works for Infomaniak and Proton — only the env values change.

## SMTP — Infomaniak (primary)

You already have the `teamorg.ch` mailbox and domain at Infomaniak, so this needs no
extra plan or token:

- `SMTP_HOST=mail.infomaniak.com`, `SMTP_PORT=465`, `SMTP_USER=info@teamorg.ch`,
  `SMTP_PASS=<mailbox password>`.
- If the Infomaniak account has **2FA**, create an **application password**
  (Infomaniak → My account → Security → Application passwords) and use that as `SMTP_PASS`.
- Because the domain's DNS is managed at Infomaniak, **SPF is set automatically**; enable
  **DKIM** for the mail service in the Infomaniak Mail admin if not already on (improves
  deliverability). DMARC optional but recommended.
- Note Infomaniak's per-mailbox daily sending limit (ample for a contact form).

## SMTP — Proton (backup)

If you ever switch to Proton: requires a paid plan with SMTP submission, generate an
**SMTP token** (Proton → Settings → IMAP/SMTP) and set `SMTP_HOST=smtp.protonmail.ch`,
`SMTP_USER=<teamorg.ch address>`, `SMTP_PASS=<SMTP token>`. SPF/DKIM/DMARC for the domain
must then point at Proton.

## Cloudflare Turnstile

- Create a Turnstile widget (Cloudflare dashboard) for hostname `teamorg.ch`. Copy the
  **site key** → `TURNSTILE_SITEKEY` and **secret** → `TURNSTILE_SECRET`.
- If `TURNSTILE_SITEKEY` is unset the widget is hidden and `TURNSTILE_SECRET` unset
  skips verification (used in local dev).

## Local development

```bash
cd landing
npm install
npm run dev            # http://localhost:5173
```

- Without `TURNSTILE_*` the captcha is skipped. Without a backend on `API_URL`
  (`http://localhost:8080` by default) the form returns a graceful "server" error.
- Language: `de` (default) / `en`, switched via the nav toggle (`?lang=` → `lang` cookie).

## Follow-ups

- **Fill in legal pages**: `landing/src/routes/impressum/+page.svelte` and
  `datenschutz/+page.svelte` contain `[bracketed]` placeholders (operator name, address,
  UID, hosting location) — replace before going public.
- **Fonts**: Google Sans Flex + Roboto Flex are **self-hosted** (`landing/static/fonts/`,
  `@font-face` in `app.css`) — no requests to Google. To refresh, re-download the latin /
  latin-ext woff2 from the Google Fonts css2 endpoint.
