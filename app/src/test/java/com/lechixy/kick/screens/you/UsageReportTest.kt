package com.lechixy.kick.screens.you

import com.lechixy.kick.data.common.remote.ConnectionTransport.MOBILE
import com.lechixy.kick.data.common.remote.ConnectionTransport.WIFI
import com.lechixy.kick.data.player.local.PlaybackContentType.LIVE
import com.lechixy.kick.data.player.local.PlaybackContentType.RECORDING
import com.lechixy.kick.data.player.local.PlaybackContentType.UNCLASSIFIED
import com.lechixy.kick.data.player.local.PlaybackUsage
import com.lechixy.kick.data.player.local.PlaybackUsageMinute
import com.lechixy.kick.data.player.local.WatchMinute
import com.lechixy.kick.data.player.local.usageMinute
import com.lechixy.kick.data.player.local.watchTimeByMinute
import com.lechixy.kick.data.settings.local.PlaybackBackupData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageReportTest {
    @Test
    fun watchIntervalsSplitAtLocalMidnight() {
        val midnight = Instant.parse("2026-04-05T21:00:00Z").toEpochMilli()
        val day = LocalDate.parse("2026-04-06").toEpochDay()
        val minute = usageMinute(midnight)
        val buckets = watchTimeByMinute(midnight - 2_000, midnight + 63_000, ZoneId.of("Europe/Istanbul"))
        assertEquals(listOf(
            WatchMinute(day - 1, minute - 1, 2_000L),
            WatchMinute(day, minute, 60_000L),
            WatchMinute(day, minute + 1, 3_000L),
        ), buckets)
    }

    @Test
    fun periodsFilterDatedRecordsAndRankBySelectedMetric() {
        val now = ZonedDateTime.parse("2026-04-06T16:00:00+03:00[Europe/Istanbul]")
        val today = now.toLocalDate().toEpochDay()
        val entries = listOf(
            PlaybackUsage("alpha", "Alpha", WIFI, 10_000, 100, epochDay = today - 6),
            PlaybackUsage("alpha", "Alpha", MOBILE, 10_000, 200, epochDay = today - 6),
            PlaybackUsage("beta", "Beta", WIFI, 5_000, 900, epochDay = today),
            PlaybackUsage("alpha", "Alpha", WIFI, 10_000, 100, epochDay = today - 7),
            PlaybackUsage("alpha", "Alpha", WIFI, 10_000, 100),
        )

        val week = UsageReport(entries, UsageMetric.WATCH_TIME, UsagePeriod.WEEK, now)
        assertEquals(25_000L, week.total)
        assertEquals(mapOf(today - 6 to 20_000L, today to 5_000L), week.byDay)
        assertFalse(week.undated)
        assertEquals(listOf("alpha", "beta"), week.channels.map { it.slug })

        val data = UsageReport(entries, UsageMetric.DATA, UsagePeriod.WEEK, now)
        assertEquals(mapOf(WIFI to 1_000L, MOBILE to 200L), data.networks)
        assertEquals(listOf("beta", "alpha"), data.channels.map { it.slug })

        val month = UsageReport(entries, UsageMetric.WATCH_TIME, UsagePeriod.MONTH, now)
        assertEquals(35_000L, month.total)
        val all = UsageReport(entries, UsageMetric.WATCH_TIME, UsagePeriod.ALL, now)
        assertEquals(45_000L, all.total)
        assertEquals(month.byDay, all.byDay)
        assertTrue(all.undated)

        val custom = UsageReport(entries, UsageMetric.WATCH_TIME, UsagePeriod.CUSTOM, now, (today - 7)..(today - 6))
        assertEquals(mapOf(today - 7 to 10_000L, today - 6 to 20_000L), custom.byDay)
        assertEquals(30_000L, custom.total)
    }

    @Test
    fun last24HoursIncludesOnlyTheLatest1440MinutesAcrossMidnight() {
        val now = ZonedDateTime.parse("2026-04-06T16:00:00+03:00[Europe/Istanbul]")
        val minute = usageMinute(now.toInstant().toEpochMilli())
        val today = now.toLocalDate().toEpochDay()
        val entries = listOf(
            PlaybackUsage("alpha", "Alpha", WIFI, 30_000, 600, epochDay = today - 1, recentMinutes = mapOf(
                minute - 1_440 to PlaybackUsageMinute(20_000, 500),
                minute - 1_439 to PlaybackUsageMinute(10_000, 100),
            )),
            PlaybackUsage("beta", "Beta", MOBILE, 5_000, 200, epochDay = today,
                recentMinutes = mapOf(minute to PlaybackUsageMinute(5_000, 200))),
            PlaybackUsage("gamma", "Gamma", WIFI, 30_000, 500, epochDay = today),
        )
        val watch = UsageReport(entries, UsageMetric.WATCH_TIME, UsagePeriod.LAST_24_HOURS, now)
        assertEquals(mapOf(today - 1 to 10_000L, today to 5_000L), watch.byDay)
        assertEquals(15_000L, watch.total)
        val data = UsageReport(entries, UsageMetric.DATA, UsagePeriod.LAST_24_HOURS, now)
        assertEquals(300L, data.total)
        assertEquals(listOf("beta", "alpha"), data.channels.map { it.slug })
    }

    @Test
    fun totalsIncludeBothPlaybackTypesAndOlderRecordsForTheSelectedPeriod() {
        val now = ZonedDateTime.parse("2026-04-06T16:00:00+03:00[Europe/Istanbul]")
        val today = now.toLocalDate().toEpochDay()
        val minute = usageMinute(now.toInstant().toEpochMilli())
        val live = PlaybackUsage("alpha", "Alpha", WIFI, 12_000, 600, epochDay = today,
            recentMinutes = mapOf(minute to PlaybackUsageMinute(2_000, 100)), contentType = LIVE)
        val recording = live.copy(watchedMs = 8_000, bytes = 300,
            recentMinutes = mapOf(minute to PlaybackUsageMinute(3_000, 200)), contentType = RECORDING)
        val older = live.copy(watchedMs = 5_000, bytes = 250, recentMinutes = emptyMap(), contentType = UNCLASSIFIED)
        val entries = listOf(live, recording, older)
        assertEquals(3, entries.map { it.key }.distinct().size)

        val week = UsageReport(entries, UsageMetric.WATCH_TIME, UsagePeriod.WEEK, now)
        assertEquals(mapOf(LIVE to 12_000L, RECORDING to 8_000L, UNCLASSIFIED to 5_000L), week.byContentType)
        assertEquals(25_000L, week.total)
        assertEquals(week.total, week.channels.single().total)

        val recent = UsageReport(entries, UsageMetric.DATA, UsagePeriod.LAST_24_HOURS, now)
        assertEquals(mapOf(LIVE to 100L, RECORDING to 200L), recent.byContentType)
        assertEquals(300L, recent.total)
        assertEquals(mapOf(WIFI to 300L), recent.networks)
    }

    @Test
    fun settingsBackupStoresDayAndAcceptsLegacyUsage() {
        val legacy = Json.decodeFromString<PlaybackBackupData>(
            """{"usage":[{"channelSlug":"alpha","channelName":"Alpha","transport":"WIFI","watchedMs":1000}]}""",
        )
        assertNull(legacy.usage.single().epochDay)
        assertEquals(UNCLASSIFIED, legacy.usage.single().contentType)

        val dated = legacy.usage.single().copy(epochDay = LocalDate.parse("2026-04-06").toEpochDay(), bytes = 250)
        val minute = usageMinute(Instant.parse("2026-04-06T08:00:00Z").toEpochMilli())
        val recent = dated.copy(recentMinutes = mapOf(minute to PlaybackUsageMinute(1_000, 250)), contentType = LIVE)
        val backup = legacy.copy(usage = legacy.usage + recent)
        val restored = Json.decodeFromString<PlaybackBackupData>(Json.encodeToString(backup))
        assertEquals(backup, restored)
    }
}
