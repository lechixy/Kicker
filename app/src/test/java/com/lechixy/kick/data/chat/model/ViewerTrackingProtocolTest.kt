package com.lechixy.kick.data.chat.model

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.data.channel.model.ChannelLivestream
import com.lechixy.kick.data.player.remote.ViewerSession
import com.lechixy.kick.data.player.remote.ViewerTrackingProtocol

class ViewerTrackingProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun watchFrameMatchesCapturedProtocol() {
        val target = ViewerSession(26239555, 134732935, "01a18359-2178-766e-8ecf-e3a91e419a7e")
        val expected = json.parseToJsonElement("""{"type":"user_event","data":{"message":{"name":"tracking.user.watch.livestream","channel_id":26239555,"livestream_id":134732935,"vod_id":"01a18359-2178-766e-8ecf-e3a91e419a7e"}}}""")
        assertEquals(expected, json.parseToJsonElement(ViewerTrackingProtocol.watch(target)))
        assertEquals(json.parseToJsonElement("""{"type":"channel_handshake","data":{"message":{"channelId":"26239555"}}}"""),
            json.parseToJsonElement(ViewerTrackingProtocol.handshake(target)))
    }

    @Test fun missingVodIsNeverReplacedWithAnotherIdentifier() {
        assertTrue(runCatching { ViewerTrackingProtocol.watch(ViewerSession(1, 2, null)) }.isFailure)
        val live = json.decodeFromString<ChannelLivestream>("""{"id":2,"vod_id":"real-vod-id"}""")
        assertEquals("real-vod-id", live.vodId)
    }

    @Test fun cadenceMatchesCapture() {
        assertEquals(15_000L, ViewerTrackingProtocol.HANDSHAKE_MS)
        assertEquals(30_000L, ViewerTrackingProtocol.PING_MS)
        assertEquals(120_000L, ViewerTrackingProtocol.WATCH_MS)
        assertTrue(ViewerTrackingProtocol.PONG_TIMEOUT_MS < ViewerTrackingProtocol.PING_MS)
    }

    @Test fun nestedRaidPayloadPreservesSenderAndViewerCount() {
        val frame = json.decodeFromString<CentrifugoPushFrame>("""{"push":{"channel":"chatrooms.25951243","pub":{"data":{"event":"App\\Events\\StreamHostedEvent","data":"{\"message\":{\"id\":\"raid-id\",\"numberOfViewers\":1083,\"optionalMessage\":null},\"user\":{\"id\":28526354,\"username\":\"BlackRose\",\"isSuperAdmin\":false}}"},"tags":{"event":"App\\Events\\StreamHostedEvent"}}}}""")
        assertEquals("chatrooms.25951243", frame.push?.channel)
        val raw = frame.push!!.pub!!.data!!.data!!.jsonPrimitive.content
        val raid = json.decodeFromString<StreamHostedEvent>(raw)
        assertEquals("BlackRose", raid.user.username)
        assertEquals(1083, raid.message.numberOfViewers)
    }
}
