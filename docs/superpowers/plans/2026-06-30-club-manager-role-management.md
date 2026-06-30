# Club-Manager Role Management — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a club manager list all club users (sorted, lazy-loaded), add an existing user to a team with a role, change/remove a member's role, link an existing account to an NDS-imported player, and invite a brand-new user by email — on both web admin and mobile.

**Architecture:** New server endpoints expose a paginated club-user list, a team add-member action, and an NDS link-to-account action; change-role / remove-member / email-invite endpoints already exist and are reused. A new web screen `/manage/{clubId}/members` and an equivalent mobile screen consume these via paginated/infinite-scroll lists.

**Tech Stack:** Ktor + Exposed + Postgres (server), SvelteKit + Svelte 5 runes + Tailwind (admin web), Kotlin Multiplatform + Compose (mobile, `composeApp` + `shared`), kotlinx.serialization, Ktor HttpClient (shared).

## Global Constraints

- Branch: `feat/nds-import-followups`. Git: **merge only, never rebase**; no AI authorship trailers; commit per logical unit.
- Web gate before push: `npm run check` clean (in `admin/`). Server/shared/mobile: `./gradlew check` green.
- **Security (no-IDOR — memory `security-no-idor`):** every endpoint verifies the caller is `club_manager` of the relevant club via `requireClubRole(clubId, "club_manager", clubRepository)` (club-scoped) or `requireTeamRole(teamId, "club_manager", teamRepository)` (team-scoped), AND that the target team / NDS member / target user belongs to that club. `GET /clubs/{clubId}/users` must return only users within that club.
- User list is **server-sorted** (lastName/displayName ASC) and **paginated** (`limit`+`offset`); the UI shows the full list immediately and lazy-loads more on scroll — no minimum typing required to see results.
- Roles for team membership are `player` | `coach` only (matches existing `POST /teams/{teamId}/invites` validation). `club_manager` is club-scoped and out of scope for team add-member.
- Reuse existing endpoints where present (see "Existing building blocks").

## Existing building blocks (reuse — do not recreate)

- `PATCH /teams/{teamId}/members/{userId}/role` (club_manager) — change role. Repo: `TeamRepository.updateMemberRole`.
- `DELETE /teams/{teamId}/members/{userId}` (club_manager) — remove member. Repo: `TeamRepository.removeMember`.
- `GET /teams/{teamId}/members` — list a team's members.
- `POST /teams/{teamId}/invites { role, email }` (coach|club_manager) — invite brand-new user by email.
- `NdsRepository.claimMember(memberId, realUserId)` — links a real account to an NDS member, migrates attendance + role.
- Shared mobile repo pattern: `class TeamRepositoryImpl(private val client: HttpClient)` returning `Result<T>` with `@Serializable` private request DTOs (see `shared/.../data/repository/TeamRepositoryImpl.kt`).
- Mobile nav: `sealed class Screen(val route: String)` in `composeApp/.../navigation/Screen.kt`.

---

## File Structure

**Server**
- New model: extend `server/.../domain/models/` with `ClubUser` + `TeamRoleRef` DTOs (place in an existing models file, e.g. `Club.kt` or a new `ClubUser.kt`).
- Modify: `server/.../domain/repositories/ClubRepository.kt` (+ `ClubRepositoryImpl.kt`) — `listUsers(clubId, limit, offset)`.
- Modify: `server/.../domain/repositories/TeamRepository.kt` (+ `TeamRepositoryImpl.kt`) — `addMember(teamId, userId, role)`.
- Modify: `server/.../routes/ClubRoutes.kt` — `GET /clubs/{clubId}/users`.
- Modify: `server/.../routes/TeamRoutes.kt` — `POST /teams/{teamId}/members`.
- Modify: `server/.../routes/NdsRoutes.kt` — `POST /teams/{teamId}/nds/members/{id}/link`.
- Tests: `server/.../routes/ClubRoutesTest.kt`, `TeamRoutesTest.kt`, `NdsRoutesTest.kt`.

**Web (`admin/`)**
- Create: `admin/src/routes/(shell)/manage/[clubId]/members/+page.server.ts` (load first page + actions/proxies).
- Create: `admin/src/routes/(shell)/manage/[clubId]/members/+page.svelte` (infinite-scroll list + controls).
- Create: `admin/src/routes/(shell)/manage/[clubId]/users/+server.ts` (paged fetch proxy for scroll).
- Modify: club manage navigation to link to the new screen (`admin/src/routes/(shell)/manage/[clubId]/+page.svelte` or the sidebar).

