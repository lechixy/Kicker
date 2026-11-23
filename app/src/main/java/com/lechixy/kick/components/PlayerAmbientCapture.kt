package com.lechixy.kick.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lechixy.kick.data.settings.model.PlayerAmbientMode
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val DYNAMIC_INTERVAL_MS = 250

internal data class AmbientSeek(val sequence: Int, val previousTimestamp: Long)

private data class AmbientDemand(val playing: Boolean, val seek: AmbientSeek?, val firstFrame: Int)

@Composable
internal fun rememberAmbientPowerSaveMode(): Boolean {
    val context = LocalContext.current.applicationContext
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var enabled by remember(powerManager) { mutableStateOf(powerManager?.isPowerSaveMode == true) }
    DisposableEffect(context, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                enabled = powerManager?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        enabled = powerManager?.isPowerSaveMode == true
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled
}

@Composable
internal fun CapturePlayerAmbient(
    state: PlayerAmbientState,
    texture: TextureView?,
    mode: PlayerAmbientMode,
    liveFps: Int,
    visible: Boolean,
    playing: Boolean,
    mediaId: String,
    mediaUrl: String,
    firstFrame: Int,
    seek: AmbientSeek?,
    keepFrameWhenHidden: Boolean = false,
    preserveBlackBars: Boolean = true,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentPlaying by rememberUpdatedState(playing)
    val currentSeek by rememberUpdatedState(seek)
    val currentFirstFrame by rememberUpdatedState(firstFrame)
    val currentFps by rememberUpdatedState(liveFps)
    val keepCurrentFrame by rememberUpdatedState(keepFrameWhenHidden)
    val source = remember(texture, mode, mediaId, mediaUrl, preserveBlackBars) { Any() }
    LaunchedEffect(state, source, visible, keepFrameWhenHidden, lifecycle) {
        val owner = Any()
        state.captureOwner = owner
        if (!keepFrameWhenHidden || state.captureSource !== source) state.frame = null
        state.captureSource = source
        if (!visible || mode == PlayerAmbientMode.Off || texture == null) return@LaunchedEffect

        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val capture = AmbientCapture(state, texture, preserveBlackBars)
            var completedSeek: AmbientSeek? = null
            try {
                snapshotFlow {
                    if (currentPlaying) AmbientDemand(true, null, 0)
                    else AmbientDemand(false, currentSeek, currentFirstFrame)
                }.collectLatest { demand ->
                    if (demand.playing) {
                        completedSeek = currentSeek
                        if (mode == PlayerAmbientMode.Dynamic) {
                            capture.sampleDynamic()
                        } else {
                            capture.sampleLive { currentFps }
                        }
                    } else {
                        val pendingSeek = demand.seek?.takeUnless { it == completedSeek }
                        capture.samplePaused(mode, pendingSeek)
                        completedSeek = demand.seek
                    }
                }
            } finally {
                capture.close()
                if (state.captureOwner === owner && !keepCurrentFrame) state.frame = null
            }
        }
    }
}

