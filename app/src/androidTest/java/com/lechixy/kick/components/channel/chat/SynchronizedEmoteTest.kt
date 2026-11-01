package com.lechixy.kick.components.channel.chat

import android.graphics.drawable.Animatable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SynchronizedEmoteTest {
    @Test fun animatedCopiesSharePlaybackAndStopOnlyAfterTheLastCopyLeaves() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "synchronized-emote-test.gif")
        instrumentation.context.assets.open("synchronized-emote.gif").use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
        val url = fixture.toURI().toString()
        var acquired = 0
        var animated: Animatable? = null
        val first = withContext(Dispatchers.Main) { VisibleEmotes.acquire(context, url).also { acquired++ } }
        try {
            val second = withContext(Dispatchers.Main) { VisibleEmotes.acquire(context, url).also { acquired++ } }
            assertSame(first, second)
            withTimeout(10000) {
                while (withContext(Dispatchers.Main) { first.drawable == null && !first.failed }) delay(10)
            }
            withContext(Dispatchers.Main) {
                assertFalse("Animated GIF should decode successfully", first.failed)
                animated = first.drawable as? Animatable
                assertNotNull("GIF must be animated rather than a static bitmap", animated)
                assertTrue(animated!!.isRunning)
                assertSame(first.drawable, second.drawable)
            }
            val colors = mutableSetOf<Int>()
            withTimeout(5000) {
                while (!(Color.RED in colors && Color.GREEN in colors)) {
                    withContext(Dispatchers.Main) {
                        val drawable = first.drawable!!
                        val bitmap = Bitmap.createBitmap(drawable.bounds.width(), drawable.bounds.height(), Bitmap.Config.ARGB_8888)
                        drawable.draw(Canvas(bitmap))
                        colors.add(bitmap.getPixel(0, 0))
                        bitmap.recycle()
                    }
                    delay(40)
                }
            }
            withContext(Dispatchers.Main) {
                VisibleEmotes.release(url, first)
                acquired--
                assertTrue("Remaining copy must keep its shared clock", animated!!.isRunning)
                assertEquals(1, second.readers)
            }
        } finally {
            withContext(Dispatchers.Main) {
                repeat(acquired) { VisibleEmotes.release(url, first) }
                acquired = 0
                animated?.let { assertFalse("Offscreen animations must stop", it.isRunning) }
            }
            fixture.delete()
        }
    }
}
