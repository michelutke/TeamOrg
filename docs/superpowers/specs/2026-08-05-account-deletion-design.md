# Account Deletion — Design

Date: 2026-08-05

Store requirement: Apple guideline 5.1.1(v) requires an account-deletion path **initiated
inside the app**, and Google Play requires both an in-app path and a URL where deletion can be
requested. Nothing of the sort exists today — verified by grep across `server/`, `admin/`,
`shared/` and `composeApp/`.

## Problem

A user cannot delete their account. There is no endpoint, no UI, and no schema support.

Deleting the `users` row is not an option as the schema stands. Six foreign keys reference
`users(id)` with **no `ON DELETE` clause**, i.e. `RESTRICT`:

| Table / column | Migration |
|---|---|
| `audit_log.actor_id` | `V10__create_admin_tables.sql:7` |
| `impersonation_sessions.actor_id`, `.target_id` | `V10:28-29` |
| `invites.invited_by_user_id`, `.redeemed_by_user_id` | `V5:5,10` |
| `events.created_by` (both event tables) | `V7:16,35` |
| `attendance_records.set_by`, `.previous_set_by` | `V8:42,45` |
| `swissvolley_*.created_by` | `V13:9` |

Any user who has created an event or sent an invite — every coach — cannot be physically
deleted without first rewriting those rows. So the deletion is an **anonymization**: the row
survives, stripped of everything personal, and the team's history stays referentially intact.

## Goals

- A user deletes their own account from the mobile app and from the web app, immediately.
- After deletion, no personal data of that user remains: no email, no name, no avatar, no
  attendance, no absences, no notifications, no memberships.
- Existing sessions stop working at once — not when the JWT happens to expire.
- The account's email becomes available for a fresh registration.
- Team history (who created an event, who recorded attendance) stays intact and shows a neutral
  name.

## Non-goals

- **No grace period / undo.** Deletion is immediate and irreversible. No scheduler exists in
  this codebase and adding one for a recovery path nobody asked for is unwarranted.
- **No confirmation email.** There is no transactional mail path beyond the contact form.
- **No admin-initiated user deletion.** Super-admin user management is untouched.
- **No public info page on the landing site.** The authenticated web page doubles as the URL
  the store forms link to.
- **No physical row deletion**, and therefore no FK migrations. See above.

## Decisions

Settled before design, recorded here because each rules out an obvious alternative:

| Decision | Rejected alternative |
|---|---|
| Anonymize in place | Hard delete (needs 6 FK migrations, loses event authorship); hard delete with reassignment to a synthetic system user (needs a seeded user, rewrites history) |
| Immediate | 30-day grace period (needs a scheduler that does not exist) |
| Club owner is blocked with a reason | Auto-cancelling the Stripe subscription and soft-deleting the club — one tap would destroy every teammate's data |
| Password re-entry to confirm | Typing a literal word — anyone holding an unlocked phone could delete the account |
| Last coach of a team may delete, with a warning | Blocking — would trap a coach who has no club manager to hand off to, which is itself a store-policy risk |

## Design

### 1. Schema

`server/src/main/resources/db/migrations/V19__user_deletion.sql`:

```sql
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ NULL;
```

No FK changes, no nullability changes. That is the whole migration, and it is the point of the
anonymize approach.

### 2. Session invalidation — the load-bearing part

`UsersTable` gains the `deletedAt` column, and `User` (server domain model) is **not** extended
— the check happens before any route sees a principal.

Enforcement goes in the JWT `validate` block, `server/src/main/kotlin/ch/teamorg/plugins/Auth.kt:26-33`:

```kotlin
validate { credential ->
    val subject = credential.payload.subject ?: return@validate null
    val userId = try { UUID.fromString(subject) } catch (e: IllegalArgumentException) { return@validate null }
    if (isUserDeleted(userId)) return@validate null
    JWTPrincipal(credential.payload)
}
```

