---
phase: 03
slug: event-scheduling
status: ready
source: pencil/design.md (git:53bea27) + .project/specs/event-scheduling/ux.md + 03-CONTEXT.md
gathered: 2026-03-19
---

# Phase 03 — UI Design Contract: Event Scheduling

> **Downstream agents:** Read `pencil/design.md` (restored at git commit `53bea27`) for token reference. Screens S1–S7 map to node IDs in `pencil/playbook.pen` (same commit).

---

## 1. Design Tokens

### Colours (M3 / TeamorgTheme)
| Token | Dark | Light | Usage |
|---|---|---|---|
| `background` | `#090912` | `#FFFFFF` | Screen bg |
| `surface` | `#13131F` | `#F4F4F9` | Headers, tab bars |
| `card` | `#1C1C2E` | `#FFFFFF` | List rows, cards |
| `foreground` | `#F0F0FF` | `#0A0A1A` | Primary text |
| `mutedForeground` | `#9090B0` | `#6B7280` | Labels, secondary text |
| `border` | `#2A2A40` | `#E2E2F0` | Dividers, strokes |
| `primary` | `#4F8EF7` | `#4F8EF7` | Buttons, active nav, links |
| `primaryForeground` | `#FFFFFF` | `#FFFFFF` | Text on primary |
| `accent` | `#F97316` | `#F97316` | Orange accent |
| `destructive` | `#EF4444` | `#EF4444` | Cancel, delete |
| `colorSuccess` | `#22C55E` | `#22C55E` | Confirmed / present |
| `colorWarning` | `#FACC15` | `#FACC15` | Unsure / pending |
| `colorError` | `#EF4444` | `#EF4444` | Declined / absent |
| `colorPurple` | `#A855F7` | `#A855F7` | Other event type |
| `colorGold` | `#FACC15` | `#FACC15` | ClubManager chip |
| `colorGrey` | `#6B7280` | `#6B7280` | Inactive / muted |

### Event Type Colours
| Type | Colour token | Colour value |
|---|---|---|
| `training` | `primary` | `#4F8EF7` (Blue) |
| `match` | `colorSuccess` | `#22C55E` (Green) |
| `other` | `colorPurple` | `#A855F7` (Purple) |

### Mobile Frame
- Screen size: **390 × 844** (iPhone 15 reference)
- Status bar height: **62dp**
- Bottom nav height: **62dp**, `cornerRadius: 36`

---

## 2. Component Library

Use existing components from `fJJQz` (dark) / `yAPEw` (light). Do **not** create duplicates.

| Component | Node ID | Usage in Phase 3 |
|---|---|---|
| Button/Primary | `QB89y` | Create/Save CTAs in forms |
| Button/Ghost | `lNVAJ` | Cancel, secondary actions |
| Button/Destructive | `ORRL8` | Cancel event confirm |
| FAB | `t2TjJ` | "+" create event (coach only) |
| Chip/Training | `yzjqZ` | Filter + type badge |
| Chip/Match | `0XrEE` | Filter + type badge |
| Chip/Other | `Dizpn` | Filter + type badge |
| Chip/Cancelled | `HDc0Q` | Cancelled event badge |
| Avatar/48 | `3Viyw` | Event detail coach info |
| Input/Text | `h74Ra` | Form fields (title, location, description) |
| Toggle/Row | `3g7Dw` | Recurring toggle, min-attendees toggle |
| SegmentedControl | `HuzeF` | Month/Week calendar switch; Training/Match/Other type selector |
| BottomSheet/Header | `sVemC` | Recurring pattern sheet, scope sheet |
| SectionHeader | `nhuyT` | Form section labels |
| BottomNav | `FTbAZ` | Existing — already wired for Events + Calendar |

---

## 3. Screens

### S1 — Event List (Pencil nodes: `S5I1w` Coach / `Lrlit` Player)

**Layout:** `Scaffold` with `TopAppBar` + `LazyColumn` + `FAB`

**Top bar:**
- Title: "Events"
- No back button (root tab)

