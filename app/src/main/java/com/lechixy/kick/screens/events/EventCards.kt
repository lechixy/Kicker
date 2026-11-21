package com.lechixy.kick.screens.events

import android.icu.text.DateIntervalFormat
import android.icu.util.DateInterval
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lechixy.kick.R
import com.lechixy.kick.components.CardTagRow
import com.lechixy.kick.components.ContentGrid
import com.lechixy.kick.components.InteractiveCard
import com.lechixy.kick.components.LocalContentNavigation
import com.lechixy.kick.components.MediaCardViewCount
import com.lechixy.kick.components.cardLineHeight
import com.lechixy.kick.components.streamLanguageName
import com.lechixy.kick.data.channel.model.ChannelEvent
import com.lechixy.kick.data.chat.model.recordingStartInstant
import com.lechixy.kick.util.FormatUtils
import java.text.FieldPosition
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val BannerAspectRatio = 16f / 9f
private const val PosterAspectRatio = 3f / 4f

@Composable
internal fun EventContentGrid(content: LazyGridScope.(columns: Int, bannerMaxWidth: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bannerMaxWidth = (maxHeight * BannerAspectRatio * 0.94f).coerceIn(200.dp, 960.dp)
        ContentGrid(minCardWidth = 200.dp) { columns -> content(columns, bannerMaxWidth) }
    }
}

@Composable
internal fun EventBrandBadge(label: String) {
    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
        Text(
            label, Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun FeaturedEventHeroCard(
    event: ChannelEvent,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val surface = colors.surfaceContainer
    val date = eventDate(event, compact = false)
    val url = eventBannerUrl(event, 1600)
    var aspectRatio by remember(url) { mutableFloatStateOf(BannerAspectRatio) }
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(12.dp)).background(surface).clickable(role = Role.Button, onClick = onClick)
    ) {
        val artWidth = 0.78f
        val artHeight = maxWidth * artWidth / aspectRatio
        val cardHeight = maxOf(288.dp, artHeight)
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.fillMaxWidth(artWidth).aspectRatio(aspectRatio)) {
                EventImage(
                    url, Modifier.matchParentSize(), contentScale = ContentScale.Fit,
                    background = surface, onAspectRatio = { if (it in 1f..6f) aspectRatio = it }
                )
                if (cardHeight > artHeight) {
                    Box(Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0f to surface, 0.16f to Color.Transparent,
                            0.84f to Color.Transparent, 1f to surface
                        )
                    ))
                }
            }
        }
        Box(Modifier.matchParentSize().background(
            Brush.horizontalGradient(
                0f to surface, 0.32f to surface, 0.56f to surface.copy(alpha = 0.88f),
                0.76f to surface.copy(alpha = 0.25f), 1f to Color.Transparent
            )
        ))
        Column(
            Modifier.fillMaxWidth(if (maxWidth < 420.dp) 0.68f else 0.56f)
                .heightIn(min = cardHeight).padding(16.dp).padding(bottom = bottomInset),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (event.phase == "live") EventBrandBadge(stringResource(R.string.live_badge))
                Text(
                    event.title.orEmpty(), color = colors.onSurface, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                event.description?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = colors.onSurface, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (event.numLivestreams > 0) {
                Box(Modifier.padding(vertical = 8.dp)) { ParticipatingChannelsPill(event, onOpenEvent = onClick) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EventOrganiserLabel(event, avatarSize = 32.dp, showHostLabel = true)
                if (date != null) EventDateLabel(date, colors.onSurface, maxLines = 2)
            }
        }
    }
}

@Composable
internal fun EventPromoBanner(event: ChannelEvent, modifier: Modifier = Modifier) {
    val url = eventBannerUrl(event, 1600)
    var aspectRatio by remember(url) { mutableFloatStateOf(BannerAspectRatio) }
    EventImage(
        url,
        modifier.aspectRatio(aspectRatio),
        contentScale = ContentScale.Fit,
        onAspectRatio = { if (it in 1f..6f) aspectRatio = it }
    )
}

