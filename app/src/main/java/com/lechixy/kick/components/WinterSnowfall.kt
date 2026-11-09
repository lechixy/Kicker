package com.lechixy.kick.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.model.SnowParticleStyle
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.random.Random

private const val MAX_FLAKES = 72

private class Snowflake(random: Random) {
    val x = random.nextFloat()
    val y = random.nextFloat()
    val phase = random.nextFloat() * 6.283f
    val depth = random.nextFloat()
    val rotation = random.nextFloat() * 60f
}

@Composable
internal fun WinterSnowfall(
    visible: Boolean,
    playerBounds: () -> Rect?,
    scrollOffset: () -> Double,
    modifier: Modifier = Modifier,
) {
    val elapsed = remember { mutableDoubleStateOf(0.0) }
    val flakes = remember { Random(31).let { random -> List(MAX_FLAKES) { Snowflake(random) } } }
    if (!visible) return

    val settings = LocalSettingsRepository.current
    val style by settings.get(SettingsKeys.WINTER_SNOW_STYLE).collectAsStateWithLifecycle(SettingsKeys.WINTER_SNOW_STYLE.defaultValue)
    val intensity by settings.get(SettingsKeys.WINTER_SNOW_INTENSITY).collectAsStateWithLifecycle(SettingsKeys.WINTER_SNOW_INTENSITY.defaultValue)
    val speed by settings.get(SettingsKeys.WINTER_SNOW_SPEED).collectAsStateWithLifecycle(SettingsKeys.WINTER_SNOW_SPEED.defaultValue)
    val particleSize by settings.get(SettingsKeys.WINTER_SNOW_SIZE).collectAsStateWithLifecycle(SettingsKeys.WINTER_SNOW_SIZE.defaultValue)
    val speedFactor by rememberUpdatedState(speed / 100.0)
    val shapes = remember { List(3, ::snowflakePath) }
    val flakeStroke = remember { Stroke(width = 0.12f, cap = StrokeCap.Round) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val origin = remember { mutableStateOf(Offset.Zero) }
    val color = if (MaterialTheme.colorScheme.background.luminance() < 0.4f) {
        Color(0xFFE7F4FF)
    } else Color(0xFF607C96)

    LaunchedEffect(lifecycle) {
        val motionScale = coroutineContext[MotionDurationScale]
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = System.nanoTime()
            while (isActive) {
                val scale = motionScale?.scaleFactor ?: 1f
                val now = System.nanoTime()
                elapsed.doubleValue = if (scale > 0f) {
                    val seconds = (now - previous).coerceAtMost(100_000_000L) / 1e9
                    elapsed.doubleValue.coerceAtLeast(0.0) + seconds * speedFactor / scale
                } else -1.0
                previous = now
                delay(if (scale > 0f) 33L else 250L)
            }
        }
    }

    Canvas(modifier.onGloballyPositioned { origin.value = it.positionInRoot() }.graphicsLayer()) {
        val seconds = elapsed.doubleValue
        if (seconds < 0.0 || size.width <= 0f || size.height <= 0f) return@Canvas

        val excluded = playerBounds()?.translate(-origin.value) ?: Rect.Zero
        val baseCount = ((size.width / density) * (size.height / density) / 11_000f).toInt().coerceIn(16, 48)
        val count = (baseCount * intensity / 100f).roundToInt().coerceIn(1, MAX_FLAKES)
        val drawFlakes = style == SnowParticleStyle.Flakes.name
        val sizeFactor = particleSize / 100f
        val margin = 16.dp.toPx()
        val fallHeight = size.height + 2 * margin
        val scroll = scrollOffset() % fallHeight
        clipRect(excluded.left, excluded.top, excluded.right, excluded.bottom, ClipOp.Difference) {
            for (index in 0 until count) {
                val flake = flakes[index]
                val depth = flake.depth
                val speed = (13f + 23f * depth).dp.toPx()
                val position = flake.y * fallHeight + seconds * speed + scroll
                val y = ((position % fallHeight + fallHeight) % fallHeight).toFloat() - margin
                val sway = sin(seconds * (0.5 + depth) + flake.phase).toFloat() * (4f + 7f * depth).dp.toPx()
                val center = Offset(flake.x * size.width + sway, y)
                val alpha = 0.20f + 0.46f * depth
                if (drawFlakes) {
                    val radius = (3.4f + 4.6f * depth).dp.toPx() * sizeFactor
                    withTransform({
                        translate(center.x, center.y)
                        rotate(flake.rotation, Offset.Zero)
                        scale(radius, radius, Offset.Zero)
                    }) { drawPath(shapes[index % shapes.size], color, alpha = alpha, style = flakeStroke) }
                } else {
                    drawCircle(color, (0.9f + 1.6f * depth).dp.toPx() * sizeFactor, center, alpha)
                }
            }
        }
    }
}

private fun snowflakePath(variant: Int): Path = Path().apply {
    repeat(6) { arm ->
        val angle = arm * PI / 3
        val x = cos(angle).toFloat()
        val y = sin(angle).toFloat()
        fun segment(start: Float, end: Float, spread: Float) {
            moveTo(start * x, start * y)
            lineTo(end * x - spread * y, end * y + spread * x)
        }
        segment(0f, 1f, 0f)
        when (variant) {
            0 -> { segment(.54f, .81f, .24f); segment(.54f, .81f, -.24f) }
            1 -> {
                segment(.40f, .56f, .22f); segment(.40f, .56f, -.22f)
                segment(.72f, .89f, .18f); segment(.72f, .89f, -.18f)
            }
            else -> {
                segment(.32f, .58f, .22f); segment(.78f, .58f, .22f)
                segment(.32f, .58f, -.22f); segment(.78f, .58f, -.22f)
            }
        }
    }
}
