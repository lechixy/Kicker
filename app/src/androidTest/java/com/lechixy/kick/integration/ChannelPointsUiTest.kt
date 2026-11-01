package com.lechixy.kick.integration

import com.lechixy.kick.data.account.remote.string

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import com.lechixy.kick.KickRailLayout
import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.BalanceTooltip
import com.lechixy.kick.components.channel.chat.ChannelPointsPanel
import com.lechixy.kick.data.channel.model.ChannelReward

@RunWith(AndroidJUnit4::class)
class ChannelPointsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun railTracksPartialDragInBothDirectionsWithoutDisposingContent() {
        var progress by mutableFloatStateOf(0f)
        val disposals = AtomicInteger()
        compose.setContent {
            Box(Modifier.size(300.dp, 250.dp).testTag("screen")) {
                KickRailLayout(navigationProgress = { progress },
                    navigation = { Box(Modifier.width(80.dp).fillMaxHeight()) }) {
                    DisposableEffect(Unit) { onDispose { disposals.incrementAndGet() } }
                    Box(Modifier.fillMaxSize().testTag("page"))
                }
            }
        }
        for (fraction in listOf(0.25f, 0.6f, 1f, 0.7f, 0.2f, 0f)) {
            compose.runOnIdle { progress = fraction }
            val screen = compose.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
            val page = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
            // The page moves before release, including on the reverse drag.
            assertEquals(screen.width * (80f / 300f) * fraction, page.left - screen.left, 1.5f)
            assertEquals(screen.right, page.right, 1f)
            assertEquals(0, disposals.get())
        }
    }

    @Test fun emptyRewardsExplainThatPointsCanStillBeEarnedAndShowBalances() {
        compose.setContent { MaterialTheme {
            ChannelPointsPanel("RRaenee", emptyList(), false, 57_500, 0, {}, {})
        } }
        compose.onNodeWithText(context.getString(R.string.channel_rewards_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.channel_rewards_paused, "RRaenee")).assertIsDisplayed()
        compose.onNodeWithText("57,500").assertIsDisplayed()
        compose.onNodeWithText("0").assertIsDisplayed()
    }

    @Test fun rewardsRenderTheirCostsAndPausedStatusEvenWithInvalidColor() {
        compose.setContent { MaterialTheme {
            ChannelPointsPanel("Tester", listOf(
                ChannelReward("one", "Su İç", 500, isPaused = true, backgroundColor = "invalid"),
                ChannelReward("two", "Vurgulu Mesaj", 750, backgroundColor = "#FBCFD8")
            ), false, 310, 0, {}, {})
        } }
        compose.onNodeWithText("Su İç").assertIsDisplayed()
        compose.onNodeWithText("500").assertIsDisplayed()
        compose.onNodeWithText("750").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.channel_reward_paused)).assertIsDisplayed()
    }

    @Test fun pointsTooltipShowsFullBalanceAboveCompactText() {
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                BalanceTooltip(57_500) { Text("57.5k", Modifier.testTag("balance")) }
            }
        } }
        compose.onNodeWithTag("balance", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText(context.getString(R.string.channel_points_exact, "57,500")).assertIsDisplayed()
    }

    @Test fun kicksTooltipIncludesZeroAndCurrencyName() {
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                BalanceTooltip(0, isKicks = true) { Text("0", Modifier.testTag("balance")) }
            }
        } }
        compose.onNodeWithTag("balance", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText(context.getString(R.string.channel_kicks_exact, "0")).assertIsDisplayed()
    }
}
