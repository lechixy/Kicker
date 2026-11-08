package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.ClipDetail
import com.lechixy.kick.data.channel.model.kickClipId
import com.lechixy.kick.data.channel.repository.ClipRepository
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.buildOptimizedImageRequest
import kotlinx.coroutines.CancellationException

internal val LocalOpenChatClip = staticCompositionLocalOf<((String) -> Unit)?> { null }

@Composable
internal fun ChatClipLinks(
    text: AnnotatedString,
    showPreviews: Boolean = true,
    content: @Composable () -> Unit,
) {
    val openClip = LocalOpenChatClip.current
    val clips = remember(text, openClip != null) {
        if (openClip == null) emptyMap()
        else text.getLinkAnnotations(0, text.length).mapNotNull { range ->
            val url = (range.item as? LinkAnnotation.Url)?.url ?: return@mapNotNull null
            kickClipId(url)?.let { url to it }
        }.toMap()
    }
    if (openClip == null || clips.isEmpty()) {
        content()
        return
    }
    val defaultHandler = LocalUriHandler.current
    val handler = remember(defaultHandler, clips, openClip) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val clipId = clips[uri]
                if (clipId == null) defaultHandler.openUri(uri)
                else openClip(clipId)
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides handler, content = content)
    if (showPreviews) {
        for (id in clips.values.distinct()) {
            key(id) { ChatClipPreview(id) { openClip(id) } }
        }
    }
}

@Composable
private fun ChatClipPreview(id: String, onClick: () -> Unit) {
    val loadPreview = LocalChatContentActive.current && !LocalInspectionMode.current
    val cachedPreview = remember(id) { ClipRepository.cachedPreview(id) }
    val clip by produceState<ClipDetail?>(cachedPreview, id, loadPreview) {
        if (!loadPreview) return@produceState
        try {
            value = ClipRepository.preview(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The preview remains tappable so the player can offer an explicit retry.
        }
    }
    val context = LocalContext.current
    val thumbnail = clip?.thumbnailUrl
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp).widthIn(max = 360.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(104.dp, 60.dp).background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    AsyncImage(
                        model = remember(context, id, thumbnail) {
                            buildOptimizedImageRequest(context, thumbnail, 104, 60).newBuilder()
                                .memoryCacheKey("chat-clip:$id")
                                .diskCacheKey("chat-clip:$id")
                                .build()
                        },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Icon(
                    painterResource(R.drawable.play_arrow_24dp_e3e3e3_fill1_wght400_grad0_opsz24),
                    contentDescription = null,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .65f)).padding(4.dp),
                    tint = Color.White,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val title = clip?.title?.trim().orEmpty()
                Text(
                    title.ifEmpty { stringResource(R.string.clip_preview_label) },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                clip?.let {
                    Text(
                        "${it.channel.username} • ${FormatUtils.formatDuration(it.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
