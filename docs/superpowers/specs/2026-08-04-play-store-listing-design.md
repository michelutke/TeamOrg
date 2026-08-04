# Google Play Store listing — teamorg

Date: 2026-08-04 · Package `ch.teamorg` · Figma section `61183:6190` ("App Store Assets")
in file `iKcGJfgxUxMi2AnE9o4BAL`, page "Team Org".

## Scope

Complete Play Console listing concept: text metadata (de default + en-US), and Figma
frames for icon (512), feature graphic (1024×500) and six phone screenshots
(1080×1920) built from the existing app screens and the file's own styles/variables.

Out of scope: tablet screenshots, promo video, Data-safety form, actual PNG exports
(the frames are export-ready and named for a 1:1 export preset).

## Store configuration

| Field | Value |
|---|---|
| Category | Sports (primary). Fallback if rejected as non-sport content: Tools. |
| Tags | Team sports, Club, Calendar |
| Default language | German (`de-DE`, also served to CH) |
| Second locale | `en-US` |
| Contact | info@teamorg.ch, https://teamorg.ch |

Policy constraints applied throughout: no emojis, no rank or superlative claims
("#1", "beste", "führend"), no price or promo text anywhere in metadata or graphic
assets, no "Download now"-style call to action inside the *short* description, no
Play/Android branding in assets, no fake badges or ratings.

## 1. Text metadata

### App title (max 30)

| Locale | Title | Chars |
|---|---|---|
| de | `teamorg: Verein & Trainings` | 27 |
| en | `teamorg: Team & Training Plan` | 29 |

Brand first (exact-match brand search), then the two highest-intent keywords —
`Verein` and `Trainings` in DE; `Team` and `Training Plan` in EN.

### Short description (max 80)

| Locale | Text | Chars |
|---|---|---|
| de | `Trainings planen, Anwesenheit erfassen, Team informieren – alles in einer App.` | 77 |
| en | `Plan trainings, track attendance live, keep your whole team in the loop.` | 71 |

### Full description — de (max 4000)

```
teamorg ist die App für Sportvereine: Trainings und Spiele planen, Anwesenheit
erfassen und das ganze Team informieren. Auf dem Handy, auch ohne Empfang.

FÜR COACHES
• Trainings, Spiele und Events in Sekunden planen – einmalig, wöchentlich oder nach
  eigenem Muster
• Live-Statusliste zu jedem Termin: wer kommt, wer fehlt, wer ist unsicher
• Anwesenheit für einzelne Spieler nachtragen, wenn eine Meldung fehlt
• Kader, Rollen und Untergruppen pro Mannschaft verwalten
• Anwesenheitsquote pro Spieler, Training gegen Spiel, über die ganze Saison

FÜR SPIELER
• Alle Termine deiner Teams in einer Agenda, Liste oder Kalenderansicht
• Mit einem Tap zusagen, absagen oder als unsicher melden
• Abwesenheiten, Ferien und Verletzungen im Voraus erfassen, auch wiederkehrend
• Push-Erinnerung vor Training und Spiel, damit niemand einen Termin verpasst
• Offline nutzbar – Änderungen synchronisieren automatisch, sobald du online bist

FUNKTIONEN
• Terminplanung mit Wiederholungen: Trainingsplan und Spielplan für die ganze Saison
• Anwesenheit in Echtzeit statt Gruppenchat und Zettelwirtschaft
• Absenzen und Abwesenheitsplanung mit Begründung und Zeitraum
• Teams, Rollen und Rechte für Coach, Spieler und Clubmanager
• Untergruppen für Kader, Positionen oder Altersklassen
• Einladung per Link oder E-Mail, ohne Registrierungshürde
• Anwesenheitsstatistik als Grundlage für Saisonplanung und J+S-Nachweise
• Deutsch und Englisch, hell und dunkel

FÜR DEN GANZEN VEREIN
Vom einzelnen Team bis zum Verein mit vielen Mannschaften: Clubmanager sehen alle
Teams, Coaches ihr eigenes, Spieler nur ihre Termine. Die Vereinsverwaltung bleibt
an einem Ort, statt über Tabellen, Chats und Mailverteiler verstreut.

IN DREI SCHRITTEN STARTKLAR
1. Verein erstellen und Teams anlegen
2. Coaches und Spieler per Einladungslink an Bord holen
3. Trainings und Spiele planen, Anwesenheiten erfassen, Überblick behalten

Installiere teamorg und plane die nächste Saison mit deiner Mannschaft.

Fragen oder Feedback: info@teamorg.ch · https://teamorg.ch
```

