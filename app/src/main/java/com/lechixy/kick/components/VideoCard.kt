package com.lechixy.kick.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.channel.model.playbackId
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.buildOptimizedImageRequest

@Composable
fun VideoCardSkeleton(modifier: Modifier = Modifier) {
    MediaCardSkeleton(modifier, withAvatar = false)
}

@Composable
fun VideoCard(
    video: ChannelVideo,
    modifier: Modifier = Modifier,
    progressId: String? = video.video?.uuid ?: video.uuid ?: video.playbackId,
    onDownload: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val thumbnailUrl = remember(video.thumbnail) {
        FormatUtils.extractVideoThumbnailUrl(video.thumbnail)
    }

    Card(
        onClick,
        modifier = modifier
            .fillMaxWidth(),
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
            ) {
                AsyncImage(
                    model = remember(thumbnailUrl) {
                        buildOptimizedImageRequest(
                            context = context,
                            data = thumbnailUrl,
                            targetWidthDp = 400,
                            targetHeightDp = 225
                        )
                    },
                    contentDescription = video.sessionTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (onDownload != null) {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = .7f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            painterResource(R.drawable.download_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            stringResource(R.string.recording_download),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Süre etiketi (Sağ alt)
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = FormatUtils.formatDuration(video.duration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // İzlenme sayısı (Sol alt)
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.views_count, FormatUtils.formatCompactCount(video.views, locale)),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            PlaybackProgressBar(progressId)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.sessionTitle ?: "Untitled Broadcast",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                video.categories.firstOrNull()?.let { cat ->
                    Text(
                        text = cat.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val date = FormatUtils.formatRelativeTime(context, video.startTime)
                    ?: FormatUtils.formatRelativeTime(context, video.createdAt)
                if (date != null) {
                    Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
