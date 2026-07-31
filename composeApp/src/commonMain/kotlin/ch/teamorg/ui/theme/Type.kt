package ch.teamorg.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import teamorg.composeapp.generated.resources.Res
import teamorg.composeapp.generated.resources.roboto_flex

// Maps 1:1 to Figma local text styles (M3 Expressive redesign).
// Roboto Flex variable font bundled; one FontFamily per weight with an
// explicit wght axis setting so iOS/skiko instances the weight correctly.
@Composable
private fun robotoFlex(weight: FontWeight): FontFamily {
    val font = Font(
        Res.font.roboto_flex,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
    )
    return remember(weight) { FontFamily(font) }
}

@Composable
fun teamorgTypography(): Typography {
    val body = robotoFlex(FontWeight.Normal)
    val label = robotoFlex(FontWeight.Medium)
    val title = robotoFlex(FontWeight.Bold)
    val headline = robotoFlex(FontWeight.ExtraBold)
    return remember(body, label, title, headline) {
        Typography(
            displayLarge = TextStyle(
                fontFamily = headline,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 45.sp,
                lineHeight = 52.sp
            ),
            displaySmall = TextStyle(
                fontFamily = headline,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 36.sp,
                lineHeight = 44.sp
            ),
            headlineLarge = TextStyle(
                fontFamily = headline,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                lineHeight = 40.sp
            ),
            headlineMedium = TextStyle(
                fontFamily = headline,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                lineHeight = 36.sp
            ),
            headlineSmall = TextStyle(
                fontFamily = headline,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                lineHeight = 32.sp
            ),
            titleLarge = TextStyle(
                fontFamily = title,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp
            ),
            titleMedium = TextStyle(
                fontFamily = title,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                lineHeight = 24.sp
            ),
            titleSmall = TextStyle(
                fontFamily = label,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 20.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            bodySmall = TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            labelLarge = TextStyle(
                fontFamily = label,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            labelMedium = TextStyle(
                fontFamily = label,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            labelSmall = TextStyle(
                fontFamily = label,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        )
    }
}
