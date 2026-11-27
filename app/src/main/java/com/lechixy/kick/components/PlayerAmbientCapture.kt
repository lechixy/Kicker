package com.lechixy.kick.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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
import androidx.compose.ui.unit.IntRect
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
private const val BAR_DETECTION_INTERVAL_MS = 250L
private const val BAR_SAMPLE_LIMIT = 256
private const val PROJECTION_SAMPLE_LIMIT = 96

internal data class AmbientSeek(val sequence: Int, val previousTimestamp: Long)

private data class AmbientDemand(
    val mode: PlayerAmbientMode,
    val playing: Boolean,
    val seek: AmbientSeek?,
    val firstFrame: Int,
)

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
    barOptions: VideoBarOptions = VideoBarOptions(preserve = true),
    allowCrop: Boolean = true,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentPlaying by rememberUpdatedState(playing)
    val currentSeek by rememberUpdatedState(seek)
    val currentFirstFrame by rememberUpdatedState(firstFrame)
    val currentFps by rememberUpdatedState(liveFps)
    val keepCurrentFrame by rememberUpdatedState(keepFrameWhenHidden)
    val source = remember(texture, mediaId, mediaUrl, barOptions) { Any() }
    val capturingAmbient = visible && mode != PlayerAmbientMode.Off
    val cropping = allowCrop && barOptions.removesBars
    val currentMode by rememberUpdatedState(if (capturingAmbient) mode else PlayerAmbientMode.Off)
    val active = capturingAmbient || cropping
    LaunchedEffect(state, source, active, cropping, keepFrameWhenHidden, lifecycle) {
        val owner = Any()
        state.captureOwner = owner
        if (!keepFrameWhenHidden || state.captureSource !== source) state.frame = null
        state.barLayout = VideoBarLayout()
        state.captureSource = source
        if (!active || texture == null) return@LaunchedEffect

        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val options = if (cropping) barOptions else barOptions.copy(removeHorizontal = false, removeVertical = false)
            val capture = AmbientCapture(
                state, texture, options,
                detectBars = options.removesBars || options.preserve,
            )
            var completedSeek: AmbientSeek? = null
            try {
                snapshotFlow {
                    AmbientDemand(currentMode, currentPlaying, currentSeek, if (currentPlaying) 0 else currentFirstFrame)
                }.collectLatest { demand ->
                    if (demand.mode == PlayerAmbientMode.Off) state.frame = null
                    val pendingSeek = demand.seek?.takeUnless { it == completedSeek }
                    if (pendingSeek != null) capture.resetFrame()
                    if (demand.playing) {
                        capture.awaitSeek(pendingSeek)
                        completedSeek = demand.seek
                        if (demand.mode == PlayerAmbientMode.Live) {
                            capture.sampleLive { currentFps }
                        } else {
                            capture.sampleDynamic(demand.mode)
                        }
                    } else {
                        capture.samplePaused(demand.mode, pendingSeek)
                        completedSeek = demand.seek
                    }
                }
            } finally {
                capture.close()
                if (state.captureOwner === owner) {
                    state.barLayout = VideoBarLayout()
                    if (!keepCurrentFrame) state.frame = null
                }
            }
        }
    }
}