@Composable
internal fun LiveEventCard(event: ChannelEvent, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val art = event.assets
    val url = remember(art) { eventCdnImage(art?.promoBanner, 800) ?: art?.thumbnail ?: art?.banner }
    val channelPillHeight = maxOf(21.dp, cardLineHeight(MaterialTheme.typography.labelSmall) + 6.dp) + 6.dp
    InteractiveCard(onClick = onClick, modifier = modifier.fillMaxWidth()) { mediaZoom ->
        Column {
            Box(
                Modifier.fillMaxWidth().aspectRatio(BannerAspectRatio)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                EventImage(url, Modifier.matchParentSize(), contentScale = ContentScale.Fit, imageModifier = mediaZoom)
                Box(Modifier.padding(8.dp)) {
                    EventViewerBadge(FormatUtils.formatCompactCount(event.viewCount.toLong(), locale))
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = channelPillHeight), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        event.title.orEmpty(), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (event.numLivestreams > 0) {
                        VerticalDivider(Modifier.height(16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ParticipatingChannelsPill(event)
                    }
                }
                EventTags(event)
            }
        }
    }
}

@Composable
private fun ParticipatingChannelsPill(event: ChannelEvent, onOpenEvent: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val organiser = event.organiser
    val avatar = organiser?.user?.profilePictureUrl
    var showChannels by remember { mutableStateOf(false) }
    Box {
        val content: @Composable () -> Unit = {
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    EventImage(avatar, Modifier.size(21.dp).clip(CircleShape), avatar = true)
                    Surface(color = colors.surfaceContainerHighest, shape = CircleShape) {
                        Text(
                            stringResource(R.string.events_channel_count, FormatUtils.formatCompactCount(event.numLivestreams.toLong(), locale)),
                            Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (onOpenEvent != null) {
                    Text(
                        stringResource(R.string.events_participating_channels), Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Icon(painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        null, Modifier.size(16.dp))
                }
            }
        }
        val shape = RoundedCornerShape(6.dp)
        if (onOpenEvent == null) {
            Surface(color = colors.surfaceContainerHigh, shape = shape, content = content)
        } else {
            val navigation = LocalContentNavigation.current
            Surface(onClick = { showChannels = true }, color = colors.surfaceContainerHigh, shape = shape, content = content)
            DropdownMenu(expanded = showChannels, onDismissRequest = { showChannels = false }) {
                organiser?.channel?.slug?.takeIf(String::isNotBlank)?.let { slug ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.events_open_host, organiser.user?.username ?: slug)) },
                        onClick = {
                            showChannels = false
                            navigation.channel(slug)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.events_open_details)) },
                    onClick = {
                        showChannels = false
                        onOpenEvent()
                    }
                )
            }
        }
    }
}

@Composable
internal fun UpcomingEventCard(
    event: ChannelEvent,
    modifier: Modifier = Modifier,
    isReminderSet: Boolean,
    isReminderPending: Boolean,
    onClick: () -> Unit,
    onReminderClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val date = eventDate(event, compact = true)
    val cardColor = eventSurfaceColor(event.palette?.accentPromoPoster)
    val contentColor = if (cardColor.luminance() > 0.179f) Color.Black else Color.White
    val chipColor = if (contentColor == Color.White) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.60f)
    val actionsHeight = maxOf(48.dp, cardLineHeight(MaterialTheme.typography.labelMedium) * 2 + 8.dp)
    val organiserHeight = maxOf(20.dp, cardLineHeight(MaterialTheme.typography.labelMedium))
    val art = event.assets
    val poster = remember(art) { eventCdnImage(art?.promoPoster, 800) }
    InteractiveCard(onClick = onClick, modifier = modifier.fillMaxWidth(), color = cardColor) { mediaZoom ->
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val imageFraction = if (maxWidth < 480.dp) 0.30f else 0.34f
            val imageWidth = minOf(maxWidth * imageFraction, 176.dp, (maxWidth - 232.dp).coerceAtLeast(maxWidth * 0.26f))
            val reminderMaxWidth = (maxWidth - imageWidth - 18.dp).coerceAtLeast(0.dp) * 0.46f
            Box(Modifier.matchParentSize()) {
                EventImage(
                    poster ?: art?.thumbnail ?: art?.banner,
                    Modifier.width(imageWidth).fillMaxHeight(),
                    contentScale = ContentScale.Crop, imageModifier = mediaZoom,
                    background = lerp(cardColor, scheme.surfaceContainer, 0.25f)
                )
            }
            Column(
                Modifier.fillMaxWidth().heightIn(min = imageWidth / PosterAspectRatio)
                    .padding(start = imageWidth + 10.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = actionsHeight),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (date != null) Surface(
                        modifier = Modifier.weight(1f, fill = false),
                        color = chipColor, shape = RoundedCornerShape(6.dp)
                    ) {
                        Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            EventDateLabel(date, contentColor, maxLines = 2)
                        }
                    }
                    TextButton(
                        onClick = onReminderClick,
                        enabled = !isReminderPending,
                        modifier = Modifier.widthIn(max = reminderMaxWidth),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = contentColor,
                            disabledContentColor = contentColor.copy(alpha = 0.68f)
                        )
                    ) {
                        Text(
                            stringResource(if (isReminderSet) R.string.events_reminder_set else R.string.events_remind_me),
                            style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    event.title.orEmpty(), color = contentColor,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    event.description.orEmpty(), color = contentColor, style = MaterialTheme.typography.bodySmall,
                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Box(Modifier.heightIn(min = organiserHeight), contentAlignment = Alignment.CenterStart) {
                    EventOrganiserLabel(event, avatarSize = 20.dp, textColor = contentColor)
                }
                EventTags(event, container = chipColor, color = contentColor)
            }
        }
    }
}

