@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lechixy.kick.components.channel

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.net.URI
import java.text.NumberFormat

import com.lechixy.kick.R
import com.lechixy.kick.components.SkeletonBlock
import com.lechixy.kick.components.VerifiedBadge
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelSectionState
import com.lechixy.kick.data.channel.model.KickChannelLinkDto
import com.lechixy.kick.data.channel.model.KickGoalDto
import com.lechixy.kick.util.FormatUtils

internal fun safeChannelUrl(value: String?): String? = value?.trim()?.takeIf { raw ->
    runCatching { val uri = URI(raw); uri.scheme?.lowercase() in setOf("http", "https") &&
        !uri.host.isNullOrBlank() && uri.userInfo == null }.getOrDefault(false)
}

internal fun socialChannelUrl(platform: String, value: String?): String? {
    val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    safeChannelUrl(raw)?.let { return it }
    if (raw.contains(":") || raw.startsWith("//")) return null
    if (raw.startsWith("www.") || raw.startsWith("discord.gg/") || raw.startsWith("discord.com/")) return safeChannelUrl("https://$raw")
    val handle = raw.removePrefix("@").trim('/')
    val url = when (platform) {
        "Discord" -> if (handle.all { it.isDigit() }) "https://discord.com/users/$handle" else "https://discord.gg/$handle"
        "X" -> "https://x.com/$handle"
        "Instagram" -> "https://instagram.com/$handle"
        "YouTube" -> "https://youtube.com/@$handle"
        "TikTok" -> "https://tiktok.com/@$handle"
        "Facebook" -> "https://facebook.com/$handle"
        else -> return null
    }
    return safeChannelUrl(url)
}

@Composable
internal fun ChannelAboutTabContent(
    channelDetails: ChannelDetail,
    linksState: ChannelSectionState<List<KickChannelLinkDto>>,
    goalState: ChannelSectionState<List<KickGoalDto>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    var linkFailed by remember { mutableStateOf(false) }
    val open: (String) -> Unit = { raw -> safeChannelUrl(raw)?.let { url ->
        linkFailed = runCatching { if (onLinkClick != null) onLinkClick(url) else uriHandler.openUri(url) }.isFailure
    } }
    val goal = goalState.value?.firstOrNull { it.status.equals("active", true) && it.type.equals("subscriptions", true) }
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BoxWithConstraints {
            if (maxWidth >= 650.dp && goal != null) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AboutChannelInfoCard(channelDetails, open, Modifier.weight(1f))
                SubscriptionGoalCard(goal, channelDetails.slug, open, Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AboutChannelInfoCard(channelDetails, open)
                if (goal != null) SubscriptionGoalCard(goal, channelDetails.slug, open)
            }
        }
        if (goalState.loading && goalState.value == null) {
            SkeletonBlock(Modifier.fillMaxWidth().height(96.dp), RoundedCornerShape(8.dp))
        }
        if ((linksState.loading || (!linksState.loaded && !linksState.failed)) && linksState.value == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { SkeletonBlock(Modifier.weight(1f).aspectRatio(9f / 16f), RoundedCornerShape(4.dp)) }
            }
        }
        if (linksState.failed || goalState.failed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.channel_about_load_failed), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
            }
        }
        if (linkFailed) Text(stringResource(R.string.channel_about_link_failed), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        linksState.value?.let { links ->
            if (links.isEmpty()) Text(stringResource(R.string.channel_about_no_links), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            else ChannelLinksGrid(links, open)
        }
    }
}

