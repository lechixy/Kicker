package com.lechixy.kick.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.components.channel.chat.KickLevelTier
import com.lechixy.kick.components.channel.chat.levelAnimationAsset
import com.lechixy.kick.components.channel.chat.profileDate
import com.lechixy.kick.components.streakAnimationAsset
import com.lechixy.kick.data.chat.model.parseActiveChatters

class ChatCommunityTest {
    @Test fun tierBoundariesClampToAvailableAssets() {
        val cases = listOf(-1 to 20, 0 to 20, 10 to 20, 19 to 20, 20 to 20, 29 to 20,
            30 to 30, 39 to 30, 40 to 40, 50 to 50, 60 to 60, 70 to 70, 80 to 80, 90 to 90, 120 to 90)
        cases.forEach { (level, tier) ->
            assertEquals(tier, KickLevelTier.fromLevel(level).minLevel)
            assertEquals("https://static.kick.com/tier/background/level-$tier.webp", KickLevelTier.fromLevel(level).backgroundDottedUrl)
        }
    }

    @Test fun datesAcceptMicrosecondsAndRejectMissingOrMalformedValues() {
        assertEquals("Mar 4, 2024", profileDate("2024-03-04T09:46:10.000000Z"))
        assertEquals("Feb 16, 2024", profileDate("2024-02-16T00:00:00Z"))
        assertNull(profileDate(null))
        assertNull(profileDate("not-a-date"))
    }

    @Test fun animationOnlyUsesAvailableTiersAndDatesAreLocalized() {
        listOf(null, 0, 20, 39).forEach { assertNull(levelAnimationAsset(it)) }
        for (tier in 40..90 step 10) {
            assertTrue(levelAnimationAsset(tier)!!.endsWith("level-$tier.webp"))
            assertTrue(levelAnimationAsset(tier + 9)!!.endsWith("level-$tier.webp"))
        }
        assertTrue(levelAnimationAsset(100)!!.endsWith("level-90.webp"))
        assertEquals("file:///android_asset/rewards/streaks-battery.webp", streakAnimationAsset())
        val raw = "2024-03-04T09:46:10Z"
        assertNotEquals(profileDate(raw, java.util.Locale.ENGLISH), profileDate(raw, java.util.Locale.forLanguageTag("tr")))
    }

    @Test fun activeChatterTotalIsIndependentOfThePartialList() {
        val payload = """{
            "bots":[],"chatters":[{"username":"MrUtkuu","slug":"mrutkuu","profile_picture":"avatar.webp"}],
            "moderators":[{"username":"TibetRaenn","slug":"tibetraenn"}],
            "ogs":[],"vips":[],"total_count":402
        }"""
        for (body in listOf(payload, """{"data":$payload,"message":"success"}""")) {
            val response = parseActiveChatters(Json.parseToJsonElement(body).jsonObject)
            assertEquals(402, response.totalCount)
            assertEquals("avatar.webp", response.chatters.single().profilePicture)
            assertEquals("tibetraenn", response.moderators.single().slug)
            assertTrue(response.bots.isEmpty())
        }
    }

    @Test fun missingOrInvalidDataDoesNotSilentlyBecomeAnEmptyList() {
        listOf("{}", """{"data":null}""", """{"data":{},"message":"success"}""",
            """{"data":{"total_count":-1}}""").forEach { body ->
            assertTrue(runCatching { parseActiveChatters(Json.parseToJsonElement(body).jsonObject) }.isFailure)
        }
        val empty = parseActiveChatters(Json.parseToJsonElement("""{"data":{"total_count":0,"chatters":[]}}""").jsonObject)
        assertEquals(0, empty.totalCount)
        assertTrue(empty.chatters.isEmpty())
    }
}
