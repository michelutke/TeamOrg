# Mobile NDS Duplicate Merge — Design

Date: 2026-08-05

Follows `2026-08-04-nds-member-merge-design.md`, which delivered the server behaviour and the **web**
coach UI. This spec closes the three mobile gaps that review left open.

## Problem

The server side of the duplicate merge is complete and applies to every client: the lossless
`claimMember`, the hardened `/link` guards, placeholder hiding, and attendance filtering all work on
mobile with no client change. The *coach-facing* half shipped for web only, leaving three gaps:

1. **No duplicate discovery.** Nothing in `shared/` or `composeApp/` calls
   `GET /teams/{teamId}/nds/duplicate-suggestions`, so a coach using the app is never told a duplicate
   exists.
2. **No placeholder tag.** `TeamMember` now carries `provisional`, but no mobile screen renders it, so a
   coach sees import placeholders in the roster with nothing distinguishing them from real teammates.
3. **Unusable link errors.** `TeamRepositoryImpl.linkNdsMember` collapses every non-2xx into
   `Exception("linkNdsMember: $status")`, and `ClubMembersViewModel.linkNds` surfaces `e.message`
   verbatim — a coach would see `linkNdsMember: 409 Conflict`.

Gap 3 needs reframing. `ClubMembersViewModel.linkNds` has **no UI caller anywhere in `composeApp`** —
verified by grep; mobile's only NDS surface is the import flow reached from `TeamRosterScreen`. So the
raw error string is currently unreachable dead code, and fixing the message alone would polish something
no user can trigger. Gap 3 only becomes real once gap 1 gives merging a UI. The three gaps are therefore
one feature, not three fixes.

## Goals

- A coach or club manager using the mobile app is shown duplicate roster rows and can merge them.
- Import placeholders are visibly marked as provisional on mobile.
- Merge failures produce sentences a coach can act on.

## Non-goals

- Any change to server behaviour. The endpoints are done and tested.
- An iOS-specific SwiftUI surface. `composeApp` is shared UI; iOS gets this through the same Compose
  Multiplatform screens.
- Reproducing the web's per-row "Einladen / Konto verknüpfen" affordances on mobile. Mobile gets
  discovery-driven merging only; the web page remains the full-control surface.
- Localisation infrastructure. See the language decision below.

## Language decision

**Mobile copy is English.** `composeApp` has no i18n mechanism — strings are hardcoded, and existing
copy is English ("Club Members", "No members found", "Add to team"), unlike the German admin web app.
The earlier review item called this "German link errors" by analogy with the web; following that
literally would put German sentences into an otherwise English UI. New strings are therefore English,
including the tag, which is "Provisional" and not "Provisorisch".

## Design

### 1. Shared — suggestion models

Add to `shared/src/commonMain/kotlin/ch/teamorg/domain/NdsImport.kt` (where the other NDS models live),
mirroring the server's wire shape exactly (`MovableCounts` / `DuplicateSuggestion` in
`server/.../domain/models/Nds.kt`):

```kotlin
@Serializable
data class MovableCounts(val attendance: Int = 0, val subgroups: Int = 0, val rules: Int = 0)

@Serializable
data class DuplicateCandidate(val userId: String, val displayName: String, val score: String)

@Serializable
data class DuplicateSuggestion(
    val memberId: String,
    val lastName: String,
    val firstName: String,
    val birthDate: String? = null,
    val personNumber: String? = null,
    val funktion: String,
    val candidates: List<DuplicateCandidate> = emptyList(),
    val willMove: MovableCounts = MovableCounts()
)
```

Defaults on every optional field, because the KMP client sets `ignoreUnknownKeys = true` and must
tolerate server shape drift.

### 2. Shared — typed link result

`linkNdsMember` currently returns `Result<Unit>` and throws away the status. Replace it with a sealed
result, following the established `RedeemResult` pattern (`shared/.../domain/RedeemResult.kt`, consumed
by `InviteViewModel` — repository returns the sealed type directly, no `Result` wrapper, and the
ViewModel maps it to copy):

```kotlin
sealed interface LinkMemberResult {
    data object Success : LinkMemberResult
    /** 409 — the account already holds another roster row, or this row already has a real account. */
    data object Conflict : LinkMemberResult
    /** 400 — the account cannot be linked (e.g. it is itself a placeholder). */
    data object NotLinkable : LinkMemberResult
    data class Error(val message: String) : LinkMemberResult
}
```

`TeamRepository.linkNdsMember(teamId, memberId, userId): LinkMemberResult`, and
`getDuplicateSuggestions(teamId): Result<List<DuplicateSuggestion>>` — `Result` for the read, since a
failed fetch is not a domain outcome, only an absence.

The server returns 409 for two distinct causes (target already linked elsewhere; this row already
claimed by a real account). Mobile collapses both into `Conflict` with one message, because the coach's
next action is identical either way: pick a different account, or fix it on the web.

Changing this signature touches three test doubles that must be updated in step:
`composeApp/src/commonTest/.../fake/FakeTeamRepository.kt`,
`composeApp/src/androidUnitTest/.../events/EventScreenTestFakes.kt`, and the inline fake in
`composeApp/src/commonTest/.../events/EventListViewModelTest.kt`.

### 3. Mobile — discovery and merge on the roster screen

`TeamRosterScreen` is the right home: it already renders the `TeamMember` list and already owns the NDS
import entry point (`btn_nds_import`). No new route.

