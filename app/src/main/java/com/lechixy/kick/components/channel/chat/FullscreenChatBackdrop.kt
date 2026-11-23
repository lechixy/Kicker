package com.lechixy.kick.components.channel.chat

import com.lechixy.kick.data.settings.repository.LocalBlurEffectsEnabled

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
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Choreographer
import android.view.TextureView
import android.view.ViewTreeObserver
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
import androidx.compose.runtime.LaunchedEffect
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
import com.lechixy.kick.data.settings.model.FullscreenChatBlurStyle
import com.lechixy.kick.data.settings.model.FullscreenChatShape
import com.lechixy.kick.components.PlayerVideoZoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal class FullscreenChatVideoFrame(
    val bitmap: ImageBitmap,
    val origin: IntOffset,
    val size: IntSize,
    val frostedBitmap: ImageBitmap? = null,
)

internal data class FullscreenChatBlur(
    val onBoundsChanged: (FullscreenChatBackdropRegion?) -> Unit,
    val legacyFrame: State<FullscreenChatVideoFrame?>? = null,
    val style: FullscreenChatBlurStyle = FullscreenChatBlurStyle.Soft,
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
    val frostedControl: Boolean = false,
    val style: FullscreenChatBlurStyle = FullscreenChatBlurStyle.Soft,
    val followsControlsOpacity: Boolean = true,
)

