package com.lechixy.kick.service.player

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.Allocator
import com.lechixy.kick.BuildConfig

@UnstableApi
internal class PlaybackMemoryMonitor(private val allocator: Allocator) {
    private var lastSampleMs: Long? = null

    fun sample(mode: String, nowMs: Long = SystemClock.elapsedRealtime()) {
        val previous = lastSampleMs
        if (previous != null && nowMs - previous < 10_000L) return
        lastSampleMs = nowMs

        val runtime = Runtime.getRuntime()
        val heapMax = runtime.maxMemory()
        val heapUsed = runtime.totalMemory() - runtime.freeMemory()
        val highPressure = heapUsed >= heapMax - heapMax / 4
        if (!BuildConfig.DEBUG && !highPressure) return

        val message = "mode=$mode heapUsedKb=${heapUsed / 1024} heapMaxKb=${heapMax / 1024} " +
            "nativeUsedKb=${Debug.getNativeHeapAllocatedSize() / 1024} playerBufferKb=${allocator.totalBytesAllocated / 1024}"
        if (highPressure) Log.w("KickMemory", message) else Log.d("KickMemory", message)
    }
}
