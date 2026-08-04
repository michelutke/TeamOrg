# NDS Member Merge — Design

Date: 2026-08-04

## Problem

A user who joins a team via a **generic** invite (reusable link or 8-char short code) is never
connected to an already-imported NDS roster member. Generic invites carry no `nds_member_id`, so
`POST /invites/{token}/redeem` only inserts a `team_roles` row. Result: the real account sits
beside its own provisional placeholder — the same person appears twice, their imported attendance
history stays on the placeholder, and the NDS export is still keyed to the placeholder.

Per-member invites (`POST /teams/{teamId}/nds/members/{id}/invite`) and the import wizard's `map`
decision already avoid this. Only the generic-join path leaves a duplicate, and today there is no
way for a coach to notice or repair it.

The repair *mechanics* largely exist: `POST /teams/{teamId}/nds/members/{id}/link` +
`NdsRepository.claimMember` move attendance off the placeholder, drop its team role, repoint
`nds_members.user_id`, and delete the orphan. What is missing is discovery, a confirm step, and
three correctness gaps in the existing code.

Secondary problem surfaced while scoping: provisional placeholders are visible to ordinary team
members. `GET /teams/{teamId}/members` is player-accessible (`TeamRoutes.kt:148`) and
`TeamRepositoryImpl.listMembers` does not filter on `users.provisional`.

## Goals

- A coach can merge a generically-joined account into its imported roster member, from the team page.
- Duplicates are surfaced proactively — the coach does not have to notice them.
- The merge carries over everything the placeholder holds; nothing is silently lost.
- Provisional placeholders are visible only to coaches and club managers, and clearly tagged.

## Non-goals

- Letting the joining user pick their own roster row at registration time (identity-claim risk).
- Undo / soft-delete of a merge.
- Adding `birth_date` to `users`. Matching stays name-based; the coach is the authority.
- Backfilling `nds_member_id` onto existing generic invites.

## Design

### 1. Merge mechanics — extend `claimMember`

`NdsRepository.claimMember` (`NdsRepository.kt:336`) currently moves only `attendance_responses`.
Deleting the placeholder then CASCADEs two live tables. Add both moves inside the existing
transaction, **before** the placeholder delete:

- **`sub_group_members`** — PK `(sub_group_id, user_id)`. Same collision-skip shape as the existing
  `moveAttendance`: delete placeholder rows whose `sub_group_id` the real user already holds, then
  repoint the remainder. Scope to sub_groups of this `team_id` (defensive — a placeholder cannot be
  in another team's subgroup).
- **`abwesenheit_rules`** — no team scope, no uniqueness constraint (`V8__create_attendance.sql:49`).
  Plain `UPDATE abwesenheit_rules SET user_id = <real> WHERE user_id = <provisional>`. No collision
  logic needed. Order matters: if the placeholder is deleted first, CASCADE drops the rules and
  `attendance_responses.abwesenheit_rule_id` silently becomes NULL.

Refactor `moveAttendance` into `moveRows(table, keyCol, userCol, from, to)` for the keyed tables;
`abwesenheit_rules` uses a simpler unkeyed update.

`attendance_records` is intentionally **not** moved — no code reads or writes that table; it is dead
since the unified-attendance work made `attendance_responses` the model.

### 2. Merge endpoint — harden the existing `/link`

Keep one endpoint (`POST /teams/{teamId}/nds/members/{id}/link`, `NdsRoutes.kt:533`) rather than
adding a parallel `/merge`, so the import-mapping, invite-redeem, and manual-merge paths share one
code path. Add the guards it currently lacks:

- **409** if the target user is already linked to a *different* `nds_members` row of this team.
  This rule already exists for the import path (`applyMappingSync`, `NdsRepository.kt:98`) — reuse
  `NdsMappingConflictException`.
- **400** if the target user is provisional (never merge two placeholders).
- **400** if the target user is not a member of the team's club (mirrors the import-path check at
  `NdsRoutes.kt:322`).

Existing coach/club_manager role guard and team-scoped member lookup stay as they are.

### 3. Suggestions endpoint

`GET /teams/{teamId}/nds/duplicate-suggestions` — coach / club_manager only.

For each `nds_members` row of the team **not** backed by a real account — covering both
`user_id IS NULL` and `user_id -> provisional user` (`claimMember:342` already handles the null
case) — run `NdsMemberMatcher.suggest` against the team's non-provisional members.

Response, one entry per unresolved roster row:

```
{
  memberId, lastName, firstName, birthDate, personNumber, funktion,
  candidates: [{ userId, displayName, score }],   // HIGH | MEDIUM, max 5
  willMove: { attendanceCount, subgroupCount, ruleCount }
}
```

