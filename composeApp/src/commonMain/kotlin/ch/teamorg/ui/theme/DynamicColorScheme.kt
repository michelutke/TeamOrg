package ch.teamorg.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?
