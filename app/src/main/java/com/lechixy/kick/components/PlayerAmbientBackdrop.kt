package com.lechixy.kick.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView
import com.lechixy.kick.data.settings.model.SettingsKeys
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.sync.Mutex

internal class PlayerAmbientState {
    var bounds by mutableStateOf<AmbientVideoBounds?>(null)
    var frame by mutableStateOf<AmbientFrame?>(null)
    var intensity by mutableFloatStateOf(1f)
    var spread by mutableFloatStateOf(1f)
    var fullscreenEnabled by mutableStateOf(true)
    var throughUi by mutableStateOf(true)
    var chatOpacity by mutableFloatStateOf(SettingsKeys.AMBIENT_CHAT_OPACITY.defaultValue / 100f)
    var navigationOpacity by mutableFloatStateOf(SettingsKeys.AMBIENT_NAVIGATION_OPACITY.defaultValue / 100f)
    var videoAspectRatio by mutableFloatStateOf(16f / 9f)
    val liveCopyLock = Mutex()
    var captureOwner: Any? = null
    var captureSource: Any? = null
}

internal data class AmbientVideoBounds(
    val videoInPlayer: Rect,
    val videoInWindow: Rect,
    val hasLetterboxSpace: Boolean,
)

internal sealed interface AmbientFrame {
    class Palette(val colors: List<Color>, val bars: AmbientBlackBars = AmbientBlackBars()) : AmbientFrame
    class Projection(val bitmap: Bitmap) : AmbientFrame
}

internal val LocalPlayerAmbientState = staticCompositionLocalOf<PlayerAmbientState?> { null }

@Composable
internal fun rememberPlayerAmbientState(host: PlayerHostState): PlayerAmbientState =
    remember(host.slug, host.recording?.id, host.channel?.livestream?.id) { PlayerAmbientState() }

@Composable
internal fun ambientUiVisible(state: PlayerAmbientState?): Boolean {
    val visible by remember(state) {
        derivedStateOf { state != null && state.throughUi && state.frame != null }
    }
    return visible
}

@Composable
internal fun BoxScope.NavigationAmbientBackdrop(state: PlayerAmbientState) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    val background = MaterialTheme.colorScheme.background
    PlayerAmbientBackdrop(
        state = state,
        modifier = Modifier.matchParentSize().onGloballyPositioned { origin = it.positionInWindow() },
        videoBounds = {
            origin?.let { state.bounds?.videoInWindow?.translate(-it) }
        },
        backdropColor = { background },
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerAmbientState.updateBounds(
    texture: TextureView?,
    coordinates: LayoutCoordinates?,
    zoom: PlayerVideoZoom = PlayerVideoZoom(),
) {
    val player = generateSequence(texture?.parent) { it.parent }.filterIsInstance<PlayerView>().firstOrNull()
    if (texture == null || player == null || coordinates == null || !coordinates.isAttached ||
        texture.width <= 0 || texture.height <= 0
    ) {
        bounds = null
        return
    }
    val nativeBounds = android.graphics.Rect(0, 0, texture.width, texture.height)
    player.offsetDescendantRectToMyCoords(texture, nativeBounds)
    val video = zoom.mapBounds(Rect(
        nativeBounds.left.toFloat(), nativeBounds.top.toFloat(),
        nativeBounds.right.toFloat(), nativeBounds.bottom.toFloat(),
    ), coordinates.size)
    val window = Rect(coordinates.localToWindow(video.topLeft), coordinates.localToWindow(video.bottomRight))
    val hasSpace = video.left > 1 || video.top > 1 ||
        coordinates.size.width - video.right > 1 || coordinates.size.height - video.bottom > 1
    bounds = AmbientVideoBounds(video, window, hasSpace)
    val videoSize = player.player?.videoSize
    videoAspectRatio = if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
        videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
    } else {
        video.width / video.height
    }
}

@Composable
internal fun TrackAmbientVideoLayout(
    state: PlayerAmbientState,
    texture: TextureView?,
    zoom: () -> PlayerVideoZoom = { PlayerVideoZoom() },
    coordinates: () -> LayoutCoordinates?,
) {
    val currentCoordinates by rememberUpdatedState(coordinates)
    val currentZoom by rememberUpdatedState(zoom)
    LaunchedEffect(state, texture) {
        snapshotFlow { currentZoom() }.collect { state.updateBounds(texture, currentCoordinates(), it) }
    }
    DisposableEffect(state, texture) {
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            state.updateBounds(texture, currentCoordinates(), currentZoom())
        }
        val parent = texture?.parent as? ViewGroup
        texture?.addOnLayoutChangeListener(listener)
        parent?.addOnLayoutChangeListener(listener)
        state.updateBounds(texture, currentCoordinates(), currentZoom())
        onDispose {
            parent?.removeOnLayoutChangeListener(listener)
            texture?.removeOnLayoutChangeListener(listener)
            state.bounds = null
        }
    }
}