private class AmbientCapture(
    private val state: PlayerAmbientState,
    private val texture: TextureView,
    private val preserveBlackBars: Boolean,
) {
    private var surfaceTexture: SurfaceTexture? = null
    private var surfaceCopy: AmbientSurfaceCopy? = null
    private var dynamicBitmap: Bitmap? = null
    private val dynamicPixels by lazy { IntArray(32 * 32) }

    private fun currentSurface(): SurfaceTexture? {
        val surface = texture.surfaceTexture?.takeIf { texture.isAvailable && !it.isReleased }
        if (surface !== surfaceTexture) {
            surfaceCopy?.close()
            surfaceCopy = null
            surfaceTexture = surface
            state.frame = null
        }
        return surface
    }

    suspend fun sampleDynamic() {
        while (true) {
            val previous = state.frame as? AmbientFrame.Palette
            val next = currentSurface()?.let { readPalette(it) }
            if (next == null) {
                delay(DYNAMIC_INTERVAL_MS.toLong())
            } else if (previous == null || state.frame == null ||
                previous.colors == next.colors || previous.bars != next.bars
            ) {
                state.frame = next
                delay(DYNAMIC_INTERVAL_MS.toLong())
            } else {
                animate(0f, 1f, animationSpec = tween(DYNAMIC_INTERVAL_MS, easing = LinearEasing)) { fraction, _ ->
                    state.frame = AmbientFrame.Palette(previous.colors.zip(next.colors) { start, end ->
                        lerp(start, end, fraction)
                    }, next.bars)
                }
            }
        }
    }

    suspend fun sampleLive(fps: () -> Int) {
        val cadence = AmbientFrameCadence()
        var previousSurface: SurfaceTexture? = null
        while (true) {
            // Android's Compose frame clock is driven by Choreographer.
            val frameTime = withFrameNanos { it }
            val surface = currentSurface() ?: continue
            if (surface !== previousSurface) {
                cadence.reset()
                previousSurface = surface
            }
            if (cadence.accept(surface.timestamp, frameTime, fps())) {
                if (!readProjection(surface)) cadence.retryCurrentFrame()
            }
        }
    }

    suspend fun samplePaused(mode: PlayerAmbientMode, seek: AmbientSeek?) {
        currentSurface()
        if (seek == null && state.frame != null) return
        withTimeoutOrNull(3_000L) {
            while (true) {
                withFrameNanos { it }
                val surface = currentSurface() ?: continue
                val timestamp = surface.timestamp
                if (timestamp == seek?.previousTimestamp) continue
                val captured = if (mode == PlayerAmbientMode.Dynamic) {
                    readPalette(surface)?.let { state.frame = it } != null
                } else {
                    readProjection(surface)
                }
                if (captured) break
            }
        }
    }

    private suspend fun readPalette(surface: SurfaceTexture): AmbientFrame.Palette? {
        val size = sampleSize(texture.width, texture.height, 32) ?: return null
        val buffer = dynamicBitmap?.takeIf { it.width == size.width && it.height == size.height }
            ?: Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also { dynamicBitmap = it }
        // Some devices can't read the texture layer until it draws again. The
        // underlying video surface may already have the frame, even while paused.
        if (!texture.copyAmbientBitmap(buffer)) {
            val copy = surfaceCopy ?: AmbientSurfaceCopy(surface).also { surfaceCopy = it }
            if (!state.liveCopyLock.withLock { copy.copyInto(buffer) }) return null
        }
        if (currentSurface() !== surface) return null
        return paletteFrom(buffer)
    }

    private fun paletteFrom(bitmap: Bitmap): AmbientFrame.Palette {
        val left = 0f..0.15f
        val center = 0.35f..0.65f
        val right = 0.85f..1f
        val first = 0f..0.3f
        val last = 0.7f..1f
        val bars = if (preserveBlackBars) {
            bitmap.getPixels(dynamicPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            detectAmbientBlackBars(dynamicPixels, bitmap.width, bitmap.height)
        } else {
            AmbientBlackBars()
        }
        return AmbientFrame.Palette(listOf(
            if (bars.top > 0) Color.Black else bitmap.average(first, left),
            if (bars.top > 0) Color.Black else bitmap.average(center, left),
            if (bars.top > 0) Color.Black else bitmap.average(last, left),
            if (bars.left > 0) Color.Black else bitmap.average(left, center),
            if (bars.right > 0) Color.Black else bitmap.average(right, center),
            if (bars.bottom > 0) Color.Black else bitmap.average(first, right),
            if (bars.bottom > 0) Color.Black else bitmap.average(center, right),
            if (bars.bottom > 0) Color.Black else bitmap.average(last, right),
        ), bars)
    }

    private suspend fun readProjection(surface: SurfaceTexture): Boolean {
        val size = sampleSize(texture.width, texture.height, 96) ?: return false
        val copy = surfaceCopy ?: AmbientSurfaceCopy(surface).also { surfaceCopy = it }
        val bitmap = state.liveCopyLock.withLock { copy.read(texture, size, preserveBlackBars) }
        if (currentSurface() !== surface || bitmap == null) return false
        state.frame = AmbientFrame.Projection(bitmap)
        return true
    }

    fun close() {
        surfaceCopy?.close()
    }
}

internal class AmbientFrameCadence {
    private var lastTimestamp = 0L
    private var nextFrame = Long.MIN_VALUE
    private var lastFps = 0

    fun accept(timestamp: Long, frameTime: Long, fps: Int): Boolean {
        if (timestamp == 0L || timestamp == lastTimestamp) return false
        val interval = 1_000_000_000L / fps
        if (fps == lastFps && frameTime + 750_000L < nextFrame) return false
        // Keep the cadence across fractional refresh rates and small vsync timing variations.
        nextFrame = if (fps != lastFps || frameTime - interval >= nextFrame) frameTime + interval
        else nextFrame + interval
        lastFps = fps
        lastTimestamp = timestamp
        return true
    }

    fun reset() {
        retryCurrentFrame()
        nextFrame = Long.MIN_VALUE
        lastFps = 0
    }

    fun retryCurrentFrame() {
        lastTimestamp = 0L
    }
}

private data class AmbientSampleSize(val width: Int, val height: Int)

private fun sampleSize(width: Int, height: Int, limit: Int): AmbientSampleSize? {
    if (width <= 0 || height <= 0) return null
    val scale = minOf(1f, limit.toFloat() / maxOf(width, height))
    return AmbientSampleSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
}

private fun Bitmap.average(xRange: ClosedFloatingPointRange<Float>, yRange: ClosedFloatingPointRange<Float>): Color {
    val x0 = ((width - 1) * xRange.start).roundToInt()
    val y0 = ((height - 1) * yRange.start).roundToInt()
    val x1 = ((width - 1) * xRange.endInclusive).roundToInt()
    val y1 = ((height - 1) * yRange.endInclusive).roundToInt()
    var red = 0
    var green = 0
    var blue = 0
    for (y in y0..y1) {
        for (x in x0..x1) {
            val pixel = getPixel(x, y)
            red += pixel shr 16 and 255
            green += pixel shr 8 and 255
            blue += pixel and 255
        }
    }
    val count = (x1 - x0 + 1) * (y1 - y0 + 1)
    return Color(red / count, green / count, blue / count)
}

private fun TextureView.copyAmbientBitmap(bitmap: Bitmap): Boolean {
    // TextureView leaves the destination unchanged if copying fails.
    bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
    return try {
        getBitmap(bitmap)
        android.graphics.Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) != 0
    } catch (_: IllegalStateException) {
        false
    }
}

