package com.lechixy.kick.data.player.local

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.lechixy.kick.data.common.remote.ConnectionTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
internal enum class PlaybackContentType { LIVE, RECORDING, UNCLASSIFIED }

internal data class PlaybackUsageChannel(
    val slug: String,
    val name: String,
    val contentType: PlaybackContentType,
    val avatar: String? = null,
)

@Serializable
internal data class PlaybackUsage(
    val channelSlug: String,
    val channelName: String,
    val transport: ConnectionTransport,
    val watchedMs: Long = 0,
    val bytes: Long = 0,
    val channelAvatar: String? = null,
    val epochDay: Long? = null,
    val recentMinutes: Map<Long, PlaybackUsageMinute> = emptyMap(),
    val contentType: PlaybackContentType = PlaybackContentType.UNCLASSIFIED,
) {
    internal val key get() = PlaybackUsageKey(channelSlug, transport, epochDay, contentType)
}

internal data class PlaybackUsageKey(
    val slug: String,
    val transport: ConnectionTransport,
    val epochDay: Long?,
    val contentType: PlaybackContentType,
)

@Serializable
internal data class PlaybackUsageMinute(val watchedMs: Long = 0, val bytes: Long = 0)

private const val MILLIS_PER_MINUTE = 60_000L
internal fun usageMinute(timestampMs: Long): Long = Math.floorDiv(timestampMs, MILLIS_PER_MINUTE)
internal fun last24UsageMinutes(nowMinute: Long): LongRange = (nowMinute - 24 * 60 + 1)..nowMinute
internal fun usageMinuteInstant(minute: Long): Instant = Instant.ofEpochMilli(minute * MILLIS_PER_MINUTE)

internal data class WatchMinute(val day: Long, val minute: Long, val watchedMs: Long)

internal fun watchTimeByMinute(startMs: Long, endMs: Long, zone: ZoneId): List<WatchMinute> = buildList {
    var cursor = startMs
    while (cursor < endMs) {
        val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
        val midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val minute = usageMinute(cursor)
        val endOfSlice = minOf(endMs, midnight, (minute + 1) * MILLIS_PER_MINUTE)
        add(WatchMinute(day.toEpochDay(), minute, endOfSlice - cursor))
        cursor = endOfSlice
    }
}

/** Buffers transfer updates and persists daily per-channel totals in batches. */
internal object PlaybackUsageStore {
    private const val LEGACY_KEY = "entries"
    private const val DAY_PREFIX = "day_"
    private val json = Json { ignoreUnknownKeys = true }
    private var preferences: SharedPreferences? = null
    private val pending = mutableMapOf<PlaybackUsageKey, PlaybackUsage>()
    private var clearedAt = 0L
    private val channelClearedAt = mutableMapOf<String, Long>()
    private val mutableEntries = MutableStateFlow<List<PlaybackUsage>>(emptyList())
    val entries = mutableEntries.asStateFlow()

    @Synchronized
    fun backupSnapshot(): List<PlaybackUsage> {
        flush()
        return mutableEntries.value
    }

    @Synchronized
    fun init(context: Context) {
        if (preferences != null) return
        val prefs = context.getSharedPreferences("playback_usage", Context.MODE_PRIVATE)
        mutableEntries.value = prefs.all.flatMap { (key, raw) ->
            if (raw is String && (key == LEGACY_KEY || key.startsWith(DAY_PREFIX))) {
                runCatching { json.decodeFromString<List<PlaybackUsage>>(raw) }.getOrDefault(emptyList())
            } else emptyList()
        }
        preferences = prefs
        persist(mutableEntries.value, emptySet())
    }

    @Synchronized
    fun addBytes(channel: PlaybackUsageChannel, transport: ConnectionTransport, bytes: Long) {
        if (bytes <= 0L) return
        val now = System.currentTimeMillis()
        val day = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        add(channel, transport, day, usageMinute(now), bytes = bytes)
    }

    private fun add(
        channel: PlaybackUsageChannel,
        transport: ConnectionTransport,
        epochDay: Long,
        minute: Long,
        watchedMs: Long = 0,
        bytes: Long = 0,
    ) {
        val key = PlaybackUsageKey(channel.slug, transport, epochDay, channel.contentType)
        val existing = pending[key] ?: PlaybackUsage(
            channel.slug, channel.name, transport, epochDay = epochDay, contentType = channel.contentType,
        )
        val previousMinute = existing.recentMinutes[minute] ?: PlaybackUsageMinute()
        pending[existing.key] = existing.copy(
            watchedMs = existing.watchedMs + watchedMs,
            bytes = existing.bytes + bytes,
            channelAvatar = channel.avatar ?: existing.channelAvatar,
            recentMinutes = existing.recentMinutes + (minute to PlaybackUsageMinute(
                previousMinute.watchedMs + watchedMs, previousMinute.bytes + bytes,
            )),
        )
    }

