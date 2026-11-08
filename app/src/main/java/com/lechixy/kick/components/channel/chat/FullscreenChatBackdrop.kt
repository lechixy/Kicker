package com.lechixy.kick.components.channel.chat

import android.graphics.Bitmap
import android.graphics.BlendMode as AndroidBlendMode
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Rect as AndroidRect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.TextureView
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lechixy.kick.data.settings.model.FullscreenChatShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal class FullscreenChatVideoFrame(
    val bitmap: ImageBitmap,
    val origin: IntOffset,
    val size: IntSize,
)

internal data class FullscreenChatBlur(
    val onBoundsChanged: (FullscreenChatBackdropRegion?) -> Unit,
    val legacyFrame: State<FullscreenChatVideoFrame?>? = null,
)

internal data class FullscreenChatEdges(
    val left: Boolean = false,
    val top: Boolean = false,
    val right: Boolean = false,
    val bottom: Boolean = false,
) {
    val isDocked get() = left || top || right || bottom
}

internal data class FullscreenChatBackdropRegion(
    val bounds: Rect,
    val edges: FullscreenChatEdges = FullscreenChatEdges(),
    val shape: FullscreenChatShape = FullscreenChatShape.Rounded,
)

internal data class FullscreenChatDock(val viewport: Rect, val edges: FullscreenChatEdges) {
    fun extend(bounds: Rect) = FullscreenChatBackdropRegion(
        Rect(
            if (edges.left) viewport.left else bounds.left,
            if (edges.top) viewport.top else bounds.top,
            if (edges.right) viewport.right else bounds.right,
            if (edges.bottom) viewport.bottom else bounds.bottom,
        ),
        edges,
    )
}

internal val LocalFullscreenChatDock = compositionLocalOf<FullscreenChatDock?> { null }
internal val LocalFullscreenChatShape = compositionLocalOf { FullscreenChatShape.Rounded }

internal fun FullscreenChatShape.backgroundShape(): Shape = when (this) {
    FullscreenChatShape.Square -> RectangleShape
    FullscreenChatShape.Rounded -> RoundedCornerShape(8.dp)
    FullscreenChatShape.Pill -> CircleShape
}

private fun FullscreenChatShape.radius(width: Float, height: Float, roundedRadius: Float): Float {
    val halfSide = minOf(width, height) / 2f
    return when (this) {
        FullscreenChatShape.Square -> 0f
        FullscreenChatShape.Rounded -> minOf(roundedRadius, halfSide)
        FullscreenChatShape.Pill -> halfSide
    }
}

private fun FullscreenChatEdges.cornerRadii(radius: Float) = floatArrayOf(
    if (left || top) 0f else radius,
    if (right || top) 0f else radius,
    if (right || bottom) 0f else radius,
    if (left || bottom) 0f else radius,
)

@Composable
internal fun rememberLegacyFullscreenChatFrame(
    texture: TextureView?,
    playing: Boolean,
): State<FullscreenChatVideoFrame?> = produceState<FullscreenChatVideoFrame?>(null, texture, playing) {
    if (texture == null) {
        value = null
        return@produceState
    }
    // Android 11 has no video-layer RenderEffect. Keep its readback small and reuse both buffers.
    val sampleLimit = 56
    val location = IntArray(2)
    var samples = emptyList<Bitmap>()
    var next = 0
    while (true) {
        val width = texture.width
        val height = texture.height
        if (texture.isAvailable && width > 0 && height > 0) {
            val scale = sampleLimit.toFloat() / maxOf(width, height)
            val sampleWidth = (width * scale).roundToInt().coerceAtLeast(1)
            val sampleHeight = (height * scale).roundToInt().coerceAtLeast(1)
            if (samples.firstOrNull()?.let { it.width == sampleWidth && it.height == sampleHeight } != true) {
                samples = List(2) { Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888) }
                next = 0
            }
            val sample = try {
                texture.getBitmap(samples[next])
            } catch (_: IllegalStateException) {
                null
            }
            if (sample != null) {
                texture.getLocationInWindow(location)
                value = FullscreenChatVideoFrame(sample.asImageBitmap(), IntOffset(location[0], location[1]), IntSize(width, height))
                next = 1 - next
            } else {
                value = null
            }
        } else {
            value = null
        }
        delay(if (playing) 33L else 150L)
    }
}

