# Material 3 Expressive Redesign — Figma Documentation

**Figma file:** [TeamOrg](https://www.figma.com/design/iKcGJfgxUxMi2AnE9o4BAL/TeamOrg?node-id=60797-12752) · Page "Team Org" · Section "TeamOrg Redesign"
**Date:** 2026-06-12
**Scope:** Complete redesign of all app + web admin screens using Material 3 Expressive.

## Design references

- **Design system:** Material 3 Design Kit (community library, M3 Expressive components)
- **Navigation inspiration:** [Zenith](https://github.com/1372Slash/Zenith) floating bottom toolbar
  (`HorizontalFloatingToolbar`, pill shape `RoundedCornerShape(100)`, height 68dp, bottom offset 36dp,
  `primaryContainer` background; selected item = filled primary pill with icon + label, unselected = icon only,
  spring/bouncy expand animation — see Zenith `MainScreen.kt`)

## Theme

- M3 baseline light palette (primary `#6750A4`, primaryContainer `#EADDFF`, surface `#FEF7FF`,
  surfaceContainerLow `#F7F2FA`, surfaceContainerHigh `#ECE6F0`, error `#B3261E`, tertiary `#7D5260`).
- Expressive character via: full-round pills, large corner radii (16–32), tonal containers,
  ExtraBold display type, floating nav.
- Dark mode: not yet produced (light only).

## Typography library (local Figma text styles)

All 800+ text nodes reference these styles. Body/labels: **Roboto Flex**.

| Style | Size / Weight |
|---|---|
| Display/Large · Small | 45 / 36 ExtraBold |
| Headline/Large · Medium · Small | 34 / 30 / 26 ExtraBold |
| Title/Large · Medium · Small (+Emphasized) | 22 Bold / 17 Bold / 15 Medium (15 Bold) |
| Body/Large · Medium · Small · XSmall | 16 / 14 / 13 / 12 Regular |
| Label/Large · Medium · Small · XSmall · Tiny (+Emphasized) | 14 / 13 / 12 / 11 / 10 Medium (Bold) |

**Pending manual step:** switch `Display/*`, `Headline/*`, `Title/Large` to **Google Sans Flex**
in the local styles panel (font is local-only; not loadable via plugin API).

## Components

- `TeamOrg/Floating Bottom Nav` — component set, 5 variants (`Selected=Events|Calendar|Teams|Inbox|Profile`),
  Zenith-style; instanced on all main app screens.
- Icons: Material Symbols SVG vectors (no emojis). Response glyphs ✓/✗/? are typographic, intentional.

## Screens (30)

### App (412×917) — zones 01–05 in the section

| Zone | Screens |
|---|---|
| 01 Auth & Onboarding | Login, Register, Empty State, Club Setup, Invite Redemption |
| 02 Events & Calendar | Events List, Calendar, Event Detail, Event Detail (Coach), Create Event, Sheet–Recurring Pattern, Sheet–Edit Scope |
| 03 Attendance & Absences | Sheet–Begründung (Unsure), Sheet–Coach Override, Sheet–Add Absence, My Absences, Sheet–Injury Location |
| 04 Teams & Profiles | Teams, Team Roster, Player Profile, Sheet–Invite Members, Subgroups |
| 05 Inbox & Profile | Inbox, Notification Settings, Profile, System States (snackbars / offline / empty / skeleton) |

### Web Super Admin (1440×1024) — zone 06

Admin Login, Dashboard, Clubs, Club Detail (managers, teams, danger zone),
Users (search + impersonate), Audit Log (filters, immutable), Impersonation — Club Manager View
(tertiary banner, countdown timer, end-session).

## Key UX decisions

- **Event info as structured icon rows/chips** (date, time, meetup, location) instead of plain text —
  player hero uses tile grid, coach hero uses compact chips; event cards show time range + meetup + location.
- **Coach attendance:** members grouped & sorted by response (Going → Unsure → Declined → No response),
  per-row mini ✓/✗/? controls (30px), auto-decline annotations, "Remind X members" CTA.
- **Status colors:** going/present green `#2E7D32`, declined/absent error red, unsure/excused amber `#7A5C00`,
  each with matching containers.
- **No default white fills** on layout containers — intentional whites only on sheets, login card,
  screen backgrounds, stroked elements.

## Implementation notes for dev handoff

- Floating nav replaces the previous fixed pill bottom bar; Compose equivalent is
  `HorizontalFloatingToolbar` + `ShortNavigationBarItem` (see Zenith reference above).
- Bottom sheets: top radius 32, drag handle, full-width primary CTA pill.
- Text styles map 1:1 to a Compose/Tailwind type scale; consume the Figma styles, not raw sizes.
- Admin web adapts M3E to desktop density: 260px sidebar with pill nav items, 24px-radius table cards,
  pill pagination, badge chips for status/actions.