private class AmbientSurfaceCopy(texture: SurfaceTexture) : AutoCloseable {
    private val surface = Surface(texture)
    private val completionHandler = Handler(Looper.getMainLooper())
    private var buffer: Bitmap? = null
    private var pixels = IntArray(0)
    private var scratch = IntArray(0)

    suspend fun read(
        texture: TextureView,
        size: AmbientSampleSize,
        preserveBlackBars: Boolean,
    ): Bitmap? {
        val (width, height) = size
        val bitmap = buffer?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                buffer = it
                pixels = IntArray(width * height)
                scratch = IntArray(width * height)
            }
        if (!copyInto(bitmap) && !texture.copyAmbientBitmap(bitmap)) return null
        return withContext(Dispatchers.Default) { blurProjection(bitmap, preserveBlackBars) }
    }

    // Wait for PixelCopy to finish before cancellation can release the buffer or Surface.
    suspend fun copyInto(bitmap: Bitmap): Boolean {
        val copied = suspendCoroutine { continuation ->
            try {
                PixelCopy.request(surface, bitmap, { continuation.resume(it == PixelCopy.SUCCESS) }, completionHandler)
            } catch (_: IllegalArgumentException) {
                continuation.resume(false)
            }
        }
        currentCoroutineContext().ensureActive()
        return copied
    }

    private fun blurProjection(bitmap: Bitmap, preserveBlackBars: Boolean): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val bars = if (preserveBlackBars) detectAmbientBlackBars(pixels, width, height) else AmbientBlackBars()
        val radius = (maxOf(width, height) / 12).coerceAtLeast(1)
        blurAmbientLines(pixels, scratch, height, width, width, 1, radius)
        blurAmbientLines(scratch, pixels, width, height, 1, width, radius)
        for (y in 0 until height) {
            val row = y * width
            if (y < bars.top || y >= height - bars.bottom) {
                pixels.fill(android.graphics.Color.BLACK, row, row + width)
            } else {
                pixels.fill(android.graphics.Color.BLACK, row, row + bars.left)
                pixels.fill(android.graphics.Color.BLACK, row + width - bars.right, row + width)
            }
        }
        // Canvas may retain the image in its display list after the next capture starts.
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    override fun close() = surface.release()
}

internal data class AmbientBlackBars(
    val top: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
)

internal fun detectAmbientBlackBars(pixels: IntArray, width: Int, height: Int): AmbientBlackBars {
    fun edge(start: Int, lineStride: Int, pixelStride: Int, length: Int, depth: Int): Int {
        val maxBar = depth / 3
        val minBar = (depth + 31) / 32
        var darkLines = 0
        for (line in 0..maxBar) {
            var dark = 0
            var bright = 0
            val base = start + line * lineStride
            for (position in 0 until length) {
                val pixel = pixels[base + position * pixelStride]
                val peak = maxOf(pixel shr 16 and 255, pixel shr 8 and 255, pixel and 255)
                if (peak <= 20) dark++
                if (peak >= 42) bright++
            }
            if (dark * 20 < length * 19) {
                // A distinct boundary keeps dark scenes from being treated as letterboxing.
                if (darkLines >= minBar && bright * 4 >= length) return darkLines
                if (line - darkLines >= 1 || darkLines < minBar) return 0
            } else if (line == darkLines) {
                darkLines++
            } else {
                return 0
            }
        }
        return 0
    }
    if (width < 4 || height < 4) return AmbientBlackBars()
    return AmbientBlackBars(
        top = edge(0, width, 1, width, height),
        bottom = edge((height - 1) * width, -width, 1, width, height),
        left = edge(0, 1, width, height, width),
        right = edge(width - 1, -1, width, height, width),
    )
}

private fun blurAmbientLines(
    source: IntArray, target: IntArray, lineCount: Int, lineLength: Int,
    lineStride: Int, pixelStride: Int, radius: Int,
) {
    val diameter = radius * 2 + 1
    for (line in 0 until lineCount) {
        val base = line * lineStride
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val pixel = source[base + offset.coerceIn(0, lineLength - 1) * pixelStride]
            red += pixel shr 16 and 255
            green += pixel shr 8 and 255
            blue += pixel and 255
        }
        for (position in 0 until lineLength) {
            target[base + position * pixelStride] =
                android.graphics.Color.rgb(red / diameter, green / diameter, blue / diameter)
            val leaving = source[base + (position - radius).coerceAtLeast(0) * pixelStride]
            val entering = source[base + (position + radius + 1).coerceAtMost(lineLength - 1) * pixelStride]
            red += (entering shr 16 and 255) - (leaving shr 16 and 255)
            green += (entering shr 8 and 255) - (leaving shr 8 and 255)
            blue += (entering and 255) - (leaving and 255)
        }
    }
}
