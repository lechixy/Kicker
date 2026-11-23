package com.lechixy.kick.service

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.google.common.io.ByteStreams
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.player.model.DownloadPlaylist
import com.lechixy.kick.data.player.model.LiveDownloadSnapshot
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.player.model.RecordingDownloadSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LiveRecordingDownload(
    val playbackUrl: String,
    val dvrUrl: String?,
    val broadcastStartMs: Long,
)

internal data class PreparedRecordingDownload(
    val url: String,
    val durationMs: Long,
    val bitrate: Int? = null,
    val sourceBytes: Long? = null,
    val selection: RecordingDownloadSelection = RecordingDownloadSelection(),
    val snapshot: LiveDownloadSnapshot? = null,
    val hasBroadcastTimeline: Boolean = true,
)

@OptIn(UnstableApi::class)
internal suspend fun prepareRecordingDownload(
    recording: RecordedPlayback,
    live: LiveRecordingDownload?,
): PreparedRecordingDownload = withContext(Dispatchers.IO) {
    val repository = KickRepository(KickNetwork.api)
    if (live == null) {
        val resolved = repository.resolveRecording(recording)
        val url = checkNotNull(resolved.directUrl)
        val uri = Uri.parse(url)
        require(uri.scheme == "https")
        if (uri.path.orEmpty().endsWith(".m3u8", ignoreCase = true)) {
            prepareHlsDownload(url, live = null, fromDvr = false)
        } else {
            PreparedRecordingDownload(url, resolved.durationMs, sourceBytes = readContentLength(uri))
        }
    } else {
        val dvr = live.dvrUrl ?: recording.channelSlug?.let { slug ->
            try {
                repository.getLiveDvrUrl(slug, recording.vodId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        if (dvr != null) {
            try {
                return@withContext prepareHlsDownload(dvr, live, fromDvr = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The live edge may still have segments when the DVR endpoint is unavailable.
            }
        }
        prepareHlsDownload(live.playbackUrl, live, fromDvr = false)
    }
}

@OptIn(UnstableApi::class)
private fun prepareHlsDownload(url: String, live: LiveRecordingDownload?, fromDvr: Boolean): PreparedRecordingDownload {
    val uri = Uri.parse(url)
    val root = readPlaylist(uri)
    val parsed = HlsPlaylistParser().parse(Uri.parse(root.url), root.text.byteInputStream())
    val playlists = mutableMapOf<String, DownloadPlaylist>()
    var selection = RecordingDownloadSelection()
    var bitrate: Int? = null
    val media = if (parsed is HlsMultivariantPlaylist) {
        val variant = parsed.variants.withIndex()
            .filter { (_, item) -> item.format.roleFlags and C.ROLE_FLAG_TRICK_PLAY == 0 }
            .maxBy { (_, item) -> item.format.bitrate }
        val audio = parsed.audios.withIndex()
            .filter { (_, item) -> item.groupId == variant.value.audioGroupId }
            .maxByOrNull { (_, item) -> item.format.selectionFlags and C.SELECTION_FLAG_DEFAULT }
        val mediaPlaylist = readPlaylist(variant.value.url)
        val parser = HlsPlaylistParser(parsed, null)
        val video = parser.parse(Uri.parse(mediaPlaylist.url), mediaPlaylist.text.byteInputStream()) as HlsMediaPlaylist
        bitrate = variant.value.format.bitrate.takeIf { it > 0 }
        selection = selection.copy(variantIndex = variant.index, audioIndex = audio?.index)
        if (live != null) {
            playlists[url] = root
            playlists[variant.value.url.toString()] = mediaPlaylist.freeze()
            audio?.value?.url?.let { audioUri ->
                if (audioUri != variant.value.url) playlists[audioUri.toString()] = readPlaylist(audioUri).freeze()
            }
        }
        video
    } else {
        if (live != null) playlists[url] = root.freeze()
        parsed as HlsMediaPlaylist
    }
    check(media.segments.isNotEmpty())
    val durationMs = if (live == null) media.durationUs / 1000
        else (media.segments.last().relativeStartTimeUs + media.segments.last().durationUs) / 1000
    val hasBroadcastOffset = live != null && live.broadcastStartMs > 0 && media.hasProgramDateTime
    val offset = if (live != null && hasBroadcastOffset) {
        (media.startTimeUs / 1000 - live.broadcastStartMs).coerceAtLeast(0)
    } else 0L
    val rangeBytes = media.segments.takeIf { segments -> segments.all { it.byteRangeLength > 0 } }
        ?.sumOf { it.byteRangeLength }?.takeIf { selection.audioIndex == null }
    if (bitrate == null && rangeBytes == null) {
        val sample = media.segments.firstOrNull { !it.hasGapTag && it.durationUs > 0 }
        val sampleBytes = sample?.let { readContentLength(UriUtil.resolveToUri(media.baseUri, it.url)) }
        if (sample != null && sampleBytes != null) {
            bitrate = (sampleBytes.toDouble() * 8_000_000 / sample.durationUs).toInt().takeIf { it > 0 }
        }
    }
    return PreparedRecordingDownload(
        url = url,
        durationMs = durationMs,
        bitrate = bitrate,
        sourceBytes = rangeBytes,
        selection = selection.copy(timelineOffsetMs = offset),
        snapshot = if (live == null) null else LiveDownloadSnapshot(url, playlists),
        hasBroadcastTimeline = live == null || fromDvr || hasBroadcastOffset,
    )
}

@OptIn(UnstableApi::class)
private fun readPlaylist(uri: Uri): DownloadPlaylist {
    require(uri.scheme == "https")
    val source = playbackHttpDataSourceFactory().createDataSource()
    val spec = DataSpec.Builder().setUri(uri).build()
    return DataSourceInputStream(source, spec).use { input ->
        val limit = 8 * 1024 * 1024L
        val bytes = ByteStreams.limit(input, limit + 1).readBytes()
        check(bytes.size <= limit)
        val finalUri = checkNotNull(source.uri)
        require(finalUri.scheme == "https")
        DownloadPlaylist(finalUri.toString(), bytes.decodeToString())
    }
}

@OptIn(UnstableApi::class)
private fun readContentLength(uri: Uri): Long? = runCatching {
    require(uri.scheme == "https")
    val source = playbackHttpDataSourceFactory().createDataSource()
    try {
        source.open(DataSpec.Builder().setUri(uri).setHttpMethod(DataSpec.HTTP_METHOD_HEAD).build())
        source.responseHeaders.entries.firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }
            ?.value?.firstOrNull()?.toLongOrNull()?.takeIf { it > 0 }
    } finally {
        source.close()
    }
}.getOrNull()

private fun DownloadPlaylist.freeze() = copy(text = freezeLiveDownloadPlaylist(text))

internal fun freezeLiveDownloadPlaylist(text: String): String = buildString {
    text.lineSequence().map(String::trimEnd).filter { line ->
        line.isNotEmpty() && line != "#EXT-X-ENDLIST" &&
            !line.startsWith("#EXT-X-PART:") && !line.startsWith("#EXT-X-PART-INF:") &&
            !line.startsWith("#EXT-X-SERVER-CONTROL:") && !line.startsWith("#EXT-X-PRELOAD-HINT:") &&
            !line.startsWith("#EXT-X-RENDITION-REPORT:") && !line.startsWith("#EXT-X-PREFETCH:") &&
            !line.startsWith("#EXT-X-START:")
    }.forEach { appendLine(it) }
    appendLine("#EXT-X-ENDLIST")
}

@OptIn(UnstableApi::class)
internal fun recordingDownloadMediaItem(
    url: String,
    selection: RecordingDownloadSelection?,
    snapshot: LiveDownloadSnapshot?,
    clip: Boolean,
): MediaItem = MediaItem.Builder().setUri(url).apply {
    if (snapshot != null || selection?.variantIndex != null) setMimeType(MimeTypes.APPLICATION_M3U8)
    setStreamKeys(buildList {
        selection?.variantIndex?.let { add(StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, it)) }
        selection?.audioIndex?.let { add(StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_AUDIO, it)) }
    })
    if (clip) selection?.range?.let { range ->
        setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(range.startMs).setEndPositionMs(range.endMs).build())
    }
}.build()

@OptIn(UnstableApi::class)
internal fun recordingDownloadDataSource(snapshot: LiveDownloadSnapshot?): DataSource.Factory {
    val http = playbackHttpDataSourceFactory()
    if (snapshot == null) return http
    val playlists = snapshot.playlists.mapValues { (_, playlist) ->
        Uri.parse(playlist.url) to playlist.text.encodeToByteArray()
    }
    return DataSource.Factory {
        val upstream = http.createDataSource()
        val listeners = mutableListOf<TransferListener>()
        object : DataSource by upstream {
            private var current: DataSource = upstream

            override fun addTransferListener(transferListener: TransferListener) {
                listeners += transferListener
                upstream.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                val playlist = playlists[dataSpec.uri.toString()]
                if (playlist == null) {
                    current = upstream
                    return current.open(dataSpec)
                }
                val (uri, bytes) = playlist
                current = ByteArrayDataSource(bytes).apply { listeners.forEach(::addTransferListener) }
                return current.open(dataSpec.withUri(uri))
            }

            override fun getUri() = current.uri
            override fun getResponseHeaders() = current.responseHeaders
            override fun read(buffer: ByteArray, offset: Int, length: Int) = current.read(buffer, offset, length)
            override fun close() = current.close()
        }
    }
}
