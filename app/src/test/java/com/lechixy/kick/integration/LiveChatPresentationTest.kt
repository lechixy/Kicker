package com.lechixy.kick.integration

import com.lechixy.kick.components.channel.chat.kicksGiftArtworkTier
import com.lechixy.kick.data.channel.model.DropsCampaignsResponseDto
import com.lechixy.kick.data.channel.model.DropsProgressSummaryResponseDto
import com.lechixy.kick.data.channel.model.KickGoalDto
import com.lechixy.kick.data.channel.model.resolveDropsImageUrl
import com.lechixy.kick.data.chat.model.ChatPointsUpdate
import com.lechixy.kick.data.chat.model.ChatWebSocketEvent
import com.lechixy.kick.data.chat.model.GiftsLeaderboardUpdatedEvent
import com.lechixy.kick.data.chat.model.KicksGiftedEvent
import com.lechixy.kick.data.chat.model.KicksLeaderboardUpdatedEvent
import com.lechixy.kick.data.chat.model.PointsUpdatedEvent
import com.lechixy.kick.data.chat.model.StreamHostedEvent
import com.lechixy.kick.data.chat.model.StreamHostedMessage
import com.lechixy.kick.data.chat.model.StreamHostedUser
import com.lechixy.kick.data.chat.model.TimedChatEvent
import com.lechixy.kick.data.chat.model.tickerDurationMillis
import com.lechixy.kick.screens.channel.DropRewardUiModel
import com.lechixy.kick.screens.channel.progressFrom
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChatPresentationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun hostUsesRemainingTimeFromArrivalAndHonorsDurationLimits() {
        val host = ChatWebSocketEvent.StreamHosted(
            StreamHostedEvent(StreamHostedMessage("host", 35), StreamHostedUser(17L, "Streamer"))
        )
        val duration = checkNotNull(host.tickerDurationMillis())
        val arrival = 1_000L
        val ticker = TimedChatEvent(1L, host, duration, arrival + duration)

        assertEquals(35_000L, duration)
        assertEquals(10_000L, ticker.remainingMillis(arrival + 25_000L))
        assertEquals(0L, ticker.remainingMillis(arrival + 35_000L))
        assertEquals(0L, ticker.remainingMillis(arrival + 60_000L))
        assertEquals(20_000L, host.copy(event = host.event.copy(
            message = host.event.message.copy(numberOfViewers = 1))).tickerDurationMillis())
        assertEquals(60_000L, host.copy(event = host.event.copy(
            message = host.event.message.copy(numberOfViewers = 10_000))).tickerDurationMillis())
    }

    @Test fun awardsAccumulateIndependentlyOfTheLatestBalanceAndAccount() {
        val award = PointsUpdatedEvent("EARNED", 10, 63_565, 29081495L, 26239555L)
        val first = ChatPointsUpdate.receive(null, award, Instant.EPOCH, 1L)
        val second = ChatPointsUpdate.receive(first, award.copy(balance = 63_575), Instant.EPOCH.plusSeconds(1), 2L)
        val spend = ChatPointsUpdate.receive(second, award.copy(reason = "SPENT", points = -25, balance = 63_550),
            Instant.EPOCH.plusSeconds(2), 3L)

        assertEquals(20L, spend.pendingPoints)
        assertEquals(63_550, spend.event.balance)
        assertEquals(10L, ChatPointsUpdate.receive(spend.copy(pendingPoints = 0L), award, Instant.EPOCH, 4L).pendingPoints)
        assertEquals(10L, ChatPointsUpdate.receive(spend, award.copy(userId = 123L), Instant.EPOCH, 4L).pendingPoints)
        assertEquals(10L, ChatPointsUpdate.receive(spend, award.copy(channelId = 123L), Instant.EPOCH, 4L).pendingPoints)
    }

    @Test fun decodesKicksGiftsGoalsAndBothLeaderboardShapes() {
        val gift = json.decodeFromString<KicksGiftedEvent>(
            """{"gift_transaction_id":"txn","message":"","sender":{"id":69715954,"username":"MrUKKickOfficial",
                "username_color":"#72ACED"},"gift":{"gift_id":"hell_yeah","name":"Hell Yeah","amount":1,
                "type":"BASIC","tier":"BASIC","character_limit":0,"pinned_time":0},"created_at":"2026-11-21T16:55:04.922435212Z"}"""
        )
        assertEquals("Hell Yeah", gift.gift.name)
        assertEquals("#72ACED", gift.sender.usernameColor)
        assertNull(ChatWebSocketEvent.KicksGifted(gift).tickerDurationMillis())

        val goal = json.decodeFromString<KickGoalDto>(
            """{"id":"chgoal_123","channel_id":"channel_123","type":"subscriptions","status":"active",
                "current_value":3176,"target_value":3301,"count_from_creation":true}"""
        )
        assertEquals(3176, goal.currentValue)
        assertEquals(true, goal.countFromCreation)

        val entry = """[{"user_id":28162040,"username":"BossMr","quantity":1300}]"""
        val subs = json.decodeFromString<GiftsLeaderboardUpdatedEvent>(
            """{"leaderboard":$entry,"weekly_leaderboard":$entry,"monthly_leaderboard":$entry,
                "gifter_id":90614959,"gifter_username":"SucukluYumurta_06","gifted_quantity":1}"""
        )
        val kicks = json.decodeFromString<KicksLeaderboardUpdatedEvent>(
            """{"gifts_lifetime":$entry,"gifts_lifetime_enabled":true,"gifts_week":$entry,
                "gifts_week_enabled":true,"gifts_month":$entry,"gifts_month_enabled":true}"""
        )
        assertEquals(90614959L, subs.gifterId)
        assertEquals(1300L, subs.weeklyLeaderboard.single().quantity)
        assertEquals(subs.leaderboard, kicks.giftsLifetime)
        assertEquals(subs.monthlyLeaderboard, kicks.giftsMonth)
        assertTrue(kicks.giftsWeekEnabled && kicks.giftsLifetimeEnabled && kicks.giftsMonthEnabled)
    }

    @Test fun kicksGiftArtworkUsesTheLargestTierWithinTheGiftAmount() {
        assertNull(kicksGiftArtworkTier(0))
        assertEquals(1, kicksGiftArtworkTier(49))
        assertEquals(50, kicksGiftArtworkTier(50))
        assertEquals(100, kicksGiftArtworkTier(499))
        assertEquals(500, kicksGiftArtworkTier(500))
        assertEquals(1_000, kicksGiftArtworkTier(1_999))
        assertEquals(5_000, kicksGiftArtworkTier(6_000))
        assertEquals(10_000, kicksGiftArtworkTier(49_999))
        assertEquals(50_000, kicksGiftArtworkTier(Int.MAX_VALUE))
    }

    @Test fun dropsResponsePreservesRequiredMinutesAndNormalizesArtwork() {
        val response = json.decodeFromString<DropsCampaignsResponseDto>(
            """{"data":[{"id":"campaign","name":"Sunset Drop","category_id":13155,
                "organization_logo_url":"https://ext.kick.com/drops/organization/kickdrops.png",
                "starts_at":"2026-11-18T11:00:00Z","ends_at":"2027-01-01T00:00:00Z","rule_id":1,
                "rewards":[{"id":"reward","name":"SunsetK Badge","image_url":"drops/reward-image/sunset.png",
                "required_units":15000}]}],"message":"Success"}"""
        )
        val reward = response.data.single().rewards.single()
        assertEquals(15_000, reward.requiredUnits)
        assertEquals("https://ext.kick.com/drops/reward-image/sunset.png", resolveDropsImageUrl(reward.imageUrl))
        assertEquals(response.data.single().organizationLogoUrl, resolveDropsImageUrl(response.data.single().organizationLogoUrl))
    }

    @Test fun dropsSummaryUsesServerProgressAndRemainingWatchMinutes() {
        val summary = json.decodeFromString<DropsProgressSummaryResponseDto>(
            """{"data":[{"id":"01M7NSNCG7V3T2W54EHBWD1J3W",
                "organization_logo_url":"https://ext.kick.com/drops/organization/kickdrops.png",
                "progress_units":1082,"rewards":[
                {"id":"01M7NS7WFPJJ7FSSE92S00DCGF","progress":0.07213333,"claimed":false},
                {"id":"claimed","progress":0.5,"claimed":true}]}],"message":"Success"}"""
        ).data.single()
        val reward = DropRewardUiModel("01M7NS7WFPJJ7FSSE92S00DCGF", "SunsetK Badge", "",
            progressFraction = 0f, isUnlocked = false, requiredUnits = 15_000)

        val progress = reward.progressFrom(summary)
        assertEquals(0.07213333f, progress.fraction, 0.000001f)
        assertEquals(13_918L, progress.remainingMinutes)
        assertEquals(0L, reward.copy(id = "claimed").progressFrom(summary).remainingMinutes)
        assertEquals(15_000L, reward.progressFrom(null).remainingMinutes)
    }
}
