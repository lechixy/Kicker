package com.lechixy.kick.components.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
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
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.util.getResponsiveImageUrl

internal fun Density.offlineBannerCompactHeight(fullHeightPx: Int): Int =
    minOf(128.dp.roundToPx(), (fullHeightPx * 0.75f).roundToInt())

@Composable
internal fun OfflineChannelBanner(
    channel: ChannelDetail,
    bannerUrl: String?,
    collapseProgress: State<Float>,
    onToggle: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTravelChanged: (Float) -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragFinished: () -> Unit = {},
    onDismissDrag: ((Float) -> Unit)? = null,
    onDismissDragFinished: (velocity: Float, cancelled: Boolean) -> Unit = { _, _ -> },
    isWide: Boolean = false,
    targetWidthPx: Int = 1080
) {
    val compact by remember(collapseProgress) { derivedStateOf { collapseProgress.value >= 0.5f } }
    val drag by rememberUpdatedState(onDrag)
    val finishDrag by rememberUpdatedState(onDragFinished)
    val dismissDrag by rememberUpdatedState(onDismissDrag)
    val finishDismiss by rememberUpdatedState(onDismissDragFinished)
    Box(modifier.fillMaxWidth().layout { measurable, constraints ->
        val normal = (constraints.maxWidth * 9f / 16f).roundToInt().coerceAtMost(constraints.maxHeight)
        val compact = offlineBannerCompactHeight(normal)
        onTravelChanged((normal - compact).toFloat())
        val height = (normal + (compact - normal) * collapseProgress.value).roundToInt()
        val child = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
        layout(child.width, child.height) { child.place(0, 0) }
    }.clipToBounds()
        .clickable(
            onClickLabel = stringResource(if (compact) R.string.offline_banner_expand else R.string.offline_banner_collapse),
            onClick = onToggle
        )
        .pointerInput(collapseProgress) {
            val tracker = VelocityTracker()
            var dismissGesture: Boolean? = null
            detectVerticalDragGestures(
                onDragStart = { dismissGesture = null; tracker.resetTracking() },
                onDragEnd = {
                    if (dismissGesture == true) finishDismiss(tracker.calculateVelocity().y, false)
                    else finishDrag()
                },
                onDragCancel = {
                    if (dismissGesture == true) finishDismiss(0f, true) else finishDrag()
                }
            ) { change, amount ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                if (dismissGesture == null) {
                    dismissGesture = amount > 0f && collapseProgress.value == 0f && dismissDrag != null
                }
                if (dismissGesture == true) dismissDrag?.invoke(amount) else drag(-amount)
            }
        }.background(Color.Black), contentAlignment = Alignment.Center) {
        if (!bannerUrl.isNullOrBlank()) {
            FittedBannerImage(bannerUrl)
        } else Text(stringResource(R.string.channel_offline), color = Color.LightGray)

        val compactBanner = remember(channel.bannerImage, bannerUrl, targetWidthPx) {
            channel.bannerImage?.let {
                getResponsiveImageUrl(it.srcset, it.src, it.url, it.responsive, targetWidthPx)
            } ?: bannerUrl
        }
        Box(Modifier.matchParentSize().graphicsLayer { alpha = collapseProgress.value }.background(Color.Black)) {
            FittedBannerImage(compactBanner)
            val username = channel.user?.username?.takeIf { it.isNotBlank() } ?: channel.slug
            Row(
                Modifier.align(Alignment.Center)
                    .padding(horizontal = 60.dp)
                    .semantics { if (!compact) hideFromAccessibility() }
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isWide) OfflineBadge()
                Text(
                    text = if (isWide) stringResource(R.string.offline_channel_name, username) else username,
                    color = Color.White,
                    style = if (isWide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    AsyncImage(url, null,
        Modifier.fillMaxSize().blur(24.dp, BlurredEdgeTreatment.Unbounded).graphicsLayer { alpha = 0.75f },
        alignment = Alignment.Center, contentScale = ContentScale.Crop)
    AsyncImage(url, null, Modifier.fillMaxSize(), alignment = Alignment.Center, contentScale = ContentScale.Fit)
}