`TeamRosterViewModel` already computes `isCoachOrManager` — reuse it as the gate rather than inferring
elevation again. `TeamRosterState` gains:

```kotlin
val duplicates: List<DuplicateSuggestion> = emptyList(),
val showDuplicatesSheet: Boolean = false,
val mergeInProgress: Boolean = false,
val mergeError: String? = null,
val mergedCount: Int = 0
```

Suggestions load only when `isCoachOrManager` is true, and a failed fetch leaves `duplicates` empty
without surfacing an error — discovery is an enhancement, not a function the screen depends on.

UI: a banner above the roster when `duplicates` is non-empty — "N possible duplicate(s) — review" —
opening a bottom sheet (the screen already uses bottom sheets for edit-team and subgroups, so this
matches). Each suggestion is one card showing the roster identity (name, birthdate, Personennummer,
funktion), the candidate accounts as selectable rows with HIGH marked "very likely", a line stating what
will move ("Moves 12 attendances, 1 group, 0 absence rules"), a warning that the provisional account is
deleted and the merge cannot be undone, and a Merge button.

On success: refresh the roster and the suggestions, and increment `mergedCount` so the sheet can confirm
the merge happened. On `Conflict` / `NotLinkable` / `Error`: set `mergeError` to the mapped sentence and
leave the sheet open so the coach can pick another account.

Error copy (English, mapped in the ViewModel, never a raw exception):
- `Conflict` → "This account is already linked to another member of this team."
- `NotLinkable` → "This account can't be linked."
- `Error` → "Couldn't merge. Please try again."

### 4. Mobile — the Provisional tag

Render a small "Provisional" chip on roster rows where `member.provisional` is true, styled like the
existing role chips on that screen. Only coaches and club managers ever receive those rows from the
server, so no client-side role check is needed — but the chip must not be conditioned on
`isCoachOrManager` either, since doing so would hide a tag on a row that only an elevated caller can
see anyway, adding a branch with no behavioural difference.

### 5. Remove the dead link path

Delete `ClubMembersViewModel.linkNds`. It has no UI caller and no test coverage (verified by grep), and
leaving it would give mobile two merge code paths — one of them unreachable, untested, and now
signature-incompatible with the typed result. `ClubMembersScreen` is untouched.

## Testing

`composeApp` tests use Kotest matchers with `kotlin.test` annotations, `UnconfinedTestDispatcher`, and
hand-written fakes under `commonTest/kotlin/ch/teamorg/fake/` — follow that, TDD, test first.

`TeamRosterViewModelTest` gains:
- suggestions are fetched and exposed when the caller is coach/manager
- suggestions are NOT fetched when the caller is a plain player
- a failed suggestions fetch leaves `duplicates` empty and `error` null
- a successful merge clears that suggestion from state and refreshes the roster
- `Conflict` maps to the already-linked sentence, and the sheet stays open
- `NotLinkable` maps to the can't-be-linked sentence

`FakeTeamRepository` gains a settable `duplicateSuggestionsResult` and `linkNdsMemberResult:
LinkMemberResult`, plus recording of the `(teamId, memberId, userId)` it was called with so a test can
assert the right member was merged.

No new Compose UI test infrastructure: `composeApp` has Robolectric/Compose tests under
`androidUnitTest` for some screens, but the logic worth testing here is all in the ViewModel. If adding
the banner to `TeamRosterScreen` proves straightforward to assert with the existing Compose test setup
in that source set, a single "banner appears when duplicates exist" assertion is welcome; it is not
required.

Verification gates: `./gradlew :composeApp:compileDebugKotlinAndroid`,
`./gradlew :shared:compileKotlinIosSimulatorArm64` (both currently pass and must keep passing), and the
`composeApp` unit tests. The server suite is untouched by this work but must stay at its known 4
pre-existing failures.

## Affected files

- `shared/src/commonMain/kotlin/ch/teamorg/domain/NdsImport.kt` — suggestion models
- `shared/src/commonMain/kotlin/ch/teamorg/domain/LinkMemberResult.kt` — new sealed result
- `shared/src/commonMain/kotlin/ch/teamorg/repository/TeamRepository.kt` — two signatures
- `shared/src/commonMain/kotlin/ch/teamorg/data/repository/TeamRepositoryImpl.kt` — implementations
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterViewModel.kt` — state, load, merge
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/team/TeamRosterScreen.kt` — banner, sheet, chip
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/club/ClubMembersViewModel.kt` — delete `linkNds`
- `composeApp/src/commonTest/kotlin/ch/teamorg/fake/FakeTeamRepository.kt` — fake
- `composeApp/src/commonTest/kotlin/ch/teamorg/ui/team/TeamRosterViewModelTest.kt` — tests
- `composeApp/src/androidUnitTest/kotlin/ch/teamorg/ui/events/EventScreenTestFakes.kt` — signature
- `composeApp/src/commonTest/kotlin/ch/teamorg/ui/events/EventListViewModelTest.kt` — signature

## Risks

- The mobile merge is as irreversible as the web one, on a smaller screen where mis-taps are likelier.
  Mitigated by requiring an explicit candidate selection (no pre-selected default that a single stray
  tap could confirm) plus the stated warning.
- Name-only matching is inherited from the server and is knowingly imperfect; the coach confirms every
  merge, and the roster identity is shown beside each candidate so the decision is informed.
