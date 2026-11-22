package com.lechixy.kick.data.channel.model

import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.resolveGifterBadgeRes
import com.lechixy.kick.data.chat.model.GiftLeaderboardEntry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelLeaderboardTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun endpointsMapToCyclicOrderAndKeepEnabledEmptyBoards() {
        val subscriptions = json.decodeFromString<LeaderboardResponse<SubscriptionLeaderboardData>>(
            """{"data":{"gifts_month":[{"quantity":40,"user_id":30118600,"username":"deluqe"}],
                "gifts_month_enabled":true,"gifts_enabled":true,"gifts_week_enabled":true},"message":"Success"}"""
        ).data
        val kicks = json.decodeFromString<LeaderboardResponse<KicksLeaderboardData>>(
            """{"data":{"kicks_gifts_week":[{"quantity":5000,"user_id":100462681,"username":"ilkedeniz97"}],
                "kicks_gifts_week_enabled":true,"kicks_gifts_month_enabled":true,
                "kicks_gifts_lifetime_enabled":true},"message":"Success"}"""
        ).data

        val boards = channelLeaderboardBoards(subscriptions, kicks)
        assertEquals(LeaderboardBoardType.entries, boards.map { it.type })
        assertEquals(40L, boards.first().entries.single().quantity)
        assertEquals(100462681L, boards[2].entries.single().userId)
        assertTrue(boards.last().entries.isEmpty())
        assertEquals(1L, boards.last().targetQuantity)
        assertEquals(1, boards.last().targetRank)
        assertEquals(
            listOf(LeaderboardBoardType.WEEKLY_KICKERS, LeaderboardBoardType.LIFETIME_KICKERS),
            channelLeaderboardBoards(null, kicks.copy(monthEnabled = false)).map { it.type },
        )
    }

    @Test
    fun tenthPlaceThresholdAndBadgeUseActualQuantity() {
        val values = listOf(800L, 201L, 200L, 100L, 100L, 50L, 50L, 50L, 46L, 40L)
        val entries = values.mapIndexed { index, quantity -> GiftLeaderboardEntry(index.toLong(), "user$index", quantity) }
        val board = LeaderboardBoard(LeaderboardBoardType.MONTHLY_GIFTERS, entries)

        assertEquals(10, board.targetRank)
        assertEquals(41L, board.targetQuantity)
        assertEquals(R.drawable.gifter_25_badge, resolveGifterBadgeRes(board.targetQuantity))
        assertEquals(R.drawable.gifter_150_badge, resolveGifterBadgeRes(199))
        assertEquals(R.drawable.gifter_200_badge, resolveGifterBadgeRes(200))
        assertEquals(R.drawable.gifter_800_badge, resolveGifterBadgeRes(999))
        assertEquals(R.drawable.gifter_1000_badge, resolveGifterBadgeRes(1000))
    }
}
