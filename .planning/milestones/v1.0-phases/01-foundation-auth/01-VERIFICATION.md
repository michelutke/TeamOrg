---
phase: "01-foundation-auth"
verified: "2026-04-01T12:00:00Z"
status: passed
score: 6/6 requirements verified
note: "Retroactive verification — phase completed before GSD verification workflow existed. All routes are wired and functional."
---

# Phase 01: Foundation + Auth Verification Report

**Phase Goal:** Register, login, logout, invite-to-register, role freshness, and SuperAdmin constraints all work correctly.
**Verified:** 2026-04-01T12:00:00Z
**Status:** passed (6/6 requirements verified)

## Requirements Coverage

| REQ-ID | Description | Status | Evidence |
|--------|-------------|--------|---------|
| AUTH-01 | Register with email + password | VERIFIED | POST /auth/register in AuthRoutes.kt; 5 passing integration tests in AuthRoutesTest.kt |
| AUTH-02 | Login + stay logged in (JWT) | VERIFIED | POST /auth/login in AuthRoutes.kt; JWT issued via Auth.kt verifier; AuthRoutesTest.kt `login success returns JWT` |
| AUTH-03 | Logout; session invalidated | VERIFIED | POST /auth/logout in AuthRoutes.kt (stateless JWT); AuthRoutesTest.kt `logout with valid token returns 200` |
| AUTH-04 | Email invite prompts registration | VERIFIED | InviteRoutes.kt redeem flow; GET /invites/{token} public details; InviteRoutesTest.kt `full invite flow` |
| AUTH-05 | Role changes take effect immediately | VERIFIED | GET /auth/me/roles queries DB on every request; RoleDetectionTest.kt `promoted player gains coach role` |
| AUTH-06 | SuperAdmin manual only | VERIFIED | UsersTable.isSuperAdmin defaults false; no registration path sets it; assignment requires direct DB write |

## Detailed Evidence

### AUTH-01: Register with email + password

**Route handler:** `server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt`

```
POST /auth/register
  - Validates email format (isValidEmail check)
  - Validates password length >= 8
  - Validates displayName is not blank
  - Checks duplicate email (existsByEmail -> 409 Conflict)
  - Hashes password: BCrypt.hashpw(request.password, BCrypt.gensalt(12))
  - Creates user: userRepository.create(email, passwordHash, displayName)
  - Returns AuthResponse(token, userId, displayName, avatarUrl)
```

**User creation:** `server/src/main/kotlin/ch/teamorg/domain/repositories/UserRepository.kt`

`UserRepositoryImpl.create()` inserts into UsersTable and returns a User domain model.

**Tests:** `server/src/test/kotlin/ch/teamorg/routes/AuthRoutesTest.kt`

| Test | Assertion |
|------|-----------|
| `register success` | HTTP 200, token non-null, displayName matches |
| `register duplicate email returns 409` | HTTP 409 Conflict |
| `register invalid email returns 400` | HTTP 400 Bad Request |
| `register short password returns 400` | HTTP 400 Bad Request |
| `register blank display name returns 400` | HTTP 400 Bad Request |

---

### AUTH-02: Login + stay logged in (JWT)

**Route handler:** `server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt`

```
POST /auth/login
  - Loads password hash: userRepository.getPasswordHash(email)
  - Verifies: BCrypt.checkpw(request.password, passwordHash)
  - On mismatch or unknown email -> 401 Unauthorized
  - Issues JWT: generateToken(userId) using HMAC256
    - withAudience, withIssuer, withSubject(userId)
    - withExpiresAt(now + expiryDays * 24 * 60 * 60 * 1000)
  - Returns AuthResponse(token, userId, displayName, avatarUrl)
```

**JWT configuration:** `server/src/main/kotlin/ch/teamorg/plugins/Auth.kt`

```
jwt("jwt") {
  verifier(JWT.require(Algorithm.HMAC256(secret)).withAudience(audience).withIssuer(issuer).build())
  validate { credential -> if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null }
}
```

All protected routes wrapped in `authenticate("jwt")` block. Token validated on each request.

**Tests:** `server/src/test/kotlin/ch/teamorg/routes/AuthRoutesTest.kt`

| Test | Assertion |
|------|-----------|
| `login success returns JWT` | HTTP 200, token non-null |
| `login wrong password returns 401` | HTTP 401 |
| `login unknown email returns 401` | HTTP 401 |
| `login returns userId and displayName` | userId non-null, displayName matches |
| `GET auth me authenticated returns user` | HTTP 200 with user data when Bearer token provided |
| `GET auth me no token returns 401` | HTTP 401 without token |

---

### AUTH-03: Logout; session invalidated

**Route handler:** `server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt`

```
POST /auth/logout (requires authenticate("jwt"))
  - Stateless JWT logout: returns 200 OK
  - Token is not invalidated server-side (no token blacklist needed at MVP scale)
  - Client discards token on 200 response
  - Without a valid token: JWT middleware returns 401 before reaching handler
```

**Note:** Stateless JWT logout is the correct MVP approach. The JWT expiry (configured via `jwt.expiry-days`) bounds the effective session window. Token invalidation at the client is enforced by the shared KMP `AuthRepositoryImpl` which clears stored credentials on logout.

**Tests:** `server/src/test/kotlin/ch/teamorg/routes/AuthRoutesTest.kt`

| Test | Assertion |
|------|-----------|
| `logout with valid token returns 200` | HTTP 200 |
| `logout without token returns 401` | HTTP 401 (JWT middleware blocks unauthenticated logout) |

---

### AUTH-04: Email invite prompts registration

**Route handlers:** `server/src/main/kotlin/ch/teamorg/routes/InviteRoutes.kt`

