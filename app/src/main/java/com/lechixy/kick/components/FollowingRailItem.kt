package com.lechixy.kick.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lechixy.kick.R
import com.lechixy.kick.components.account.AccountAvatar
import com.lechixy.kick.data.following.model.FollowedChannel
import kotlinx.coroutines.delay
import java.text.NumberFormat

@Composable
internal fun FollowingRailItem(channel: FollowedChannel, selected: Boolean, onClick: () -> Unit,
    expanded: Boolean = false, modifier: Modifier = Modifier) {
    val description = stringResource(
        if (channel.live) R.string.navigation_following_live else R.string.navigation_following_offline,
        channel.username
    )
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    var showPreview by remember { mutableStateOf(false) }
    LaunchedEffect(hovered, channel.live) {
        showPreview = false
        if (hovered && channel.live) {
            delay(350)
            showPreview = true
        }
    }
    Box(modifier.then(if (channel.live) Modifier.hoverable(interactions) else Modifier)) {
        RailNavigationItem(expanded = expanded, selected = selected, label = channel.username,
            description = description, onClick = {
                showPreview = false
                onClick()
            }, supportingText = channel.title?.takeIf { channel.live }.orEmpty(),
            muted = !channel.live,
            icon = { FollowingAvatar(channel) })
        if (showPreview && hovered && channel.live) FollowingRailPreview(channel)
    }
}

@Composable
private fun FollowingRailPreview(channel: FollowedChannel) {
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
                val right = anchorBounds.right + gap
                val left = anchorBounds.left - gap - popupContentSize.width
                val x = if (layoutDirection == LayoutDirection.Rtl && left >= 0 || right > maxX) left else right
                return IntOffset(x.coerceIn(0, maxX),
                    (anchorBounds.center.y - popupContentSize.height / 2).coerceIn(0, maxY))
            }
        }
    }
    Popup(position, properties = PopupProperties(dismissOnClickOutside = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(channel.title?.takeIf { it.isNotBlank() } ?: channel.username,
                    style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                channel.viewers?.takeIf { channel.showViewers }?.let { count ->
                    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                    Text(stringResource(R.string.navigation_following_viewers, NumberFormat.getIntegerInstance(locale).format(count)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FollowingAvatar(channel: FollowedChannel) {
    Box(Modifier.size(36.dp)) {
        AccountAvatar(channel.avatar, channel.username, Modifier.fillMaxSize())
        if (channel.live) {
            Box(Modifier.align(Alignment.BottomEnd).size(9.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape))
        } else {
            Box(Modifier.matchParentSize().clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)))
        }
    }
}

@Composable
internal fun FollowingRailHeader(expanded: Boolean) {
    val headingHeight = (40f * LocalDensity.current.fontScale).coerceAtLeast(40f).dp
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
        Box(Modifier.fillMaxWidth().height(headingHeight).clipToBounds()) {
            Crossfade(expanded, animationSpec = tween(220), label = "following heading") { wide ->
                Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(if (wide) 240.dp else 80.dp).height(headingHeight),
                    contentAlignment = Alignment.CenterStart) {
                    Text(stringResource(R.string.following),
                        Modifier.fillMaxWidth().padding(horizontal = if (wide) 20.dp else 4.dp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
