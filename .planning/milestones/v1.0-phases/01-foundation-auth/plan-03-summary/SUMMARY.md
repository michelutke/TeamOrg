# Plan 03 Summary — Theme & Navigation

## Accomplishments
- Implemented Material 3 theme with exact design tokens from `pencil/design.md`.
- Created `TeamorgTheme` with support for Dark Mode (default) and Light Mode.
- Defined extended color palette via `CompositionLocal` for status and semantic colors not in standard M3 `ColorScheme`.
- Set up Navigation3 shell with `NavHost` and bottom navigation.
- Created placeholder screens for all 5 tabs and auth destinations.
- Custom Bottom Navigation Bar styled as a pill (62dp height, 36dp corner radius).

## Key Files Created
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/theme/Color.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/theme/Type.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/theme/Shape.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/theme/TeamorgTheme.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/navigation/Screen.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/navigation/AppNavigation.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/components/TeamorgBottomBar.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/TeamorgApp.kt`
- `composeApp/src/commonMain/kotlin/ch/teamorg/ui/placeholder/PlaceholderScreen.kt`

## Deviations / Notes
- **Icons**: Used `androidx.compose.material:material-icons-extended` equivalents for Lucide icons for now to avoid additional dependency issues in early setup.
- **Navigation State**: Implemented a basic `backstack` using `mutableStateListOf` inside `TeamorgApp` for placeholder functionality. This will be integrated with `AuthViewModel` in Plan 04 to handle auth-gated routing.
- **Package name**: Used `ch.teamorg` as requested.

## Next Steps
- Implement Plan 04 (Auth state + ViewModel + Koin + DataStore).
- Connect `TeamorgApp` navigation to `AuthState` logic.