**Mobile (`composeApp` + `shared`)**
- New shared model: `shared/.../domain/ClubUser.kt`.
- Modify: `shared/.../repository/ClubRepository.kt` (+ impl) — `listClubUsers(clubId, limit, offset)`, `addTeamMember(teamId, userId, role)`, `linkNdsMember(teamId, memberId, userId)`. Reuse existing `updateRole`/`removeMember` on `TeamRepository` if present; add if missing.
- Create: `composeApp/.../ui/club/ClubMembersScreen.kt` + `ClubMembersViewModel.kt`.
- Modify: `composeApp/.../navigation/Screen.kt` + `AppNavigation.kt` — add `ClubMembers(clubId)` route + entry point for club managers.

---

## Shared DTO contract (used by web + mobile + server, identical JSON)

```
ClubUser {
  userId: String
  displayName: String
  email: String
  avatarUrl: String? 
  teamRoles: [ TeamRoleRef ]   // the user's roles within THIS club only
}
TeamRoleRef { teamId: String, teamName: String, role: String }
```

Server endpoint `GET /clubs/{clubId}/users?limit=50&offset=0` → `List<ClubUser>` sorted by `displayName` ASC. A short page (< limit) signals the end.

---

## Server

### Task 1: ClubRepository.listUsers

**Files:**
- Modify: `server/.../domain/models/` (add `ClubUser`, `TeamRoleRef`)
- Modify: `server/.../domain/repositories/ClubRepository.kt`, `ClubRepositoryImpl.kt`
- Test: `server/src/test/kotlin/ch/teamorg/routes/ClubRoutesTest.kt`