**Filter row** (horizontal scroll, sticky below top bar):
- Team chips: one per joined team (tap = toggle filter); "All Teams" default
- Separator `|`
- Type chips: All / Training / Match / Other (single-select)
- Chips use `Chip/*` components; active = filled, inactive = ghost

**List item anatomy:**
```
[TypeIcon 24dp] [Title · bold]         [TeamBadge(s)]
               [Date · muted]   [RecurringIcon ⟳ if series]
```
- Type icon: Lucide `dumbbell` (training), `trophy` (match), `calendar` (other); colour = event type colour
- Date format: `"Fri, 6 Jun · 18:00"` (local time, no TZ label)
- Multi-day: `"Fri 6 Jun — Sun 8 Jun"`
- Multi-team badge: `"2 teams"` grey chip when `event_teams.length > 1`
- **Cancelled state:** entire row `alpha = 0.4`; append `Chip/Cancelled` after title
- Row tap → S3 Event Detail

**FAB:** `t2TjJ` — visible **only** if `userRole == Coach`; navigates to S4 Create

**Empty state:** centred icon + "No events yet" + "Create your first event" CTA (coach) or "No upcoming events" (player)

**Offline state:** `OfflineBanner` (`DMLmP`) pinned below top bar; list still populated from cache

---

### S2 — Calendar View (Pencil nodes: `vyGwO` Coach / `tON9m` Player)

**Layout:** `Scaffold` with `TopAppBar` + segmented control + calendar body

**Top bar:**
- `SegmentedControl` (`HuzeF`) centred: "Month" | "Week"
- Coach: `⋯` menu with "Create Event" shortcut

**Month View (kizitonwose HorizontalCalendar):**
- Each day cell: date number + coloured dots (max 3; `+N` if more)
- Dot colours = event type colours
- Selected day → slide-up `BottomSheet` with day's event list (same item format as S1)
- Today cell: `primary` underline on date number
- Cancelled events: grey dot only

**Week View (kizitonwose WeekCalendar):**
- 7-column time-block grid
- Each event block: coloured left border (type colour) + title (truncated 1 line) + time
- Multi-day events: span across day columns
- Tap block → S3 Event Detail
- Cancelled blocks: `alpha = 0.4` + strikethrough title

**FAB:** same rule as S1 (coach only)

---

### S3 — Event Detail (Pencil nodes: `2uF5C` dark / `NDxOi` light)

**Layout:** `Scaffold` with `TopAppBar` + `Column` scroll body

**Top bar:**
- Back arrow
- Event title (truncated)
- Coach only: `⋯` icon → dropdown: Edit / Duplicate / Cancel

**Cancelled banner** (if `status == cancelled`):
- Full-width `colorError` tinted strip: "This event has been cancelled"
- Shown at very top of body, below app bar

**Body sections (in order):**

1. **Header card:**
   - Type chip (`Chip/Training|Match|Other`)
   - Title — `headlineMedium`
   - Recurring indicator: `⟳` icon + "Recurring" label if `series_id != null`

2. **Time section:**
   - Start: `"Fri, 6 Jun 2026 · 18:00"` (local time)
   - End: same format (omit date if same day: `"· 20:00"`)
   - Multi-day: show start + end on separate lines with `—` connector
   - Meetup time (if set): `"Meetup: 17:30"` in `mutedForeground`
   - **No TZ label** on detail (CONTEXT decision)

3. **Location section** (if set):
   - Location text + "Open in Maps" `Button/Ghost`

4. **Team / Sub-group section:**
   - Team chip(s)
   - Sub-group chip (if `event_subgroups` not empty): `"⊂ Sub-group name"`
   - Multi-team: "2 teams" chip → tap → expand list

5. **Description** (if set):
   - Collapsible: show 3 lines, "Show more" if longer

6. **Min attendees** (if set):
   - `"Min. attendees: N"` in muted row

7. **Attendance placeholder card:**
   - `card` background, dashed border
   - Text: "Attendance — Coming soon"
   - Phase 4 will replace this card (do not remove the layout slot)

