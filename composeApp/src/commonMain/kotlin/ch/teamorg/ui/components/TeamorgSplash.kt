package ch.teamorg.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OVERSHOOT_EASING = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private val MARK_SIZE = 140.dp
private val BLOCK_SIZE = MARK_SIZE * (160f / 560f)
private val STEM_WIDTH = MARK_SIZE * (160f / 560f)
private val STEM_HEIGHT = MARK_SIZE * (360f / 560f)
private val BLOCK2_X = MARK_SIZE * (200f / 560f)
private val BLOCK3_X = MARK_SIZE * (400f / 560f)
private val STEM_X = MARK_SIZE * (200f / 560f)
private val STEM_Y = MARK_SIZE * (200f / 560f)
private val BLOCK_CORNER_RADIUS = BLOCK_SIZE * (48f / 160f)
private const val DROP_IN_OFFSET_DP = 100f

@Composable
fun TeamorgSplash(onFinished: () -> Unit) {
    val markColor = MaterialTheme.colorScheme.primary
    val block1Offset = remember { Animatable(-DROP_IN_OFFSET_DP) }
    val block1Alpha = remember { Animatable(0f) }
    val block2Offset = remember { Animatable(-DROP_IN_OFFSET_DP) }
    val block2Alpha = remember { Animatable(0f) }
    val block3Offset = remember { Animatable(-DROP_IN_OFFSET_DP) }
    val block3Alpha = remember { Animatable(0f) }
    val stemScaleY = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                delay(100)
                launch { block1Offset.animateTo(0f, tween(500, easing = OVERSHOOT_EASING)) }
                launch { block1Alpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            }
            launch {
                delay(250)
                launch { block2Offset.animateTo(0f, tween(500, easing = OVERSHOOT_EASING)) }
                launch { block2Alpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            }
            launch {
                delay(400)
                launch { block3Offset.animateTo(0f, tween(500, easing = OVERSHOOT_EASING)) }
                launch { block3Alpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            }
            launch {
                delay(700)
                stemScaleY.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
            }
        }
        delay(300)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF2C313B), Color(0xFF181C23))
                )
            )
    ) {
        Box(
            modifier = Modifier
                .size(MARK_SIZE)
                .align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = block1Offset.value.dp)
                    .size(BLOCK_SIZE)
                    .graphicsLayer { alpha = block1Alpha.value }
                    .clip(RoundedCornerShape(BLOCK_CORNER_RADIUS))
                    .background(markColor)
            )
            Box(
                modifier = Modifier
                    .offset(x = BLOCK2_X, y = block2Offset.value.dp)
                    .size(BLOCK_SIZE)
                    .graphicsLayer { alpha = block2Alpha.value }
                    .clip(RoundedCornerShape(BLOCK_CORNER_RADIUS))
                    .background(markColor)
            )
            Box(
                modifier = Modifier
                    .offset(x = BLOCK3_X, y = block3Offset.value.dp)
                    .size(BLOCK_SIZE)
                    .graphicsLayer { alpha = block3Alpha.value }
                    .clip(RoundedCornerShape(BLOCK_CORNER_RADIUS))
                    .background(markColor)
            )
            Box(
                modifier = Modifier
                    .offset(x = STEM_X, y = STEM_Y)
                    .size(STEM_WIDTH, STEM_HEIGHT)
                    .graphicsLayer {
                        scaleY = stemScaleY.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(BLOCK_CORNER_RADIUS))
                    .background(markColor)
            )
        }
    }
}