@Composable
internal fun BoxScope.FullscreenChatBackdrop(
    opacity: Float,
    blur: FullscreenChatBlur?,
) {
    val dock = LocalFullscreenChatDock.current
    val shape = LocalFullscreenChatShape.current
    if (blur == null && dock?.edges?.isDocked != true) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = opacity), shape.backgroundShape()))
        return
    }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val region = anchor?.takeUnless { it.isEmpty }?.let {
        (dock?.extend(it) ?: FullscreenChatBackdropRegion(it)).copy(shape = shape)
    }
    val onBoundsChanged = blur?.onBoundsChanged
    DisposableEffect(onBoundsChanged) {
        onDispose { onBoundsChanged?.invoke(null) }
    }
    SideEffect { onBoundsChanged?.invoke(region) }
    Box(Modifier.matchParentSize().onGloballyPositioned { anchor = it.boundsInWindow() }) {
        val original = anchor
        if (original != null && region != null) {
            val bounds = region.bounds
            Box(
                Modifier.layout { measurable, constraints ->
                    val left = (original.left - bounds.left).roundToInt().coerceAtLeast(0)
                    val top = (original.top - bounds.top).roundToInt().coerceAtLeast(0)
                    val right = (bounds.right - original.right).roundToInt().coerceAtLeast(0)
                    val bottom = (bounds.bottom - original.bottom).roundToInt().coerceAtLeast(0)
                    val child = measurable.measure(Constraints.fixed(
                        constraints.maxWidth + left + right, constraints.maxHeight + top + bottom,
                    ))
                    layout(constraints.maxWidth, constraints.maxHeight) { child.place(-left, -top) }
                }.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
                    val mask = if (size.width > 0f && size.height > 0f) {
                        ShaderBrush(featherMask(Rect(0f, 0f, size.width, size.height), 28.dp.toPx(), region.edges))
                    } else null
                    val corners = region.edges.cornerRadii(shape.radius(size.width, size.height, 8.dp.toPx()))
                    val outline = if (corners.any { it > 0f }) Path().apply {
                        addRoundRect(RoundRect(0f, 0f, size.width, size.height,
                            CornerRadius(corners[0]), CornerRadius(corners[1]), CornerRadius(corners[2]), CornerRadius(corners[3])))
                    } else null
                    fun DrawScope.drawBackdrop() {
                        val frame = blur?.legacyFrame?.value
                        if (frame != null) {
                            drawImage(
                                frame.bitmap,
                                dstOffset = IntOffset(
                                    (frame.origin.x - bounds.left).roundToInt(),
                                    (frame.origin.y - bounds.top).roundToInt(),
                                ),
                                dstSize = frame.size,
                                filterQuality = FilterQuality.Low,
                            )
                        }
                        drawRect(Color.Black.copy(alpha = opacity))
                        mask?.let { drawRect(it, blendMode = BlendMode.DstIn) }
                    }
                    onDrawBehind {
                        if (outline == null) drawBackdrop() else clipPath(outline) { drawBackdrop() }
                    }
                }
            )
        }
    }
}

@Composable
internal fun FullscreenChatVideoBlur(texture: TextureView?, region: FullscreenChatBackdropRegion?, opacity: () -> Float = { 1f }) {
    if (Build.VERSION.SDK_INT < 31 || texture == null || region == null || region.bounds.isEmpty) return
    val radius = with(LocalDensity.current) { 15.dp.toPx() }
    val maxFeather = with(LocalDensity.current) { 28.dp.toPx() }
    val roundedRadius = with(LocalDensity.current) { 8.dp.toPx() }
    val shader = remember(texture) { if (Build.VERSION.SDK_INT >= 33) ProgressiveBlurShader() else null }
    val maskedBlur = remember(texture) { if (Build.VERSION.SDK_INT < 33) MaskedVideoBlur() else null }
    val currentRegion by rememberUpdatedState(region)
    val currentOpacity by rememberUpdatedState(opacity)
    val scope = rememberCoroutineScope()
    DisposableEffect(texture, radius, maxFeather, roundedRadius, shader) {
        val location = IntArray(2)
        var lastRegion: FullscreenChatBackdropRegion? = null
        var lastOpacity = 0f
        var lastTextureSize = IntSize.Zero
        fun updateEffect() {
            texture.getLocationInWindow(location)
            val windowRegion = currentRegion
            val bounds = windowRegion.bounds.translate(-location[0].toFloat(), -location[1].toFloat())
            val textureSize = IntSize(texture.width, texture.height)
            val textureBounds = Rect(0f, 0f, textureSize.width.toFloat(), textureSize.height.toFloat())
            val alpha = currentOpacity().coerceIn(0f, 1f)
            val visibleRegion = if (alpha > 0f && !textureBounds.isEmpty && bounds.overlaps(textureBounds)) {
                windowRegion.copy(bounds = bounds)
            } else null
            val visibleOpacity = if (visibleRegion != null) alpha else 0f
            if (visibleRegion == lastRegion && visibleOpacity == lastOpacity && textureSize == lastTextureSize) return
            lastRegion = visibleRegion
            lastOpacity = visibleOpacity
            lastTextureSize = textureSize
            val effect = visibleRegion?.let {
                val cornerRadius = it.shape.radius(bounds.width, bounds.height, roundedRadius)
                shader?.effect(it, textureSize, radius, maxFeather, visibleOpacity, cornerRadius)
                    ?: maskedBlur?.effect(it, radius, maxFeather, visibleOpacity, cornerRadius)
            }
            texture.setRenderEffect(effect)
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateEffect() }
        texture.addOnLayoutChangeListener(listener)
        updateEffect()
        val updates = scope.launch {
            snapshotFlow { currentRegion to currentOpacity() }.collect { updateEffect() }
        }
        onDispose {
            updates.cancel()
            texture.removeOnLayoutChangeListener(listener)
            texture.setRenderEffect(null)
        }
    }
}

