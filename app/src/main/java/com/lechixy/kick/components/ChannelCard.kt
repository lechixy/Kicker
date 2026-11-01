package com.lechixy.kick.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import coil3.compose.AsyncImage

import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.util.buildOptimizedImageRequest

@Composable
fun ChannelCardSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SkeletonBlock(Modifier.size(46.dp), CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(18.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(12.dp))
        }
    }
}

@Composable
fun ChannelCard(
    channel: ChannelDetail,
    modifier: Modifier = Modifier,
    onChannelClick: (channelSlug: String) -> Unit = {},
    onChannelClickWithSource: ((channelSlug: String, source: ExpansionSource) -> Unit)? = null
) {
    val context = LocalContext.current
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var avatarCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val handleCardClick = {
        val cardRect = cardCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
        val avatarRect = avatarCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
        if (cardRect != null && onChannelClickWithSource != null) {
            val source = ExpansionSource(
                cardRect = cardRect,
                mediaRect = avatarRect,
                cornerRadius = 12.dp,
                thumbnailUrl = channel.livestream?.thumbnail?.src,
                avatarUrl = channel.user?.profilePic,
                title = channel.user?.username,
                username = channel.slug,
                isLive = channel.livestream != null,
                isStreamCard = false
            )
            onChannelClickWithSource(channel.slug, source)
        } else {
            onChannelClick(channel.slug)
        }
    }

    Card(
        onClick = handleCardClick,
        modifier = modifier
            .widthIn(min = 280.dp)
            .onGloballyPositioned { cardCoordinates = it },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = remember(channel.user?.profilePic) {
                    buildOptimizedImageRequest(
                        context = context,
                        data = channel.user?.profilePic,
                        targetWidthDp = 48,
                        targetHeightDp = 48
                    )
                },
                contentDescription = channel.user?.username,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .onGloballyPositioned { avatarCoordinates = it },
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = channel.user?.username ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.verified) {
                        VerifiedBadge(
                            modifier = Modifier.size(14.dp),
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
                Text(
                    text = channel.slug,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (channel.livestream != null) {
                LiveBadge()
            }
        }
    }
}