private val frostedRadius = 24.dp
private val frostedChatFeather = 4.dp

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
    soft: Boolean = true,
    frosted: Boolean = false,
    mediaKey: Any? = null,
    zoomScale: () -> Float = { 1f },
): State<FullscreenChatVideoFrame?> {
    if (!LocalBlurEffectsEnabled.current) return remember { mutableStateOf(null) }
    val frame = remember(texture, soft, frosted, mediaKey) { mutableStateOf<FullscreenChatVideoFrame?>(null) }
    val isPlaying by rememberUpdatedState(playing)
    val currentZoomScale by rememberUpdatedState(zoomScale)
    val radius = with(LocalDensity.current) { frostedRadius.toPx() }
    LaunchedEffect(frame, radius) {
        if (texture == null || (!soft && !frosted)) return@LaunchedEffect
        // Read the texture once and reuse two buffers so drawing can retain the previous sample.
        val location = IntArray(2)
        val sampleLimit = if (frosted) 160 else 56
        var buffers: LegacyBackdropBuffers? = null
        var next = 0
        var lastTimestamp: Long? = null
        var lastSurface: SurfaceTexture? = null
        while (true) {
            val width = texture.width
            val height = texture.height
            val surface = texture.surfaceTexture
            if (surface !== lastSurface) {
                lastSurface = surface
                lastTimestamp = null
                frame.value = null
            }
            val timestamp = surface?.timestamp
            if (texture.isAvailable && width > 0 && height > 0) {
                val zoom = currentZoomScale()
                val size = IntSize((width * zoom).roundToInt(), (height * zoom).roundToInt())
                texture.getLocationInWindow(location)
                val origin = IntOffset(location[0], location[1])
                val current = frame.value
                if (current == null || current.size != size || current.origin != origin ||
                    timestamp == null || timestamp == 0L || timestamp != lastTimestamp
                ) {
                    val scale = (sampleLimit.toFloat() / maxOf(size.width, size.height)).coerceAtMost(1f)
                    val sampled = IntSize(
                        (size.width * scale).roundToInt().coerceAtLeast(1),
                        (size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                    val activeBuffers = buffers?.takeIf { it.size == sampled } ?: LegacyBackdropBuffers(
                        sampled, downsample = soft && frosted, frosted = frosted,
                    ).also { buffers = it }
                    val sample = try {
                        texture.getBitmap(activeBuffers.raw[next])
                    } catch (_: IllegalStateException) {
                        null
                    }
                    if (sample != null) {
                        val images = if (frosted) withContext(Dispatchers.Default) {
                            activeBuffers.prepare(sample, next, radius * scale)
                        } else sample to null
                        if (texture.surfaceTexture === surface) {
                            frame.value = FullscreenChatVideoFrame(
                                images.first.asImageBitmap(), origin, size, images.second?.asImageBitmap(),
                            )
                        } else {
                            frame.value = null
                        }
                        lastTimestamp = timestamp
                        next = 1 - next
                    } else {
                        frame.value = null
                    }
                }
            } else {
                frame.value = null
            }
            delay(if (isPlaying) 33L else 150L)
        }
    }
    return frame
}

@Composable
internal fun BoxScope.FullscreenChatBackdrop(
    opacity: Float,
    blur: FullscreenChatBlur?,
    edges: FullscreenChatEdges? = null,
) {
    val activeBlur = blur.takeIf { LocalBlurEffectsEnabled.current }
    val dock = LocalFullscreenChatDock.current
    val shape = LocalFullscreenChatShape.current
    if (activeBlur == null && edges == null && dock?.edges?.isDocked != true) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = opacity), shape.backgroundShape()))
        return
    }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val region = anchor?.takeUnless { it.isEmpty }?.let {
        val bounds = when {
            edges != null -> FullscreenChatBackdropRegion(it, edges)
            dock != null -> dock.extend(it)
            else -> FullscreenChatBackdropRegion(it)
        }
        bounds.copy(shape = shape, style = activeBlur?.style ?: FullscreenChatBlurStyle.Soft)
    }
    val onBoundsChanged = activeBlur?.onBoundsChanged
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
                    val frosted = region.style == FullscreenChatBlurStyle.FrostedGlass
                    val feather = if (frosted) frostedChatFeather else 28.dp
                    val mask = if (size.width > 0f && size.height > 0f) {
                        ShaderBrush(featherMask(Rect(0f, 0f, size.width, size.height), feather.toPx(), region.edges))
                    } else null
                    val corners = region.edges.cornerRadii(shape.radius(size.width, size.height, 8.dp.toPx()))
                    val outline = if (corners.any { it > 0f }) Path().apply {
                        addRoundRect(RoundRect(0f, 0f, size.width, size.height,
                            CornerRadius(corners[0]), CornerRadius(corners[1]), CornerRadius(corners[2]), CornerRadius(corners[3])))
                    } else null
                    fun DrawScope.drawBackdrop() {
                        val frame = activeBlur?.legacyFrame?.value
                        val bitmap = if (frosted) frame?.frostedBitmap else frame?.bitmap
                        if (frame != null && bitmap != null) {
                            drawImage(
                                bitmap,
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
internal fun VideoBackdropBlur(
    texture: TextureView?,
    regions: List<FullscreenChatBackdropRegion>,
    controlOpacity: () -> Float = { 1f },
    zoom: () -> PlayerVideoZoom = { PlayerVideoZoom() },
    reduceNoise: Boolean = false,
    opacity: () -> Float = { 1f },
) {
    if (Build.VERSION.SDK_INT < 31 || texture == null) return
    val blurRegions = if (LocalBlurEffectsEnabled.current) regions else emptyList()
    val noiseReduction = remember(reduceNoise) {
        if (reduceNoise && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RenderEffect.createRuntimeShaderEffect(RuntimeShader(VIDEO_NOISE_REDUCTION_SHADER), "video")
        } else null
    }
    if (blurRegions.isEmpty() && noiseReduction == null) return
    val radius = with(LocalDensity.current) { 15.dp.toPx() }
    val maxFeather = with(LocalDensity.current) { 28.dp.toPx() }
    val glassRadius = with(LocalDensity.current) { frostedRadius.toPx() }
    val glassFeather = with(LocalDensity.current) { frostedChatFeather.toPx() }
    val controlFeather = with(LocalDensity.current) { 1.dp.toPx() }
    val roundedRadius = with(LocalDensity.current) { 8.dp.toPx() }
    val masks = remember(texture, blurRegions.size) { List(blurRegions.size) { VideoBlurMask() } }
    val opaquePixel = remember(texture) {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8).apply { eraseColor(android.graphics.Color.WHITE) }
    }
    val progressiveEffects = remember(texture, blurRegions.size) {
        arrayOfNulls<ProgressiveBlurShader>(blurRegions.size)
    }
    val currentRegions by rememberUpdatedState(blurRegions)
    val currentControlOpacity by rememberUpdatedState(controlOpacity)
    val currentOpacity by rememberUpdatedState(opacity)
    val currentZoom by rememberUpdatedState(zoom)
    val scope = rememberCoroutineScope()
    DisposableEffect(texture, radius, maxFeather, glassRadius, glassFeather, controlFeather, roundedRadius, masks, noiseReduction) {
        val location = IntArray(2)
        var lastRegions = emptyList<FullscreenChatBackdropRegion>()
        var lastOpacity = 0f
        var lastControlOpacity = 0f
        var lastTextureSize = IntSize.Zero
        var lastOrigin = IntOffset.Zero
        var lastZoomScale = 1f
        var appliedEffect: RenderEffect? = null
        val choreographer = if (Build.VERSION.SDK_INT < 33) Choreographer.getInstance() else null
        val refreshTexture = object : Choreographer.FrameCallback {
            private var lastSurface: SurfaceTexture? = null
            private var lastTimestamp: Long? = null

            override fun doFrame(frameTimeNanos: Long) {
                val effect = appliedEffect ?: return
                val surface = texture.surfaceTexture
                val timestamp = surface?.timestamp
                if (surface != null && (surface !== lastSurface || timestamp != lastTimestamp)) {
                    // Android 12 can reuse a filtered TextureView layer after its texture changes.
                    texture.setRenderEffect(RenderEffect.createOffsetEffect(0f, 0f, effect))
                }
                lastSurface = surface
                lastTimestamp = timestamp
                choreographer?.postFrameCallback(this)
            }
        }
        fun updateEffect() {
            texture.getLocationInWindow(location)
            val origin = IntOffset(location[0], location[1])
            val textureSize = IntSize(texture.width, texture.height)
            val zoomScale = currentZoom().scale
            val regionsInWindow = currentRegions
            val textureBounds = Rect(0f, 0f, textureSize.width.toFloat(), textureSize.height.toFloat())
            val alpha = currentOpacity().coerceIn(0f, 1f)
            val visibleOpacity = if (!textureBounds.isEmpty) alpha else 0f
            val visibleControlOpacity = currentControlOpacity().coerceIn(0f, 1f)
            if (regionsInWindow == lastRegions && visibleOpacity == lastOpacity &&
                visibleControlOpacity == lastControlOpacity && textureSize == lastTextureSize &&
                origin == lastOrigin && zoomScale == lastZoomScale) return
            lastRegions = regionsInWindow
            lastOpacity = visibleOpacity
            lastControlOpacity = visibleControlOpacity
            lastTextureSize = textureSize
            lastOrigin = origin
            lastZoomScale = zoomScale
            var softEffect: RenderEffect? = null
            var softMask: RenderEffect? = null
            var frostedMask: RenderEffect? = null
            var frostedBounds: Rect? = null
            regionsInWindow.forEachIndexed { index, regionInWindow ->
                val shifted = regionInWindow.bounds.translate(-origin.x.toFloat(), -origin.y.toFloat())
                val bounds = Rect(shifted.topLeft / zoomScale, shifted.bottomRight / zoomScale)
                val region = regionInWindow.copy(bounds = bounds)
                val alpha = when {
                    !region.frostedControl -> visibleOpacity
                    region.followsControlsOpacity -> visibleControlOpacity
                    else -> 1f
                }
                if (alpha > 0f && !bounds.isEmpty && bounds.overlaps(textureBounds)) {
                    val cornerRadius = region.shape.radius(bounds.width, bounds.height, roundedRadius / zoomScale)
                    if (region.style == FullscreenChatBlurStyle.FrostedGlass) {
                        val feather = if (region.frostedControl) controlFeather else glassFeather
                        val mask = masks[index].effect(region, feather / zoomScale, alpha, cornerRadius)
                        frostedMask = composite(frostedMask, mask)
                        frostedBounds = frostedBounds?.let {
                            Rect(minOf(it.left, bounds.left), minOf(it.top, bounds.top),
                                maxOf(it.right, bounds.right), maxOf(it.bottom, bounds.bottom))
                        } ?: bounds
                    } else if (Build.VERSION.SDK_INT >= 33) {
                        val shader = progressiveEffects[index] ?: ProgressiveBlurShader().also {
                            progressiveEffects[index] = it
                        }
                        val next = shader.effect(region, textureSize, radius / zoomScale, maxFeather / zoomScale, alpha, cornerRadius)
                        softEffect = softEffect?.let { RenderEffect.createChainEffect(next, it) } ?: next
                    } else {
                        val mask = masks[index].effect(region, maxFeather / zoomScale, alpha, cornerRadius)
                        softMask = composite(softMask, mask)
                    }
                }
            }
            var effect = softEffect
            softMask?.let {
                effect = composite(
                    RenderEffect.createOffsetEffect(0f, 0f),
                    blurredOverlay(it, textureSize, radius / zoomScale, Shader.TileMode.CLAMP),
                )
            }
            frostedMask?.let {
                // Each blur branch samples the texture itself, even where two masks overlap.
                val source = RenderEffect.createOffsetEffect(0f, 0f)
                val crop = checkNotNull(frostedBounds).inflate(3f * glassRadius / zoomScale).intersect(textureBounds)
                val blurSource = if (crop == textureBounds) source else {
                    val cropMask = RenderEffect.createBitmapEffect(opaquePixel, null, AndroidRect(
                        floor(crop.left).toInt(), floor(crop.top).toInt(),
                        ceil(crop.right).toInt(), ceil(crop.bottom).toInt(),
                    ))
                    RenderEffect.createBlendModeEffect(source, cropMask, AndroidBlendMode.DST_IN)
                }
                effect = composite(
                    effect ?: source,
                    blurredOverlay(it, textureSize, glassRadius / zoomScale, Shader.TileMode.MIRROR, blurSource),
                )
            }
            // Compose both filters on the same TextureView so they share the video input.
            if (noiseReduction != null) {
                effect = effect?.let { RenderEffect.createChainEffect(it, noiseReduction) } ?: noiseReduction
            }
            val hadEffect = appliedEffect != null
            appliedEffect = effect
            texture.setRenderEffect(effect)
            if (!hadEffect && effect != null) choreographer?.postFrameCallback(refreshTexture)
            if (effect == null) choreographer?.removeFrameCallback(refreshTexture)
        }
        // The video can move in the window without the TextureView itself being laid out again.
        val listener = ViewTreeObserver.OnPreDrawListener {
            updateEffect()
            true
        }
        val observer = texture.viewTreeObserver
        observer.addOnPreDrawListener(listener)
        val updates = scope.launch {
            snapshotFlow { currentZoom() to Triple(currentRegions, currentOpacity(), currentControlOpacity()) }
                .collect { texture.invalidate() }
        }
        onDispose {
            updates.cancel()
            choreographer?.removeFrameCallback(refreshTexture)
            (if (observer.isAlive) observer else texture.viewTreeObserver).removeOnPreDrawListener(listener)
            texture.setRenderEffect(null)
        }
    }
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

@RequiresApi(31)
private fun composite(background: RenderEffect?, foreground: RenderEffect): RenderEffect =
    if (background == null) foreground
    else RenderEffect.createBlendModeEffect(background, foreground, AndroidBlendMode.SRC_OVER)

@RequiresApi(31)
private fun blurredOverlay(
    mask: RenderEffect,
    textureSize: IntSize,
    radius: Float,
    edge: Shader.TileMode,
    input: RenderEffect = RenderEffect.createOffsetEffect(0f, 0f),
): RenderEffect {
    val blurred = RenderEffect.createBlurEffect(
        radius.coerceAtMost(textureSize.width.toFloat()),
        radius.coerceAtMost(textureSize.height.toFloat()),
        input,
        edge,
    )
    return RenderEffect.createBlendModeEffect(blurred, mask, AndroidBlendMode.DST_IN)
}

@RequiresApi(31)
private class VideoBlurMask {
    private data class MaskKey(val width: Int, val height: Int, val radius: Float, val edges: FullscreenChatEdges)
    private var maskKey: MaskKey? = null
    private var maskBitmap: Bitmap? = null

    fun effect(region: FullscreenChatBackdropRegion, maxFeather: Float, opacity: Float, cornerRadius: Float): RenderEffect {
        val mask = RenderEffect.createShaderEffect(featherMask(region.bounds, maxFeather, region.edges, opacity))
        val roundedMask = cornerMask(region, cornerRadius)
        return if (roundedMask == null) mask
        else RenderEffect.createBlendModeEffect(mask, roundedMask, AndroidBlendMode.DST_IN)
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