**Interfaces:**
- Produces: `suspend fun listUsers(clubId: UUID, limit: Int, offset: Int): List<ClubUser>` — distinct users holding any team role in the club, sorted by `displayName` ASC, each with their in-club `teamRoles`. Excludes `provisional` users (synthetic NDS placeholders).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `club users lists members with their team roles sorted`() = withTeamorgTestApplication {
    val mgr = register("cm@example.com"); promoteToSuperAdmin(mgr.userId)
    val clubId = createClub(mgr.token, "Roles")
    // seed: 2 teams, add a coach + a player (helpers mirror existing tests)
    val users = createJsonClient().get("/clubs/$clubId/users?limit=50&offset=0") {
        header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
    }.body<List<ClubUser>>()
    assertTrue(users.isNotEmpty())
    assertTrue(users.all { it.teamRoles.isNotEmpty() })
    assertTrue(users.none { it.email.endsWith("@import.teamorg.local") }) // provisional excluded
    assertEquals(users.map { it.displayName }, users.map { it.displayName }.sorted())
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.ClubRoutesTest'`
Expected: FAIL — endpoint/route + `ClubUser` missing.

- [ ] **Step 3: Add DTOs**

```kotlin
@Serializable
data class TeamRoleRef(val teamId: String, val teamName: String, val role: String)

@Serializable
data class ClubUser(
    val userId: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String? = null,
    val teamRoles: List<TeamRoleRef> = emptyList()
)
```

- [ ] **Step 4: Implement listUsers**

In `ClubRepository.kt`:

```kotlin
suspend fun listUsers(clubId: UUID, limit: Int, offset: Int): List<ClubUser>
```

In `ClubRepositoryImpl.kt` (Exposed: join `TeamsTable` (club) → `TeamRolesTable` → `UsersTable`; group in Kotlin). Sketch:

```kotlin
override suspend fun listUsers(clubId: UUID, limit: Int, offset: Int): List<ClubUser> = transaction {
    val rows = (TeamsTable innerJoin TeamRolesTable innerJoin UsersTable)
        .select(UsersTable.id, UsersTable.displayName, UsersTable.email, UsersTable.avatarUrl,
                TeamsTable.id, TeamsTable.name, TeamRolesTable.role)
        .where { (TeamsTable.clubId eq clubId) and (UsersTable.provisional eq false) }
        .toList()
    rows.groupBy { it[UsersTable.id] }
        .map { (uid, rs) ->
            val first = rs.first()
            ClubUser(
                userId = uid.toString(),
                displayName = first[UsersTable.displayName],
                email = first[UsersTable.email],
                avatarUrl = first[UsersTable.avatarUrl],
                teamRoles = rs.map { TeamRoleRef(it[TeamsTable.id].toString(), it[TeamsTable.name], it[TeamRolesTable.role]) }
            )
        }
        .sortedBy { it.displayName.lowercase() }
        .drop(offset).take(limit)
}
```

(If `TeamsTable`/`TeamRolesTable` join columns differ, use the same join style as `AttendanceRepositoryImpl.getCheckInEntries`. Pagination via `drop/take` is acceptable for club-sized lists; switch to SQL `limit/offset` only if profiling demands.)

- [ ] **Step 5: Add the route**

In `ClubRoutes.kt`, inside `route("/{clubId}")`:

```kotlin
get("/users") {
    val clubId = UUID.fromString(call.parameters["clubId"])
    if (!call.requireClubRole(clubId, "club_manager", clubRepository)) return@get
    val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
    val offset = (call.request.queryParameters["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
    call.respond(clubRepository.listUsers(clubId, limit, offset))
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.ClubRoutesTest'`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/domain server/src/main/kotlin/ch/teamorg/routes/ClubRoutes.kt server/src/test/kotlin/ch/teamorg/routes/ClubRoutesTest.kt
git commit -m "feat(server): paginated club-users list with team roles"
```

---

### Task 2: TeamRepository.addMember + POST /teams/{teamId}/members

**Files:**
- Modify: `server/.../domain/repositories/TeamRepository.kt`, `TeamRepositoryImpl.kt`
- Modify: `server/.../routes/TeamRoutes.kt`
- Test: `server/src/test/kotlin/ch/teamorg/routes/TeamRoutesTest.kt`

**Interfaces:**
- Produces: `suspend fun addMember(teamId: UUID, userId: UUID, role: String): TeamMember` — inserts a team role (idempotent: if the user already has a role on the team, update it to `role`). Endpoint `POST /teams/{teamId}/members { userId, role }` (club_manager), `role ∈ {player, coach}`, returns the `TeamMember`. 409/400 on bad input; 404 if user not in the same club is **not** required, but the user must exist.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `club manager adds an existing user to a team as coach`() = withTeamorgTestApplication {
    val mgr = register("cm2@example.com"); promoteToSuperAdmin(mgr.userId)
    val clubId = createClub(mgr.token, "Add")
    val teamId = /* create a team in clubId via existing helper */ createTeam(mgr.token, clubId, "T1")
    val bob = register("bob@example.com")
    val resp = createJsonClient().post("/teams/$teamId/members") {
        header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        contentType(ContentType.Application.Json)
        setBody(AddMemberRequest(userId = bob.userId, role = "coach"))
    }
    assertEquals(HttpStatusCode.OK, resp.status)
    val members = createJsonClient().get("/teams/$teamId/members") {
        header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
    }.body<List<TeamMember>>()
    assertTrue(members.any { it.userId == bob.userId && it.role == "coach" })
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.TeamRoutesTest'`
Expected: FAIL — route + `AddMemberRequest` + `addMember` missing.

- [ ] **Step 3: Implement the repo method**

In `TeamRepository.kt`:

```kotlin
suspend fun addMember(teamId: UUID, userId: UUID, role: String): TeamMember
```

In `TeamRepositoryImpl.kt` (use `TeamRolesTable.upsert` keyed on (teamId, userId) so re-adding updates the role; then return via the same projection `listMembers` uses for a single user):

```kotlin
override suspend fun addMember(teamId: UUID, userId: UUID, role: String): TeamMember = transaction {
    TeamRolesTable.upsert(keys = arrayOf(TeamRolesTable.teamId, TeamRolesTable.userId)) {
        it[TeamRolesTable.teamId] = teamId
        it[TeamRolesTable.userId] = userId
        it[TeamRolesTable.role] = role
    }
    // reuse the existing single-member projection (mirror updateMemberRole's return path)
    memberRow(teamId, userId)
}
```

(If `updateMemberRole` already builds a `TeamMember` from (teamId,userId), extract that into a private `memberRow` and reuse it here.)

- [ ] **Step 4: Add the route**

In `TeamRoutes.kt`, alongside the existing `patch("/members/{userId}/role")`:

```kotlin
post("/members") {
    val teamId = UUID.fromString(call.parameters["teamId"])
    if (!call.requireTeamRole(teamId, "club_manager", teamRepository = teamRepository)) return@post
    val body = call.receive<AddMemberRequest>()
    if (body.role !in listOf("player", "coach"))
        return@post call.respond(HttpStatusCode.BadRequest, "Invalid role")
    val userId = runCatching { UUID.fromString(body.userId) }.getOrNull()
        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid userId")
    if (userRepository.findById(userId) == null)
        return@post call.respond(HttpStatusCode.NotFound, "User not found")
    call.respond(teamRepository.addMember(teamId, userId, body.role))
}
```

Add near `UpdateRoleRequest`:

```kotlin
@Serializable
data class AddMemberRequest(val userId: String, val role: String)
```

(Inject `userRepository` if not already present in `TeamRoutes` — mirror how `NdsRoutes` injects it.)

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.TeamRoutesTest'`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepository.kt server/src/main/kotlin/ch/teamorg/domain/repositories/TeamRepositoryImpl.kt server/src/main/kotlin/ch/teamorg/routes/TeamRoutes.kt server/src/test/kotlin/ch/teamorg/routes/TeamRoutesTest.kt
git commit -m "feat(server): add existing user to a team with a role"
```

---

### Task 3: POST /teams/{teamId}/nds/members/{id}/link

**Files:**
- Modify: `server/.../routes/NdsRoutes.kt`
- Test: `server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt`

**Interfaces:**
- Produces: `POST /teams/{teamId}/nds/members/{id}/link { userId }` (coach|club_manager) → calls `ndsRepository.claimMember(memberId, userId)`; 200 with the updated `NdsMember`. Validates the member belongs to the team and the user exists. After this, the member is claimed and attendance migrates off the provisional placeholder (same as invite redeem).

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `club manager links an existing account to an imported player`() = withTeamorgTestApplication {
    val mgr = register("cm3@example.com"); promoteToSuperAdmin(mgr.userId)
    val clubId = createClub(mgr.token, "Link")
    val res = importAll(mgr.token, clubId)          // existing helper → team + roster + attendance
    val teamId = UUID.fromString(res.teamId)
    val lara = createJsonClient().get("/teams/$teamId/nds/members") {
        header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
    }.body<List<NdsMember>>().single { it.lastName == "Müller" }
    val realUser = register("lara.real@example.com")
    val linked = createJsonClient().post("/teams/$teamId/nds/members/${lara.id}/link") {
        header(HttpHeaders.Authorization, "Bearer ${mgr.token}")
        contentType(ContentType.Application.Json)
        setBody(NdsMemberLinkRequest(userId = realUser.userId))
    }
    assertEquals(HttpStatusCode.OK, linked.status)
    val updated = linked.body<NdsMember>()
    assertEquals(realUser.userId, updated.userId.toString())
    assertTrue(updated.claimed)
    val movedToReal = transaction {
        AttendanceRecordsTable.selectAll()
            .where { AttendanceRecordsTable.userId eq UUID.fromString(realUser.userId) }.count()
    }
    assertTrue(movedToReal > 0)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`
Expected: FAIL — route + `NdsMemberLinkRequest` missing.

- [ ] **Step 3: Implement the route**

In `NdsRoutes.kt`, near the existing `post("/teams/{teamId}/nds/members/{id}/invite")`:

```kotlin
@Serializable
data class NdsMemberLinkRequest(val userId: String)

// inside authenticate("jwt"):
post("/teams/{teamId}/nds/members/{id}/link") {
    val teamId = UUID.fromString(call.parameters["teamId"])
    val memberId = UUID.fromString(call.parameters["id"])
    if (!call.requireTeamRole(teamId, "coach", "club_manager", teamRepository = teamRepository)) return@post
    val member = ndsRepository.getMember(memberId)
    if (member == null || member.teamId != teamId)
        return@post call.respond(HttpStatusCode.NotFound, "Mitglied nicht gefunden")
    val userId = runCatching { UUID.fromString(call.receive<NdsMemberLinkRequest>().userId) }.getOrNull()
        ?: return@post call.respond(HttpStatusCode.BadRequest, "Ungültige userId")
    if (userRepository.findById(userId) == null)
        return@post call.respond(HttpStatusCode.NotFound, "Konto nicht gefunden")
    ndsRepository.claimMember(memberId, userId)
    val updated = ndsRepository.getMember(memberId)
        ?: return@post call.respond(HttpStatusCode.NotFound, "Mitglied nicht gefunden")
    call.respond(HttpStatusCode.OK, updated)
}
```

(`userRepository` is already injected in `NdsRoutes`.)

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :server:test --tests 'ch.teamorg.routes.NdsRoutesTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/src/main/kotlin/ch/teamorg/routes/NdsRoutes.kt server/src/test/kotlin/ch/teamorg/routes/NdsRoutesTest.kt
git commit -m "feat(server): link existing account to an imported NDS player"
```

---

## Web admin

### Task 4: Club-members screen — load + paged proxy

**Files:**
- Create: `admin/src/routes/(shell)/manage/[clubId]/members/+page.server.ts`
- Create: `admin/src/routes/(shell)/manage/[clubId]/users/+server.ts`

**Interfaces:**
- Consumes: `GET /clubs/{clubId}/users?limit&offset` (Task 1), `GET /clubs/{clubId}/teams` (existing).
- Produces: page `load` returns `{ users: ClubUser[], teams: {id,name}[], pageSize }` (first page). The `+server.ts` GET returns the next page JSON for infinite scroll.

- [ ] **Step 1: Define the TS type + load**

`+page.server.ts`:

```ts
import { apiGet } from '$lib/server/api';
import { assertClubAccess } from '$lib/server/guards';
import type { PageServerLoad, Actions } from './$types';

export interface TeamRoleRef { teamId: string; teamName: string; role: string }
export interface ClubUser { userId: string; displayName: string; email: string; avatarUrl: string | null; teamRoles: TeamRoleRef[] }

const PAGE = 50;

export const load: PageServerLoad = async ({ params, locals }) => {
    assertClubAccess(locals, params.clubId);
    const [users, teams] = await Promise.all([
        apiGet<ClubUser[]>(`/clubs/${params.clubId}/users?limit=${PAGE}&offset=0`, locals.token!),
        apiGet<{ id: string; name: string }[]>(`/clubs/${params.clubId}/teams`, locals.token!)
    ]);
    return { users, teams, pageSize: PAGE };
};
```

- [ ] **Step 2: Paged proxy for scroll**

`users/+server.ts`:

```ts
import { json } from '@sveltejs/kit';
import { apiGet } from '$lib/server/api';
import { assertClubAccess } from '$lib/server/guards';
import type { RequestHandler } from './$types';

export const GET: RequestHandler = async ({ params, locals, url }) => {
    assertClubAccess(locals, params.clubId);
    const limit = url.searchParams.get('limit') ?? '50';
    const offset = url.searchParams.get('offset') ?? '0';
    const users = await apiGet(`/clubs/${params.clubId}/users?limit=${limit}&offset=${offset}`, locals.token!);
    return json(users);
};
```

- [ ] **Step 3: Verify**

Run (in `admin/`): `npm run check`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add 'admin/src/routes/(shell)/manage/[clubId]/members/+page.server.ts' 'admin/src/routes/(shell)/manage/[clubId]/users/+server.ts'
git commit -m "feat(web): club-members loader + paged users proxy"
```

---

### Task 5: Club-members screen — actions

**Files:**
- Modify: `admin/src/routes/(shell)/manage/[clubId]/members/+page.server.ts`

**Interfaces:**
- Produces form actions: `addMember` (POST `/teams/{teamId}/members`), `changeRole` (PATCH `/teams/{teamId}/members/{userId}/role`), `removeMember` (DELETE `/teams/{teamId}/members/{userId}`), `inviteByEmail` (POST `/teams/{teamId}/invites`), `linkNds` (POST `/teams/{teamId}/nds/members/{id}/link`).

- [ ] **Step 1: Add the actions**

Append to `+page.server.ts` (uses `apiPost`, `apiPatch`, `apiDelete`):

```ts
import { apiGet, apiPost, apiPatch, apiDelete } from '$lib/server/api';
import { fail } from '@sveltejs/kit';
import { ApiError } from '$lib/server/guards';

export const actions: Actions = {
    addMember: async ({ request, params, locals }) => {
        assertClubAccess(locals, params.clubId);
        const f = await request.formData();
        const teamId = f.get('teamId') as string;
        const userId = f.get('userId') as string;
        const role = f.get('role') as string;
        try { await apiPost(`/teams/${teamId}/members`, locals.token!, { userId, role }); return { ok: 'added' }; }
        catch (e) { return fail(e instanceof ApiError ? e.status : 500, { error: 'addFailed' }); }
    },
    changeRole: async ({ request, params, locals }) => {
        assertClubAccess(locals, params.clubId);
        const f = await request.formData();
        const teamId = f.get('teamId') as string; const userId = f.get('userId') as string; const role = f.get('role') as string;
        try { await apiPatch(`/teams/${teamId}/members/${userId}/role`, locals.token!, { role }); return { ok: 'role' }; }
        catch (e) { return fail(e instanceof ApiError ? e.status : 500, { error: 'roleFailed' }); }
    },
    removeMember: async ({ request, params, locals }) => {
        assertClubAccess(locals, params.clubId);
        const f = await request.formData();
        const teamId = f.get('teamId') as string; const userId = f.get('userId') as string;
        try { await apiDelete(`/teams/${teamId}/members/${userId}`, locals.token!); return { ok: 'removed' }; }
        catch (e) { return fail(e instanceof ApiError ? e.status : 500, { error: 'removeFailed' }); }
    },
    inviteByEmail: async ({ request, params, locals }) => {
        assertClubAccess(locals, params.clubId);
        const f = await request.formData();
        const teamId = f.get('teamId') as string; const role = f.get('role') as string; const email = f.get('email') as string;
        try { await apiPost(`/teams/${teamId}/invites`, locals.token!, { role, email }); return { ok: 'invited' }; }
        catch (e) { return fail(e instanceof ApiError ? e.status : 500, { error: 'inviteFailed' }); }
    },
    linkNds: async ({ request, params, locals }) => {
        assertClubAccess(locals, params.clubId);
        const f = await request.formData();
        const teamId = f.get('teamId') as string; const memberId = f.get('memberId') as string; const userId = f.get('userId') as string;
        try { await apiPost(`/teams/${teamId}/nds/members/${memberId}/link`, locals.token!, { userId }); return { ok: 'linked' }; }
        catch (e) { return fail(e instanceof ApiError ? e.status : 500, { error: 'linkFailed' }); }
    }
};
```

- [ ] **Step 2: Verify**

Run (in `admin/`): `npm run check`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add 'admin/src/routes/(shell)/manage/[clubId]/members/+page.server.ts'
git commit -m "feat(web): club-members management actions"
```

---

### Task 6: Club-members screen — UI with infinite scroll

**Files:**
- Create: `admin/src/routes/(shell)/manage/[clubId]/members/+page.svelte`
- Modify: club manage nav (`admin/src/routes/(shell)/manage/[clubId]/+page.svelte` — add a link/button "Mitglieder verwalten").

**Interfaces:**
- Consumes: `data.users`, `data.teams`, `data.pageSize`; the paged proxy `GET /manage/{clubId}/users?limit&offset`.

- [ ] **Step 1: Build the list + lazy loading**

Create `+page.svelte`. Core script (Svelte 5 runes):

```svelte
<script lang="ts">
  import { enhance } from '$app/forms';
  import type { PageData } from './$types';
  import type { ClubUser } from './+page.server';
  let { data }: { data: PageData } = $props();
  let users = $state<ClubUser[]>(data.users);
  let offset = $state(data.users.length);
  let done = $state(data.users.length < data.pageSize);
  let loading = $state(false);
  let filter = $state('');

  const shown = $derived(
    filter.trim()
      ? users.filter((u) => `${u.displayName} ${u.email}`.toLowerCase().includes(filter.toLowerCase()))
      : users
  );

  async function loadMore() {
    if (loading || done) return;
    loading = true;
    try {
      const res = await fetch(`/manage/${data.clubId ?? ''}/users?limit=${data.pageSize}&offset=${offset}`);
      const next = (await res.json()) as ClubUser[];
      users = [...users, ...next];
      offset += next.length;
      if (next.length < data.pageSize) done = true;
    } finally { loading = false; }
  }
</script>
```

(Resolve `clubId` from `$page.params` if not in `data` — import `page` from `$app/stores` or pass `clubId` through `load`.)

List markup: a top filter `<input bind:value={filter} placeholder="Filtern…" />` (optional narrowing, not required to see results), then `{#each shown as u (u.userId)}` rows showing name/email + their `teamRoles` chips, plus per-row controls (a small menu/inline forms) wired to the actions:
- **Add to team:** a `<form method="POST" action="?/addMember" use:enhance>` with a team `<select>` (from `data.teams`), role `<select>` (player/coach), hidden `userId`.
- **Change role / remove:** inline forms posting `?/changeRole` / `?/removeMember` with hidden `teamId`+`userId`.
- **Invite by email:** a `<form action="?/inviteByEmail">` with email + role + team.
- **Link to imported player:** only relevant from the NDS member side; expose the reverse here as "Konto mit importiertem Spieler verknüpfen" if the team has unclaimed NDS members (optional in this screen; the primary link UI stays on the team-detail NDS section — add a `?/linkNds` form there picking from `data.users`).

Add a sentinel div + `IntersectionObserver` (or a "Mehr laden" button calling `loadMore()`) at the list bottom for lazy loading.

- [ ] **Step 2: Add nav link**

In the club manage page, add a button/link `href={'/manage/' + clubId + '/members'}` labelled "Mitglieder verwalten" (use the existing button styles on that page).

- [ ] **Step 3: Verify**

Run (in `admin/`): `npm run check`
Expected: no errors.

- [ ] **Step 4: Manual smoke**

Start the dev server; open `/manage/{clubId}/members`; confirm the full user list shows immediately (no typing needed), scrolling loads more, and add/change/remove/invite work.

- [ ] **Step 5: Commit**

```bash
git add 'admin/src/routes/(shell)/manage/[clubId]/members/+page.svelte' 'admin/src/routes/(shell)/manage/[clubId]/+page.svelte'
git commit -m "feat(web): club-members management screen with lazy loading"
```

---

### Task 7: Team-detail — link existing account to NDS member

**Files:**
- Modify: `admin/src/routes/(shell)/app/teams/[teamId]/+page.server.ts` (add `linkNds` action + load club users for the picker)
- Modify: `admin/src/routes/(shell)/app/teams/[teamId]/+page.svelte` (add "Konto verknüpfen" next to "Einladen")

**Interfaces:**
- Consumes: `POST /teams/{teamId}/nds/members/{id}/link` (Task 3); `GET /clubs/{clubId}/users` for the account picker.

- [ ] **Step 1: Load club users for the picker**

In `[teamId]/+page.server.ts` load, when `canManage`, also fetch the club's users (resolve clubId via the team) and return `clubUsers`.

- [ ] **Step 2: Add the action**

```ts
linkNdsMember: async ({ request, params, locals }) => {
    // mirror inviteNdsMember; read memberId + userId; POST /teams/{teamId}/nds/members/{memberId}/link {userId}
}
```

- [ ] **Step 3: UI control**

In the NDS member row, for `!m.claimed`, add a second button "Konto verknüpfen" that reveals a `<select>` of `data.clubUsers` + a submit posting `?/linkNdsMember` with hidden `memberId`.

- [ ] **Step 4: Verify**

Run (in `admin/`): `npm run check`
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add 'admin/src/routes/(shell)/app/teams/[teamId]/+page.server.ts' 'admin/src/routes/(shell)/app/teams/[teamId]/+page.svelte'
git commit -m "feat(web): link existing account to an imported NDS player"
```

---

## Mobile (composeApp + shared)

### Task 8: Shared ClubUser model + repository methods

**Files:**
- Create: `shared/src/commonMain/kotlin/ch/teamorg/domain/ClubUser.kt`
- Modify: `shared/.../repository/ClubRepository.kt` (+ impl), and `TeamRepository`(+impl) if `updateRole`/`addMember`/`removeMember` are missing.
- Test: `shared/src/commonTest/kotlin/ch/teamorg/...` (extend a fake/repo test if present)

**Interfaces:**
- Produces (shared):
  - `ClubRepository.listClubUsers(clubId: String, limit: Int, offset: Int): Result<List<ClubUser>>` → `GET /clubs/$clubId/users?limit&offset`
  - `TeamRepository.addMember(teamId: String, userId: String, role: String): Result<Unit>` → `POST /teams/$teamId/members`
  - `TeamRepository.updateRole(teamId: String, userId: String, role: String): Result<Unit>` → `PATCH /teams/$teamId/members/$userId/role` (add if missing)
  - reuse existing `TeamRepository.removeMember`
  - `NdsRepository.linkMember(teamId: String, memberId: String, userId: String): Result<Unit>` → `POST /teams/$teamId/nds/members/$memberId/link` (add to the shared NDS repo, or to `TeamRepository` if no shared NDS repo exists)

- [ ] **Step 1: Add the model**

```kotlin
package ch.teamorg.domain
import kotlinx.serialization.Serializable

@Serializable data class TeamRoleRef(val teamId: String, val teamName: String, val role: String)
@Serializable data class ClubUser(
    val userId: String, val displayName: String, val email: String,
    val avatarUrl: String? = null, val teamRoles: List<TeamRoleRef> = emptyList()
)
```

- [ ] **Step 2: Add repository methods (mirror the shown Ktor pattern)**

In each impl, follow the existing `Result<>`-wrapped style from `TeamRepositoryImpl.kt`. Example for add member:

```kotlin
@Serializable private data class AddMemberRequest(val userId: String, val role: String)

override suspend fun addMember(teamId: String, userId: String, role: String): Result<Unit> = try {
    val r = client.post("/teams/$teamId/members") {
        contentType(ContentType.Application.Json); setBody(AddMemberRequest(userId, role))
    }
    if (r.status == HttpStatusCode.OK) Result.success(Unit)
    else Result.failure(Exception("addMember: ${r.status}"))
} catch (e: Exception) { Result.failure(e) }
```

Implement `listClubUsers`, `updateRole`, `linkMember` analogously with the exact endpoints above.

- [ ] **Step 3: Build + test**

Run: `./gradlew :shared:jvmTest`
Expected: PASS (extend fakes to satisfy new interface methods so existing tests compile).

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/ch/teamorg/domain/ClubUser.kt shared/src/commonMain/kotlin/ch/teamorg/repository shared/src/commonMain/kotlin/ch/teamorg/data/repository shared/src/commonTest
git commit -m "feat(shared): club-users + role-management repository methods"
```

---

### Task 9: Mobile ClubMembers screen + ViewModel + nav

**Files:**
- Create: `composeApp/.../ui/club/ClubMembersViewModel.kt`, `ClubMembersScreen.kt`
- Modify: `composeApp/.../navigation/Screen.kt`, `AppNavigation.kt`, `di/UiModule.kt` (register the VM)

**Interfaces:**
- Consumes: shared repo methods from Task 8.
- Produces: `Screen.ClubMembers(clubId: String)` route reachable for users who manage a club.

- [ ] **Step 1: Add the route**

In `Screen.kt`:

```kotlin
@Serializable
data class ClubMembers(val clubId: String) : Screen("club_members/{clubId}")
```

- [ ] **Step 2: ViewModel**

Create `ClubMembersViewModel` exposing a `StateFlow<UiState>` with `users: List<ClubUser>`, `teams: List<...>`, `loading`, `endReached`, plus `loadMore()`, `addToTeam(teamId,userId,role)`, `changeRole(...)`, `remove(...)`, `invite(...)`, `linkNds(...)`. `loadMore` calls `listClubUsers(clubId, PAGE, offset)` and appends; sets `endReached` when a short page returns. Mirror an existing list ViewModel (e.g. `EventListViewModel`) for structure, coroutine scope, and DI.

- [ ] **Step 3: Screen**

Create `ClubMembersScreen` with a `LazyColumn` over `users`, each row showing name/email + role chips and an overflow menu/bottom-sheet with the actions. Trigger `loadMore()` when the last item becomes visible (observe `LazyListState.layoutInfo`). Add an optional top filter `TextField` that narrows the already-loaded list (no minimum length). Reuse existing components/theme (`extendedColors`, `PillShape`, bottom-sheet patterns from `ui/attendance`).

- [ ] **Step 4: Wire nav + DI + entry point**

In `AppNavigation.kt` add the `composable` for `Screen.ClubMembers`. Register the VM in `UiModule.kt` (mirror existing VM registrations). Add an entry point reachable by club managers (e.g. a button on the Teams/Profile screen gated on the user managing ≥1 club).

- [ ] **Step 5: Build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/ch/teamorg/ui/club composeApp/src/commonMain/kotlin/ch/teamorg/navigation composeApp/src/commonMain/kotlin/ch/teamorg/di/UiModule.kt
git commit -m "feat(mobile): club-members management screen with lazy loading"
```

---

## Final verification

- [ ] **Server/shared/mobile:** `./gradlew check` green.
- [ ] **Web:** `cd admin && npm run check` clean.
- [ ] Manual smoke (web): open `/manage/{clubId}/members`, list shows immediately, scroll loads more; add Bob to a team as coach; change his role; remove him; invite a new email; from a team with unclaimed NDS members, link an existing account to a player and confirm it becomes claimed.
- [ ] Manual smoke (mobile): open the ClubMembers screen as a club manager; same operations work.

---

## Self-review notes (coverage vs. spec #5 + decisions)

- List club users (sorted, paginated, no-search-to-see) → Task 1 (server) + Tasks 4/6 (web) + Tasks 8/9 (mobile).
- Add existing user to team → Task 2 + web Task 6 + mobile Task 9.
- Change/remove role → existing endpoints, surfaced in web Task 6 + mobile Task 9.
- Link account → imported player → Task 3 (server) + web Task 7 + mobile Tasks 8/9.
- Invite brand-new by email → existing endpoint, surfaced in web Task 6 + mobile Task 9.
- All new endpoints club_manager-guarded and club-scoped (no-IDOR). Provisional NDS placeholder users excluded from the club-users list (Task 1).
- Web placement = dedicated `/manage/{clubId}/members` screen (per the approved spec), with the NDS link control also on team-detail (Task 7) where unclaimed members live.
