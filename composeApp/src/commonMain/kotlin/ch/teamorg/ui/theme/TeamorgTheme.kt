package ch.teamorg.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Light only — dark mode not yet designed (M3 Expressive redesign, light palette)
@Composable
fun TeamorgTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalTeamorgExtendedColors provides TeamorgLightExtendedColors) {
        MaterialTheme(
            colorScheme = TeamorgLightColorScheme,
            typography = TeamorgTypography,
            shapes = TeamorgShapes,
            content = content
        )
    }
}

val MaterialTheme.extendedColors: TeamorgExtendedColors
    @Composable get() = LocalTeamorgExtendedColors.current
