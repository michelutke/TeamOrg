package ch.teamorg.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val BLOCK_STAGGER_MS = 160
private const val CYCLE_DURATION_MS = 1200
private const val BLOCK_TRAVEL_FRACTION = 0.35f
private const val BLOCK_GAP_FRACTION = 0.25f
private const val BLOCK_CORNER_FRACTION = 0.3f

@Composable
fun TeamorgLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "TeamorgLoader")
    val block = size / (3f + 2f * BLOCK_GAP_FRACTION)
    val gap = block * BLOCK_GAP_FRACTION

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        repeat(3) { index ->
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(CYCLE_DURATION_MS / 2, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * BLOCK_STAGGER_MS)
                ),
                label = "block$index"
            )

            Box(
                modifier = Modifier
                    .size(block)
                    .offset(y = -(block * BLOCK_TRAVEL_FRACTION * progress))
                    .alpha(0.5f + 0.5f * progress)
                    .clip(RoundedCornerShape(block * BLOCK_CORNER_FRACTION))
                    .background(color)
            )
        }
    }
}
