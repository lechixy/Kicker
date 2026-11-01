package com.lechixy.kick.components.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.ChannelDetail

@Composable
internal fun OfflineChannelBanner(
    channel: ChannelDetail,
    bannerUrl: String?,
    collapseProgress: State<Float>,
    onCollapse: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTravelChanged: (Float) -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragFinished: () -> Unit = {},
    onDismissDrag: ((Float) -> Unit)? = null,
    onDismissDragFinished: (velocity: Float, cancelled: Boolean) -> Unit = { _, _ -> }
) {
    Box(modifier.fillMaxWidth().layout { measurable, constraints ->
        val normal = (constraints.maxWidth * 9f / 16f).roundToInt()
        val compact = 84.dp.roundToPx().coerceAtMost(normal)
        onTravelChanged((normal - compact).toFloat())
        val height = (normal + (compact - normal) * collapseProgress.value).roundToInt()
        val child = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
        layout(child.width, child.height) { child.place(0, 0) }
    }.clipToBounds()
        .clickable(onClickLabel = stringResource(R.string.offline_banner_collapse), onClick = onCollapse)
        .pointerInput(onCollapse) {
            val tracker = VelocityTracker()
            var dismissGesture: Boolean? = null
            detectVerticalDragGestures(
                onDragStart = { dismissGesture = null; tracker.resetTracking() },
                onDragEnd = {
                    if (dismissGesture == true) onDismissDragFinished(tracker.calculateVelocity().y, false)
                    else onDragFinished()
                },
                onDragCancel = {
                    if (dismissGesture == true) onDismissDragFinished(0f, true) else onDragFinished()
                }
            ) { change, amount ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                if (dismissGesture == null) dismissGesture = amount > 0f && onDismissDrag != null
                if (dismissGesture == true) onDismissDrag?.invoke(amount) else onDrag(-amount)
            }
        }.background(Color.Black), contentAlignment = Alignment.Center) {
        if (!bannerUrl.isNullOrBlank()) {
            FittedBannerImage(bannerUrl)
        } else Text(stringResource(R.string.channel_offline), color = Color.LightGray)

        val compactBanner = channel.bannerImage?.src ?: channel.bannerImage?.url ?: bannerUrl
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = collapseProgress.value }) {
            FittedBannerImage(compactBanner)
        }
        Row(Modifier.align(Alignment.CenterStart)
            .padding(start = 68.dp, end = 12.dp)
            .graphicsLayer { alpha = collapseProgress.value }
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OfflineBadge()
            Text(stringResource(R.string.offline_channel_name, channel.user?.username ?: channel.slug),
                color = Color.White, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Match the player's plain icon controls, with a full touch target and an inset.
        IconButton(onClick = onBackClick, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(48.dp)) {
            Icon(painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                stringResource(R.string.offline_minimize), tint = Color.White)
        }
    }
}

@Composable
private fun FittedBannerImage(url: String?) {
    // Preserve the entire image; a blurred copy fills the unused area on wide/tall displays.
    AsyncImage(url, null, Modifier.fillMaxSize().blur(24.dp).graphicsLayer { alpha = 0.55f }, contentScale = ContentScale.Crop)
    AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
}