### Full description — en (max 4000)

```
teamorg is the app for sports clubs: plan trainings and games, track attendance and
keep the whole team informed. On your phone, even without a signal.

FOR COACHES
• Schedule trainings, games and events in seconds – one-off, weekly or on your own
  recurring pattern
• A live status list for every event: who is in, who is out, who is unsure
• Set attendance for a player yourself when a response is missing
• Manage your squad, roles and subgroups per team
• Attendance rate per player, training versus game, across the whole season

FOR PLAYERS
• Every event of your teams in one agenda, list or calendar view
• Accept, decline or mark yourself unsure with a single tap
• Record absences, holidays and injuries in advance, including recurring ones
• Push reminders before training and games so nobody misses an event
• Works offline – changes sync automatically once you are back online

FEATURES
• Event scheduling with recurrence: training plan and game plan for the full season
• Real-time attendance instead of group chats and paper lists
• Absence planning with reason and date range
• Teams, roles and permissions for coach, player and club manager
• Subgroups for squads, positions or age groups
• Invite by link or email, without a sign-up hurdle
• Attendance statistics as a basis for season planning and reporting
• German and English, light and dark theme

FOR THE WHOLE CLUB
From a single team to a club with many squads: club managers see every team, coaches
see their own, players see only their events. Club administration stays in one place
instead of spread across spreadsheets, chats and mailing lists.

READY IN THREE STEPS
1. Create your club and add teams
2. Bring coaches and players on board with an invite link
3. Plan trainings and games, track attendance, keep the overview

Install teamorg and plan the next season with your team.

Questions or feedback: info@teamorg.ch · https://teamorg.ch
```

## 2. Design system to use

Use only what already exists in the file — no new variables, no new fonts.

- Colours: `M3` collection, mode **Cyan DT** for the graphite/cyan store look
  (`Schemes/Surface`, `Schemes/On Surface`, `Schemes/Primary`,
  `Schemes/Primary Container`, `Schemes/On Primary Container`,
  `Schemes/Surface Container`). Brand reference hex: graphite `#22262E`,
  cyan `#64D8E8`, teal `#0E6577` (cyan never on white).
