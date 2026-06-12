---
plan: "02"
wave: 2
phase: 1
title: "Ktor server — DB + Flyway + JWT auth endpoints"
depends_on: ["01"]
autonomous: true
files_modified:
  - server/src/main/kotlin/ch/teamorg/Application.kt
  - server/src/main/kotlin/ch/teamorg/plugins/Auth.kt
  - server/src/main/kotlin/ch/teamorg/plugins/Routing.kt
  - server/src/main/kotlin/ch/teamorg/plugins/Serialization.kt
  - server/src/main/kotlin/ch/teamorg/plugins/Koin.kt
  - server/src/main/kotlin/ch/teamorg/infra/DatabaseFactory.kt
  - server/src/main/kotlin/ch/teamorg/db/tables/UsersTable.kt
  - server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt
  - server/src/main/kotlin/ch/teamorg/middleware/AuthMiddleware.kt
  - server/src/main/resources/application.conf
  - server/src/main/resources/db/migrations/V1__create_users.sql
  - server/src/main/resources/db/migrations/V2__create_roles.sql
  - server/src/test/kotlin/ch/teamorg/routes/AuthRoutesTest.kt
  - server/src/test/kotlin/ch/teamorg/test/IntegrationTestBase.kt
  - local.properties.example
requirements:
  - AUTH-01
  - AUTH-02
  - AUTH-03
  - AUTH-05
  - AUTH-06
---

# Plan 02 — Ktor Server: DB + Flyway + JWT Auth Endpoints

## Goal
Running Ktor server with PostgreSQL, Flyway migrations, and working auth endpoints (register, login, logout). All endpoints have integration tests.

## Context
- JWT contains `user_id` only — roles loaded from DB per request (ADR-008)
- No refresh tokens for MVP — re-login on expiry
- Password hashing: bcrypt
- SuperAdmin: `users.is_super_admin` boolean column, DB-checked, not grantable via API
- Role system: roles live in join tables (Phase 2+), not in users table

## Tasks

<task id="02-01" title="Application.kt + plugins">
`Application.kt` — configure all plugins:
- `configureSerialization()` — kotlinx-serialization JSON
- `configureAuth()` — JWT config (secret from env, issuer, audience, realm)
- `configureRouting()` — mount all routes
- `configureKoin()` — DI modules

`application.conf` (HOCON):
```
ktor {
  deployment { port = 8080 }
  application { modules = [ch.teamorg.ApplicationKt.module] }
}
jwt {
  secret = ${JWT_SECRET}
  issuer = "teamorg"
  audience = "teamorg-users"
  realm = "teamorg"
  expiry-days = 30
}
database {
  url = ${DATABASE_URL}
  driver = "org.postgresql.Driver"
}
```
All secrets from environment variables — no hardcoded values.
</task>

<task id="02-02" title="DatabaseFactory + Flyway">
`DatabaseFactory.kt`:
- HikariCP connection pool (max 10 connections)
- Flyway auto-migration on startup
- Exposed `Database.connect(dataSource)`

Fail fast if DB unreachable on startup.
</task>

<task id="02-03" title="Flyway migrations — user schema">
`V1__create_users.sql`:
```sql
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  display_name TEXT NOT NULL,
  avatar_url TEXT,
  is_super_admin BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);
```

`V2__create_refresh_noop.sql` — placeholder comment (refresh tokens deferred to V2).
</task>

<task id="02-04" title="UsersTable (Exposed DSL)">
`UsersTable.kt` — Exposed `object UsersTable : Table("users")` with all columns mapped.

`UserRepository` interface + `UserRepositoryImpl`:
- `findByEmail(email): UserRow?`
- `findById(id: UUID): UserRow?`
- `create(email, passwordHash, displayName): UserRow`
- `existsByEmail(email): Boolean`
</task>

<task id="02-05" title="Auth routes">
`POST /auth/register`:
- Body: `{ email, password, displayName }`
- Validate: email format, password >= 8 chars, displayName non-empty
- Check email not already registered (409 if taken)
- Hash password with bcrypt (cost 12)
- Insert user, return JWT

`POST /auth/login`:
- Body: `{ email, password }`
- Verify email exists + password matches hash
- Return JWT + `{ userId, displayName, avatarUrl }`

`POST /auth/logout`:
- Authenticated endpoint (JWT bearer)
- No server-side session state (stateless JWT) — client drops token
- Returns 200 OK (client handles the actual logout)

`GET /auth/me`:
- Authenticated — returns current user's profile (`{ userId, email, displayName, avatarUrl, isSuperAdmin }`)
- Used by app on startup to validate stored token

JWT payload: `{ sub: userId, iss: "teamorg", aud: "teamorg-users", exp: now+30d }`
</task>

<task id="02-06" title="AuthMiddleware">
`AuthMiddleware.kt` — Ktor `authenticate {}` wrapper:
- Extracts `userId` from JWT claim `sub`
- Loads user from DB (fail 401 if not found)
- Injects `UserPrincipal(userId, isSuperAdmin)` into call

All protected routes use `authenticate("jwt") {}`.
</task>

<task id="02-07" title="Integration tests">
`IntegrationTestBase.kt`:
- Uses `testApplication {}` with H2 in-memory DB
- Flyway runs migrations against H2 on setup
- Provides `client` helper with JSON content-type

`AuthRoutesTest.kt`:
- `test register — success`
- `test register — duplicate email returns 409`
- `test register — invalid email returns 400`
- `test register — short password returns 400`
- `test login — success, returns JWT`
- `test login — wrong password returns 401`
- `test login — unknown email returns 401`
- `test GET /auth/me — authenticated returns user`
- `test GET /auth/me — no token returns 401`
- `test GET /auth/me — expired token returns 401`
</task>

<task id="02-08" title="local.properties.example">
```
# Copy to local.properties (never commit local.properties)
DATABASE_URL=jdbc:postgresql://localhost:5432/teamorg_dev
JWT_SECRET=change_me_in_production_min_32_chars
```
</task>

## Verification

```bash
./gradlew :server:test
# All AuthRoutesTest tests pass
```

## must_haves
- [ ] `POST /auth/register` creates user and returns JWT
- [ ] `POST /auth/login` validates credentials and returns JWT
- [ ] `GET /auth/me` returns user for valid JWT, 401 for invalid
- [ ] All 9 auth test cases pass
- [ ] No plaintext passwords stored (bcrypt only)
- [ ] No hardcoded secrets (all from env/HOCON)
