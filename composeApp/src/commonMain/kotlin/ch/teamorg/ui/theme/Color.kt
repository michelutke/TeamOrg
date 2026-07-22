package ch.teamorg.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Graphite Cyan palette — light
val LightPrimary = Color(0xFF0E6577)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFBFEAF4)
val LightOnPrimaryContainer = Color(0xFF001F26)
val LightSecondary = Color(0xFF4B6268)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFCDE7EC)
val LightOnSecondaryContainer = Color(0xFF051F24)
val LightTertiary = Color(0xFF545D92)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFDCE1FF)
val LightOnTertiaryContainer = Color(0xFF101A4B)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color(0xFFF7F9FA)
val LightOnBackground = Color(0xFF181C1F)
val LightSurface = Color(0xFFF7F9FA)
val LightOnSurface = Color(0xFF181C1F)
val LightSurfaceVariant = Color(0xFFDBE4E8)
val LightOnSurfaceVariant = Color(0xFF40484C)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF1F4F6)
val LightSurfaceContainer = Color(0xFFEBEEF0)
val LightSurfaceContainerHigh = Color(0xFFE5E9EB)
val LightSurfaceContainerHighest = Color(0xFFDFE3E6)
val LightOutline = Color(0xFF70787C)
val LightOutlineVariant = Color(0xFFC3CBD1)
val LightInversePrimary = Color(0xFF64D8E8)
val LightInverseSurface = Color(0xFF2D3135)
val LightInverseOnSurface = Color(0xFFEFF1F3)

// Graphite Cyan palette — dark
val DarkPrimary = Color(0xFF64D8E8)
val DarkOnPrimary = Color(0xFF00363F)
val DarkPrimaryContainer = Color(0xFF004E5A)
val DarkOnPrimaryContainer = Color(0xFFA9EDF8)
val DarkSecondary = Color(0xFFB1CBD1)
val DarkOnSecondary = Color(0xFF1C3438)
val DarkSecondaryContainer = Color(0xFF334A4F)
val DarkOnSecondaryContainer = Color(0xFFCDE7EC)
val DarkTertiary = Color(0xFFB8C4EA)
val DarkOnTertiary = Color(0xFF212D61)
val DarkTertiaryContainer = Color(0xFF384479)
val DarkOnTertiaryContainer = Color(0xFFDCE1FF)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF14181D)
val DarkOnBackground = Color(0xFFE2E4E8)
val DarkSurface = Color(0xFF14181D)
val DarkOnSurface = Color(0xFFE2E4E8)
val DarkSurfaceVariant = Color(0xFF40484C)
val DarkOnSurfaceVariant = Color(0xFF9AA3AD)
val DarkSurfaceContainerLowest = Color(0xFF0F1317)
val DarkSurfaceContainerLow = Color(0xFF1B2026)
val DarkSurfaceContainer = Color(0xFF1F252C)
val DarkSurfaceContainerHigh = Color(0xFF222831)
val DarkSurfaceContainerHighest = Color(0xFF2A313A)
val DarkOutline = Color(0xFF8A939B)
val DarkOutlineVariant = Color(0xFF3A424C)
val DarkInversePrimary = Color(0xFF0E6577)
val DarkInverseSurface = Color(0xFFE2E4E8)
val DarkInverseOnSurface = Color(0xFF2E3338)

// Status colors (attendance/responses) — light
val LightStatusGoing = Color(0xFF1F6B37)
val LightStatusGoingContainer = Color(0xFFD7F0DC)
val LightStatusUnsure = Color(0xFF7A5C00)
val LightStatusUnsureContainer = Color(0xFFF8ECC8)
val LightStatusDeclined = Color(0xFFA83A30)
val LightStatusDeclinedContainer = Color(0xFFFADCD8)

// Status colors (attendance/responses) — dark
val DarkStatusGoing = Color(0xFF8CDCA0)
val DarkStatusGoingContainer = Color(0xFF173D24)
val DarkStatusUnsure = Color(0xFFF0C86C)
val DarkStatusUnsureContainer = Color(0xFF3F3117)
val DarkStatusDeclined = Color(0xFFF2A099)
val DarkStatusDeclinedContainer = Color(0xFF42201C)

val TeamorgLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inversePrimary = LightInversePrimary,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface
)

val TeamorgDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inversePrimary = DarkInversePrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface
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
    going = LightStatusGoing,
    goingContainer = LightStatusGoingContainer,
    unsure = LightStatusUnsure,
    unsureContainer = LightStatusUnsureContainer,
    declined = LightStatusDeclined,
    declinedContainer = LightStatusDeclinedContainer,
)

val TeamorgDarkExtendedColors = TeamorgExtendedColors(
    going = DarkStatusGoing,
    goingContainer = DarkStatusGoingContainer,
    unsure = DarkStatusUnsure,
    unsureContainer = DarkStatusUnsureContainer,
    declined = DarkStatusDeclined,
    declinedContainer = DarkStatusDeclinedContainer,
)

val LocalTeamorgExtendedColors = staticCompositionLocalOf { TeamorgLightExtendedColors }
