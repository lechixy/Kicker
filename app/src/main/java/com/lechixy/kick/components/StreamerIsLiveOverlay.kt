package com.lechixy.kick.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lechixy.kick.R
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

private val SignalGreen = Color(0xFF53FC18)
private val SignalAqua = Color(0xFF00F5D4)
private const val RevealDurationMs = 4_600f

/** Signal acquisition → block rupture → large LIVE reveal → pixel wipe. No media player or input capture. */
@Composable
fun StreamerIsLiveOverlay(username: String, announcementId: Long, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val dynamicColors by LocalSettingsRepository.current.get(SettingsKeys.DYNAMIC_COLOR)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.DYNAMIC_COLOR.defaultValue)
    val signalGreen = if (dynamicColors) MaterialTheme.colorScheme.primary else SignalGreen
    val signalAqua = if (dynamicColors) MaterialTheme.colorScheme.tertiary else SignalAqua
    val backdrop = if (dynamicColors) MaterialTheme.colorScheme.surface else Color(0xFF070907)
    val foreground = if (dynamicColors) MaterialTheme.colorScheme.onSurface else Color.White
    val finish by rememberUpdatedState(onFinished)
    val animate = remember(announcementId) { ValueAnimator.areAnimatorsEnabled() }
    var time by remember(announcementId) { mutableFloatStateOf(if (animate) 0f else 1_200f) }
    LaunchedEffect(announcementId) {
        if (!animate) { delay(1_800); finish(); return@LaunchedEffect }
        val start = withFrameNanos { it }
        do {
            time = ((withFrameNanos { it } - start) / 1_000_000f).coerceAtMost(RevealDurationMs)
        } while (time < RevealDurationMs)
        finish()
    }
    val entry = (time / 180f).coerceIn(0f, 1f)
    val exit = ((RevealDurationMs - time) / 420f).coerceIn(0f, 1f)
    val reveal = FastOutSlowInEasing.transform(((time - 620f) / 380f).coerceIn(0f, 1f))
    val burst = when {
        time in 610f..1_020f -> (1f - (time - 610f) / 410f)
        time in 1_900f..2_020f -> .65f
        time in 3_100f..3_220f -> .75f
        else -> 0f
    }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().graphicsLayer { alpha = entry * exit }, contentAlignment = Alignment.Center) {
        val compact = maxHeight < 250.dp
        val headlineSp = minOf(maxWidth.value * .20f, maxHeight.value * .34f, 120f).coerceAtLeast(30f)
        val colorPhase = (sin(time / 620f) + 1f) * .5f
        val signalColor = lerp(signalAqua, signalGreen, colorPhase)
        val headlineBrush = Brush.horizontalGradient(listOf(signalColor, signalGreen, lerp(signalGreen, signalAqua, colorPhase)))
        Canvas(Modifier.fillMaxSize()) {
            drawRect(backdrop.copy(alpha = .94f))
            drawRect(Brush.radialGradient(listOf(signalGreen.copy(alpha = .20f * reveal), Color.Transparent),
                center = Offset(size.width * (.25f + colorPhase * .18f), size.height * .48f), radius = size.width * .65f))
            drawRect(Brush.radialGradient(listOf(signalAqua.copy(alpha = .17f * reveal), Color.Transparent),
                center = Offset(size.width * (.78f - colorPhase * .16f), size.height * .52f), radius = size.width * .55f))
            // Perspective streaks accelerate away from the center, like the reward reveal reference.
            val radius = sqrt(size.width * size.width + size.height * size.height) * .6f
            val intensity = if (time < 1_200f) .8f else .25f + burst * .6f
            for (i in 0 until 36) {
                val angle = i * 2.399963f
                val dx = cos(angle); val dy = sin(angle)
                val period = 550f + (i * 83 % 700)
                val travel = ((time / period + (i * 37 % 101) / 101f) % 1f)
                val head = radius * (.28f + travel * .95f)
                val tail = (head - radius * (.08f + (i % 4) * .035f)).coerceAtLeast(radius * .24f)
                val start = center + Offset(dx * tail, dy * tail)
                val end = center + Offset(dx * head, dy * head)
                val color = if (i % 3 == 0) signalAqua else signalGreen
                val opacity = ((1f - travel) * intensity).coerceIn(0f, 1f)
                val streak = Brush.linearGradient(listOf(Color.Transparent, color.copy(alpha = opacity)), start, end)
                drawLine(streak, start, end, strokeWidth = (if (i % 5 == 0) 5f else 2.8f).dp.toPx(), cap = StrokeCap.Round, alpha = .16f)
                drawLine(streak, start, end, strokeWidth = (if (i % 5 == 0) 1.6f else .7f).dp.toPx(), cap = StrokeCap.Round)
            }
            val cell = (size.width / 24).coerceAtLeast(1f)
            val step = (time / 55).toInt()
            // A sparse deterministic pixel field moves from the edges into the reveal.
            for (i in 0 until 48) {
                val column = (i * 13 + step / 3) % 24
                val row = (i * 7) % 15
                val x = column * cell
                val y = (row / 15f) * size.height
                val edge = if (column < 5 || column > 18) 1f else .25f
                val noise = ((i * 37 + step * 17) % 101) / 100f
                val opacity = if (time < 700) .15f + noise * .5f else .035f + burst * noise * .55f
                if (noise > .4f) drawRect(lerp(signalAqua, signalGreen, noise).copy(alpha = (opacity * edge).coerceIn(0f, 1f)), Offset(x, y), Size(cell * (1 + i % 3), cell * .3f))
            }
            // Chunky pixel clusters crawl along the edges; bursts split their aqua/green channels.
            val pixel = (size.minDimension / 30f).coerceAtLeast(2f)
            for (corner in 0 until 4) {
                for (tile in 0 until 18) {
                    val seed = (tile * 29 + corner * 17 + step * 11) % 97
                    if (seed < 36) continue
                    val column = (tile * 3 + step / 4) % 9
                    val row = (tile * 7 + step / 6) % 12
                    val x = if (corner % 2 == 0) column * pixel else size.width - (column + 1) * pixel
                    val y = if (corner < 2) row * pixel else size.height - (row + 1) * pixel
                    val color = if (seed % 3 == 0) signalAqua else signalGreen
                    val strength = (.12f + burst * .6f) * (seed / 97f)
                    drawRect(color.copy(alpha = strength * .15f), Offset(x - pixel, y - pixel), Size(pixel * 4, pixel * 3))
                    drawRect(color.copy(alpha = strength), Offset(x, y), Size(pixel * (1 + tile % 2), pixel))
                    if (burst > .1f && tile % 3 == 0) drawRect(signalAqua.copy(alpha = burst * .35f),
                        Offset(x + pixel * 2, y), Size(pixel, pixel))
                }
            }
            if (time < 760) {
                val progress = (time / 760).coerceIn(0f, 1f)
                drawRect(signalGreen, Offset(size.width * .18f, size.height * .72f), Size(size.width * .64f * progress, 3.dp.toPx()))
                drawRect(signalGreen.copy(alpha = .2f), Offset(size.width * .18f, size.height * .72f), Size(size.width * .64f, 3.dp.toPx()), style = Stroke(1.dp.toPx()))
            }
            if (burst > 0f) {
                val y = size.height * (.15f + ((step * 17) % 70) / 100f)
                drawRect(signalGreen.copy(alpha = burst * .6f), Offset(0f, y), Size(size.width, 2.dp.toPx()))
            }
            val sweep = ((time - 700f) / 600f).coerceIn(0f, 1f)
            if (time in 700f..1_300f) drawRect(signalGreen.copy(alpha = (1 - sweep) * .18f),
                Offset(size.width * sweep, 0f), Size(size.width * .12f, size.height))
        }
        if (time < 700) {
            Text(stringResource(R.string.live_signal_incoming), color = signalColor,
                fontFamily = FontFamily.Monospace, fontSize = if (compact) 12.sp else 16.sp, letterSpacing = 3.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).graphicsLayer {
                alpha = reveal
                scaleX = 1.18f - reveal * .18f
                scaleY = .65f + reveal * .35f
            }) {
            Text(stringResource(R.string.live_signal_connected), fontFamily = FontFamily.Monospace,
                color = signalColor, fontSize = if (compact) 9.sp else 12.sp, letterSpacing = 3.sp)
            Text(stringResource(R.string.live_reveal_headline),
                style = TextStyle(brush = headlineBrush, shadow = Shadow(signalColor.copy(alpha = .45f), Offset.Zero, 14f)),
                fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif,
                fontSize = headlineSp.sp, lineHeight = (headlineSp * .95f).sp,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.drawWithContent {
                    if (burst <= 0f) drawContent() else {
                        for (slice in 0 until 8) {
                            val top = size.height * slice / 8
                            val shift = sin((time / 28).toInt() * 17f + slice * 9f) * 22.dp.toPx() * burst
                            clipRect(top = top, bottom = top + size.height / 8 + 1) {
                                translate(left = shift) { this@drawWithContent.drawContent() }
                            }
                        }
                        // Small displaced tiles break up the larger horizontal text slices.
                        val pixel = 5.dp.toPx()
                        for (tile in 0 until 9) {
                            val x = ((tile * 71 + (time / 35).toInt() * 13) % 101) / 101f * size.width
                            val y = ((tile * 37) % 7) / 7f * size.height
                            drawRect((if (tile % 2 == 0) signalAqua else signalGreen).copy(alpha = burst * .45f),
                                Offset(x, y), Size(pixel * (1 + tile % 3), pixel))
                        }
                    }
                })
            Text(username, color = foreground, fontWeight = FontWeight.Bold, fontSize = if (compact) 18.sp else 28.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
