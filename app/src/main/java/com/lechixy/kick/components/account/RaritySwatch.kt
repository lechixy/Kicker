package com.lechixy.kick.components.account

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.sin

import com.lechixy.kick.theme.KickVoltGreen

/** Reward rarity colors are fixed domain colors and do not follow the app theme. */
@Composable
internal fun RaritySwatch(rarity: String, modifier: Modifier = Modifier) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val scale = minOf(size.width, size.height) / 20f
        val origin = Offset((size.width - 20f * scale) / 2, (size.height - 20f * scale) / 2)
        fun point(x: Float, y: Float) = origin + Offset(x * scale, y * scale)
        val bounds = Size(18f * scale, 18f * scale)
        val corners = CornerRadius(1.5f * scale)
        if (rarity.lowercase() in setOf("mythic", "mythical")) {
            // Reproduce RarityMythical.svg: radial base, Difference layer, then Plus layer.
            val angle = Math.toRadians(48.691)
            val centerX = (-0.19 - cos(angle) * -0.19 + sin(angle) * 2.463).toFloat()
            val centerY = (2.463 - sin(angle) * -0.19 - cos(angle) * 2.463).toFloat()
            drawRoundRect(
                brush = Brush.radialGradient(
                    0.1f to Color(0xFF80FCFF), 0.3f to Color(0xFFFCF2AA),
                    0.5f to Color(0xFFFFA0EC), 0.7f to Color(0xFF61A4FF), 0.9f to Color(0xFF65FFC2),
                    center = point(centerX, centerY), radius = 21.8519f * scale
                ), topLeft = point(1f, 1f), size = bounds, cornerRadius = corners
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    0f to Color.White, 0.25f to Color.Black, 0.34f to Color.Black,
                    0.54f to Color.White, 0.84f to Color.Black, 1f to Color.White,
                    start = point(1f, 1f), end = point(19f, 19f)
                ), topLeft = point(1f, 1f), size = bounds, cornerRadius = corners, blendMode = BlendMode.Difference
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    0f to Color.White, 0.25f to Color.Black, 0.34f to Color.Black,
                    0.54f to Color.White, 0.78f to Color.Black, 0.93f to Color.White, 1f to Color.White,
                    start = point(1f, 1f), end = point(19f, 19f)
                ), topLeft = point(1f, 1f), size = bounds, cornerRadius = corners, blendMode = BlendMode.Plus
            )
        } else {
            val color = when (rarity.lowercase()) {
                "legendary" -> Color.Yellow
                "epic" -> Color(0xFFA855F7)
                "rare" -> Color(0xFF00B5E0)
                "uncommon" -> KickVoltGreen
                else -> Color(0xFF949494)
            }
            drawRoundRect(color, point(1f, 1f), bounds, corners)
        }
    }
}
