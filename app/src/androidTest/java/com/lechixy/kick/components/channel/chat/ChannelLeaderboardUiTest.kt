package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.ChannelLeaderboardUiState
import com.lechixy.kick.data.channel.model.LeaderboardBoard
import com.lechixy.kick.data.channel.model.LeaderboardBoardType
import com.lechixy.kick.data.chat.model.GiftLeaderboardEntry
import com.lechixy.kick.theme.KickTheme
import com.lechixy.kick.theme.Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChannelLeaderboardUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun stripShowsTheBackdropAndCentersEntriesUntilTheyOverflow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val board = LeaderboardBoard(
            LeaderboardBoardType.WEEKLY_GIFTERS,
            listOf(GiftLeaderboardEntry(1, "LeaderboardMemberWithALongName", 42)),
        )
        val state = ChannelLeaderboardUiState(isLoading = false, boards = listOf(board))
        val width = mutableStateOf(340.dp)
        compose.setContent {
            KickTheme(Theme.LIGHT, dynamicColor = false) {
                Box(Modifier.width(width.value).background(Color.Red).testTag("leaderboardBackdrop")) {
                    ChannelLeaderboardStrip(state, paused = true, onClick = {})
                }
            }
        }

        val backdrop = compose.onNodeWithTag("leaderboardBackdrop")
        val badge = compose.onNodeWithContentDescription(context.getString(R.string.leaderboard_rank, 1), useUnmergedTree = true)
        val quantity = compose.onNodeWithText("42", useUnmergedTree = true)
        val stripBounds = backdrop.fetchSemanticsNode().boundsInRoot
        val entryCenter = (badge.fetchSemanticsNode().boundsInRoot.left + quantity.fetchSemanticsNode().boundsInRoot.right) / 2
        assertEquals(stripBounds.center.x, entryCenter, 1f)
        val pixels = backdrop.captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[1, pixels.height / 2])

        compose.runOnIdle { width.value = 110.dp }
        val narrowBounds = backdrop.fetchSemanticsNode().boundsInRoot
        val badgeLeft = badge.fetchSemanticsNode().boundsInRoot.left
        val padding = with(compose.density) { 10.dp.roundToPx() }
        assertEquals(narrowBounds.left + padding, badgeLeft, 1f)
    }

    @Test
    fun stripOpensTheDisplayedBoardAndDialogClosesWithBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val monthly = LeaderboardBoard(LeaderboardBoardType.MONTHLY_GIFTERS, listOf(GiftLeaderboardEntry(1, "MonthlyLeader", 210)))
        val weekly = LeaderboardBoard(LeaderboardBoardType.WEEKLY_GIFTERS, emptyList())
        val kicks = LeaderboardBoard(LeaderboardBoardType.WEEKLY_KICKERS, listOf(GiftLeaderboardEntry(2, "KicksLeader", 300)))
        val state = mutableStateOf(ChannelLeaderboardUiState(isLoading = false, boards = listOf(monthly, weekly, kicks)))
        val initialBoard = mutableStateOf<LeaderboardBoardType?>(null)
        compose.setContent {
            KickTheme(Theme.LIGHT, dynamicColor = false) {
                ChannelLeaderboardStrip(state.value, paused = true, onClick = { initialBoard.value = it })
                initialBoard.value?.let { board ->
                    ChannelLeaderboardDialog(
                        state.value, board, onUserClick = {}, onActionCtaClick = { _, _ -> },
                        onRetry = {}, onDismiss = { initialBoard.value = null },
                    )
                }
            }
        }
        val monthlyTitle = compose.onNodeWithText(context.getString(R.string.leaderboard_monthly_gifters))
        compose.onNodeWithText("MonthlyLeader").performClick()
        monthlyTitle.assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.player_close)).assertDoesNotExist()
        pressBack()
        monthlyTitle.assertDoesNotExist()
        compose.onNodeWithText("KicksLeader").assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(boards = listOf(
                monthly, weekly.copy(entries = listOf(GiftLeaderboardEntry(3, "WeeklyLeader", 101))), kicks,
            ))
        }
        compose.onNodeWithText("MonthlyLeader").assertDoesNotExist()
        compose.onNodeWithText("KicksLeader").assertDoesNotExist()
        compose.onNodeWithText("WeeklyLeader").performClick()
        compose.onNodeWithText(context.getString(R.string.leaderboard_weekly_gifters)).assertExists()
    }

    @Test
    fun startsAtMonthlyAndWrapsToEnabledEmptyWeeklyBoard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val quantities = listOf(800L, 201L, 200L, 100L, 100L, 50L, 50L, 50L, 46L, 40L)
        val entries = quantities.mapIndexed { index, quantity -> GiftLeaderboardEntry(index.toLong(), "user$index", quantity) }
        val boards = LeaderboardBoardType.entries.map { type ->
            val boardEntries = when (type) {
                LeaderboardBoardType.MONTHLY_GIFTERS -> entries
                LeaderboardBoardType.WEEKLY_GIFTERS -> emptyList()
                else -> entries.take(1)
            }
            LeaderboardBoard(type, boardEntries)
        }
        compose.setContent {
            KickTheme(Theme.LIGHT, dynamicColor = false) {
                ChannelLeaderboardPager(ChannelLeaderboardUiState(isLoading = false, boards = boards), {}, { _, _ -> }, {})
            }
        }

        val monthly = context.getString(R.string.leaderboard_monthly_gifters)
        compose.onNodeWithText(monthly).assertExists()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.leaderboard_subs, 41, "41"),
            substring = true).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.leaderboard_previous)).performClick()
        compose.onNodeWithText(context.getString(R.string.leaderboard_weekly_gifters)).assertExists()
        compose.onNodeWithText(context.getString(R.string.leaderboard_empty)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.leaderboard_next)).performClick()
        compose.onNodeWithText(monthly).assertExists()
    }
}
