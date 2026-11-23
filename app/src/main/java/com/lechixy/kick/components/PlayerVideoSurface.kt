package com.lechixy.kick.components

import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
) {
    val currentZoom = rememberUpdatedState(zoom)
    AndroidView(
        factory = { context ->
            // The texture surface follows Compose transforms and clipping.
            (LayoutInflater.from(context).inflate(R.layout.kick_player_view, null) as PlayerView).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyVideoZoom(currentZoom.value()) }
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
            view.applyVideoZoom(currentZoom.value())
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

private fun PlayerView.applyVideoZoom(zoom: PlayerVideoZoom) {
    // Keep the texture at viewport size while zooming and panning the rendered view.
    scaleX = zoom.scale
    scaleY = zoom.scale
    translationX = zoom.normalizedTranslation.x * width
    translationY = zoom.normalizedTranslation.y * height
}