@RequiresApi(31)
private class MaskedVideoBlur {
    private data class MaskKey(val width: Int, val height: Int, val radius: Float, val edges: FullscreenChatEdges)
    private var maskKey: MaskKey? = null
    private var maskBitmap: Bitmap? = null

    fun effect(region: FullscreenChatBackdropRegion, radius: Float, maxFeather: Float, opacity: Float, cornerRadius: Float): RenderEffect {
        val source = RenderEffect.createOffsetEffect(0f, 0f)
        val blurred = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        val mask = RenderEffect.createShaderEffect(featherMask(region.bounds, maxFeather, region.edges, opacity))
        val feathered = RenderEffect.createBlendModeEffect(blurred, mask, AndroidBlendMode.DST_IN)
        val roundedMask = cornerMask(region, cornerRadius)
        val overlay = if (roundedMask == null) feathered
            else RenderEffect.createBlendModeEffect(feathered, roundedMask, AndroidBlendMode.DST_IN)
        return RenderEffect.createBlendModeEffect(source, overlay, AndroidBlendMode.SRC_OVER)
    }

    private fun cornerMask(region: FullscreenChatBackdropRegion, radius: Float): RenderEffect? {
        val radii = region.edges.cornerRadii(radius)
        if (radii.none { it > 0f }) return null
        val bounds = region.bounds
        val destination = AndroidRect(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.right.roundToInt(), bounds.bottom.roundToInt())
        if (destination.isEmpty) return null
        val key = MaskKey(destination.width(), destination.height(), radius, region.edges)
        val bitmap = maskBitmap?.takeIf { maskKey == key } ?: run {
            // The GPU scales this small alpha mask to the chat bounds.
            val sampleScale = minOf(1f, 256f / maxOf(key.width, key.height))
            Bitmap.createBitmap((key.width * sampleScale).roundToInt().coerceAtLeast(1),
                (key.height * sampleScale).roundToInt().coerceAtLeast(1), Bitmap.Config.ALPHA_8).also {
                val outline = AndroidPath().apply {
                    addRoundRect(RectF(0f, 0f, key.width.toFloat(), key.height.toFloat()),
                        FloatArray(8) { index -> radii[index / 2] }, AndroidPath.Direction.CW)
                }
                Canvas(it).apply {
                    scale(it.width.toFloat() / key.width, it.height.toFloat() / key.height)
                    drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG))
                }
                maskKey = key
                maskBitmap = it
            }
        }
        return RenderEffect.createBitmapEffect(bitmap, null, destination)
    }
}

private fun feather(length: Float, maxFeather: Float) = minOf(length * 0.42f, maxFeather)

