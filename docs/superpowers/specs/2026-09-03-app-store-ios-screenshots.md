# App Store screenshots (iPhone) — teamorg

Date: 2026-09-03 · Bundle `ch.teamorg` · Figma section `61446:6224` ("App Store Assets — iOS")
in file `iKcGJfgxUxMi2AnE9o4BAL`, page "Team Org".

Companion to `2026-08-04-play-store-listing-design.md`, which defines the same system for
Google Play. Read that spec first — brand rules, colour/type sources and the shared
screenshot anatomy are defined there and only the deltas are repeated here.

## Scope

Six iPhone screenshot frames at 1290×2796, German captions, exported to
`brand/store/ios/`. Out of scope: iPad screenshots, App Preview video, iOS text
metadata (title/subtitle/keywords/description), the EN caption set.

## Size

App Store Connect requires one 6.9" iPhone set; it downscales that set for every smaller
iPhone. Accepted 6.9" portrait sizes are 1320×2868 and **1290×2796** — this set uses the
latter. No 6.5"/5.5" upload is needed.

## Frames

Section `61446:6224` at canvas (18659, 5676), 4370×6392, three columns × two rows,
200 px gutters.

| # | Frame name | Node | DE caption | Source screen |
|---|---|---|---|---|
| 1 | `ios-phone-01-events` | `61446:6225` | Alle Termine im Blick | `App / Events List` `60805:71` |
| 2 | `ios-phone-02-attendance` | `61446:6226` | Zusagen in Echtzeit | `App / Event Detail (Coach)` `60838:156` |
| 3 | `ios-phone-03-absences` | `61446:6227` | Abwesenheiten einmal erfassen | `App / My Absences` `60816:105` |
| 4 | `ios-phone-04-reminders` | `61446:6228` | Erinnerung vor jedem Training | `App / Notification Settings` `60824:139` |
| 5 | `ios-phone-05-roles` | `61446:6229` | Rollen für jedes Teammitglied | `App / Team Roster` `60819:122` |
| 6 | `ios-phone-06-stats` | `61446:6230` | Anwesenheitsquote pro Spieler | `App / Player Profile` `60820:122` |

Captions and screen order mirror the Play set exactly, so the two listings read as one
campaign. Source screens are `clone()`d into the frames, never moved.

## Anatomy

```
1290×2796 frame, fill = Schemes/Surface (VariableID:54778:426), M3 mode pinned to Cyan DT
├─ tessellation, 1290×900 at 0,0 — roster-T tile (120 px box) on the 172×142 grid,
│                 odd rows offset 86, 9 columns × 8 rows, layer opacity 6 %, clipped
├─ caption-block — auto-layout VERTICAL, x 108, y 165, width 1074 FIXED, height HUG
│   └─ caption  — Google Sans Flex ExtraBold @92, line-height 105 %,
│                 fill bound to Schemes/On Surface (VariableID:54778:427), FILL width
└─ device-frame — 912×1983 bezel, x 189, y 613, leaving 200 px to the frame bottom.
    │             Fill Surface Container High (VariableID:54778:454), 2 px Outline
    │             Variant stroke (VariableID:54778:431), corner radius 96,
    │             drop shadow 0/32/72 @32 % black, clipped
    └─ screen   — 874×1945, inset 19, corner radius 78. Holds the source screen
                  (412×917) rescaled ×2.1214, paddingTop lowered 68→20 before the
                  rescale so no empty status-bar band shows.
```

Deltas from the Play geometry, all driven by the taller 0.461 aspect ratio: caption 76→92,
device 650×1408 → 912×1983, bottom padding 110→200. Corner radii are raw values, not the
`Shape` collection variables — 48 px on a 912-wide bezel reads as an Android slab, and
device art is not part of the product's shape scale. Every fill and text colour is still
bound to an `M3` variable.

## Exports

`brand/store/ios/ios-phone-0{1..6}-*.png`, PNG, scale 1, 1290×2796. Re-export from the
frame nodes above after any edit.

## Known gaps

- **Trailing whitespace.** Frames 3–6 use source screens whose content fills roughly half
  the 917 pt canvas, so the lower part of the device screen is empty white. Fixing it means
  extending those source screens in the `TeamOrg Redesign` section (more absence rows, more
  roster members, more recent events), not changing the store frames.
- **English set.** Captions exist in `2026-08-04-play-store-listing-design.md` §3.3. The App
  Store needs a separate screenshot upload per localisation, so an `ios-phone-*-en` set has
  to be duplicated when the EN listing is filled in.
- **The screens are the Android/Compose renders.** Compose Multiplatform draws the same UI on
  both platforms, so this is representative, but nothing in these frames was captured from a
  running iOS build.
- **The Play frames are currently broken.** In `61183:6190` every `play-phone-*` device is
  rotated −35.6° and pushed outside its frame, with a duplicate device left behind. The
  frames need repair before the Play assets are exported again; the iOS set was rebuilt from
  this spec's geometry rather than cloned from them.
