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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

import com.lechixy.kick.R
import com.lechixy.kick.components.channel.MetadataPill
import com.lechixy.kick.components.channel.TagChip
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
    onCategoryClick: (String) -> Unit = LocalContentNavigation.current.category,
    compact: Boolean = false,
    compactViewerCount: Boolean = compact,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val avatarSize = if (compact) 32 else 40
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
                    viewerCount = stream.viewerCount.takeIf { stream.showViewCount },
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

    InteractiveCard(
        onClick = handleCardClick,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { cardCoordinates = it },
    ) { mediaZoom ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(if (compact) RoundedCornerShape(8.dp) else RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
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
                        modifier = Modifier.fillMaxSize().then(mediaZoom),
                        contentScale = ContentScale.Crop
                    )
                }

                Row(
                    modifier = Modifier
                        .align(if (compact) Alignment.TopStart else Alignment.BottomStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!compact || !stream.showViewCount) LiveBadge()

                    if (stream.showViewCount) {
                        Surface(
                            color = Color.Black.copy(alpha = if (compact) 0.75f else 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            val count = if (compactViewerCount) {
                                FormatUtils.formatCompactCount(stream.viewerCount.toLong(), locale)
                            } else {
                                FormatUtils.formatViewersCount(stream.viewerCount, locale)
                            }
                            MediaCardViewCount(
                                count = count,
                                description = stringResource(R.string.viewers),
                                fontWeight = if (compact) FontWeight.Bold else null,
                            )
                        }
                    }
                    if (stream.isMature) {
                        TagChip(
                            tag = stringResource(R.string.channel_mature_badge),
                            containerColor = Color.Black.copy(alpha = if (compact) 0.75f else 0.7f),
                            contentColor = Color.White,
                        )
                    }
                }
                PlaybackProgressBar(recordingId, Modifier.align(Alignment.BottomCenter))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (compact) 8.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = remember(stream.channel?.profilePic, avatarSize) {
                        buildOptimizedImageRequest(
                            context = context,
                            data = stream.channel?.profilePic,
                            targetWidthDp = if (compact) avatarSize else 44,
                            targetHeightDp = if (compact) avatarSize else 44
                        )
                    },
                    contentDescription = stream.channel?.username,
                    modifier = Modifier
                        .size(avatarSize.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                if (compact) {
                    CompactStreamMetadata(stream, onCategoryClick, Modifier.weight(1f))
                } else Column(modifier = Modifier.weight(1f)) {
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
                    val category = stream.category
                    Text(
                        text = category?.name.orEmpty(),
                        modifier = if (category == null) Modifier else Modifier.clickable { onCategoryClick(category.slug) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStreamMetadata(stream: Livestream, onCategoryClick: (String) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            stream.channel?.username.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stream.title.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        CardTagRow(spacing = 4.dp, chipVerticalPadding = 4.dp) {
            stream.category?.let { category ->
                MetadataPill(category.name) { onCategoryClick(category.slug) }
            }
            stream.language?.takeIf(String::isNotBlank)?.let {
                MetadataPill(streamLanguageName(it))
            }
        }
    }
}
