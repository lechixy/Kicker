package com.lechixy.kick.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.components.PlayerMode
import com.lechixy.kick.data.channel.model.ChannelClip
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelLivestream
import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.channel.model.playbackId
import com.lechixy.kick.data.player.model.PlaybackRequest
import com.lechixy.kick.data.player.model.PlaybackResponse
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.player.model.toRecording

class RecordedPlaybackTest {
    private val offline = ChannelDetail(id = 1, slug = "channel")
    private val live = offline.copy(livestream = ChannelLivestream(id = 42), playbackUrl = "https://example.com/live.m3u8")
    private val recording = RecordedPlayback("01a173e5-f220-78ca-9ddd-d29e8cc11441", "Recording", 60_000)

    @Test fun recordingSurvivesLiveStartStopAndAnnouncements() {
        val host = PlayerHostState()
        host.open("channel")
        host.onChannelLoaded("channel", live)
        host.playRecording(recording)
        host.setFullscreen(true)
        host.onChannelLoaded("channel", offline)
        assertEquals(PlayerMode.Fullscreen, host.mode)
        assertEquals(recording, host.recording)
        host.onChannelLoaded("channel", live)
        host.triggerLiveAnnouncement(123)
        assertNull(host.activeLiveAnnouncementId)
        assertEquals(recording, host.recording)
        host.playLive()
        assertNull(host.recording)
        assertEquals(live, host.channel)
        host.onChannelLoaded("channel", offline)
        assertEquals(PlayerMode.Expanded, host.mode)
    }

    @Test fun offlineRecordingCanMinimizeAndSwitchChannels() {
        val host = PlayerHostState()
        host.open("channel")
        host.onChannelLoaded("channel", offline)
        host.playRecording(recording)
        host.minimize()
        assertEquals(PlayerMode.Mini, host.mode)
        host.onChannelLoaded("channel", offline)
        assertEquals(PlayerMode.Mini, host.mode)
        host.open("other")
        assertNull(host.recording)
        assertNull(host.channel)
    }

    @Test fun staleMetadataCannotReplaceSelectedRecording() {
        val host = PlayerHostState()
        host.open("channel")
        host.playRecording(recording)
        val next = recording.copy(id = "next", title = "Next")
        host.playRecording(next)
        host.updateRecordingMetadata(recording.id, "Stale", 10)
        assertEquals(next, host.recording)
        host.close()
        assertNull(host.recording)
    }

    @Test fun offlineLiveSessionStillClosesInsteadOfMinimizing() {
        val host = PlayerHostState()
        host.open("channel")
        host.onChannelLoaded("channel", offline)
        host.minimize()
        assertTrue(host.isExiting)
        host.finishClose()
        assertEquals(PlayerMode.Hidden, host.mode)
    }

    @Test fun restoredSessionKeepsRecordingWhenChannelReloads() {
        val host = PlayerHostState()
        host.open("channel")
        host.playRecording(recording)
        val saved = with(PlayerHostState.Saver) {
            androidx.compose.runtime.saveable.SaverScope { true }.save(host)
        }
        val restored = PlayerHostState.Saver.restore(requireNotNull(saved))!!
        restored.onChannelLoaded("channel", live)
        assertEquals(recording, restored.recording)
        restored.onChannelLoaded("channel", offline)
        restored.minimize()
        assertEquals(PlayerMode.Mini, restored.mode)
    }

    @Test fun playbackBodyMatchesApiContract() {
        val json = Json { encodeDefaults = true }
        val body = json.parseToJsonElement(json.encodeToString(PlaybackRequest())).jsonObject
        assertEquals(setOf("video_player", "video_session", "user_session"), body.keys)
        assertTrue(body.getValue("video_player").jsonObject.getValue("player").jsonObject.isEmpty())
        assertTrue(body.getValue("video_session").jsonObject.isEmpty())
        assertEquals("true", body.getValue("user_session").jsonObject.getValue("non_personalised_ads").jsonPrimitive.content)
    }

    @Test fun nestedVideoUuidIsKeptForVodIdResolution() {
        val json = Json { ignoreUnknownKeys = true }
        val video = json.decodeFromString<ChannelVideo>("""{"id":42,"video":{"uuid":"cb18022c-aa57-49ff-9d2a-843f02e34d52"}}""")
        assertEquals("cb18022c-aa57-49ff-9d2a-843f02e34d52", video.toRecording()?.videoUuid)
        val withVodId = video.copy(vodId = recording.id)
        assertEquals(recording.id, withVodId.playbackId)
        assertNull(withVodId.toRecording()?.videoUuid)
        val response = json.decodeFromString<PlaybackResponse>("""{"playback_url":{"live":"live-url","vod":"vod-url"},"video_session":{"video_duration":19925}}""")
        assertEquals("vod-url", response.playbackUrl.vod)
        assertEquals(19_925L, response.videoSession.durationSeconds)
    }

    @Test fun clipDurationsAreConvertedFromSecondsAndUrlAliasesAreAccepted() {
        val clip = Json.decodeFromString<ChannelClip>("""{"id":"clip_1","duration":12.5,"playback_url":"https://example.com/clip.mp4","views_count":35}""")
        assertEquals(12_500L, clip.toRecording().durationMs)
        assertEquals(35L, clip.toRecording().views)
        assertEquals("https://example.com/clip.mp4", clip.toRecording().directUrl)
    }
}