---

### S4 — Create / Edit Event Form (Pencil nodes: `gaD2C` create dark / `p6DNb` create light / `21Qtq` edit dark / `fhGRl` edit light)

**Layout:** `Scaffold` with `TopAppBar` + `Column` scroll + bottom sticky CTA bar

**Top bar:**
- Back arrow
- Title: "New Event" or "Edit Event"

**Form sections (sectioned `Column`, each headed by `SectionHeader` `nhuyT`):**

#### Section 1 — Basic
- **Title:** `Input/Text` (`h74Ra`) — required, placeholder "Event title"
- **Type:** `SegmentedControl` (`HuzeF`) — Training | Match | Other
- **Team(s):** multi-select chip group — user's teams; at least one required

#### Section 2 — Time
- **Start date:** date picker row — tapping opens `DatePickerDialog` (Material 3)
  - Design node: `mNOVF` (dark) / `20pZ0` (light) — date picker full-screen modal
- **Start time:** time picker row — tapping opens `TimePickerDialog`
  - Design node: `EBbYR` (dark) / `6E3xt` (light)
- **TZ label:** `"CET"` (or local abbrev) shown inline next to time — informational, no picker
- **End date/time:** same pickers; default = start date + 90 minutes
- **Meetup time** (optional): `Toggle/Row` to reveal time picker

#### Section 3 — Location (optional)
- `Input/Text` — placeholder "Venue or address"

#### Section 4 — Audience
- **Sub-group filter:** shown only if team is selected
  - Default: "Whole team" (no restriction)
  - Multi-select chip list of team's sub-groups
  - Design node: `g2gXC` (dark) / `yBJPo` (light)

#### Section 5 — Options
- **Min attendees:** `Toggle/Row` + number field (shown when toggle on)
- **Description:** `Input/Text` multiline, optional

#### Section 6 — Recurring
- `Toggle/Row` (`3g7Dw`) labelled "Recurring event"
- When on → opens S5 bottom sheet
- When S5 confirmed: shows summary chip below toggle (e.g. "Weekly on Mon, Wed · Until 1 Aug")
- Tap summary chip → re-opens S5 to edit

**Sticky CTA bar** (bottom, above nav):
- Primary button: "Create Event" / "Save Changes"
- Ghost button: "Cancel"

**Validation:**
- Title empty → inline error under field
- End time before start time → inline error under end time
- No team selected → inline error under team field
- Save blocked until valid

---

### S5 — Recurring Pattern Sheet (Pencil nodes: `2Ub2q` dark / `0bGW1` light)

**Triggered by:** Recurring toggle in S4. Full-height `ModalBottomSheet` with `BottomSheet/Header` (`sVemC`).

**Header:** "Recurring pattern" + drag handle

**Content:**
1. **Repeat type:** Radio group or segmented — Daily | Weekly | Custom interval
2. **[Weekly only] Weekday grid:** 7 chips Mon–Sun, multi-select
3. **[Custom] Interval field:** number input + "days" label
4. **End date:** `Toggle/Row` "End date" → date picker when toggled
   - Default: no end (indefinite)

**Footer:**
- "Done" `Button/Primary` → closes sheet, writes summary to S4
- "Cancel" `Button/Ghost`

---

### S6 — Edit Recurring Scope Sheet (Pencil nodes: `raffO` dark / `Cxc0M` light)

**Triggered by:** Edit or Cancel on a recurring event. Full-height `ModalBottomSheet`.

**Header:** "Edit recurring event" (or "Cancel recurring event") + drag handle

**Radio options:**
1. "This event only"
2. "This and future events"
3. "All events in series"

**Footer:**
- "Continue" `Button/Primary` → dismisses, proceeds to edit form or cancel confirm
- "Cancel" `Button/Ghost`

---

### S7 — Sub-group Management (Pencil node not in scope for Phase 3 screens — derive from team settings pattern)

