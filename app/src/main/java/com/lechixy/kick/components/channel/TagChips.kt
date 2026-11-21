package com.lechixy.kick.components.channel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.lechixy.kick.R
import com.lechixy.kick.components.LocalContentNavigation
import com.lechixy.kick.data.channel.model.ChannelEvent

@Composable
internal fun ChannelMediaTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun ChannelMediaTags(
    categories: List<Pair<String, String>>,
    tags: List<String>,
    event: ChannelEvent?,
    language: String?,
    isMature: Boolean,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val navigation = LocalContentNavigation.current
    val containerColor = contentColor.copy(alpha = 0.1f)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isMature) {
            TagChip(
                tag = stringResource(R.string.channel_mature_badge),
                containerColor = containerColor,
                contentColor = contentColor,
            )
        }
        categories.forEach { (name, slug) ->
            val title = if (name == "Just Chatting") stringResource(R.string.category_just_chatting) else name
            TagChip(title, containerColor = containerColor, contentColor = contentColor, onClick = { navigation.category(slug) })
        }
        event?.title?.takeIf { it.isNotBlank() }?.let { title ->
            val accent = event.palette?.accentPromoPoster
            val (eventBackground, eventForeground) = remember(accent, containerColor, contentColor) {
                resolveEventChipColors(accent, containerColor, contentColor)
            }
            TagChip(
                tag = title,
                icon = R.drawable.local_activity_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                containerColor = eventBackground.copy(alpha = eventBackground.alpha.coerceAtMost(0.8f)),
                contentColor = eventForeground,
                onClick = (event.slug?.takeIf(String::isNotBlank) ?: event.id?.takeIf(String::isNotBlank))?.let { slug ->
                    { navigation.event(slug) }
                },
            )
        }
        tags.filter(String::isNotBlank).distinct().forEach {
            TagChip(it, containerColor = containerColor, contentColor = contentColor)
        }
        language?.takeIf(String::isNotBlank)?.let { label ->
            val (languageBackground, languageForeground) = remember(label, containerColor, contentColor) {
                resolveLanguageChipColors(label, containerColor, contentColor)
            }
            val title = when (label) {
                "English" -> stringResource(R.string.language_english)
                "Turkish" -> stringResource(R.string.language_turkish)
                else -> label
            }
            TagChip(title, containerColor = languageBackground, contentColor = languageForeground)
        }
    }
}

@Composable
internal fun MetadataPill(text: String, onClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    TagChip(
        tag = text,
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurfaceVariant,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.3f)),
        onClick = onClick,
    )
}

@Composable
fun TagChip(
    tag: String,
    @DrawableRes icon: Int? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(4.dp)
    Surface(
        color = containerColor,
        shape = shape,
        border = border,
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
