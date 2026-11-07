package com.lechixy.kick.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.lechixy.kick.R
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository

@Composable
fun ColoredAppText() {
    val repository = LocalSettingsRepository.current
    val setting = SettingsKeys.TOP_BAR_LOGO_ANIMATION
    val animate by remember(repository) { repository.get(setting) }
        .collectAsStateWithLifecycle(initialValue = setting.defaultValue)
    Text(
        text = stringResource(R.string.app_name),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        style = if (animate) animatedAppTextStyle() else TextStyle.Default,
        fontSize = 22.sp
    )
}

@Composable
private fun animatedAppTextStyle(): TextStyle {
    val currentFontSizePx = with(LocalDensity.current) { 40.sp.toPx() }
    val currentFontSizeDoublePx = currentFontSizePx * 2
    val transition = rememberInfiniteTransition(label = "colored_app_text_transition")

    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = currentFontSizeDoublePx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_offset"
    )

    val glowBlur by transition.animateFloat(
        initialValue = 18f,
        targetValue = 42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_blur"
    )

    val gradient = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
    )

    return TextStyle(
        brush = Brush.linearGradient(
            colors = gradient,
            start = Offset(offset, offset),
            end = Offset(
                offset + currentFontSizePx,
                offset + currentFontSizePx
            ),
            tileMode = TileMode.Mirror
        ),
        shadow = Shadow(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            offset = Offset(0f, 0f),
            blurRadius = glowBlur
        )
    )
}