private class AmbientCapture(
    private val state: PlayerAmbientState,
    private val texture: TextureView,
    private val options: VideoBarOptions,
    private val detectBars: Boolean,
) {
    private var surfaceTexture: SurfaceTexture? = null
    private var surfaceCopy: AmbientSurfaceCopy? = null
    private var buffer: Bitmap? = null
    private var detectionBuffer: Bitmap? = null
    private var detectionPixels = IntArray(0)
    private var tracker = VideoBarTracker()
    private var lastDetectionTime = -BAR_DETECTION_INTERVAL_MS
    private var lastDetectionTimestamp = 0L
    private var hasSample = false
    private val projection = AmbientProjection()

    fun resetFrame() {
        tracker = VideoBarTracker()
        lastDetectionTime = -BAR_DETECTION_INTERVAL_MS
        lastDetectionTimestamp = 0L
        hasSample = false
        state.barLayout = VideoBarLayout()
        state.frame = null
    }

    private fun currentSurface(): SurfaceTexture? {
        val surface = texture.surfaceTexture?.takeIf { texture.isAvailable && !it.isReleased }
        if (surface !== surfaceTexture) {
            surfaceCopy?.close()
            surfaceCopy = null
            surfaceTexture = surface
            resetFrame()
        }
        return surface
    }

    suspend fun sampleDynamic(mode: PlayerAmbientMode) {
        while (true) {
            val previous = state.frame as? AmbientFrame.Palette
            val previousLayout = state.barLayout
            val result = currentSurface()?.let { readFrame(it, mode) }
            val next = result?.frame as? AmbientFrame.Palette
            if (result != null && previous != null && next != null && state.frame != null &&
                previous.colors != next.colors && previousLayout == result.layout
            ) {
                animate(0f, 1f, animationSpec = tween(DYNAMIC_INTERVAL_MS, easing = LinearEasing)) { fraction, _ ->
                    state.frame = AmbientFrame.Palette(previous.colors.zip(next.colors) { start, end ->
                        lerp(start, end, fraction)
                    })
                }
            } else {
                result?.let(::publish)
                delay(DYNAMIC_INTERVAL_MS.toLong())
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
                val result = readFrame(surface, PlayerAmbientMode.Live)
                if (result == null) cadence.retryCurrentFrame() else publish(result)
            }
        }
    }

    suspend fun awaitSeek(seek: AmbientSeek?) {
        if (seek == null) return
        while (true) {
            withFrameNanos { it }
            val timestamp = currentSurface()?.timestamp ?: continue
            if (timestamp != 0L && timestamp != seek.previousTimestamp) return
        }
    }

    suspend fun samplePaused(mode: PlayerAmbientMode, seek: AmbientSeek?) {
        currentSurface()
        val frameMatchesMode = when (mode) {
            PlayerAmbientMode.Off -> true
            PlayerAmbientMode.Dynamic -> state.frame is AmbientFrame.Palette
            PlayerAmbientMode.Live -> state.frame is AmbientFrame.Projection
        }
        if (seek == null && hasSample && frameMatchesMode && state.barLayout == barLayout()) return
        withTimeoutOrNull(3_000L) {
            while (true) {
                withFrameNanos { it }
                val surface = currentSurface() ?: continue
                val timestamp = surface.timestamp
                if (timestamp == 0L || timestamp == seek?.previousTimestamp) continue
                val result = readFrame(surface, mode, paused = true) ?: continue
                publish(result)
                break
            }
        }
    }

    private suspend fun readFrame(
        surface: SurfaceTexture,
        mode: PlayerAmbientMode,
        paused: Boolean = false,
    ): AmbientSample? {
        val timestamp = surface.timestamp
        if (timestamp == 0L) return null
        val now = SystemClock.elapsedRealtime()
        val shouldDetect = detectBars && (paused ||
            (timestamp != lastDetectionTimestamp && now - lastDetectionTime >= BAR_DETECTION_INTERVAL_MS))
        if (mode == PlayerAmbientMode.Off && !shouldDetect) return null
        val limit = when {
            shouldDetect -> BAR_SAMPLE_LIMIT
            mode == PlayerAmbientMode.Live -> PROJECTION_SAMPLE_LIMIT
            else -> 32
        }
        val size = sampleSize(texture.width, texture.height, limit) ?: return null
        val cached = if (shouldDetect) detectionBuffer else buffer
        val bitmap = cached?.takeIf { it.width == size.width && it.height == size.height }
            ?: Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also {
                if (shouldDetect) detectionBuffer = it else buffer = it
            }
        if (mode == PlayerAmbientMode.Live || detectBars || !texture.copyAmbientBitmap(bitmap)) {
            val copy = surfaceCopy ?: AmbientSurfaceCopy(surface).also { surfaceCopy = it }
            if (!state.liveCopyLock.withLock { copy.copyInto(bitmap) } && !texture.copyAmbientBitmap(bitmap)) return null
        }
        if (currentSurface() !== surface) return null
        if (shouldDetect) {
            val (width, height) = size
            if (detectionPixels.size < width * height) detectionPixels = IntArray(width * height)
            lastDetectionTimestamp = timestamp
            lastDetectionTime = now
            val bars = withContext(Dispatchers.Default) {
                bitmap.getPixels(detectionPixels, 0, width, 0, 0, width, height)
                detectAmbientBlackBars(detectionPixels, width, height, options.detectColored, strict = paused)
            }
            if (currentSurface() !== surface) return null
            tracker.update(bars.normalized(width, height), width, height, paused)
        }
        val layout = barLayout()
        val area = layout.crop.bitmapBounds(size)
        val frame = when (mode) {
            PlayerAmbientMode.Off -> null
            PlayerAmbientMode.Dynamic -> paletteFrom(bitmap, area, layout.mask)
            PlayerAmbientMode.Live -> withContext(Dispatchers.Default) {
                AmbientFrame.Projection(projection.blur(bitmap, area, layout.mask))
            }
        }
        if (currentSurface() !== surface) return null
        return AmbientSample(frame, layout)
    }

    private fun barLayout() = VideoBarLayout(options.crop(tracker.bars), options.mask(tracker.bars))

    private fun publish(sample: AmbientSample) {
        hasSample = true
        state.barLayout = sample.layout
        state.frame = sample.frame
    }

    private fun paletteFrom(bitmap: Bitmap, area: IntRect, bars: VideoBars): AmbientFrame.Palette {
        val left = 0f..0.15f
        val center = 0.35f..0.65f
        val right = 0.85f..1f
        val first = 0f..0.3f
        val last = 0.7f..1f
        return AmbientFrame.Palette(listOf(
            if (bars.top > 0) Color.Black else bitmap.average(area, first, left),
            if (bars.top > 0) Color.Black else bitmap.average(area, center, left),
            if (bars.top > 0) Color.Black else bitmap.average(area, last, left),
            if (bars.left > 0) Color.Black else bitmap.average(area, left, center),
            if (bars.right > 0) Color.Black else bitmap.average(area, right, center),
            if (bars.bottom > 0) Color.Black else bitmap.average(area, first, right),
            if (bars.bottom > 0) Color.Black else bitmap.average(area, center, right),
            if (bars.bottom > 0) Color.Black else bitmap.average(area, last, right),
        ))
    }

    fun close() {
        surfaceCopy?.close()
    }
}

