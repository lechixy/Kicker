package com.lechixy.kick.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lechixy.kick.components.channel.chat.FullscreenChatVideoFrame
import com.lechixy.kick.components.channel.chat.FullscreenChatBackdropRegion
import com.lechixy.kick.data.settings.model.FullscreenChatShape
import kotlin.math.roundToInt

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
    content: @Composable () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        FrostedPlayerControl(frame, backdrop)
        IconButton(onClick, modifier = Modifier.matchParentSize(), content = content)
    }
}

@Composable
internal fun BoxScope.FrostedPlayerControl(
    frame: State<FullscreenChatVideoFrame?>?,
    backdrop: FrostedPlayerRegions? = null,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val key = remember { Any() }
    DisposableEffect(backdrop) { onDispose { backdrop?.regions?.remove(key) } }
    Box(Modifier.matchParentSize().onGloballyPositioned {
        if (backdrop != null) {
            backdrop.regions[key] = FullscreenChatBackdropRegion(
                it.boundsInWindow(),
                shape = FullscreenChatShape.Pill,
                frostedControl = true,
            )
        }
    }.clip(CircleShape)) {
        if (frame != null) {
            Box(Modifier.matchParentSize().onGloballyPositioned { origin = it.positionInWindow() }
                .blur(12.dp)
                .drawBehind {
                    frame.value?.let { sample ->
                        drawImage(
                            sample.bitmap,
                            dstOffset = IntOffset(
                                (sample.origin.x - origin.x).roundToInt(),
                                (sample.origin.y - origin.y).roundToInt(),
                            ),
                            dstSize = sample.size,
                            filterQuality = FilterQuality.Low,
                        )
                    }
                })
        }
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .14f)))
    }
}
