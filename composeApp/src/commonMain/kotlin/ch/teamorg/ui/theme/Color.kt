package ch.teamorg.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// M3 baseline palette (Figma M3 Expressive redesign)
val Primary = Color(0xFF6750A4)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFEADDFF)
val OnPrimaryContainer = Color(0xFF21005D)
val Secondary = Color(0xFF625B71)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFE8DEF8)
val OnSecondaryContainer = Color(0xFF1D192B)
val Tertiary = Color(0xFF7D5260)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFD8E4)
val OnTertiaryContainer = Color(0xFF31111D)
val Error = Color(0xFFB3261E)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFF9DEDC)
val OnErrorContainer = Color(0xFF410E0B)
val Surface = Color(0xFFFEF7FF)
val OnSurface = Color(0xFF1D1B20)
val SurfaceVariant = Color(0xFFE7E0EC)
val OnSurfaceVariant = Color(0xFF49454F)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF7F2FA)
val SurfaceContainer = Color(0xFFF3EDF7)
val SurfaceContainerHigh = Color(0xFFECE6F0)
val SurfaceContainerHighest = Color(0xFFE6E0E9)
val Outline = Color(0xFF79747E)
val OutlineVariant = Color(0xFFCAC4D0)

// Status colors (attendance/responses)
val StatusGoing = Color(0xFF2E7D32)
val StatusGoingContainer = Color(0xFFDCEDC8)
val StatusUnsure = Color(0xFF7A5C00)
val StatusUnsureContainer = Color(0xFFFFF0C7)
val StatusDeclined = Error
val StatusDeclinedContainer = ErrorContainer

val TeamorgLightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    outline = Outline,
    outlineVariant = OutlineVariant
)

@Immutable
data class TeamorgExtendedColors(
    val going: Color,
    val goingContainer: Color,
    val unsure: Color,
    val unsureContainer: Color,
    val declined: Color,
    val declinedContainer: Color,
)

val TeamorgLightExtendedColors = TeamorgExtendedColors(
    going = StatusGoing,
    goingContainer = StatusGoingContainer,
    unsure = StatusUnsure,
    unsureContainer = StatusUnsureContainer,
    declined = StatusDeclined,
    declinedContainer = StatusDeclinedContainer,
)

val LocalTeamorgExtendedColors = staticCompositionLocalOf { TeamorgLightExtendedColors }
