package com.lechixy.kick.components

import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
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
    onTextureChanged: (TextureView?) -> Unit = {},
) {
    val textureCallback = rememberUpdatedState(onTextureChanged)
    AndroidView(
        factory = { context ->
            // The texture surface follows Compose transforms and clipping.
            (LayoutInflater.from(context).inflate(R.layout.kick_player_view, null) as PlayerView).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                textureCallback.value(videoSurfaceView as? TextureView)
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.keepScreenOn = isPlaying
        },
        onRelease = {
            textureCallback.value(null)
            it.player = null
        },
        modifier = modifier,
    )
}
