package com.lechixy.kick.components.channel.chat

import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleStartEffect
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.gif.repeatCount
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.*

internal val LocalChatAnimationsEnabled = staticCompositionLocalOf { true }

/** One drawable and animation clock per visible emote URL, including replies and pinned messages. */
@Composable
internal fun SynchronizedEmote(url: String, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val animated = LocalChatAnimationsEnabled.current
    val active = LocalChatContentActive.current
    var shared by remember(url, animated) { mutableStateOf<SharedEmote?>(null) }
    LifecycleStartEffect(url, animated, active) {
        val entry = if (active) VisibleEmotes.acquire(context, url, animated) else null
        shared = entry
        onStopOrDispose {
            shared = null
            if (entry != null) VisibleEmotes.release(url, entry, animated)
        }
    }
    val entry = shared
    ChatTooltip(emoteTooltipLabel(name)) {
        Box(modifier.semantics { contentDescription = name }) {
            if (entry?.failed == true) {
                Text(":$name:", maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Canvas(Modifier.matchParentSize()) {
                    // Read in the draw phase: all copies invalidate together without recomposing chat rows.
                    entry?.frame
                    val drawable = entry?.drawable ?: return@Canvas
                    val sourceWidth = drawable.intrinsicWidth.coerceAtLeast(1)
                    val sourceHeight = drawable.intrinsicHeight.coerceAtLeast(1)
                    val scale = minOf(size.width / sourceWidth, size.height / sourceHeight)
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val saved = native.save()
                        native.translate((size.width - sourceWidth * scale) / 2, (size.height - sourceHeight * scale) / 2)
                        // Transform each canvas, not the shared drawable: replies can use a different size.
                        native.scale(scale, scale)
                        drawable.draw(native)
                        native.restoreToCount(saved)
                    }
                }
            }
        }
    }
}

internal fun emoteTooltipLabel(name: String): String = ":${name.trim().trim(':').ifBlank { "emote" }}:"

/** Accessed on the main thread; entries and their jobs disappear with the last visible copy. */
internal object VisibleEmotes {
    private val entries = mutableMapOf<Pair<String, Boolean>, SharedEmote>()
    fun acquire(context: Context, url: String, animated: Boolean = true): SharedEmote = entries.getOrPut(url to animated) {
        SharedEmote(context, url, animated)
    }.also { it.readers++ }

    fun release(url: String, entry: SharedEmote, animated: Boolean = true) {
        if (--entry.readers == 0) {
            entries.remove(url to animated)
            entry.close()
        }
    }
}

internal class SharedEmote(context: Context, url: String, animated: Boolean) : Drawable.Callback {
    var readers = 0
    var drawable by mutableStateOf<Drawable?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var frame by mutableIntStateOf(0)
        private set
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            try {
                val request = ImageRequest.Builder(context).data(url).size(192)
                    // A drawable has one callback. Keep it owned here, outside Coil's shared memory cache.
                    // Encoded files still use Coil's disk cache when scrolled back into view.
                    .memoryCachePolicy(CachePolicy.DISABLED).repeatCount(-1).build()
                val result = SingletonImageLoader.get(context).execute(request)
                val image = (result as? SuccessResult)?.image
                if (image == null) { failed = true; return@launch }
                val decoded = image.asDrawable(context.resources)
                decoded.setBounds(0, 0, decoded.intrinsicWidth.coerceAtLeast(1), decoded.intrinsicHeight.coerceAtLeast(1))
                decoded.callback = this@SharedEmote
                drawable = decoded
                if (animated) (decoded as? Animatable)?.start()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
    }

    override fun invalidateDrawable(who: Drawable) { frame++ }
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        handler.postAtTime(what, this, `when`)
    }
    override fun unscheduleDrawable(who: Drawable, what: Runnable) { handler.removeCallbacks(what, this) }

    fun close() {
        scope.cancel()
        (drawable as? Animatable)?.stop()
        drawable?.callback = null
        handler.removeCallbacksAndMessages(this)
        drawable = null
    }
}