private data class AmbientSample(val frame: AmbientFrame?, val layout: VideoBarLayout)

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

private fun VideoBars.bitmapBounds(size: AmbientSampleSize) = IntRect(
    (left * size.width).roundToInt(),
    (top * size.height).roundToInt(),
    size.width - (right * size.width).roundToInt(),
    size.height - (bottom * size.height).roundToInt(),
)

private fun sampleSize(width: Int, height: Int, limit: Int): AmbientSampleSize? {
    if (width <= 0 || height <= 0) return null
    val scale = minOf(1f, limit.toFloat() / maxOf(width, height))
    return AmbientSampleSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
}

private fun Bitmap.average(area: IntRect, xRange: ClosedFloatingPointRange<Float>, yRange: ClosedFloatingPointRange<Float>): Color {
    val x0 = area.left + ((area.width - 1) * xRange.start).roundToInt()
    val y0 = area.top + ((area.height - 1) * yRange.start).roundToInt()
    val x1 = area.left + ((area.width - 1) * xRange.endInclusive).roundToInt()
    val y1 = area.top + ((area.height - 1) * yRange.endInclusive).roundToInt()
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

    override fun close() = surface.release()
}

private class AmbientProjection {
    private var pixels = IntArray(0)
    private var scratch = IntArray(0)
    private var reducedBitmap: Bitmap? = null
    private val canvas = Canvas()
    private val filter = Paint(Paint.FILTER_BITMAP_FLAG)

    fun blur(bitmap: Bitmap, area: IntRect, mask: VideoBars): Bitmap {
        val scale = minOf(1f, PROJECTION_SAMPLE_LIMIT.toFloat() / maxOf(bitmap.width, bitmap.height))
        val width = (area.width * scale).roundToInt().coerceAtLeast(1)
        val height = (area.height * scale).roundToInt().coerceAtLeast(1)
        if (pixels.size < width * height) {
            pixels = IntArray(width * height)
            scratch = IntArray(width * height)
        }
        if (scale == 1f) {
            bitmap.getPixels(pixels, 0, width, area.left, area.top, width, height)
        } else {
            // The higher-resolution detection frame uses the same projection budget as every other frame.
            val reduced = reducedBitmap?.takeIf { it.width == width && it.height == height }
                ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { reducedBitmap = it }
            canvas.setBitmap(reduced)
            canvas.drawBitmap(
                bitmap, Rect(area.left, area.top, area.right, area.bottom), Rect(0, 0, width, height), filter,
            )
            reduced.getPixels(pixels, 0, width, 0, 0, width, height)
        }
        // Avoid projecting the original color of a retained bar onto adjacent edges.
        val content = mask.bitmapBounds(AmbientSampleSize(width, height))
        if (!mask.isEmpty) {
            for (y in 0 until height) {
                val row = y * width
                if (y < content.top || y >= content.bottom) {
                    pixels.fill(android.graphics.Color.BLACK, row, row + width)
                } else {
                    pixels.fill(android.graphics.Color.BLACK, row, row + content.left)
                    pixels.fill(android.graphics.Color.BLACK, row + content.right, row + width)
                }
            }
        }
        val radius = (maxOf(width, height) / 12).coerceAtLeast(1)
        blurAmbientLines(pixels, scratch, height, width, width, 1, radius)
        blurAmbientLines(scratch, pixels, width, height, 1, width, radius)
        // Canvas may retain the image in its display list after the next capture starts.
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
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
