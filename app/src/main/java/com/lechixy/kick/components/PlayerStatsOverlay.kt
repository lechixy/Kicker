package com.lechixy.kick.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.service.player.PLAYBACK_CHART_SAMPLE_COUNT
import com.lechixy.kick.service.player.PlaybackDiagnostics
import com.lechixy.kick.service.player.videoSizeLabel
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun PlayerStatsOverlay(
    videoId: String?,
    viewport: IntSize,
    volumePercent: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats by PlaybackDiagnostics.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { PlayerStatsFormatter(locale) }
    val dates = remember(locale) { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", locale) }
    val unavailable = "—"
    val samples = stats.chartSamples
    val bandwidthHistory = remember(samples) { samples.map { it.bandwidthBps } }
    val networkHistory = remember(samples) { samples.map { it.networkBytesPerSecond } }
    val bufferHistory = remember(samples) { samples.map { it.bufferedMs } }

    Surface(
        modifier = modifier,
        color = Color(0xE6141414),
        contentColor = Color.White,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(start = 10.dp, end = 6.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.player_stats_for_nerds),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        stringResource(R.string.player_stats_close),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                StatsRow(R.string.player_stats_video_id, "${videoId ?: unavailable} / ${stats.sessionId ?: unavailable}")
                StatsRow(R.string.player_stats_connection, format.bitRate(stats.bandwidthBps)) {
                    StatsChart(bandwidthHistory, Color(0xFF7AC7FF))
                }
                StatsRow(R.string.player_stats_network, format.byteRate(stats.networkBytesPerSecond)) {
                    StatsChart(networkHistory, Color(0xFFC5A5FF), bars = true)
                }
                StatsRow(R.string.player_stats_buffer, format.duration(stats.bufferedMs)) {
                    StatsChart(bufferHistory, Color(0xFF8FE6A5))
                }
                val viewportSize = videoSizeLabel(viewport.width, viewport.height, null).takeIf { viewport != IntSize.Zero }
                StatsRow(
                    R.string.player_stats_frames,
                    stringResource(R.string.player_stats_frames_value, viewportSize ?: unavailable, stats.droppedFrames,
                        stats.droppedFrames + stats.renderedFrames)
                )
                val currentSize = videoSizeLabel(stats.width, stats.height, stats.frameRate)
                StatsRow(R.string.player_stats_resolution, "${currentSize ?: unavailable} / ${stats.bestVideoSize ?: unavailable}")
                StatsRow(R.string.player_stats_volume, stringResource(R.string.player_stats_volume_value, volumePercent))
                StatsRow(R.string.player_stats_codecs, "${stats.videoCodec ?: unavailable} / ${stats.audioCodec ?: unavailable}")
                StatsRow(R.string.player_stats_color, stats.color ?: unavailable)
                stats.videoBitrate?.let { StatsRow(R.string.player_stats_bitrate, format.bitRate(it.toLong())) }
                if (stats.live) {
                    StatsRow(R.string.player_stats_live, "${format.duration(stats.liveOffsetMs)} / ${format.duration(stats.targetOffsetMs)}")
                }
                StatsRow(
                    R.string.player_stats_stalls,
                    "${stats.rebuffers} / ${format.duration(stats.bufferingMs)}"
                )
                StatsRow(
                    R.string.player_stats_rendering,
                    "${stats.skippedFrames} / ${format.duration(stats.processingOffsetMs)} / ${stats.audioUnderruns}"
                )
                val mode = when {
                    stats.buffering -> "BUF"
                    stats.playing -> "PLAY"
                    stats.active -> "PAUSE"
                    else -> "IDLE"
                }
                StatsRow(
                    R.string.player_stats_mystery,
                    "$mode ${if (stats.live) "LIVE" else "VOD"} • ${format.number(stats.speed)}× " +
                        "• t:${format.duration(stats.positionMs)} b:${format.duration(stats.bufferedPositionMs)}"
                )
                StatsRow(R.string.player_stats_date, stats.sampledAtMs.takeIf { it > 0 }?.let { dates.format(Date(it)) } ?: unavailable)
                stats.errorCode?.let { StatsRow(R.string.player_stats_error, it) }
            }
        }
    }
}

@Composable
private fun StatsRow(label: Int, value: String, chart: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = if (chart == null) Alignment.Top else Alignment.CenterVertically
    ) {
        Text(
            stringResource(label),
            Modifier.weight(.43f),
            color = Color(0xFFCBCFD0),
            style = MaterialTheme.typography.labelSmall
        )
        if (chart == null) {
            StatsValue(value, Modifier.weight(.57f))
        } else {
            Row(
                Modifier.weight(.57f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) { chart() }
                StatsValue(value, Modifier.widthIn(min = 64.dp), TextAlign.End)
            }
        }
    }
}

@Composable
private fun StatsValue(value: String, modifier: Modifier, textAlign: TextAlign = TextAlign.Start) {
    Text(
        value,
        modifier,
        textAlign = textAlign,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum"
        )
    )
}

@Composable
private fun StatsChart(values: List<Long?>, color: Color, bars: Boolean = false) {
    val maximum = remember(values) { values.maxOfOrNull { it ?: 0L }?.coerceAtLeast(1L) ?: 1L }
    Canvas(Modifier.fillMaxWidth().height(19.dp)) {
        val inset = 2.dp.toPx()
        val bottom = size.height - inset
        val step = size.width / PLAYBACK_CHART_SAMPLE_COUNT
        val grid = Color.White.copy(alpha = .15f)
        drawLine(grid, Offset(0f, bottom), Offset(size.width, bottom))
        drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))

        val path = Path()
        var latest: Offset? = null
        values.forEachIndexed { index, value ->
            if (value == null) {
                latest = null
                return@forEachIndexed
            }
            val x = (PLAYBACK_CHART_SAMPLE_COUNT - values.size + index + .5f) * step
            val y = bottom - (value.toFloat() / maximum).coerceIn(0f, 1f) * (bottom - inset)
            if (bars) {
                if (value > 0) drawLine(color, Offset(x, bottom), Offset(x, y), (step * .66f).coerceAtLeast(1f))
            } else if (latest == null) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            latest = Offset(x, y)
        }
        if (!bars) {
            drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            latest?.let { drawCircle(color, 1.5.dp.toPx(), it) }
        }
    }
}