with a helper in the same file querying `UsersTable.deletedAt` for that id, in the style of
`isImpersonationSessionLive` in `AuthMiddleware.kt:60`. Returning `null` triggers the existing
`challenge`, so a deleted user's token yields the same 401 as an expired one.

**Why `validate` and not `authenticateUser`:** eleven of sixteen route files never call
`authenticateUser` — `AbwesenheitRoutes`, `AttendanceRoutes`, `EventRoutes`, `NdsRoutes`,
`NotificationRoutes`, `SubGroupRoutes`, `IntegrationRoutes`, `ImpersonationRoutes`,
`AdminRoutes`, `ContactRoutes`, `StripeWebhookRoutes` — they read the principal directly.
A check placed in `authenticateUser` would leave every one of those reachable with a deleted
user's token. `validate` is the only single choke point that covers all of them.

**Cost:** one indexed primary-key lookup per authenticated request. `authenticateUser` already
does a full `findById` on the same row for the five files that use it, so this is not a new
class of overhead. It is documented in a comment beside the helper so a future reader does not
"optimize" it away and silently restore working tokens for deleted accounts.

Login must reject deleted accounts too. `UserRepository.getPasswordHash(email)` is what
`/auth/login` uses (`AuthRoutes.kt:102`); it returns the hash for a scrubbed row whose
`password_hash` is `'!'`, which `BCrypt.checkpw` cannot match — so login already fails. That is
accidental, not designed: it depends on the scrub value. The spec makes it explicit by having
`getPasswordHash` and `findByEmail` exclude `deleted_at IS NOT NULL` rows, which also frees the
email for re-registration (see §3).

### 3. The deletion transaction

New `UserDeletionRepository` (`server/src/main/kotlin/ch/teamorg/domain/repositories/`), one
method:

```kotlin
sealed interface DeleteAccountOutcome {
    data object Deleted : DeleteAccountOutcome
    data class OwnsClubs(val clubNames: List<String>) : DeleteAccountOutcome
}

interface UserDeletionRepository {
    fun deleteAccount(userId: UUID): DeleteAccountOutcome
}
```

A separate repository rather than more methods on `UserRepository`: the operation spans nine
tables and has its own precondition logic, and `UserRepository` is already a
single-table CRUD surface.

**Precondition, checked inside the transaction:** the user owns at least one club
(`clubs.owner_user_id = userId`) whose `status <> 'deleted'` → return `OwnsClubs` with the club
names, change nothing. Checked inside the transaction so a concurrent ownership transfer cannot
slip between check and delete.

**Then, in order:**