@Composable
internal fun PlayerAmbientBackdrop(
    state: PlayerAmbientState,
    modifier: Modifier = Modifier,
    videoBounds: () -> Rect? = { state.bounds?.videoInPlayer },
    backdropColor: (() -> Color)? = null,
    opacity: () -> Float = { 1f },
) {
    val lightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    Canvas(modifier) {
        val frame = state.frame ?: return@Canvas
        val layerAlpha = opacity()
        if (layerAlpha <= 0f) return@Canvas
        val video = videoBounds() ?: return@Canvas
        if (video.isEmpty || !video.left.isFinite() || !video.top.isFinite() ||
            !video.right.isFinite() || !video.bottom.isFinite()
        ) return@Canvas
        val spread = min(112.dp.toPx(), min(video.width, video.height) * 0.55f) * state.spread
        val alpha = (if (lightTheme) 0.18f else 0.38f) * state.intensity * layerAlpha
        val glow = video.inflate(spread)
        val projection = (frame as? AmbientFrame.Projection)?.let { ambientImageShader(it.bitmap, video) }
        val drawGlow: DrawScope.() -> Unit = {
            when (frame) {
                is AmbientFrame.Palette -> drawAmbientPalette(frame.colors, video, spread, alpha)
                is AmbientFrame.Projection -> drawAmbientProjection(checkNotNull(projection), video, glow, alpha, paint)
            }
        }
        if (backdropColor != null) {
            drawRect(backdropColor().copy(alpha = layerAlpha))
            // Fill the exposed viewport even when the resting video slot is uncovered.
            val fillAlpha = alpha * 0.35f
            when (frame) {
                is AmbientFrame.Palette -> {
                    val colors = frame.colors
                    var red = 0f
                    var green = 0f
                    var blue = 0f
                    colors.forEach { color ->
                        red += color.red
                        green += color.green
                        blue += color.blue
                    }
                    val tint = Color(red / colors.size, green / colors.size, blue / colors.size, fillAlpha)
                    drawRect(tint)
                }
                is AmbientFrame.Projection -> {
                    paint.shader = projection
                    paint.alpha = (fillAlpha * 255).toInt()
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                    paint.shader = null
                }
            }
            clipRect { drawGlow() }
        } else {
            clipRect(video.left, video.top, video.right, video.bottom, ClipOp.Difference, drawGlow)
        }
    }
}

internal fun fitAmbientVideo(slot: IntRect, aspectRatio: Float): Rect {
    val width = min(slot.width.toFloat(), slot.height * aspectRatio)
    val height = width / aspectRatio
    val left = slot.left + (slot.width - width) / 2f
    val top = slot.top + (slot.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private val ambientPaletteAnchors = listOf(
    Offset(0.12f, 0f), Offset(0.5f, 0f), Offset(0.88f, 0f),
    Offset(0f, 0.5f), Offset(1f, 0.5f),
    Offset(0.12f, 1f), Offset(0.5f, 1f), Offset(0.88f, 1f),
)

private fun DrawScope.drawAmbientPalette(colors: List<Color>, video: Rect, spread: Float, alpha: Float) {
    ambientPaletteAnchors.forEachIndexed { index, anchor ->
        val horizontalEdge = anchor.y != 0.5f
        val along = max(spread, (if (horizontalEdge) video.width else video.height) * 0.38f)
        val center = Offset(video.left + video.width * anchor.x, video.top + video.height * anchor.y)
        val width = if (horizontalEdge) along else spread
        val height = if (horizontalEdge) spread else along
        val canvas = drawContext.canvas
        canvas.save()
        try {
            canvas.translate(center.x, center.y)
            canvas.scale(width, height)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(colors[index].copy(alpha = alpha), Color.Transparent),
                    center = Offset.Zero,
                    radius = 1f,
                ),
                radius = 1f,
                center = Offset.Zero,
            )
        } finally {
            canvas.restore()
        }
    }
}

private val ambientFadeColors = intArrayOf(0, -1, -1, 0)

private fun ambientImageShader(bitmap: Bitmap, video: Rect): BitmapShader =
    BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply {
            setScale(video.width / bitmap.width, video.height / bitmap.height)
            postTranslate(video.left, video.top)
        })
    }

private fun DrawScope.drawAmbientProjection(image: BitmapShader, video: Rect, glow: Rect, alpha: Float, paint: Paint) {
    // Clamp the blurred image to its edge pixels, then fade it along both axes.
    val horizontal = LinearGradient(
        glow.left, 0f, glow.right, 0f, ambientFadeColors,
        floatArrayOf(0f, (video.left - glow.left) / glow.width, (video.right - glow.left) / glow.width, 1f),
        Shader.TileMode.CLAMP,
    )
    val vertical = LinearGradient(
        0f, glow.top, 0f, glow.bottom, ambientFadeColors,
        floatArrayOf(0f, (video.top - glow.top) / glow.height, (video.bottom - glow.top) / glow.height, 1f),
        Shader.TileMode.CLAMP,
    )
    val mask = ComposeShader(horizontal, vertical, PorterDuff.Mode.DST_IN)
    paint.shader = ComposeShader(image, mask, PorterDuff.Mode.DST_IN)
    paint.alpha = (alpha * 255).toInt()
    drawContext.canvas.nativeCanvas.drawRect(glow.left, glow.top, glow.right, glow.bottom, paint)
    paint.shader = null
}
