package com.lechixy.kick.integration

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.chunk.MediaChunk
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.service.player.StreamTrackSelectionFactory
import com.lechixy.kick.service.player.StreamVideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class StreamVideoQualityTest {
    private val low = videoFormat(width = 426, height = 240, bitrate = 350_000)
    private val medium = videoFormat(width = 640, height = 360, bitrate = 800_000)
    private val high = videoFormat(width = 1920, height = 1080, bitrate = 5_000_000)
    private var quality = StreamVideoQuality()

    @Test
    fun miniAndFullSizeSelectFutureSegmentsWhileKeepingTheQueue() {
        val selection = createSelection(20_000_000)
        selection.selectNext(emptyList())
        assertEquals(1080, selection.selectedFormat.height)

        quality = StreamVideoQuality(compactHeightLimit = 360)
        val highQualityQueue = listOf(chunk(high, 0), chunk(high, 1))
        selection.selectNext(highQualityQueue)
        assertEquals(360, selection.selectedFormat.height)
        assertEquals(highQualityQueue.size, selection.evaluateQueueSize(0, highQualityQueue))

        quality = StreamVideoQuality()
        val compactQueue = List(16) { chunk(medium, it) }
        selection.selectNext(compactQueue)
        assertEquals(1080, selection.selectedFormat.height)
        assertEquals(compactQueue.size, selection.evaluateQueueSize(0, compactQueue))
    }

    @Test
    fun manualSelectionAndAutoUseTheSameSelectionWithDifferentBandwidthRules() {
        val selection = createSelection(950_000)
        selection.selectNext(emptyList())
        assertEquals(240, selection.selectedFormat.height)

        quality = StreamVideoQuality(preferredHeight = 720)
        val lowQualityQueue = listOf(chunk(low, 0), chunk(low, 1))
        selection.selectNext(lowQualityQueue)
        assertEquals(360, selection.selectedFormat.height)

        quality = StreamVideoQuality()
        selection.selectNext(listOf(chunk(medium, 0), chunk(medium, 1)))
        assertEquals(240, selection.selectedFormat.height)
    }

    @Test
    fun compactQualityFallsBackToTheSmallestStreamWhenNoRenditionFits() {
        quality = StreamVideoQuality(compactHeightLimit = 144)
        val selection = createSelection(20_000_000)
        selection.selectNext(emptyList())
        assertEquals(240, selection.selectedFormat.height)
    }

    private fun createSelection(bandwidth: Long): ExoTrackSelection {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val meter = DefaultBandwidthMeter.Builder(context).setInitialBitrateEstimate(bandwidth).build()
        val group = TrackGroup("video", high, medium, low)
        return StreamTrackSelectionFactory { quality }.createTrackSelections(
            arrayOf(ExoTrackSelection.Definition(group, 0, 1, 2)),
            meter,
            MediaPeriodId("video"),
            Timeline.EMPTY,
        ).single()!!.also { it.enable() }
    }

    private fun ExoTrackSelection.selectNext(queue: List<MediaChunk>) {
        updateSelectedTrack(
            0,
            queue.lastOrNull()?.endTimeUs ?: 0L,
            C.TIME_UNSET,
            queue,
            Array(length()) { MediaChunkIterator.EMPTY },
        )
    }

    private fun chunk(format: Format, index: Int): MediaChunk = object : MediaChunk(
        ByteArrayDataSource(byteArrayOf(0)),
        DataSpec.Builder().setUri("https://example.test/segment-$index.ts").build(),
        format,
        C.SELECTION_REASON_ADAPTIVE,
        null,
        index * 2_000_000L,
        (index + 1) * 2_000_000L,
        index.toLong(),
        null,
    ) {
        override fun isLoadCompleted(): Boolean = true
        override fun cancelLoad() = Unit
        override fun load() = Unit
    }

    private fun videoFormat(width: Int, height: Int, bitrate: Int): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_H264)
        .setWidth(width)
        .setHeight(height)
        .setAverageBitrate(bitrate)
        .build()
}
