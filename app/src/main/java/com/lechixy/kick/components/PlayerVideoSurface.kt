package com.lechixy.kick.components

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.lechixy.kick.R

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerVideoSurface(
    player: Player?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    zoom: () -> PlayerVideoZoom = { PlayerVideoZoom() },
    showControls: Boolean = false,
    textureState: MutableState<TextureView?>? = null,
    crop: () -> VideoBars = { VideoBars.None },
) {
    val currentZoom = rememberUpdatedState(zoom)
    val currentCrop = rememberUpdatedState(crop)
    AndroidView(
        factory = { context ->
            // The texture surface follows Compose transforms and clipping.
            (LayoutInflater.from(context).inflate(R.layout.kick_player_view, null) as PlayerView).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                val onLayout = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    applyVideoTransform(currentZoom.value(), currentCrop.value())
                }
                addOnLayoutChangeListener(onLayout)
                videoSurfaceView?.addOnLayoutChangeListener(onLayout)
            }
        },
        update = { view ->
            // AndroidView can be moved or replaced by the player host. Publish the
            // texture from the active view instead of publishing during inflation.
            textureState?.value = view.videoSurfaceView as? TextureView
            if (view.player !== player) view.player = player
            if (view.useController != showControls) {
                view.useController = showControls
                if (showControls) {
                    view.setShowPreviousButton(false)
                    view.setShowNextButton(false)
                    view.setShowSubtitleButton(false)
                    view.controllerShowTimeoutMs = 0
                    view.controllerHideOnTouch = false
                    view.showController()
                }
            }
            view.keepScreenOn = isPlaying
            view.applyVideoTransform(currentZoom.value(), currentCrop.value())
        },
        onRelease = {
            if (textureState != null && textureState.value === it.videoSurfaceView) {
                textureState.value = null
            }
            it.player = null
        },
        modifier = modifier,
    )
}

private fun PlayerView.applyVideoTransform(zoom: PlayerVideoZoom, crop: VideoBars) {
    val texture = videoSurfaceView as? TextureView
    val geometry = if (!crop.isEmpty && texture != null && texture.width > 0 && texture.height > 0) {
        videoCropGeometry(untransformedVideoBounds(texture), IntSize(width, height), crop)
    } else null
    // Clip and scale the view without changing the decoder's output or the captured frame.
    val source = geometry?.source
    clipBounds = source?.let { Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
    val scale = zoom.scale * (geometry?.scale ?: 1f)
    scaleX = scale
    scaleY = scale
    translationX = zoom.normalizedTranslation.x * width + (source?.let { (width / 2f - it.center.x) * scale } ?: 0f)
    translationY = zoom.normalizedTranslation.y * height + (source?.let { (height / 2f - it.center.y) * scale } ?: 0f)
}
