package com.lechixy.kick.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

import com.lechixy.kick.R
import com.lechixy.kick.data.common.model.Livestream
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.buildOptimizedImageRequest

@Composable
fun StreamCardSkeleton(modifier: Modifier = Modifier) {
    MediaCardSkeleton(modifier, withAvatar = true)
}

@Composable
fun StreamCard(
    stream: Livestream,
    modifier: Modifier = Modifier,
    isDataSaverActive: Boolean = false,
    onClick: () -> Unit = {},
    onClickWithSource: ((ExpansionSource) -> Unit)? = null,
    recordingId: String? = null,
    onCategoryClick: (String) -> Unit = com.lechixy.kick.components.LocalContentNavigation.current.category
) {
    val context = LocalContext.current
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var thumbnailCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val handleCardClick = {
        if (onClickWithSource != null) {
            val cardRect = cardCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
            val thumbRect = thumbnailCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
            if (cardRect != null) {
                val source = ExpansionSource(
                    cardRect = cardRect,
                    mediaRect = thumbRect,
                    cornerRadius = 12.dp,
                    thumbnailUrl = if (!isDataSaverActive) stream.thumbnail?.src else null,
                    avatarUrl = stream.channel?.profilePic,
                    title = stream.title,
                    username = stream.channel?.username,
                    category = stream.category?.name,
                    viewerCount = stream.viewerCount,
                    isLive = true,
                    isStreamCard = true
                )
                onClickWithSource(source)
            } else {
                onClick()
            }
        } else {
            onClick()
        }
    }

    Card(
        onClick = handleCardClick,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { cardCoordinates = it },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .onGloballyPositioned { thumbnailCoordinates = it },
                contentAlignment = Alignment.Center
            ) {
                if (isDataSaverActive) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.energy_savings_leaf_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = "Data Saver",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    AsyncImage(
                        model = remember(stream.thumbnail?.src) {
                            buildOptimizedImageRequest(
                                context = context,
                                data = stream.thumbnail?.src,
                                targetWidthDp = 400,
                                targetHeightDp = 225
                            )
                        },
                        contentDescription = stream.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LiveBadge()

                    if (stream.showViewCount) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.viewers_count, FormatUtils.formatViewersCount(stream.viewerCount, androidx.compose.ui.platform.LocalConfiguration.current.locales[0])),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            PlaybackProgressBar(recordingId)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = remember(stream.channel?.profilePic) {
                        buildOptimizedImageRequest(
                            context = context,
                            data = stream.channel?.profilePic,
                            targetWidthDp = 44,
                            targetHeightDp = 44
                        )
                    },
                    contentDescription = stream.channel?.username,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stream.title ?: "No Title",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stream.channel?.username ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    stream.category?.let { cat ->
                        Text(
                            text = cat.name,
                            modifier = Modifier.clickable { onCategoryClick(cat.slug) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
