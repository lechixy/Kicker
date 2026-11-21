package com.lechixy.kick.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lechixy.kick.components.channel.chat.FullscreenChatBackdropRegion
import com.lechixy.kick.components.channel.chat.FullscreenChatVideoFrame
import com.lechixy.kick.data.settings.model.FullscreenChatBlurStyle
import com.lechixy.kick.data.settings.model.FullscreenChatShape
import kotlin.math.roundToInt

internal val playbackButtonEnter = scaleIn(
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
    initialScale = 0.5f,
)
internal val playbackButtonExit = scaleOut(tween(160), targetScale = 0.65f)

@Stable
internal class FrostedPlayerRegions {
    val regions = mutableStateMapOf<Any, FullscreenChatBackdropRegion>()
}

@Composable
internal fun FrostedPlaybackButton(
    onClick: () -> Unit,
    frame: State<FullscreenChatVideoFrame?>?,
    modifier: Modifier = Modifier,
    backdrop: FrostedPlayerRegions? = null,
    videoReady: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        FrostedPlayerControl(frame, backdrop, videoReady)
        IconButton(onClick, modifier = Modifier.matchParentSize(), content = content)
    }
}

@Composable
internal fun BoxScope.FrostedPlayerControl(
    frame: State<FullscreenChatVideoFrame?>?,
    backdrop: FrostedPlayerRegions? = null,
    videoReady: Boolean = false,
    followsControlsOpacity: Boolean = true,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val key = remember { Any() }
    DisposableEffect(backdrop) { onDispose { backdrop?.regions?.remove(key) } }
    Box(Modifier.matchParentSize().onGloballyPositioned {
        origin = it.positionInWindow()
        if (backdrop != null) {
            backdrop.regions[key] = FullscreenChatBackdropRegion(
                it.boundsInWindow(),
                shape = FullscreenChatShape.Pill,
                frostedControl = true,
                style = FullscreenChatBlurStyle.FrostedGlass,
                followsControlsOpacity = followsControlsOpacity,
            )
        }
    }.clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.1f), CircleShape).drawBehind {
        val sample = frame?.value
        val bitmap = sample?.frostedBitmap
        if (sample != null && bitmap != null) {
            drawImage(
                bitmap,
                dstOffset = IntOffset(
                    (sample.origin.x - origin.x).roundToInt(),
                    (sample.origin.y - origin.y).roundToInt(),
                ),
                dstSize = sample.size,
                filterQuality = FilterQuality.Low,
            )
        }
        val hasBackdrop = videoReady && (frame == null || bitmap != null)
        drawRect(Color.Black.copy(alpha = if (hasBackdrop) 0.24f else 0.38f))
    })
}