private fun featherMask(bounds: Rect, maxFeather: Float, edges: FullscreenChatEdges, opacity: Float = 1f): Shader {
    fun gradient(x0: Float, y0: Float, x1: Float, y1: Float, length: Float, alpha: Float, start: Boolean, end: Boolean): LinearGradient {
        val fade = feather(length, maxFeather) / length
        val colors = intArrayOf(0, 40, 215, 255, 255, 215, 40, 0)
        if (start) colors.fill(255, 0, 3)
        if (end) colors.fill(255, 5, colors.size)
        colors.indices.forEach { colors[it] = ((colors[it] * alpha).roundToInt() shl 24) or 0xFFFFFF }
        return LinearGradient(
            x0, y0, x1, y1,
            colors,
            floatArrayOf(0f, fade * .25f, fade * .75f, fade, 1f - fade, 1f - fade * .75f, 1f - fade * .25f, 1f),
            if (Build.VERSION.SDK_INT >= 31) Shader.TileMode.DECAL else Shader.TileMode.CLAMP,
        )
    }
    return ComposeShader(
        gradient(bounds.left, 0f, bounds.right, 0f, bounds.width, opacity, edges.left, edges.right),
        gradient(0f, bounds.top, 0f, bounds.bottom, bounds.height, 1f, edges.top, edges.bottom),
        PorterDuff.Mode.DST_IN,
    )
}

@RequiresApi(33)
private class ProgressiveBlurShader {
    private val shader = RuntimeShader("""
        uniform shader video;
        uniform float4 bounds;
        uniform float2 feather;
        uniform float radius;
        uniform float opacity;
        uniform float4 docked;
        uniform float2 frameSize;
        uniform float cornerRadius;

        half4 sampleVideo(float2 p) {
            return video.eval(clamp(p, float2(0.5), frameSize - float2(0.5)));
        }

        half4 main(float2 p) {
            half4 center = sampleVideo(p);
            if (p.x < bounds.x || p.y < bounds.y || p.x > bounds.z || p.y > bounds.w) return center;
            float2 start = mix(smoothstep(float2(0.0), feather, p - bounds.xy), float2(1.0), docked.xy);
            float2 end = mix(smoothstep(float2(0.0), feather, bounds.zw - p), float2(1.0), docked.zw);
            float strength = min(start.x, end.x) * min(start.y, end.y);
            float2 fromStart = p - bounds.xy;
            float2 fromEnd = bounds.zw - p;
            float dockX = fromStart.x < fromEnd.x ? docked.x : docked.z;
            float dockY = fromStart.y < fromEnd.y ? docked.y : docked.w;
            float2 corner = cornerRadius - min(fromStart, fromEnd);
            if (dockX == 0.0 && dockY == 0.0 && corner.x > 0.0 && corner.y > 0.0) {
                float cornerFade = min(cornerRadius * 0.4, min(feather.x, feather.y));
                strength = min(strength, smoothstep(0.0, cornerFade, cornerRadius - length(corner)));
            }
            if (strength <= 0.001) return center;

            float r = radius * strength;
            float inner = r * 0.42;
            float diag = r * 0.58;
            half4 softened = center * 0.14
                + (sampleVideo(p + float2(inner, 0.0)) + sampleVideo(p - float2(inner, 0.0))
                   + sampleVideo(p + float2(0.0, inner)) + sampleVideo(p - float2(0.0, inner))) * 0.10
                + (sampleVideo(p + float2(diag, diag)) + sampleVideo(p - float2(diag, diag))
                   + sampleVideo(p + float2(diag, -diag)) + sampleVideo(p + float2(-diag, diag))) * 0.075
                + (sampleVideo(p + float2(r, 0.0)) + sampleVideo(p - float2(r, 0.0))
                   + sampleVideo(p + float2(0.0, r)) + sampleVideo(p - float2(0.0, r))) * 0.04;
            return mix(center, softened, opacity);
        }
    """.trimIndent())

    fun effect(region: FullscreenChatBackdropRegion, textureSize: IntSize, radius: Float, maxFeather: Float, opacity: Float, cornerRadius: Float): RenderEffect {
        val (bounds, edges) = region
        shader.setFloatUniform("bounds", bounds.left, bounds.top, bounds.right, bounds.bottom)
        shader.setFloatUniform("feather", feather(bounds.width, maxFeather), feather(bounds.height, maxFeather))
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("opacity", opacity)
        shader.setFloatUniform("cornerRadius", cornerRadius)
        shader.setFloatUniform("frameSize", textureSize.width.toFloat(), textureSize.height.toFloat())
        shader.setFloatUniform("docked", if (edges.left) 1f else 0f, if (edges.top) 1f else 0f,
            if (edges.right) 1f else 0f, if (edges.bottom) 1f else 0f)
        return RenderEffect.createRuntimeShaderEffect(shader, "video")
    }
}