`willMove` feeds the confirm dialog. Rows with no candidates are omitted.

`listTeamUsersForMatching` (`NdsRepository.kt:278`) must filter `users.provisional = false` —
unfiltered, placeholders match each other and every roster row suggests itself.

Matching accuracy is knowingly limited: that function derives first/last name by splitting
`displayName` on the first space, and `birthDate` is only populated for already-linked rows, so a
freshly-joined account matches on name alone. Accepted — the coach confirms every merge.

### 4. Hide provisional members from players

- `TeamMember` gains `provisional: Boolean = false`, in both `server/.../models/ClubTeam.kt:39` and
  `shared/.../domain/Club.kt:37`. The default keeps older mobile builds deserializing; the KMP client
  already sets `ignoreUnknownKeys = true` (`shared/.../network/HttpClientFactory.kt:20`).
- `TeamRepositoryImpl.listMembers(teamId, includeProvisional)`. `GET /teams/{teamId}/members`
  (`TeamRoutes.kt:148`) passes `includeProvisional = teamRepository.hasRole(caller, teamId, "coach",
  "club_manager")`. Players receive no placeholder rows at all.
- Coach / club_manager UI renders a "Provisorisch" tag on placeholder rows.
- **Attendance knock-on:** attendance responses are not name-joined server-side; clients resolve
  names from the members list. Once players stop receiving placeholders, placeholder attendance rows
  would become unresolvable userIds in player-facing attendance views. Attendance response lists are
  therefore filtered on the same rule: non-elevated callers do not receive responses belonging to
  provisional users. Coaches see everything.

### 5. UI — team page NDS section only

`admin/src/routes/(shell)/app/teams/[teamId]`, which already renders the NDS roster with the
Einladen / Verknüpfen actions:

- Banner when suggestions are non-empty: "N mögliche Duplikate — prüfen".
- Expanding shows, per suggestion, the roster row (name, birthdate, Personennummer) beside the
  candidate account (name, email), plus what will move.
- Confirm posts to the existing `linkNdsMember` action (`+page.server.ts:121`).
- No undo, no audit row. Once subgroups and absence rules move, a placeholder holds nothing unique.

No new route, and no duplicate affordance on `manage/[clubId]/members`.

### 6. Error handling

- Suggestions endpoint: role guard failure → existing `requireTeamRole` behaviour. Empty result is a
  200 with an empty list, not a 404.
- Merge conflicts return the German message shape already used by the import wizard, so the team page
  can surface them inline without new i18n plumbing.
- A merge that races another merge of the same roster row: the 409 double-link guard catches it;
  `claimMember` is idempotent when `provisionalUserId == realUserId` (`NdsRepository.kt:346`).

## Testing

Server (`NdsRoutesTest` already has the fixtures — imported team, `lara` placeholder, real accounts):

- `claimMember` moves `sub_group_members` and skips rows colliding with the real user's existing
  membership.
- `claimMember` moves `abwesenheit_rules`, and `attendance_responses.abwesenheit_rule_id` still
  points at the moved rule after the placeholder is deleted.
- `/link` → 409 when the target user is already linked to another roster row of the team.
- `/link` → 400 when the target user is provisional, and when they are not in the club.
- Suggestions excludes already-claimed rows, excludes provisional candidates, and includes
  `user_id IS NULL` rows.
- `GET /teams/{id}/members` hides placeholders from a player and returns them with
  `provisional = true` to a coach.
- Attendance response list omits provisional-user responses for a player caller, includes them for a
  coach.

Web (`admin`): suggestion banner renders only when suggestions exist; confirm submits the expected
`memberId` + `userId`.

## Affected files

- `server/.../domain/repositories/NdsRepository.kt` — `claimMember`, `moveRows`,
  `listTeamUsersForMatching`
- `server/.../routes/NdsRoutes.kt` — `/link` guards, new `/duplicate-suggestions`
- `server/.../domain/repositories/TeamRepositoryImpl.kt` + `routes/TeamRoutes.kt` — member visibility
- `server/.../routes/AttendanceRoutes.kt` / `AttendanceRepositoryImpl.kt` — response filtering
- `server/.../domain/models/ClubTeam.kt`, `shared/.../domain/Club.kt` — `provisional` field
- `admin/src/routes/(shell)/app/teams/[teamId]/` — banner, merge preview
- `docs/invite-flow-contract.md` — document that generic invites need a post-hoc merge

## Open risks

- Name-only matching will occasionally suggest the wrong person (twins, shared surnames). Mitigated
  by mandatory coach confirmation with both identities shown, not by the matcher.
- Hiding placeholders from players is a visible behaviour change for existing clubs mid-season:
  rosters will appear to shrink for players. Worth a release note.
