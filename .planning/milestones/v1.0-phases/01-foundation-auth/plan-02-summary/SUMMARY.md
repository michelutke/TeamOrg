# Summary - Plan 02: Ktor Server — DB + Flyway + JWT Auth Endpoints

Completed the implementation of the Ktor server foundation including database connectivity, migrations, and authentication.

## Accomplishments

### 1. Server Infrastructure
- Configured `Application.kt` with necessary plugins: `Serialization`, `Auth` (JWT), `Routing`, and `Koin` (DI).
- Created `application.conf` with support for environment variables (`JWT_SECRET`, `DATABASE_URL`).
- Implemented `DatabaseFactory` using HikariCP and Flyway for automated migrations.

### 2. Database & Models
- Defined `UsersTable` using Exposed DSL.
- Created Flyway migrations for the `users` table (`V1__create_users.sql`).
- Implemented `UserRepository` with Exposed for CRUD operations on users.
- Created `User` domain model.

### 3. Authentication & Security
- Integrated `jbcrypt` for secure password hashing (cost 12).
- Implemented `POST /auth/register`: validates input, hashes password, and returns JWT.
- Implemented `POST /auth/login`: verifies credentials and returns JWT + user profile.
- Implemented `POST /auth/logout`: stateless endpoint returning 200 OK.
- Implemented `GET /auth/me`: protected endpoint returning current user profile.
- Created `AuthMiddleware` (via `authenticateUser` helper) to extract and validate users from JWT tokens.

### 4. Testing
- Built `IntegrationTestBase` using `testApplication` and H2 in-memory database.
- Implemented `AuthRoutesTest` with 9 test cases covering:
    - Successful registration and login.
    - Input validation (email format, password length).
    - Conflict handling (duplicate email).
    - Authentication failures (wrong password, unknown email, missing token).
- Note: Automated test execution was skipped in this session due to missing `java` in the execution environment, but the test code is complete and follows the requirements.

## Key Files Created
- `server/src/main/kotlin/ch/teamorg/Application.kt`
- `server/src/main/kotlin/ch/teamorg/plugins/Auth.kt`
- `server/src/main/kotlin/ch/teamorg/plugins/Routing.kt`
- `server/src/main/kotlin/ch/teamorg/plugins/Serialization.kt`
- `server/src/main/kotlin/ch/teamorg/plugins/Koin.kt`
- `server/src/main/kotlin/ch/teamorg/infra/DatabaseFactory.kt`
- `server/src/main/kotlin/ch/teamorg/db/tables/UsersTable.kt`
- `server/src/main/kotlin/ch/teamorg/domain/repositories/UserRepository.kt`
- `server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt`
- `server/src/main/kotlin/ch/teamorg/middleware/AuthMiddleware.kt`
- `server/src/main/resources/application.conf`
- `server/src/main/resources/db/migrations/V1__create_users.sql`
- `server/src/test/kotlin/ch/teamorg/routes/AuthRoutesTest.kt`
- `server/src/test/kotlin/ch/teamorg/test/IntegrationTestBase.kt`
- `local.properties.example`

## Deviations
- **Java Environment:** Local `java` binary was not found in the `exec` environment, so `./gradlew :server:test` could not be verified locally. Code was written to be strictly compliant with the requirements.
- **V2 Migration:** Named `V2__create_roles_noop.sql` instead of `V2__create_roles.sql` to clarify its placeholder status.

## Conventional Commits
All changes were committed with conventional commit messages.
