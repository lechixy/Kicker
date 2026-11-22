package com.lechixy.kick.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    reduceNoise: Boolean = false,
    onTextureChanged: (TextureView?) -> Unit = {},
) {
    val textureCallback = rememberUpdatedState(onTextureChanged)
    val currentZoom = rememberUpdatedState(zoom)
    val noiseReduction = remember(reduceNoise) {
        if (reduceNoise && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RenderEffect.createRuntimeShaderEffect(RuntimeShader(VIDEO_NOISE_REDUCTION_SHADER), "video")
        } else null
    }
    AndroidView(
        factory = { context ->
            // The texture surface follows Compose transforms and clipping.
            (LayoutInflater.from(context).inflate(R.layout.kick_player_view, null) as PlayerView).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyVideoZoom(currentZoom.value()) }
                textureCallback.value(videoSurfaceView as? TextureView)
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.keepScreenOn = isPlaying
            view.applyVideoZoom(currentZoom.value())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // The TextureView has its own effects for the fullscreen chat backdrop.
                view.setRenderEffect(noiseReduction)
            }
        },
        onRelease = {
            textureCallback.value(null)
            it.player = null
        },
        modifier = modifier,
    )
}

// Blend nearby colors only across small differences to preserve text and object edges.
private const val VIDEO_NOISE_REDUCTION_SHADER = """
    uniform shader video;

    half weight(half4 center, half4 neighbor) {
        half3 delta = abs(neighbor.rgb - center.rgb);
        half difference = max(delta.r, max(delta.g, delta.b));
        return (1.0 - smoothstep(0.02, 0.10, difference)) * neighbor.a;
    }

    half4 main(float2 xy) {
        half4 center = video.eval(xy);
        half4 left = video.eval(xy + float2(-1.0, 0.0));
        half4 right = video.eval(xy + float2(1.0, 0.0));
        half4 top = video.eval(xy + float2(0.0, -1.0));
        half4 bottom = video.eval(xy + float2(0.0, 1.0));

        half wl = weight(center, left);
        half wr = weight(center, right);
        half wt = weight(center, top);
        half wb = weight(center, bottom);
        half3 rgb = (4.0 * center.rgb + wl * left.rgb + wr * right.rgb
                    + wt * top.rgb + wb * bottom.rgb) / (4.0 + wl + wr + wt + wb);
        return half4(mix(center.rgb, rgb, center.a), center.a);
    }
"""

private fun PlayerView.applyVideoZoom(zoom: PlayerVideoZoom) {
    // Keep the texture at viewport size while zooming and panning the rendered view.
    scaleX = zoom.scale
    scaleY = zoom.scale
    translationX = zoom.normalizedTranslation.x * width
    translationY = zoom.normalizedTranslation.y * height
}
