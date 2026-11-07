package com.lechixy.kick.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import com.lechixy.kick.components.account.AccountAvatar
import com.lechixy.kick.data.following.model.FollowedChannel

@Composable
internal fun FollowingRailItem(channel: FollowedChannel, selected: Boolean, onClick: () -> Unit,
    expanded: Boolean = false, modifier: Modifier = Modifier) {
    val description = stringResource(
        if (channel.live) R.string.navigation_following_live else R.string.navigation_following_offline,
        channel.username
    )
    RailNavigationItem(expanded = expanded, selected = selected, label = channel.username,
        description = description, onClick = onClick, modifier = modifier,
        supportingText = channel.title?.takeIf { channel.live }.orEmpty(),
        muted = !channel.live,
        icon = { FollowingAvatar(channel) })
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