@Composable
internal fun eventSurfaceColor(hex: String?): Color {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val accent = remember(hex) { hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)).copy(alpha = 1f) }.getOrNull() } }
    return accent?.let { lerp(surface, it, 0.46f) } ?: surface
}

@Composable
internal fun EventViewerBadge(count: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = Color.Black.copy(alpha = 0.72f), contentColor = Color.White) {
        MediaCardViewCount(count, stringResource(R.string.viewers))
    }
}

@Composable
internal fun EventOrganiserLabel(
    event: ChannelEvent,
    avatarSize: Dp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    showHostLabel: Boolean = false
) {
    val organiser = event.organiser ?: return
    val username = organiser.user?.username ?: organiser.channel?.slug ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EventImage(organiser.user?.profilePictureUrl, Modifier.size(avatarSize).clip(CircleShape), avatar = true)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    username, Modifier.weight(1f, fill = false), color = textColor, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (organiser.channel?.verified == true) {
                    Icon(
                        painterResource(R.drawable.verified_badge), stringResource(R.string.events_verified_host),
                        Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (showHostLabel) {
                Text(stringResource(R.string.events_host), style = MaterialTheme.typography.labelSmall, color = textColor)
            }
        }
    }
}

@Composable
private fun EventTags(
    event: ChannelEvent,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val languageTag = event.defaultLanguage?.takeIf(String::isNotBlank) ?: event.languages.firstOrNull()
    val locale = LocalConfiguration.current.locales[0]
    val language = remember(languageTag, locale) {
        languageTag?.let {
            val tag = it.replace('_', '-')
            Locale.forLanguageTag(tag).getDisplayName(locale).ifBlank { streamLanguageName(tag) }
        }
    }
    CardTagRow(spacing = 6.dp, chipVerticalPadding = 6.dp) {
        listOfNotNull(event.category?.name?.takeIf(String::isNotBlank), language).forEach { label ->
            Surface(Modifier.widthIn(max = 120.dp), color = container, shape = RoundedCornerShape(6.dp)) {
                Text(label, Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun EventDateLabel(date: String, color: Color, maxLines: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(painterResource(R.drawable.calendar_month_24dp_e3e3e3_fill1_wght400_grad0_opsz24),
            null, Modifier.size(14.dp), tint = color)
        Text(date, color = color, style = MaterialTheme.typography.labelSmall, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun eventDate(event: ChannelEvent, compact: Boolean): String? {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    return remember(event.startTime, event.endTime, compact, locale, zone) {
        val start = recordingStartInstant(event.startTime) ?: return@remember null
        val end = recordingStartInstant(event.endTime)?.takeIf { it > start }
        if (end != null) {
            val startDate = start.atZone(zone).toLocalDate()
            val endDate = end.atZone(zone).toLocalDate()
            val skeleton = when {
                !compact -> "yMMMMdjm"
                startDate == endDate -> "MMMdjm"
                startDate.year == endDate.year -> "MMMd"
                else -> "yMMMd"
            }
            DateIntervalFormat.getInstance(skeleton, locale).apply {
                timeZone = android.icu.util.TimeZone.getTimeZone(zone.id)
            }.format(DateInterval(start.toEpochMilli(), end.toEpochMilli()), StringBuffer(), FieldPosition(0)).toString()
        } else {
            DateTimeFormatter.ofLocalizedDateTime(if (compact) FormatStyle.MEDIUM else FormatStyle.LONG, FormatStyle.SHORT)
                .withLocale(locale).withZone(zone).format(start)
        }
    }
}

@Composable
internal fun EventImage(
    url: String?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    avatar: Boolean = false,
    imageModifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onAspectRatio: ((Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val request = remember(context, url) {
        ImageRequest.Builder(context).data(url?.takeIf(String::isNotBlank)).crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build()
    }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.clipToBounds().background(background)) {
        if (!loaded) {
            Icon(
                painterResource(if (avatar) R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                    else R.drawable.local_activity_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                null, Modifier.align(Alignment.Center).size(if (avatar) 14.dp else 28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.matchParentSize().then(imageModifier),
            contentScale = contentScale,
            onSuccess = {
                loaded = true
                val image = it.result.image
                if (image.width > 0 && image.height > 0) onAspectRatio?.invoke(image.width.toFloat() / image.height)
            }
        )
    }
}

@Composable
private fun eventBannerUrl(event: ChannelEvent, width: Int): String? {
    val art = event.assets
    return remember(art, width) {
        eventCdnImage(art?.promoBanner, width) ?: art?.banner ?: art?.background ?: art?.thumbnail
    }
}

private fun eventCdnImage(url: String?, width: Int): String? {
    val source = url?.takeIf(String::isNotBlank) ?: return null
    val image = source.toHttpUrlOrNull() ?: return source
    if (image.host != "content.kick.com" || image.encodedQuery != null) return source
    return image.newBuilder().encodedQuery("width=$width,format=webp,quality=75").build().toString()
}