1. Delete personal rows:
   - `abwesenheit_rules` (user's own absence rules)
   - `attendance_responses` (their RSVPs)
   - `attendance_records` where `user_id = userId` (their attendance history as a subject; rows
     where they are only `set_by` are left, since those belong to other people's history)
   - `notifications`, `notification_settings`, `notification_reminders`,
     `event_reminder_overrides`
   - `sub_group_members`, `team_roles`, `club_roles`
2. `nds_members`: set `user_id = NULL` where it points at this user. The imported roster row
   survives as unclaimed, which is exactly the state a never-registered import is in, and the
   duplicate-merge feature can re-link it later.
3. Delete the avatar file via `FileStorageService.delete(path)` if `avatar_url` is set — the
   file lives on the `uploads` volume and would otherwise be an orphaned, publicly-served
   image of a person who asked to be deleted. This is the one step that touches the filesystem,
   so it runs **after** the DB work but is derived from the row read at the start; a failure to
   delete the file is logged and does not roll back the deletion.
4. Scrub `users`:
   - `email` → `deleted-<userId>@deleted.invalid` (`.invalid` is the reserved TLD, RFC 2606 —
     it can never collide with a real address, and the uuid keeps the `UNIQUE` constraint happy)
   - `display_name` → `Gelöschtes Konto`
   - `avatar_url` → `NULL`
   - `password_hash` → `'!'` (the unusable-hash convention already used for NDS provisional
     users)
   - `deleted_at` → `now()`
5. Write an `audit_log` row: `action = "user.self_delete"`, `actor_id = userId`. The FK is
   `RESTRICT` and the row still exists, so this is valid — and it is the only remaining record
   that the account existed.

Steps 1-2, 4-5 are one Exposed `transaction`. `attendance_responses` and `attendance_records`
would cascade anyway on a physical delete, but here nothing cascades, so every table is
explicit.

**Deliberate residue, and why it is not personal data:**
- `events.created_by` / `attendance_records.set_by` still point at the scrubbed row, rendering
  as "Gelöschtes Konto".
- `invites` rows the user sent or redeemed keep the reference; an unredeemed invite they sent
  stays usable by its recipient.
- `audit_log` keeps their actions. That is the record required to show a deletion happened.

### 4. Endpoint

`DELETE /auth/me`, inside the existing `authenticate("jwt")` block and
`rateLimit(RateLimits.AUTH)` — the same wrapping `/auth/change-password` has
(`AuthRoutes.kt:129`), because this is a credential-verifying endpoint and belongs in the tight
bucket.

```kotlin
@Serializable
data class DeleteAccountRequest(val password: String)
```

Flow, mirroring `/auth/change-password` exactly:

1. `call.authenticateUser(userRepository) { user -> ... }`
2. `getPasswordHashById(userId)`; `BCrypt.checkpw(request.password.take(MAX_PASSWORD_LENGTH), hash)`
   → **401** `"Password is incorrect"` on mismatch
3. `userDeletionRepository.deleteAccount(userId)`
   - `Deleted` → **204 No Content**
   - `OwnsClubs(names)` → **409** with a body the clients can act on:
     `{"reason":"owns_clubs","clubs":["SV Example"]}`

The 409 body is structured rather than a prose string so both clients can render their own
localized sentence — the web app is German, the mobile app English.

### 5. Web UI (German)

`admin/src/routes/(shell)/app/profile/+page.svelte` gains a "Konto löschen" section at the
bottom, visually separated (destructive zone), containing an explanation and a link to a
dedicated confirm route rather than an inline button — a mis-click on a profile page must not
be one step from deletion.

New `admin/src/routes/(shell)/app/profile/delete/+page.svelte` + `+page.server.ts`:

- Heading "Konto endgültig löschen"
- **What gets deleted:** E-Mail-Adresse und Name, Profilbild, alle Anwesenheits-Rückmeldungen
  und Abwesenheiten, Benachrichtigungen und deren Einstellungen, Team- und Vereins-Mitgliedschaften
- **What stays:** Events, die du erstellt hast, und Anwesenheiten, die du erfasst hast, bleiben
  für dein Team erhalten — dein Name wird dort durch "Gelöschtes Konto" ersetzt
- Warning: **Das kann nicht rückgängig gemacht werden.**
- If the user coaches a team: "Teams, die du als Trainer betreust, haben danach keinen Trainer
  mehr, bis ein Vereinsmanager einen neuen zuweist." — the page's `load` reads
  `/auth/me/roles` (which already returns team roles) to decide whether to show it.
- Password field + red "Konto löschen" button, submitted as a SvelteKit form action.
- On 204: clear the session cookie and redirect to `/login` with a confirmation notice.
- On 401: "Das Passwort ist falsch."
- On 409 `owns_clubs`: "Du bist Besitzer von <Verein>. Übertrage die Besitzer-Rolle an einen
  anderen Vereinsmanager oder lösche den Verein, bevor du dein Konto löschst." — with no
  password field re-shown, since retrying cannot help.

### 6. Mobile UI (English)

Copy is English: `composeApp` has no i18n and its existing strings are English. Same reasoning
as the NDS merge feature.

`shared/src/commonMain/kotlin/ch/teamorg/domain/DeleteAccountResult.kt`:

```kotlin
sealed interface DeleteAccountResult {
    data object Success : DeleteAccountResult
    data object InvalidPassword : DeleteAccountResult
    data class OwnsClubs(val clubNames: List<String>) : DeleteAccountResult
    data class Error(val message: String) : DeleteAccountResult
}
```

`AuthRepository.deleteAccount(password: String): DeleteAccountResult` — the sealed type
directly, no `Result` wrapper, following the `RedeemResult` / `LinkMemberResult` pattern.
`AuthRepositoryImpl` maps 204/200 → `Success`, 401 → `InvalidPassword`, 409 → parse the body
into `OwnsClubs`, anything else → `Error`. On `Success` it calls the existing `logout()` so the
stored token is cleared even if the UI layer fails afterwards — the token is dead server-side
regardless, and leaving it on disk only produces confusing 401s.

UI: `PlayerProfileScreen` with `isNavProfile = true` is where Logout already lives
(`AppNavigation.kt:273-301`) — the delete entry goes there, below Logout, in a destructive
style. It opens a dialog:

- Title "Delete account"
- One-line summary plus an **info icon** that expands the full what-is-deleted / what-is-kept
  list inline in the dialog (per the requested disclosure). Collapsed by default so the dialog
  stays small; expanded state is local to the dialog.
- The last-coach warning when the user coaches a team, using the roles the app already has.
- Password field, "This cannot be undone." line, Delete button `enabled` only when the password
  field is non-empty and no request is in flight.
- Error mapping (never a raw status): `InvalidPassword` → "That password is incorrect."
  `OwnsClubs` → "You own <club>. Transfer ownership to another club manager, or delete the club,
  before deleting your account." `Error` → "Couldn't delete your account. Please try again."
- On success: dialog closes, the app navigates to login via the existing logout path.

`PlayerProfileViewModel` state gains `showDeleteDialog`, `deleteInProgress`, `deleteError`, and
the ViewModel exposes `deleteAccount(password)`. The ViewModel needs `AuthRepository`; if it
does not already have it, it is added as a constructor dependency in the Koin module, and the
existing test fakes for that ViewModel are updated in step.

## Testing

TDD. Server tests are `kotlin.test` + `IntegrationTestBase`; `shared` jvmTest is `kotlin.test`
+ Ktor `MockEngine`; `composeApp` is Kotest matchers with `kotlin.test` annotations,
`UnconfinedTestDispatcher`, and hand-written fakes.

New `server/src/test/kotlin/ch/teamorg/routes/AccountDeletionTest.kt`:

- wrong password → 401, and the account still works afterwards
- happy path → 204; the `users` row has `deleted_at` set, a `deleted-…@deleted.invalid` email,
  `Gelöschtes Konto` as name
- **the user's existing token is rejected (401) on an unrelated endpoint afterwards** — the
  session-invalidation guarantee, and the test that would catch a regression to the
  `authenticateUser`-only placement
- login with the old email and old password → 401
- registering again with the same email → succeeds
- personal rows are gone: no `attendance_responses`, `abwesenheit_rules`, `team_roles`,
  `club_roles`, `sub_group_members`, `notification_settings` for that user
- an event the user created still exists, and its `created_by` still resolves
- an `nds_members` row that pointed at the user has `user_id IS NULL`
- club owner with a non-deleted club → 409 with the club name, and **nothing was deleted**
  (assert a personal row still exists — a 409 that half-deleted would be the worst outcome here)
- an owner whose club is already `status = 'deleted'` → 204
- `audit_log` has a `user.self_delete` row for the user

`shared/src/jvmTest/.../AuthRepositoryDeleteAccountTest.kt`: MockEngine per status — 204, 200,
401, 409 with a body, 500 — asserting the mapped `DeleteAccountResult`, and that `Success`
cleared the stored token.

`composeApp` `PlayerProfileViewModelTest`: dialog open/close resets error state; a successful
delete triggers the logout callback; `InvalidPassword` and `OwnsClubs` map to their sentences
and leave the dialog open; Delete is blocked while a request is in flight.

Verification gates: `./gradlew :server:test` (4 known pre-existing failures, must not grow),
`./gradlew :composeApp:testDebugUnitTest`, `./gradlew :shared:jvmTest`,
`./gradlew :composeApp:compileDebugKotlinAndroid`,
`./gradlew :shared:compileKotlinIosSimulatorArm64`, and `cd admin && npm run check`.

No automated web or Compose UI test: the admin app's tests are Playwright E2E against
production, which is read-only, and a destructive flow cannot be exercised there. The web and
mobile walkthroughs are owed manually.

## Affected files

**Server**
- `server/src/main/resources/db/migrations/V19__user_deletion.sql` — new
- `server/src/main/kotlin/ch/teamorg/db/tables/UsersTable.kt` — `deletedAt`
- `server/src/main/kotlin/ch/teamorg/plugins/Auth.kt` — reject deleted users in `validate`
- `server/src/main/kotlin/ch/teamorg/domain/repositories/UserRepository.kt` — exclude deleted
  rows from `findByEmail` / `getPasswordHash`
- `server/src/main/kotlin/ch/teamorg/domain/repositories/UserDeletionRepository.kt` — new
- `server/src/main/kotlin/ch/teamorg/routes/AuthRoutes.kt` — `DELETE /auth/me`
- `server/src/main/kotlin/ch/teamorg/di/*` — register the repository in Koin
- `server/src/test/kotlin/ch/teamorg/routes/AccountDeletionTest.kt` — new

**Shared**
- `shared/src/commonMain/kotlin/ch/teamorg/domain/DeleteAccountResult.kt` — new
- `shared/src/commonMain/kotlin/ch/teamorg/repository/AuthRepository.kt` — `deleteAccount`
- `shared/src/commonMain/kotlin/ch/teamorg/data/repository/AuthRepositoryImpl.kt` — mapping
- `shared/src/jvmTest/kotlin/ch/teamorg/repository/AuthRepositoryDeleteAccountTest.kt` — new

**Mobile**
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileScreen.kt` — entry + dialog
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/PlayerProfileViewModel.kt` — state + action
- `composeApp/src/commonMain/kotlin/ch/teamorg/di/*` — `AuthRepository` into the ViewModel if absent
- `composeApp` test fakes for `AuthRepository` — updated for the new method
- `composeApp/src/commonTest/kotlin/ch/teamorg/ui/team/PlayerProfileViewModelTest.kt`

**Web**
- `admin/src/routes/(shell)/app/profile/+page.svelte` — destructive section
- `admin/src/routes/(shell)/app/profile/delete/+page.svelte` + `+page.server.ts` — new

**Docs**
- `docs/store-data-safety.md` — the deletion URL and in-app path, for the store forms
  (created by the transport-encryption spec; this adds the deletion section)

## Risks

- **Session invalidation is the one place a silent failure is catastrophic**: if the check ends
  up somewhere that not all routes pass through, a "deleted" user keeps full access. Mitigated
  by placing it in `validate` and by the explicit token-rejection test.
- **A partial deletion is worse than no deletion.** Mitigated by one transaction for all DB
  work, the precondition checked inside it, and a test asserting the 409 path deletes nothing.
  The avatar-file removal is deliberately outside the transaction and only logs on failure —
  an orphaned file is a lesser harm than a rolled-back deletion.
- **Anonymized rows are not physically deleted.** A future GDPR request for hard erasure would
  need the FK migration this spec avoids. The residue is limited to authorship references and
  the audit log, which is a defensible retention position, but it is a knowing trade-off rather
  than an oversight.
- **`Gelöschtes Konto` is German in an otherwise English mobile UI.** It is stored server-side
  and read by both clients. Accepted: the value is a database placeholder, the product's primary
  market is German-speaking, and localizing a stored string would require a client-side
  substitution keyed on a magic value.
