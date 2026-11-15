package com.lechixy.kick.components

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lechixy.kick.data.settings.model.PlayerAmbientMode
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val DYNAMIC_INTERVAL_MS = 250

internal data class AmbientSeek(val sequence: Int, val previousTimestamp: Long)

private data class AmbientDemand(val playing: Boolean, val seek: AmbientSeek?, val firstFrame: Int)

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
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentPlaying by rememberUpdatedState(playing)
    val currentSeek by rememberUpdatedState(seek)
    val currentFirstFrame by rememberUpdatedState(firstFrame)
    val currentFps by rememberUpdatedState(liveFps)
    val keepCurrentFrame by rememberUpdatedState(keepFrameWhenHidden)
    val source = remember(texture, mode, mediaId, mediaUrl) { Any() }
    LaunchedEffect(state, source, visible, keepFrameWhenHidden, lifecycle) {
        val owner = Any()
        state.captureOwner = owner
        if (!keepFrameWhenHidden || state.captureSource !== source) state.frame = null
        state.captureSource = source
        if (!visible || mode == PlayerAmbientMode.Off || texture == null) return@LaunchedEffect

        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val capture = AmbientCapture(state, texture)
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

private class AmbientCapture(private val state: PlayerAmbientState, private val texture: TextureView) {
    private var surfaceTexture: SurfaceTexture? = null
    private var liveCopy: AmbientLiveCopy? = null
    private var dynamicBitmap: Bitmap? = null

    private fun currentSurface(): SurfaceTexture? {
        val surface = texture.surfaceTexture?.takeIf { texture.isAvailable && !it.isReleased }
        if (surface !== surfaceTexture) {
            liveCopy?.close()
            liveCopy = null
            surfaceTexture = surface
            state.frame = null
        }
        return surface
    }

    suspend fun sampleDynamic() {
        while (true) {
            val previous = state.frame as? AmbientFrame.Palette
            val next = currentSurface()?.takeIf { it.timestamp != 0L }?.let { readPalette() }
            if (previous == null || next == null || state.frame == null || previous.colors == next.colors) {
                state.frame = next
                delay(DYNAMIC_INTERVAL_MS.toLong())
            } else {
                animate(0f, 1f, animationSpec = tween(DYNAMIC_INTERVAL_MS, easing = LinearEasing)) { fraction, _ ->
                    state.frame = AmbientFrame.Palette(previous.colors.zip(next.colors) { start, end ->
                        lerp(start, end, fraction)
                    })
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
                readProjection(surface)
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
                if (timestamp == 0L || timestamp == seek?.previousTimestamp) continue
                if (mode == PlayerAmbientMode.Dynamic) state.frame = readPalette()
                else readProjection(surface)
                break
            }
        }
    }

    private fun readPalette(): AmbientFrame.Palette? {
        val size = sampleSize(texture.width, texture.height, 32) ?: return null
        val bitmap = dynamicBitmap?.takeIf { it.width == size.width && it.height == size.height }
            ?: Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also { dynamicBitmap = it }
        // TextureView leaves the destination unchanged if copying fails.
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        try {
            texture.getBitmap(bitmap)
        } catch (_: IllegalStateException) {
            return null
        }
        if (android.graphics.Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) == 0) return null

        val left = 0f..0.15f
        val center = 0.35f..0.65f
        val right = 0.85f..1f
        val first = 0f..0.3f
        val last = 0.7f..1f
        return AmbientFrame.Palette(listOf(
            bitmap.average(first, left), bitmap.average(center, left), bitmap.average(last, left),
            bitmap.average(left, center), bitmap.average(right, center),
            bitmap.average(first, right), bitmap.average(center, right), bitmap.average(last, right),
        ))
    }

    private suspend fun readProjection(surface: SurfaceTexture) {
        val size = sampleSize(texture.width, texture.height, 96) ?: return
        val copy = liveCopy ?: AmbientLiveCopy(surface).also { liveCopy = it }
        val bitmap = state.liveCopyLock.withLock { copy.read(size) }
        if (currentSurface() === surface) state.frame = bitmap?.let { AmbientFrame.Projection(it) }
    }

    fun close() {
        liveCopy?.close()
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
        lastTimestamp = 0L
        nextFrame = Long.MIN_VALUE
        lastFps = 0
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

private class AmbientLiveCopy(texture: SurfaceTexture) : AutoCloseable {
    private val surface = Surface(texture)
    private val completionHandler = Handler(Looper.getMainLooper())
    private var buffer: Bitmap? = null
    private var pixels = IntArray(0)
    private var scratch = IntArray(0)

    suspend fun read(size: AmbientSampleSize): Bitmap? = withContext(Dispatchers.Default) {
        val (width, height) = size
        val bitmap = buffer?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                buffer = it
                pixels = IntArray(width * height)
                scratch = IntArray(width * height)
            }
        // Finish any outstanding copy before releasing its target buffer or Surface.
        val result = suspendCoroutine { continuation ->
            try {
                PixelCopy.request(surface, bitmap, { continuation.resume(it) }, completionHandler)
            } catch (_: IllegalArgumentException) {
                continuation.resume(PixelCopy.ERROR_SOURCE_INVALID)
            }
        }
        if (result != PixelCopy.SUCCESS) return@withContext null

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val radius = (maxOf(width, height) / 12).coerceAtLeast(1)
        blurAmbientLines(pixels, scratch, height, width, width, 1, radius)
        blurAmbientLines(scratch, pixels, width, height, 1, width, radius)
        // Canvas may retain the image in its display list after the next capture starts.
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    override fun close() = surface.release()
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