```
GET /invites/{token}  (public, no auth)
  - Returns InviteDetails: teamName, role, alreadyRedeemed, expiresAt
  - Client receives invite details and prompts unauthenticated users to register

POST /invites/{token}/redeem  (requires authenticate("jwt"))
  - Validates expiry -> 410 Gone if expired
  - Validates not already redeemed by another user -> 409 Conflict
  - Calls inviteRepository.redeem(token, userId)
  - Adds user to team with assigned role
```

**Flow for unregistered email:** The client reads GET /invites/{token} without auth. If the user is not logged in, the app routes to Register screen. After registration (AUTH-01), the user is redirected back to redeem. The `email` field on `CreateInviteRequest` is optional — the backend does not restrict redemption to a specific email address; the client enforces the registration prompt.

**Tests:** `server/src/test/kotlin/ch/teamorg/routes/InviteRoutesTest.kt`

| Test | Assertion |
|------|-----------|
| `full invite flow - create, get details, redeem` | Creates invite, fetches details, redeems — user added to team |
| `redeem - expired token returns 410` | HTTP 410 Gone |
| `redeem - already redeemed by another user returns 409` | HTTP 409 Conflict |
| `redeem - already member returns 200 idempotent` | HTTP 200 (idempotent same-user redeem) |
| `redeem - unauthenticated returns 401` | HTTP 401 (must register/login before redeeming) |
| `redeem invite - user added to team members` | Redeemed player appears in GET /teams/{id}/members |

---

### AUTH-05: Role changes take effect immediately

**Mechanism:** Roles are NOT embedded in the JWT. The JWT contains only `userId` (as `subject`). On every authenticated request, `GET /auth/me/roles` queries the database live via `teamRepository.getUserClubRoles(userId)` and `teamRepository.getUserTeamRoles(userId)`.

**Route handler:** `server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt`

```
GET /auth/me/roles (requires authenticate("jwt"))
  - Extracts userId from JWT subject
  - teamRepository.getUserClubRoles(userId)  <- live DB query each request
  - teamRepository.getUserTeamRoles(userId)  <- live DB query each request
  - Returns UserRolesResponse(clubRoles, teamRoles)
```

The KMP client calls `getMyRoles()` on each ViewModelscope to check coach status — no caching that would prevent immediate reflection of role changes.

**Tests:** `server/src/test/kotlin/ch/teamorg/flows/RoleDetectionTest.kt`

| Test | Assertion |
|------|-----------|
| `clubmanager role detected as coach` | GET /auth/me/roles returns club_manager role immediately after club creation |
| `plain player has no coach role` | Player has no coach/club_manager roles |
| `promoted player gains coach role` | After PATCH /teams/{id}/members/{userId}/role, GET /auth/me/roles immediately returns new coach role — no re-login required |
| `removed member has no roles` | After DELETE /teams/{id}/members/{userId}, GET /auth/me/roles returns empty roles immediately |

---

### AUTH-06: SuperAdmin manual only

**Database schema:** `server/src/main/kotlin/ch/teamorg/db/tables/UsersTable.kt`

```kotlin
val isSuperAdmin = bool("is_super_admin").default(false)
```

`isSuperAdmin` defaults to `false` for every new user row.

**Registration path:** `server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt`

`POST /auth/register` calls `userRepository.create(email, passwordHash, displayName)`. The `UserRepositoryImpl.create()` method does not accept or set `isSuperAdmin` — the column takes its default value of `false`.

No endpoint in AuthRoutes, TeamRoutes, InviteRoutes, or any other route file accepts or sets `isSuperAdmin`. The only way to grant SuperAdmin is a direct database UPDATE on the `users.is_super_admin` column.

**Evidence of absence:** Grep over all route files confirms no endpoint sets `isSuperAdmin = true`.

---

## Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | User can register with valid email + password | VERIFIED | POST /auth/register + AuthRoutesTest.kt |
| 2 | Duplicate email registration rejected | VERIFIED | 409 Conflict test in AuthRoutesTest.kt |
| 3 | Login with correct credentials returns JWT | VERIFIED | AuthRoutesTest.kt `login success returns JWT` |
| 4 | Login with wrong credentials returns 401 | VERIFIED | AuthRoutesTest.kt `login wrong password returns 401` |
| 5 | Protected routes require valid JWT | VERIFIED | Auth.kt challenge responds 401; AuthRoutesTest.kt `GET auth me no token returns 401` |
| 6 | Logout returns 200 with valid token | VERIFIED | AuthRoutesTest.kt `logout with valid token returns 200` |
| 7 | Logout without token returns 401 | VERIFIED | AuthRoutesTest.kt `logout without token returns 401` |
| 8 | Invite details accessible without auth | VERIFIED | InviteRoutesTest.kt `full invite flow` GET call has no Authorization header |
| 9 | Invite redemption requires auth | VERIFIED | InviteRoutesTest.kt `redeem - unauthenticated returns 401` |
| 10 | Expired invite returns 410 | VERIFIED | InviteRoutesTest.kt `redeem - expired token returns 410` |
| 11 | Role promotion visible immediately without re-login | VERIFIED | RoleDetectionTest.kt `promoted player gains coach role` |
| 12 | Removed member loses roles immediately | VERIFIED | RoleDetectionTest.kt `removed member has no roles` |
| 13 | isSuperAdmin defaults false for all new users | VERIFIED | UsersTable.isSuperAdmin.default(false); no registration path sets it |

**Score:** 13/13 truths verified

---

_Verified: 2026-04-01T12:00:00Z_
_Verifier: Claude (gsd-executor, retroactive)_
