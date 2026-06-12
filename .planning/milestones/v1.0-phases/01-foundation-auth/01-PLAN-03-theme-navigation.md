---
plan: "03"
wave: 2
phase: 1
title: "Material 3 theme + design tokens + Navigation3 shell"
depends_on: ["01"]
autonomous: true
files_modified:
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/theme/TeamorgTheme.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/theme/Color.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/theme/Type.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/theme/Shape.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/navigation/Screen.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/components/TeamorgBottomBar.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/TeamorgApp.kt
  - composeApp/src/commonMain/kotlin/ch/teamorg/ui/placeholder/PlaceholderScreen.kt
requirements:
  - AUTH-01
  - AUTH-02
---

# Plan 03 — Material 3 Theme + Design Tokens + Navigation3 Shell

## Goal
M3 theme fully wired with Teamorg design tokens. Navigation3 shell with bottom nav and placeholder screens for all 5 tabs. App renders with correct visual identity on both platforms.

## Context
- Design tokens from `pencil/design.md` — exact hex values, no approximations
- Dark mode default; light mode toggle supported
- M3 `ColorScheme` — NO dynamic color (Material You disabled)
- Bottom nav: 5 tabs (Events, Calendar, Teams, Inbox, Profile)
- Nav pill: 62px height, cornerRadius 36

## Tasks

<task id="03-01" title="Color.kt — design token mapping to M3 ColorScheme">
Map Teamorg tokens to M3 `ColorScheme`.

Dark scheme:
```kotlin
val TeamorgDarkColorScheme = darkColorScheme(
    background = Color(0xFF090912),      // --background
    surface = Color(0xFF13131F),         // --surface
    surfaceVariant = Color(0xFF1C1C2E),  // --card
    onBackground = Color(0xFFF0F0FF),    // --foreground
    onSurface = Color(0xFFF0F0FF),
    onSurfaceVariant = Color(0xFF9090B0),// --muted-foreground
    outline = Color(0xFF2A2A40),         // --border
    primary = Color(0xFF4F8EF7),         // --primary
    onPrimary = Color(0xFFFFFFFF),       // --primary-foreground
    secondary = Color(0xFFF97316),       // --accent (orange)
    onSecondary = Color(0xFFFFFFFF),
    error = Color(0xFFEF4444),           // --destructive / --color-error
    onError = Color(0xFFFFFFFF),
    // Status colors as extension properties (not M3 standard)
)

val TeamorgLightColorScheme = lightColorScheme(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF4F4F9),
    surfaceVariant = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0A0A1A),
    onSurface = Color(0xFF0A0A1A),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE2E2F0),
    primary = Color(0xFF4F8EF7),         // unchanged in light
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFF97316),       // unchanged in light
    onSecondary = Color(0xFFFFFFFF),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
)
```

Extension colors (status + semantic, not in M3 standard):
```kotlin
data class TeamorgExtendedColors(
    val success: Color,
    val warning: Color,
    val colorPurple: Color,
    val colorGold: Color,
    val colorGrey: Color,
    val cardBackground: Color,
)
val LocalTeamorgExtendedColors = staticCompositionLocalOf { /* dark defaults */ }
```
</task>

<task id="03-02" title="Type.kt + Shape.kt">
`Type.kt` — M3 `Typography` with Roboto (system default for KMP compatibility in Phase 1; custom font in later phase if needed).

`Shape.kt` — M3 `Shapes`:
- `small` = `RoundedCornerShape(8.dp)`
- `medium` = `RoundedCornerShape(12.dp)`
- `large` = `RoundedCornerShape(16.dp)`
- `extraLarge` = `RoundedCornerShape(36.dp)` — nav pill, bottom sheet handles
</task>

<task id="03-03" title="TeamorgTheme.kt">
```kotlin
@Composable
fun TeamorgTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TeamorgDarkColorScheme else TeamorgLightColorScheme
    val extendedColors = if (darkTheme) TeamorgDarkExtendedColors else TeamorgLightExtendedColors

    CompositionLocalProvider(LocalTeamorgExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TeamorgTypography,
            shapes = TeamorgShapes,
            content = content
        )
    }
}

// Convenience accessor
val MaterialTheme.extendedColors: TeamorgExtendedColors
    @Composable get() = LocalTeamorgExtendedColors.current
```
</task>

<task id="03-04" title="Screen.kt — Navigation3 destinations">
Sealed class / enum for all top-level destinations:
```kotlin
sealed class Screen(val route: String) {
    object Events : Screen("events")
    object Calendar : Screen("calendar")
    object Teams : Screen("teams")
    object Inbox : Screen("inbox")
    object Profile : Screen("profile")
    // Auth screens (no bottom nav)
    object Login : Screen("login")
    object Register : Screen("register")
    object EmptyState : Screen("empty_state")  // no club/team yet
}
```
</task>

<task id="03-05" title="TeamorgBottomBar.kt">
Bottom nav pill component:
- Height 62dp, cornerRadius 36dp
- Background: `MaterialTheme.colorScheme.surface`
- 5 items: Events (calendar-days), Calendar (calendar), Teams (users), Inbox (bell), Profile (user)
- Icons: Lucide (use `lucide-compose` or vector drawables matching Lucide names)
- Active item: `primary` color; inactive: `colorGrey`
- Uses M3 `NavigationBar` + `NavigationBarItem` styled with custom colors to achieve pill shape
</task>

<task id="03-06" title="AppNavigation.kt + TeamorgApp.kt">
`AppNavigation.kt` — Navigation3 `NavHost`:
- Auth graph: Login, Register (no bottom nav)
- Main graph: all 5 tabs with bottom nav
- Deep link support stub: `teamorg://invite/player/{userId}` (wired in Phase 2)

`TeamorgApp.kt` — root composable:
- Observe `AuthState` (from AuthViewModel — plan 04)
- Route: unauthenticated → Login, authenticated + no team → EmptyState, authenticated + has team → Main nav
- Wrap everything in `TeamorgTheme`
</task>

<task id="03-07" title="Placeholder screens">
`PlaceholderScreen.kt` — reusable placeholder composable shown in tabs not yet implemented:
```kotlin
@Composable
fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
```
Used for Calendar, Teams, Inbox in Phase 1. Replaced screen by screen in Phases 2–5.
</task>

## Verification

```bash
./gradlew :composeApp:testDebugUnitTest
```

Visual check (manual, Phase 1 milestone):
- App launches with dark background `#090912`
- Bottom nav visible with correct icon colors
- Primary blue `#4F8EF7` on active tab

## must_haves
- [ ] `TeamorgTheme` wraps entire app
- [ ] Dark mode colors exactly match `pencil/design.md` hex values
- [ ] Light mode correctly overrides dark tokens
- [ ] Navigation3 NavHost routes between auth and main graph
- [ ] Bottom nav renders with 5 tabs, pill shape
- [ ] No Material You / dynamic color
