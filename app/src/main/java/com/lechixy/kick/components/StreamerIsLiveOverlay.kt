package com.lechixy.kick.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

import com.lechixy.kick.R

private val KickNeonGreen = Color(0xFF53FC18)
private val ValorantCrimson = Color(0xFFFF2A54)
private val GlitchCyan = Color(0xFF00F0FF)
private val TacticalDarkBg = Color(0xFF090D10)
private val TacticalMidBg = Color(0xFF10181D)

private const val TOTAL_ANIMATION_MS = 3600f

/**
 * VALORANT "MATCH FOUND" inspired tactical announcement overlay with Kick glitch effects.
 * Rendered purely visually in front of the player without intercepting touch events or blocking playback.
 */
@Composable
fun StreamerIsLiveOverlay(
    username: String,
    announcementId: Long,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    var elapsedMs by remember(announcementId) { mutableFloatStateOf(0f) }

    LaunchedEffect(announcementId) {
        val startNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val ms = ((nowNanos - startNanos) / 1_000_000f).coerceAtLeast(0f)
            elapsedMs = ms
            if (ms >= TOTAL_ANIMATION_MS) {
                currentOnFinished()
                break
            }
        }
    }

    val rawText = stringResource(R.string.streamer_is_live, username)
    val headlineText = remember(rawText) { rawText.uppercase(Locale.getDefault()) }
    val watermarkText = remember(headlineText) {
        "$headlineText   //   $headlineText   //   $headlineText"
    }

    // Phase progress calculations
    val t = elapsedMs.coerceIn(0f, TOTAL_ANIMATION_MS)

    // 1. Horizontal laser line ignition (0..220ms expand, 3250..3600ms collapse)
    val lineWidthFraction = when {
        t < 220f -> FastOutSlowInEasing.transform((t / 220f).coerceIn(0f, 1f))
        t > 3250f -> 1f - FastOutSlowInEasing.transform(((t - 3250f) / 350f).coerceIn(0f, 1f))
        else -> 1f
    }

    // 2. Tactical center band vertical expansion (90..420ms open, 3080..3460ms close)
    val bandOpenProgress = when {
        t < 90f -> 0f
        t < 420f -> {
            val p = ((t - 90f) / 330f).coerceIn(0f, 1f)
            // Slight tactical overshoot
            val eased = FastOutSlowInEasing.transform(p)
            val overshoot = if (p < 0.8f) (eased / 0.8f) * 1.04f else 1.04f - ((p - 0.8f) / 0.2f) * 0.04f
            overshoot.coerceIn(0f, 1.06f)
        }
        t > 3080f -> {
            val p = ((t - 3080f) / 380f).coerceIn(0f, 1f)
            (1f - FastOutSlowInEasing.transform(p)).coerceIn(0f, 1f)
        }
        else -> 1f
    }

    // 3. Overall overlay alpha
    val overlayAlpha = when {
        t < 60f -> (t / 60f).coerceIn(0f, 1f)
        t > 3350f -> (1f - (t - 3350f) / 250f).coerceIn(0f, 1f)
        else -> 1f
    }

    // 4. Headline slam-in & slow cinematic tension scale
    val textVisibleProgress = when {
        t < 150f -> 0f
        t < 430f -> FastOutSlowInEasing.transform(((t - 150f) / 280f).coerceIn(0f, 1f))
        t > 3050f -> (1f - ((t - 3050f) / 280f)).coerceIn(0f, 1f)
        else -> 1f
    }

    val textScale = when {
        t < 150f -> 1.48f
        t < 430f -> {
            val p = FastOutSlowInEasing.transform(((t - 150f) / 280f).coerceIn(0f, 1f))
            1.48f - 0.48f * p
        }
        t <= 3050f -> {
            val holdP = LinearEasing.transform(((t - 430f) / (3050f - 430f)).coerceIn(0f, 1f))
            1.0f + 0.045f * holdP
        }
        else -> {
            val exitP = ((t - 3050f) / 350f).coerceIn(0f, 1f)
            1.045f + 0.12f * exitP
        }
    }

    // 5. Kick Glitch intensity & deterministic frame-based offsets
    val glitchIntensity = computeGlitchIntensity(t)
    val glitchStep = (t / 32f).toInt()
    val glitchWaveX = if (glitchIntensity > 0.01f) {
        sin(glitchStep * 12.9898f) * 14f * glitchIntensity
    } else {
        0f
    }
    val glitchWaveY = if (glitchIntensity > 0.01f) {
        cos(glitchStep * 78.233f) * 4.5f * glitchIntensity
    } else {
        0f
    }
    val rgbSplitPx = if (glitchIntensity > 0.01f) {
        (3f + abs(sin(glitchStep * 4.7f)) * 11f) * glitchIntensity
    } else {
        1.2f
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { alpha = overlayAlpha },
        contentAlignment = Alignment.Center
    ) {
        val isCompact = maxWidth < 460.dp || maxHeight < 240.dp
        val bandHeightDp = (maxHeight * 0.42f).coerceIn(82.dp, 168.dp)
        val mainFontSize = when {
            maxWidth < 340.dp -> 19.sp
            isCompact -> 24.sp
            maxWidth < 720.dp -> 30.sp
            else -> 38.sp
        }
        val watermarkFontSize = when {
            isCompact -> 52.sp
            maxWidth < 720.dp -> 68.sp
            else -> 88.sp
        }

        // Background Canvas: Vignette, Shockwave, Tactical Band, Scanlines, Corner Reticles & Glitch Slices
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawTacticalOverlayCanvas(
                t = t,
                lineWidthFraction = lineWidthFraction,
                bandOpenProgress = bandOpenProgress.coerceIn(0f, 1f),
                bandTargetHeightPx = bandHeightDp.toPx(),
                glitchIntensity = glitchIntensity,
                glitchStep = glitchStep
            )
        }

        // Center Band Content (Clipped to the opening tactical band height)
        if (bandOpenProgress > 0.02f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bandHeightDp * bandOpenProgress.coerceIn(0f, 1.06f))
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                // Scrolling oversized VALORANT-style background watermark text
                val watermarkDriftDp = ((t / TOTAL_ANIMATION_MS) * 120f) - 60f
                Text(
                    text = watermarkText,
                    color = Color.White.copy(alpha = 0.055f * textVisibleProgress),
                    fontSize = watermarkFontSize,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 6.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.graphicsLayer {
                        translationX = watermarkDriftDp.dp.toPx() + glitchWaveX * 0.5f
                        scaleY = 1.15f
                    }
                )

                // Foreground Headline & Tactical Sub-elements
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer {
                            alpha = textVisibleProgress
                            scaleX = textScale
                            scaleY = textScale
                            translationX = glitchWaveX
                            translationY = glitchWaveY
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top tactical accent bar + diamond
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = if (isCompact) 4.dp else 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(if (isCompact) 28.dp else 44.dp)
                                .height(2.dp)
                                .graphicsLayer { alpha = 0.85f }
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRect(color = KickNeonGreen)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Canvas(Modifier.width(6.dp).height(6.dp)) {
                            val dPath = Path().apply {
                                moveTo(size.width / 2f, 0f)
                                lineTo(size.width, size.height / 2f)
                                lineTo(size.width / 2f, size.height)
                                lineTo(0f, size.height / 2f)
                                close()
                            }
                            drawPath(dPath, color = ValorantCrimson)
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(if (isCompact) 28.dp else 44.dp)
                                .height(2.dp)
                                .graphicsLayer { alpha = 0.85f }
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRect(color = KickNeonGreen)
                            }
                        }
                    }

                    // Glitch RGB Split + Main Headline Stack
                    Box(contentAlignment = Alignment.Center) {
                        val baseTextStyle = TextStyle(
                            fontSize = mainFontSize,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = if (isCompact) 2.2.sp else 3.5.sp,
                            textAlign = TextAlign.Center
                        )

                        // Crimson / Magenta Chromatic Aberration Layer (Left-Up Shift)
                        Text(
                            text = headlineText,
                            style = baseTextStyle,
                            color = ValorantCrimson.copy(alpha = (0.78f * (0.35f + glitchIntensity * 0.65f)).coerceIn(0f, 0.9f)),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer {
                                translationX = -rgbSplitPx
                                translationY = -rgbSplitPx * 0.28f
                            }
                        )

                        // Kick Neon Green / Cyan Chromatic Aberration Layer (Right-Down Shift)
                        Text(
                            text = headlineText,
                            style = baseTextStyle,
                            color = (if (glitchStep % 2 == 0) KickNeonGreen else GlitchCyan).copy(
                                alpha = (0.82f * (0.35f + glitchIntensity * 0.65f)).coerceIn(0f, 0.92f)
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer {
                                translationX = rgbSplitPx
                                translationY = rgbSplitPx * 0.28f
                            }
                        )

                        // Primary Crisp White Tactical Text
                        Text(
                            text = headlineText,
                            style = baseTextStyle.copy(
                                shadow = Shadow(
                                    color = KickNeonGreen.copy(alpha = 0.55f),
                                    offset = Offset.Zero,
                                    blurRadius = 18f
                                )
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun computeGlitchIntensity(t: Float): Float {
    return when {
        // Initial slam glitch burst
        t in 70f..440f -> {
            val p = (t - 70f) / 370f
            (1f - p * 0.7f).coerceIn(0f, 1f)
        }
        // Periodic Kick glitch spikes during hold
        t in 920f..1060f -> {
            1f - abs((t - 990f) / 70f).coerceIn(0f, 1f) * 0.4f
        }
        t in 1700f..1830f -> {
            0.9f - abs((t - 1765f) / 65f).coerceIn(0f, 1f) * 0.35f
        }
        t in 2440f..2580f -> {
            1f - abs((t - 2510f) / 70f).coerceIn(0f, 1f) * 0.3f
        }
        // Exit collapse glitch burst
        t in 3020f..3480f -> {
            val p = ((t - 3020f) / 460f).coerceIn(0f, 1f)
            (0.45f + sin(p * Math.PI.toFloat()) * 0.55f).coerceIn(0f, 1f)
        }
        else -> 0f
    }
}

private fun DrawScope.drawTacticalOverlayCanvas(
    t: Float,
    lineWidthFraction: Float,
    bandOpenProgress: Float,
    bandTargetHeightPx: Float,
    glitchIntensity: Float,
    glitchStep: Int
) {
    val w = size.width
    val h = size.height
    val centerY = h / 2f
    val centerX = w / 2f

    // 1. Subtle tactical vignette & initial neon ignition flash
    val flashAlpha = if (t < 260f) (1f - t / 260f) * 0.28f else 0f
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.55f),
                Color.Black.copy(alpha = 0.25f),
                Color.Black.copy(alpha = 0.55f)
            )
        )
    )
    if (flashAlpha > 0.005f) {
        drawRect(color = KickNeonGreen.copy(alpha = flashAlpha))
    }

    // 2. Expanding shockwave rectangle on initial slam (140..620ms)
    if (t in 140f..620f) {
        val shockP = FastOutSlowInEasing.transform(((t - 140f) / 480f).coerceIn(0f, 1f))
        val shockW = w * (0.45f + 0.52f * shockP)
        val shockH = bandTargetHeightPx * (0.8f + 0.9f * shockP)
        val shockAlpha = (1f - shockP) * 0.65f
        drawRect(
            color = KickNeonGreen.copy(alpha = shockAlpha),
            topLeft = Offset(centerX - shockW / 2f, centerY - shockH / 2f),
            size = Size(shockW, shockH),
            style = Stroke(width = 2.dp.toPx())
        )
    }

    // 3. Center horizontal tactical band backdrop
    val currentBandH = (bandTargetHeightPx * bandOpenProgress).coerceAtLeast(0f)
    val bandTop = centerY - currentBandH / 2f
    val bandBottom = centerY + currentBandH / 2f

    if (currentBandH > 1f) {
        clipRect(left = 0f, top = bandTop, right = w, bottom = bandBottom) {
            // Deep tactical horizontal gradient
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        TacticalDarkBg.copy(alpha = 0.94f),
                        TacticalMidBg.copy(alpha = 0.90f),
                        TacticalDarkBg.copy(alpha = 0.94f)
                    )
                ),
                topLeft = Offset(0f, bandTop),
                size = Size(w, currentBandH)
            )

            // Center radial Kick green & crimson glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        KickNeonGreen.copy(alpha = 0.16f + glitchIntensity * 0.10f),
                        ValorantCrimson.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = (w * 0.48f).coerceAtLeast(1f)
                ),
                topLeft = Offset(0f, bandTop),
                size = Size(w, currentBandH)
            )

            // Diagonal VALORANT-style tactical slashes on left and right edges
            val slashStep = 18.dp.toPx()
            val slashWidth = 7.dp.toPx()
            val edgeRegionW = (w * 0.22f).coerceAtLeast(60f)
            val driftPx = ((t / TOTAL_ANIMATION_MS) * slashStep * 3f) % slashStep
            var xPos = -slashStep + driftPx
            while (xPos < edgeRegionW) {
                val alphaFactor = (1f - (xPos / edgeRegionW)).coerceIn(0f, 1f) * 0.12f
                val pathLeft = Path().apply {
                    moveTo(xPos, bandBottom)
                    lineTo(xPos + currentBandH * 0.45f, bandTop)
                    lineTo(xPos + currentBandH * 0.45f + slashWidth, bandTop)
                    lineTo(xPos + slashWidth, bandBottom)
                    close()
                }
                drawPath(pathLeft, color = KickNeonGreen.copy(alpha = alphaFactor))

                val rightX = w - xPos
                val pathRight = Path().apply {
                    moveTo(rightX, bandTop)
                    lineTo(rightX - currentBandH * 0.45f, bandBottom)
                    lineTo(rightX - currentBandH * 0.45f - slashWidth, bandBottom)
                    lineTo(rightX - slashWidth, bandTop)
                    close()
                }
                drawPath(pathRight, color = ValorantCrimson.copy(alpha = alphaFactor))
                xPos += slashStep
            }

            // Horizontal CRT scanlines inside the band
            val scanlineSpacing = 4.dp.toPx().coerceAtLeast(2f)
            var scanY = bandTop
            while (scanY < bandBottom) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.26f),
                    start = Offset(0f, scanY),
                    end = Offset(w, scanY),
                    strokeWidth = 1.dp.toPx()
                )
                scanY += scanlineSpacing
            }

            // Sweeping horizontal scanner beam
            val sweepProgress = ((t % 1200f) / 1200f)
            val sweepY = bandTop + currentBandH * sweepProgress
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        KickNeonGreen.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    startY = sweepY - 12.dp.toPx(),
                    endY = sweepY + 12.dp.toPx()
                ),
                topLeft = Offset(0f, sweepY - 12.dp.toPx()),
                size = Size(w, 24.dp.toPx())
            )
        }
    }

    // 4. Top and Bottom horizontal laser border lines shooting from center
    val lineHalfW = (w * lineWidthFraction) / 2f
    if (lineHalfW > 1f) {
        val lineStroke = 2.dp.toPx()
        val glowStroke = 6.dp.toPx()

        // Glow under top & bottom border lines
        drawLine(
            color = KickNeonGreen.copy(alpha = 0.35f),
            start = Offset(centerX - lineHalfW, bandTop),
            end = Offset(centerX + lineHalfW, bandTop),
            strokeWidth = glowStroke
        )
        drawLine(
            color = KickNeonGreen.copy(alpha = 0.35f),
            start = Offset(centerX - lineHalfW, bandBottom),
            end = Offset(centerX + lineHalfW, bandBottom),
            strokeWidth = glowStroke
        )

        // Crisp top & bottom border lines
        drawLine(
            color = KickNeonGreen,
            start = Offset(centerX - lineHalfW, bandTop),
            end = Offset(centerX + lineHalfW, bandTop),
            strokeWidth = lineStroke
        )
        drawLine(
            color = KickNeonGreen,
            start = Offset(centerX - lineHalfW, bandBottom),
            end = Offset(centerX + lineHalfW, bandBottom),
            strokeWidth = lineStroke
        )

        // VALORANT-style tactical corner brackets & center notches when band is open
        if (bandOpenProgress > 0.3f) {
            val bracketInset = 14.dp.toPx()
            val bracketLen = 14.dp.toPx()
            val bracketPadY = 6.dp.toPx()
            val bStroke = 2.dp.toPx()
            val bColor = Color.White.copy(alpha = 0.75f * bandOpenProgress)

            // Top-Left Bracket
            drawLine(bColor, Offset(bracketInset, bandTop + bracketPadY), Offset(bracketInset + bracketLen, bandTop + bracketPadY), bStroke)
            drawLine(bColor, Offset(bracketInset, bandTop + bracketPadY), Offset(bracketInset, bandTop + bracketPadY + bracketLen), bStroke)

            // Top-Right Bracket
            drawLine(bColor, Offset(w - bracketInset, bandTop + bracketPadY), Offset(w - bracketInset - bracketLen, bandTop + bracketPadY), bStroke)
            drawLine(bColor, Offset(w - bracketInset, bandTop + bracketPadY), Offset(w - bracketInset, bandTop + bracketPadY + bracketLen), bStroke)

            // Bottom-Left Bracket
            drawLine(bColor, Offset(bracketInset, bandBottom - bracketPadY), Offset(bracketInset + bracketLen, bandBottom - bracketPadY), bStroke)
            drawLine(bColor, Offset(bracketInset, bandBottom - bracketPadY), Offset(bracketInset, bandBottom - bracketPadY - bracketLen), bStroke)

            // Bottom-Right Bracket
            drawLine(bColor, Offset(w - bracketInset, bandBottom - bracketPadY), Offset(w - bracketInset - bracketLen, bandBottom - bracketPadY), bStroke)
            drawLine(bColor, Offset(w - bracketInset, bandBottom - bracketPadY), Offset(w - bracketInset, bandBottom - bracketPadY - bracketLen), bStroke)
        }
    }

    // 5. Kick Glitch horizontal slice bars during glitch bursts
    if (glitchIntensity > 0.05f) {
        val sliceCount = 7
        for (i in 0 until sliceCount) {
            val seed = glitchStep * 31 + i * 17
            val hash1 = abs(sin(seed * 12.9898f))
            val hash2 = abs(cos(seed * 78.233f))
            val hash3 = abs(sin(seed * 43.123f))

            if (hash1 < glitchIntensity * 0.85f) {
                val sliceH = (2f + hash2 * 14f).dp.toPx()
                val sliceW = w * (0.12f + hash3 * 0.55f)
                val sliceY = (bandTop - 16.dp.toPx()) + (currentBandH + 32.dp.toPx()) * ((hash1 * 1.7f + i * 0.19f) % 1f)
                val sliceX = (w - sliceW) * hash2
                val sliceColor = when (i % 4) {
                    0 -> KickNeonGreen.copy(alpha = 0.72f * glitchIntensity)
                    1 -> ValorantCrimson.copy(alpha = 0.68f * glitchIntensity)
                    2 -> GlitchCyan.copy(alpha = 0.65f * glitchIntensity)
                    else -> Color.White.copy(alpha = 0.55f * glitchIntensity)
                }
                drawRect(
                    color = sliceColor,
                    topLeft = Offset(sliceX, sliceY),
                    size = Size(sliceW, sliceH)
                )
            }
        }
    }
}