- Type: local text styles `Display/Large`, `Display/Small`, `Headline/Medium`
  (Google Sans Flex ExtraBold) for captions; `Label/Large` (Roboto Flex Medium)
  for the numbering. No M3/* Roboto styles in store art.
- Corner radii: `Shape` collection (`Corner/Extra-large`, `Corner/Full`).
- Mark geometry (from `brand/README.md`): 560×560 box, block `u` = 160, gap ¼u,
  radius 0.3u — rects (0,0,160,160), (200,0,160,160), (400,0,160,160),
  (200,200,160,360).

## 3. Figma frames

All frames live directly in section `61183:6190` (x 13268, y 5244, 5462×5899).
Layout, relative to section origin, 200 px gutters:

| Frame name | Size | Offset in section |
|---|---|---|
| `play-icon-512` | 512×512 | 200, 200 |
| `play-feature-1024x500` | 1024×500 | 912, 200 |
| `play-phone-01-events` | 1080×1920 | 200, 900 |
| `play-phone-02-attendance` | 1080×1920 | 1480, 900 |
| `play-phone-03-absences` | 1080×1920 | 2760, 900 |
| `play-phone-04-reminders` | 1080×1920 | 200, 2980 |
| `play-phone-05-roles` | 1080×1920 | 1480, 2980 |
| `play-phone-06-stats` | 1080×1920 | 2760, 2980 |

### 3.1 `play-icon-512`

Graphite ground (`Schemes/Surface`, Cyan DT) with a subtle top-left → bottom-right
linear gradient to `Schemes/Surface Container`; full-bleed square, no rounded corners
(Play applies the mask). Roster-"T" mark in `Schemes/Primary` (cyan), centred, mark
box 340×340 — 66 % of canvas, so the whole mark survives Play's circular/squircle
masking on every launcher. No text, no wordmark, no badge, no border.

### 3.2 `play-feature-1024x500`

Graphite ground, same gradient. Tessellated roster-"T" pattern (the landing
`TessellationPattern` motif) in cyan at 8 % opacity, filling the frame, clipped.
Centred horizontal lockup: 128×128 cyan mark + `teamorg` wordmark in
`Display/Large` on `Schemes/On Surface`. Everything inside a 154 px horizontal /
75 px vertical safe inset (15 %) because Play crops this asset for tablet and TV
placements. No tagline, no small type, no screenshots, no device frames.

### 3.3 Phone screenshots (×6)

Shared anatomy, identical across all six so the set reads as one system:

```
1080×1920 frame, fill = Schemes/Surface (graphite)
├─ pattern band, top 0–560, tessellation motif, cyan @6%, clipped
├─ caption block, x 90, y 130, width 900
│   ├─ step chip  — Label/Large, On Primary Container on Primary Container,
│   │               Corner/Full, e.g. "01"
│   └─ caption    — Headline/Medium (Google Sans Flex ExtraBold), On Surface,
│                   max 2 lines, 3–5 words
└─ device mockup — 720×1603 (source screen 412×917 rescaled ×1.748),
                   x 180, y 290 relative to frame, Corner/Extra-large clip,
                   1 px Outline Variant stroke, drop shadow 0/32/64 @28 % black
```

Captions and source screens:

| # | Frame | DE caption | EN caption | Source node |
|---|---|---|---|---|
| 1 | `play-phone-01-events` | Alle Termine im Blick | Every session at a glance | `App / Events List` `60805:71` |
| 2 | `play-phone-02-attendance` | Zusagen in Echtzeit | Live RSVPs from everyone | `App / Event Detail (Coach)` `60838:156` |
| 3 | `play-phone-03-absences` | Abwesenheiten einmal erfassen | Set absences once | `App / My Absences` `60816:105` |
| 4 | `play-phone-04-reminders` | Erinnerung vor jedem Training | Reminders before every training | `App / Sheet – Set Reminder` `61074:194` |
| 5 | `play-phone-05-roles` | Rollen für jedes Teammitglied | Roles for every member | `App / Team Roster` `60819:122` |
| 6 | `play-phone-06-stats` | Anwesenheitsquote pro Spieler | Attendance rates per player | `App / Player Profile` `60820:122` |

Source screens are copied (`clone()`) into the screenshot frames, never moved, so the
redesign page stays intact. If `App / Player Profile` carries no attendance figures,
frame 6 keeps the clone and gets a visible `TODO: stats screen` annotation rather
than invented UI.

Localisation: captions are TEXT nodes named `caption`. The DE string is the built
state; the EN string is recorded in this spec and applied to a duplicated
`play-phone-*-en` set only when the EN listing is filled in — Play needs separate
screenshot uploads per locale, so no component variants are used.

## 4. Acceptance criteria

- Eight frames exist in section `61183:6190`, named exactly as in §3, at the given
  sizes; no frame overlaps another.
- Every fill and text colour is bound to an `M3` variable or an existing text style —
  zero raw hex except the mark gradient stops.
- Icon: mark ≤ 66 % of canvas, no text, square, 512×512.
- Feature graphic: no element inside the 15 % outer inset; no type smaller than
  `Display/Small`.
- Each screenshot: caption 3–5 words, device fully inside the frame, caption not
  overlapping the device.
- Text metadata is within the character limits stated in §1 and contains no emoji,
  no superlative or ranking claim, and no price.
