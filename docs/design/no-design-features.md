# Features Without Figma Design — M3 Expressive Redesign

Implemented with M3 Expressive component library styling, no dedicated Figma reference.
**Review each item** — confirm styling or request a design.

## App — Auth & Onboarding

| Feature | File | Treatment |
|---|---|---|
| Password visibility toggle | `ui/login/LoginScreen.kt`, `ui/register/RegisterScreen.kt` | Material Visibility icon as field trailingIcon |
| Loading spinners in CTA buttons | all auth screens | CircularProgressIndicator inside pill button |
| Error/info snackbars | Login, Register, EmptyState | M3 snackbar, error container colors |
| "Join a team" paste-link field + Join button | `ui/emptystate/EmptyStateScreen.kt` | Tonal button + filled field in surfaceContainerLow card (Figma shows only a "Redeem an invite" button) |
| "Let a coach add you" profile-link share card | `ui/emptystate/EmptyStateScreen.kt` | surfaceContainerLow card, primary copy icon |
| Empty-state illustration | `ui/emptystate/EmptyStateScreen.kt` | Groups icon in primaryContainer circle (Figma bitmap not exported) |
| Club logo picker | `ui/club/ClubSetupScreen.kt` | AddAPhoto circle, primaryContainer (not in Figma; Figma's "Manager email" field NOT added — no backing state) |
| Invite logged-out variant, retry-on-error, invited-by/role chip | `ui/invite/InviteScreen.kt` | M3 pills; role chip substitutes Figma expiry chip (no expiry in state) |

## App — Events & Calendar

| Feature | File | Treatment |
|---|---|---|
| Reminder lead-time row | `ui/events/EventDetailScreen.kt` | Tonal list row, primary alarm icon |
| "Remind X members" CTA | — | **NOT implemented** — designed in Figma but no remind logic/callback exists in ViewModel; needs feature work |
| Attendance summary bar ("12/16 responded") | — | **NOT implemented** — no aggregate count exposed; grouped list conveys data |
| Recurring toggle interaction | `ui/events/CreateEditEventScreen.kt` | Kept switch (Figma shows chevron row); summary text matches Figma format |
| Sub-groups sheet, min-attendees stepper | `ui/events/CreateEditEventScreen.kt` | M3 pill list / tonal stepper card |
| List/Calendar segmented toggle, week grouping | `ui/events/EventListScreen.kt` | M3 segmented buttons kept; Figma week-section headers omitted (needs list-grouping logic) |

## App — Attendance & Absences

| Feature | File | Treatment |
|---|---|---|
| Weekday selector (recurring absence) | `ui/attendance/WeekdaySelector.kt` | Primary-filled circles (Figma Add Absence shows only period dates) |
| Attendance % progress bar | `ui/attendance/AttendanceStatsBar.kt` | M3 primary progress on tonal track |
| Edit-absence mode, date-picker dialog, sheet close buttons | `AddAbsenceSheet.kt`, `CoachOverrideSheet.kt` | Kept, themed |
| Active/Ended badge on absence card | `ui/attendance/AbsenceCard.kt` | goingContainer pill (Figma shows delete icon instead) |

## App — Teams & Profiles

| Feature | File | Treatment |
|---|---|---|
| Member long-press actions (promote/remove dialogs) | `ui/team/TeamRosterScreen.kt` | Radius-28 dialogs, pill buttons |
| Edit Club / Edit Team / Create Team sheets | `TeamsListScreen.kt`, `TeamEditSheet.kt` | M3 sheets, top radius 32 |
| Coach-editable jersey/position card + text dialogs | `ui/team/PlayerProfileScreen.kt` | surfaceContainerLow card, M3 dialogs |
| Sub-groups as bottom sheet | `ui/team/SubGroupSheet.kt` | Figma 60822 is a full screen; kept sheet navigation, applied screen styling. Avatar stacks omitted (model has only memberCount) |
| Player Profile "Message"/"Call" chips, recent events, contact card | — | **NOT implemented** — Figma 60820 designs features that don't exist (no contact/messaging data) |
| Profile tab "Edit profile", my-teams rows, sign-out rows | — | **NOT implemented** in PlayerProfileScreen — Figma 60825 designs rows whose features live elsewhere / don't exist |
| Role pill on team cards, "View all 16 members" link | — | Omitted — no per-team role in state; roster already shows all |

## App — Inbox & Profile

| Feature | File | Treatment |
|---|---|---|
| Delete-all confirmation dialog | `ui/inbox/InboxScreen.kt` | extraLarge dialog, error confirm |
| Load-failure error state | `ui/inbox/InboxScreen.kt` | Tonal card |
| Team picker chips | `ui/inbox/NotificationSettingsScreen.kt` | Pill FilterChips |
| Custom hours/minutes reminder input, "No reminder" option | `ui/inbox/ReminderPickerSheet.kt` | M3 OutlinedTextField / TextButton |
| PlaceholderScreen | `ui/placeholder/PlaceholderScreen.kt` | System States empty-state card pattern |

## Web Admin

| Feature | File | Treatment |
|---|---|---|
| Team detail page (info/edit, invite links, members, archive/remove) | `admin/src/routes/admin/clubs/[clubId]/teams/[teamId]/+page.svelte` | Full page restyled with system (cards, pills, chips) — no Figma node |
| Create-club inline form | `clubs/+page.svelte` | Card with M3 filled fields (Figma shows button only) |
| Create-team inline form | `clubs/[clubId]/teams/+page.svelte` | Same |
| Club edit / add-manager forms, reactivate flow, confirmation modals | `clubs/[clubId]/+page.svelte` | Cards / 28px-radius dialogs |
| Breadcrumb + impersonation tabs | `clubs/[clubId]/+layout.svelte` | Styled per Club Detail design |
| Clubs search field (in Figma) | — | **NOT implemented** — no backend search on route; would be logic change |

## Global deviations

- **Fonts:** Google Sans Flex / Roboto Flex not bundled in Compose app (system fonts, correct sizes/weights). Admin web uses Roboto Flex from Google Fonts; `font-display` falls back from Google Sans Flex.
- **Dark mode:** removed — design is light-only per Figma.
- **Floating bottom nav:** custom Compose implementation (pill, spring label expand) instead of `HorizontalFloatingToolbar` (not in current CMP material3 version).
