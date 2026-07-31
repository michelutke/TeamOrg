package ch.teamorg.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun TeamorgTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = (if (dynamicColor) platformDynamicColorScheme(darkTheme) else null)
        ?: if (darkTheme) TeamorgDarkColorScheme else TeamorgLightColorScheme
    val extended = if (darkTheme) TeamorgDarkExtendedColors else TeamorgLightExtendedColors
    CompositionLocalProvider(LocalTeamorgExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = teamorgTypography(),
            shapes = TeamorgShapes,
            content = content
        )
    }
}

val MaterialTheme.extendedColors: TeamorgExtendedColors
    @Composable get() = LocalTeamorgExtendedColors.current
