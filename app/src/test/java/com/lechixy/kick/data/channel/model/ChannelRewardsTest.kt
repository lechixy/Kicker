package com.lechixy.kick.data.channel.model

import com.lechixy.kick.data.account.support.objectJson
import com.lechixy.kick.data.account.support.signedInAuth

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.formatExactBalance
import com.lechixy.kick.data.channel.model.parseChannelRewards
import com.lechixy.kick.data.chat.repository.AccountChatRepository

class ChannelRewardsTest {
    private fun parse(value: String) = parseChannelRewards(Json.parseToJsonElement(value))

    @Test fun acceptsEmptyEnvelopeAndBareRewardList() {
        assertTrue(parse("""{"data":[],"message":"OK"}""").isEmpty())
        val rewards = parse("""[
            {"id":"one","title":"Su İç","cost":500,"is_enabled":true,"is_paused":true,"background_color":"#FFD899"},
            {"id":"two","title":"Vurgulu Mesaj","cost":750,"is_enabled":true,"is_paused":false,"is_user_input_required":true,"background_color":"#FBCFD8"}
        ]""")
        assertEquals(2, rewards.size)
        assertTrue(rewards[0].isPaused)
        assertEquals(500L, rewards[0].cost)
        assertEquals("#FFD899", rewards[0].backgroundColor)
        assertTrue(rewards[1].isUserInputRequired)
        assertFalse(rewards[1].isPaused)
    }

    @Test fun acceptsPopulatedEnvelopeButDoesNotHideInvalidResponsesAsEmpty() {
        assertEquals("one", parse("""{"data":[{"id":"one","title":"Reward","cost":5}]}""").single().id)
        listOf("{}", "null", """{"data":null}""", """[{"id":"one","title":"Reward","cost":-1}]""")
            .forEach { assertTrue(runCatching { parse(it) }.isFailure) }
    }

    @Test fun balancesAreExactWithThousandsSeparators() {
        assertEquals("57,500", formatExactBalance(57_500))
        assertEquals("0", formatExactBalance(0))
        assertEquals("1,234,567", formatExactBalance(1_234_567))
    }

    @Test fun requestsOnlyEnabledRewardsForTheSelectedChannel() = runBlocking {
        val auth = signedInAuth { request ->
            assertEquals("kick.com", request.url.host)
            assertEquals("/api/v2/channels/tester/rewards", request.url.encodedPath)
            assertEquals("true", request.url.queryParameter("is_enabled"))
            objectJson("""{"data":[],"message":"OK"}""")
        }
        assertTrue(AccountChatRepository(auth).rewards(
            com.lechixy.kick.data.channel.model.ChannelDetail(id = 100, slug = "tester")).isEmpty())
    }
}
