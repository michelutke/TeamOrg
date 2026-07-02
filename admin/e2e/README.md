# Admin E2E tests (Playwright)

End-to-end coverage for the unified-attendance web surfaces
(spec: `docs/superpowers/specs/2026-07-01-unified-attendance-design.md`).

## What it covers

- **Login** (`auth.setup.ts`) — logs in once via `/login`, reuses the session.
- **Events list** — the coach-only "Check-in offen" awaiting filter appears and filters.
- **Create event** — the `defaultResponse` select offers `none` / `accepted` / `declined`, defaulting to `none`.
- **Awaiting event detail** — the finalize button ("CheckIn abschliessen") and the coach edit popup (Anwesend / Abgemeldet / "Nicht entschuldigt") are present.
- **Finalize → reopen round-trip** — only with `E2E_ALLOW_MUTATION=1` (it mutates attendance state).

Data-dependent tests (`Awaiting check-in event`) **skip cleanly** when the account
has no awaiting-check-in event, so the suite is stable across instances.

## Requirements

- A **running** admin instance (local dev, staging, or a throwaway stack — **not production** if you enable mutation).
- A **coach or club-manager** test account (the awaiting/finalize surfaces are coach-only).

## Run

```bash
cd admin
npm install
npx playwright install chromium

E2E_BASE_URL=http://localhost:5173 \
E2E_EMAIL=coach@example.com \
E2E_PASSWORD='…' \
npm run test:e2e
```

To exercise the destructive finalize/reopen flow (point at a throwaway/local stack only):

```bash
E2E_ALLOW_MUTATION=1 E2E_BASE_URL=… E2E_EMAIL=… E2E_PASSWORD=… npm run test:e2e
```

## Env vars

| Var | Required | Purpose |
|-----|----------|---------|
| `E2E_BASE_URL` | yes | Base URL of the running admin (the config refuses to default to production). |
| `E2E_EMAIL` | yes | Coach/club-manager login email. |
| `E2E_PASSWORD` | yes | That account's password. |
| `E2E_ALLOW_MUTATION` | no | `1` enables the finalize/reopen round-trip. Omit on shared instances. |

Secrets are never committed — everything is read from the environment.
`e2e/.auth/`, `test-results/`, and `playwright-report/` are git-ignored.
