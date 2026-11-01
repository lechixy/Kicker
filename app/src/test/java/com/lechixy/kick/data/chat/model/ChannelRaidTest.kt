package com.lechixy.kick.data.chat.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChannelRaidTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun destinationComesFromHostedInsteadOfSourceChannel() {
        val raid = json.decodeFromString<ChannelRaid>("""{"channel":{"id":27528474,"slug":"utkdev","current_livestream":{"id":134196540}},"slug":"blackrose","hosted":{"id":27496420,"username":"BlackRose","slug":"blackrose","viewers_count":1893,"category":"IRL","preview_thumbnail":{"src":"https://example.com/preview.webp"}}}""")
        assertEquals(27528474L, raid.channel.id)
        assertEquals("blackrose", raid.targetSlug)
        val stream = raid.toLivestream()
        assertEquals(27496420L, stream.channel?.id)
        assertEquals(1893, stream.viewerCount)
        assertEquals("https://example.com/preview.webp", stream.thumbnail?.src)
    }

    @Test fun batchesCanMixAdjacentAcknowledgementsNewlinesAndPublications() {
        val ack = """{"id":6,"subscribe":{}}"""
        val unsubscribe = """{"id":7,"unsubscribe":{}}"""
        val event = """{"push":{"pub":{"data":{"content":"braces } { and \"quoted\" text"}}}}"""
        val frames = splitCentrifugoFrames(ack + unsubscribe + "\n{}\n" + event)
        assertEquals(listOf(ack, unsubscribe, "{}", event), frames)
        frames.forEach { json.parseToJsonElement(it) }
    }
}
