package com.lechixy.kick.data.channel.repository

import android.os.SystemClock
import com.lechixy.kick.data.channel.model.ClipDetail
import com.lechixy.kick.data.common.remote.KickNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore

internal object ClipRepository {
    private val cache = ClipDetailCache(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        nowMs = SystemClock::elapsedRealtime,
        fetch = { id -> KickNetwork.api.getClipDetail(id).data },
    )

    fun cachedPreview(id: String) = cache.cached(id, PREVIEW_TTL_MS)
    suspend fun preview(id: String) = cache.get(id, PREVIEW_TTL_MS, isPreview = true)
    suspend fun getClip(id: String) = cache.get(id, PLAYBACK_TTL_MS)

    private const val PREVIEW_TTL_MS = 30 * 60_000L
    private const val PLAYBACK_TTL_MS = 60_000L
}

internal class ClipDetailCache(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val fetch: suspend (String) -> ClipDetail,
) {
    private data class CachedClip(val detail: ClipDetail, val cachedAtMs: Long)

    private val lock = Any()
    private val clips = LinkedHashMap<String, CachedClip>(128, .75f, true)
    private val inFlight = mutableMapOf<String, Deferred<ClipDetail>>()
    private val previewSlots = Semaphore(3)

    fun cached(id: String, maxAgeMs: Long): ClipDetail? = synchronized(lock) {
        freshClip(id, maxAgeMs)
    }

    suspend fun get(id: String, maxAgeMs: Long, isPreview: Boolean = false): ClipDetail {
        val existing = synchronized(lock) {
            freshClip(id, maxAgeMs)?.let { return it }
            inFlight[id]
        }
        if (existing != null) return existing.await()

        // Opening a clip can fetch immediately even when chat previews occupy every slot.
        val slot = previewSlots.takeIf { isPreview }
        slot?.acquire()
        val request = synchronized(lock) {
            freshClip(id, maxAgeMs)?.let {
                slot?.release()
                return it
            }
            inFlight[id]?.also { slot?.release() } ?: startRequest(id, slot)
        }
        return request.await()
    }

    // The shared request survives an individual chat row scrolling out of view.
    private fun startRequest(id: String, slot: Semaphore?): Deferred<ClipDetail> {
        val request = scope.async(start = CoroutineStart.LAZY) {
            fetch(id).also { detail ->
                check(detail.playbackUrl.isNotBlank())
                synchronized(lock) {
                    clips[id] = CachedClip(detail, nowMs())
                    if (clips.size > 128) clips.remove(clips.keys.first())
                }
            }
        }
        inFlight[id] = request
        request.invokeOnCompletion {
            synchronized(lock) { inFlight.remove(id) }
            slot?.release()
        }
        request.start()
        return request
    }

    private fun freshClip(id: String, maxAgeMs: Long) = clips[id]
        ?.takeIf { nowMs() - it.cachedAtMs < maxAgeMs }
        ?.detail
}
