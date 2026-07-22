package ch.teamorg.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

private const val UNIT = 560f

private data class MarkRect(val x: Float, val y: Float, val w: Float, val h: Float)

private val BLOCK_RECTS = listOf(
    MarkRect(0f, 0f, 160f, 160f),
    MarkRect(200f, 0f, 160f, 160f),
    MarkRect(400f, 0f, 160f, 160f),
)
private val STEM_RECT = MarkRect(200f, 200f, 160f, 360f)
private const val CORNER_RADIUS = 48f

@Composable
fun TeamorgMark(
    modifier: Modifier = Modifier,
    blockColor: Color = MaterialTheme.colorScheme.primary,
    stemColor: Color = blockColor,
) {
    Canvas(modifier = modifier) {
        val scale = size.minDimension / UNIT
        val cornerRadius = CornerRadius(CORNER_RADIUS * scale, CORNER_RADIUS * scale)
        BLOCK_RECTS.forEach { rect ->
            drawMarkRect(rect, scale, cornerRadius, blockColor)
        }
        drawMarkRect(STEM_RECT, scale, cornerRadius, stemColor)
    }
}

private fun DrawScope.drawMarkRect(
    rect: MarkRect,
    scale: Float,
    cornerRadius: CornerRadius,
    color: Color,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(rect.x * scale, rect.y * scale),
        size = Size(rect.w * scale, rect.h * scale),
        cornerRadius = cornerRadius,
    )
}