    @Synchronized
    fun addWatchInterval(channel: PlaybackUsageChannel, transport: ConnectionTransport, since: Long, now: Long) {
        val start = maxOf(since, clearedAt, channelClearedAt[channel.slug] ?: 0L)
        if (now <= start) return
        val endMs = System.currentTimeMillis()
        watchTimeByMinute(endMs - (now - start), endMs, ZoneId.systemDefault()).forEach { (day, minute, duration) ->
            add(channel, transport, day, minute, watchedMs = duration)
        }
    }

    @Synchronized
    fun clear(channelSlug: String? = null) {
        val now = SystemClock.elapsedRealtime()
        if (channelSlug == null) {
            clearedAt = now
            channelClearedAt.clear()
            pending.clear()
            persist(emptyList(), mutableEntries.value.map { it.epochDay }.toSet())
        } else {
            channelClearedAt[channelSlug] = now
            pending.keys.removeAll { it.slug == channelSlug }
            val (removed, remaining) = mutableEntries.value.partition { it.channelSlug == channelSlug }
            persist(remaining, removed.map { it.epochDay }.toSet())
        }
    }

    @Synchronized
    fun flush() {
        if (pending.isEmpty()) return
        val combined = mutableEntries.value.associateBy { it.key }.toMutableMap()
        pending.forEach { (key, usage) ->
            val previous = combined[key]
            combined[key] = usage.copy(
                watchedMs = usage.watchedMs + (previous?.watchedMs ?: 0L),
                bytes = usage.bytes + (previous?.bytes ?: 0L),
                channelAvatar = usage.channelAvatar ?: previous?.channelAvatar,
                recentMinutes = previous?.recentMinutes.orEmpty().toMutableMap().apply {
                    usage.recentMinutes.forEach { (minute, sample) ->
                        val current = get(minute) ?: PlaybackUsageMinute()
                        put(minute, PlaybackUsageMinute(current.watchedMs + sample.watchedMs, current.bytes + sample.bytes))
                    }
                },
            )
        }
        val changedDays = pending.keys.map { it.epochDay }.toSet()
        pending.clear()
        persist(combined.values.toList(), changedDays)
    }

    @Synchronized
    fun restore(entries: List<PlaybackUsage>) {
        flush()
        val combined = mutableEntries.value.associateBy { it.key }.toMutableMap()
        val changedDays = mutableSetOf<Long?>()
        entries.filter { it.isValid() }.forEach { entry ->
            val key = entry.key
            val current = combined[key]
            val minutes = current?.recentMinutes.orEmpty().toMutableMap()
            if (entry.epochDay != null) entry.recentMinutes.forEach { (minute, sample) ->
                if (sample.watchedMs >= 0 && sample.bytes >= 0) {
                    val previous = minutes[minute] ?: PlaybackUsageMinute()
                    minutes[minute] = PlaybackUsageMinute(maxOf(sample.watchedMs, previous.watchedMs), maxOf(sample.bytes, previous.bytes))
                }
            }
            val merged = entry.copy(
                channelName = current?.channelName ?: entry.channelName,
                watchedMs = maxOf(entry.watchedMs, current?.watchedMs ?: 0, minutes.values.sumOf { it.watchedMs }),
                bytes = maxOf(entry.bytes, current?.bytes ?: 0, minutes.values.sumOf { it.bytes }),
                channelAvatar = current?.channelAvatar ?: entry.channelAvatar,
                recentMinutes = minutes,
            )
            if (merged != current) {
                combined[key] = merged
                changedDays += entry.epochDay
            }
        }
        persist(combined.values.toList(), changedDays)
    }

    private fun persist(entries: List<PlaybackUsage>, changedDays: Set<Long?>) {
        val daysToWrite = changedDays.toMutableSet()
        val recent = last24UsageMinutes(usageMinute(System.currentTimeMillis()))
        val retained = entries.map { entry ->
            if (entry.recentMinutes.keys.all { it in recent }) entry else {
                daysToWrite += entry.epochDay
                entry.copy(recentMinutes = entry.recentMinutes.filterKeys { it in recent })
            }
        }
        if (daysToWrite.isEmpty()) return
        val byDay = retained.filter { it.epochDay in daysToWrite }.groupBy { it.epochDay }
        preferences?.edit()?.apply {
            daysToWrite.forEach { day ->
                val key = day?.let { "$DAY_PREFIX$it" } ?: LEGACY_KEY
                val dailyEntries = byDay[day]
                if (dailyEntries == null) remove(key) else putString(key, json.encodeToString(dailyEntries))
            }
        }?.apply()
        mutableEntries.value = retained
    }
}

private fun PlaybackUsage.isValid(): Boolean = channelSlug.isNotBlank() && watchedMs >= 0 && bytes >= 0 &&
    (epochDay == null || epochDay in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay())