@Composable
internal fun AboutChannelInfoCard(details: ChannelDetail, onLinkClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.channel_about_username, details.user?.username ?: details.slug), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (details.verified) VerifiedBadge(Modifier.size(18.dp), colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimaryContainer))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(FormatUtils.formatFollowersCount(details.followersCount, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.followers), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val user = details.user
            val socials = listOf(Triple("Discord", user?.discord, R.drawable.discord_logo), Triple("X", user?.twitter, R.drawable.x_logo),
                Triple("Instagram", user?.instagram, R.drawable.instagram_logo), Triple("YouTube", user?.youtube, R.drawable.youtube_logo),
                Triple("TikTok", user?.tiktok, R.drawable.tiktok_logo), Triple("Facebook", user?.facebook, null))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                socials.forEach { (platform, handle, drawable) ->
                    socialChannelUrl(platform, handle)?.let { url ->
                        TextButton(onClick = { onLinkClick(url) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            if (drawable != null) Icon(painterResource(drawable), platform, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(6.dp))
                            Text(if (handle?.startsWith("http") == true) platform else handle.orEmpty(),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text(user?.bio?.takeIf { it.isNotBlank() } ?: stringResource(R.string.channel_about_no_bio),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SubscriptionGoalCard(goal: KickGoalDto, slug: String, onLinkClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loader = remember(context) { ImageLoader.Builder(context).components { add(AnimatedImageDecoder.Factory()) }.build() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    val progress = if (goal.targetValue > 0) (goal.currentValue.toFloat() / goal.targetValue).coerceIn(0f, 1f) else 0f
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.channel_goal_remaining, (goal.targetValue - goal.currentValue).coerceAtLeast(0)),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onLinkClick("https://kick.com/$slug/subscribe") }) {
                    Icon(painterResource(R.drawable.kick_logo), stringResource(R.string.channel_goal_subscribe), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.CenterStart) {
                    StripedProgressBar(progress, Modifier.fillMaxWidth().height(16.dp))
                    val emoji = goal.progressBarEmojiId?.takeIf { it.isNotBlank() }
                    if (emoji != null) BoxWithConstraints(Modifier.fillMaxSize()) {
                        val position = (maxWidth - 36.dp) * progress
                        val float = rememberInfiniteTransition(label = "goal_emoji")
                            .animateFloat(0f, -4f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "goal_emoji_float")
                        AsyncImage(model = "https://files.kick.com/$emoji/fullsize", contentDescription = null,
                            imageLoader = loader, modifier = Modifier.offset(x = position).size(36.dp)
                                .graphicsLayer { translationY = float.value.dp.toPx() })
                    }
                }
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text("${NumberFormat.getIntegerInstance().format(goal.currentValue)} / ${FormatUtils.formatFollowersCount(goal.targetValue.toString(), androidx.compose.ui.platform.LocalConfiguration.current.locales[0])}",
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun StripedProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.surfaceContainerHighest
    val stripes = rememberInfiniteTransition(label = "subscription_stripes")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(2_000, easing = androidx.compose.animation.core.LinearEasing)), label = "stripe_offset")
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }) {
        drawRect(background)
        val width = size.width * progress
        clipRect(right = width) {
            drawRect(Brush.horizontalGradient(listOf(Color(0xFFFFA800), Color(0xFFFFC700))), size = Size(width, size.height))
            val spacing = 14.dp.toPx()
            // One full stripe period per cycle makes the rightward motion seamless.
            var x = -size.height - spacing + stripes.value * spacing
            while (x < width + size.height) {
                drawLine(Color.White.copy(alpha = 0.3f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 5.dp.toPx())
                x += spacing
            }
        }
    }
}

@Composable
internal fun ChannelLinksGrid(links: List<KickChannelLinkDto>, onLinkClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        links.sortedBy { it.order }.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { link ->
                    val url = safeChannelUrl(link.link)
                    Surface(Modifier.weight(1f).clickable(enabled = url != null) { url?.let(onLinkClick) },
                        shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column {
                            if (link.image != null) AsyncImage(
                                model = remember(link.image.url) { ImageRequest.Builder(context).data(link.image.url).crossfade(true)
                                    .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED).build() },
                                contentDescription = link.title ?: link.description,
                                modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f), contentScale = ContentScale.Crop)
                            if (!link.title.isNullOrBlank()) Text(link.title, Modifier.padding(8.dp), fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge)
                            if (!link.description.isNullOrBlank()) Text(link.description, Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