> Phase 3 exposes sub-group targeting in event forms (S4 Section 4). Full sub-group CRUD (create/edit/delete) lives in Team Settings — use existing `TeamRosterScreen` list pattern.

**Sub-group list item:**
- Group name + member count chip
- Swipe left: delete (with confirm dialog if events target this group)
- Tap: edit sheet (name field + player multi-select)

**Create flow:** FAB `+` → bottom sheet with name field + player multi-select → Save

---

## 4. Interaction Patterns

### Offline Banner
- `OfflineBanner` (`DMLmP`) pinned below top bar in S1 and S2 when no network
- List still shows cached data (3-month window)
- Empty + offline → "No cached events" message (not error)

### Cancelled Event Visuals
| Screen | Treatment |
|---|---|
| S1 list row | `alpha = 0.4` + `Chip/Cancelled` |
| S2 month dot | Grey dot |
| S2 week block | `alpha = 0.4` + strikethrough title |
| S3 detail | Full-width error banner at top |

### Recurring Indicator
- `⟳` (Lucide `repeat`) icon shown on S1 rows and S2 blocks when `series_id != null`
- Always shows scope sheet (S6) before opening edit form for recurring events

### Role-based Visibility
- FAB "+" → coaches only (`userRole == Coach` in ViewModel state)
- "⋯" menu on detail (Edit / Duplicate / Cancel) → coaches only
- Players see read-only detail

### Navigation Transitions
- List → Detail: standard push slide
- Detail → Edit form: push slide
- Bottom sheets (S5, S6): slide-up modal, dismiss on drag down or Cancel tap

---

## 5. Screen Mapping to Navigation Graph

| Screen | Route (`Screen.*`) | Stack behaviour |
|---|---|---|
| S1 Event List | `Screen.Events` | Root tab (replaces `PlaceholderScreen` line 88) |
| S2 Calendar | `Screen.Calendar` | Root tab (replaces `PlaceholderScreen` line 89) |
| S3 Event Detail | `Screen.EventDetail(id)` | Push onto Events or Calendar |
| S4 Create Event | `Screen.CreateEvent` | Push from FAB |
| S4 Edit Event | `Screen.EditEvent(id)` | Push from detail ⋯ menu |
| S6 Scope Sheet | Composable inside EditEvent | Modal overlay |
| S5 Recurring Sheet | Composable inside CreateEvent/EditEvent | Modal overlay |

---

## 6. Pencil Node Reference (Restoration)

The `pencil/playbook.pen` file was deleted in commit `462fe66` (project migration). Original design is at commit `53bea27`. Key nodes for Phase 3:

| Node ID | Screen |
|---|---|
| `S5I1w` | V1-C — Event List (dark) |
| `Lrlit` | V1-P — Event List (dark) |
| `iRz5V` | V1-C — Event List (light) |
| `UhlZ9` | V1-P — Event List (light) |
| `vyGwO` | V1-C — Event Calendar (dark) |
| `tON9m` | V1-P — Event Calendar (dark) |
| `8RVYu` | V1-C — Event Calendar (light) |
| `JRE44` | V1-P — Event Calendar (light) |
| `gaD2C` | V1 — New Event (dark) |
| `p6DNb` | V1 — New Event (light) |
| `21Qtq` | V1 — Event Edit (dark) |
| `fhGRl` | V1 — Event Edit (light) |
| `2uF5C` | V1 — Event Detail (dark) |
| `NDxOi` | V1 — Event Detail (light) |
| `mNOVF` | V1 — NE-Date Picker (dark) |
| `EBbYR` | V1 — NE-Time Picker (dark) |
| `2Ub2q` | V1 — NE-Recurring Config (dark) |
| `g2gXC` | V1 — NE-Sub-groups (dark) |
| `raffO` | V1 — EE-Modal recurring (dark) |

Restore command: `git show 53bea27:pencil/playbook.pen > pencil/playbook.pen`

---

*Phase: 03-event-scheduling*
*UI-SPEC gathered: 2026-03-19 from git history (53bea27) + ux.md + context.md*
