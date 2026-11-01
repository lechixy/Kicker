package com.lechixy.kick.components.channel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt

@Composable
fun TagChip(
    tag: String,
    @DrawableRes icon: Int? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(4.dp)
    Surface(
        color = containerColor,
        shape = shape,
        modifier = Modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = tag,
                color = contentColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private const val MIN_CHIP_CONTRAST = 5.5

fun resolveEventChipColors(
    accentHex: String?,
    defaultContainerColor: Color,
    defaultContentColor: Color
): Pair<Color, Color> {
    if (accentHex.isNullOrBlank()) return defaultContainerColor to defaultContentColor
    return try {
        val normalizedHex = if (accentHex.startsWith("#")) accentHex else "#$accentHex"
        val containerArgb = normalizedHex.toColorInt()
        val containerColor = Color(containerArgb)

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(containerArgb, hsl)

        if (hsl[1] > 0.08f) {
            hsl[1] = (hsl[1] * 1.25f).coerceIn(0.60f, 0.92f)
        }
        hsl[2] = maxOf(hsl[2] + 0.45f, 0.70f).coerceAtMost(0.92f)

        var contentArgb = ColorUtils.HSLToColor(hsl)
        while (ColorUtils.calculateContrast(contentArgb, containerArgb) < MIN_CHIP_CONTRAST && hsl[2] < 0.95f) {
            hsl[2] = (hsl[2] + 0.03f).coerceAtMost(0.95f)
            contentArgb = ColorUtils.HSLToColor(hsl)
        }

        containerColor to Color(contentArgb)
    } catch (_: Exception) {
        defaultContainerColor to defaultContentColor
    }
}

fun resolveLanguageChipColors(
    language: String?,
    defaultContainerColor: Color,
    defaultContentColor: Color
): Pair<Color, Color> {
    return when (language?.trim()?.lowercase()) {
        "turkish" -> resolveFlagChipColors(
            containerHex = "#E30A17",
            contentHex = "#FFFFFF",
            defaultContainerColor = defaultContainerColor,
            defaultContentColor = defaultContentColor
        )
        else -> defaultContainerColor to defaultContentColor
    }
}

private fun resolveFlagChipColors(
    containerHex: String,
    contentHex: String,
    defaultContainerColor: Color,
    defaultContentColor: Color
): Pair<Color, Color> {
    return try {
        var containerArgb = containerHex.toColorInt()
        val contentArgb = contentHex.toColorInt()

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(containerArgb, hsl)

        while (ColorUtils.calculateContrast(contentArgb, containerArgb) < MIN_CHIP_CONTRAST && hsl[2] > 0.15f) {
            hsl[2] = (hsl[2] - 0.01f).coerceAtLeast(0.15f)
            containerArgb = ColorUtils.HSLToColor(hsl)
        }

        Color(containerArgb) to Color(contentArgb)
    } catch (_: Exception) {
        defaultContainerColor to defaultContentColor
    }
}