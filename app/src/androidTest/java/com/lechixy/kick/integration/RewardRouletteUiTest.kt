package com.lechixy.kick.integration

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.view.WindowManager
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import com.lechixy.kick.components.account.ChallengeCardItem
import com.lechixy.kick.components.account.RewardRouletteReveal
import com.lechixy.kick.components.account.ShieldRoulette
import com.lechixy.kick.data.account.model.Challenge
import com.lechixy.kick.data.account.model.ChallengeClaim
import com.lechixy.kick.data.account.model.Condition
import com.lechixy.kick.data.account.model.RewardWinner
import com.lechixy.kick.data.account.model.ShieldReward
import com.lechixy.kick.data.account.model.Window
import com.lechixy.kick.theme.KickTheme
import com.lechixy.kick.theme.Theme

@RunWith(AndroidJUnit4::class)
class RewardRouletteUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var loader: ImageLoader
    private val winner = RewardWinner("winner", "common",
        "https://ext.cdn.kick.com/chat/emotes/cards/4Weird_Common_card_f5966c651beb469eaf110792e89807d4.png",
        "https://ext.cdn.kick.com/chat/emotes/shields/4Weird_Common_shield_c9048a7def964958a6dd3fd0162182fd.png")
    private val claim get() = ChallengeClaim("fixture-challenge", roulette = listOf(
        ShieldReward("rare", "https://ext.cdn.kick.com/chat/emotes/shields/wideNODDERS_Rare_shield_fdd10b6a49a648f290f9291b51913615.png"),
        ShieldReward("winner", winner.thumbnailUrl!!),
        ShieldReward("uncommon", "https://ext.cdn.kick.com/chat/emotes/shields/OMEGADANCE_Uncommon_shield_5d9268438e4340b9a9f65d85bdbdeaa2.png")), winner = winner)
    @Before fun prepare() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        compose.activityRule.scenario.onActivity {
            it.setShowWhenLocked(true)
            it.setTurnScreenOn(true)
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        loader = ImageLoader.Builder(context).build()
    }
    @After fun cleanup() { loader.shutdown() }
    private fun capture(name: String) {
        val file = File(context.getExternalFilesDir(null), name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun rouletteStopsWithServerWinnerUnderMarker() {
        var stopped by mutableStateOf(false)
        compose.setContent { KickTheme(theme = Theme.DARK, dynamicColor = false) {
            Surface(Modifier.width(420.dp).padding(20.dp)) { ShieldRoulette(claim, loader) { stopped = true } }
        } }
        compose.waitUntil(12_000) { stopped }
        val reel = compose.onNodeWithTag("reward-reel").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("reward-winning-shield").fetchSemanticsNode().boundsInRoot
        capture("reward-roulette-test.png")
        assertTrue("Winner must stop beneath center marker: reel=$reel target=$target", kotlin.math.abs(target.center.x - reel.center.x) <= target.width / 4f + 2f)
    }

    @Test fun winnerRevealConfirmsLocallyAndSurvivesRecomposition() {
        val confirmations = AtomicInteger()
        var revision by mutableIntStateOf(0)
        compose.setContent { KickTheme(theme = Theme.DARK, dynamicColor = false) {
            Surface(Modifier.width(420.dp)) { Column {
                RewardRouletteReveal(claim, loader) { confirmations.incrementAndGet() }
                androidx.compose.material3.Text("Revision $revision")
            } }
        } }
        compose.waitUntil(12_000) { compose.onAllNodesWithTag("reward-winner").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.waitForIdle()
        compose.runOnIdle { revision++ }
        compose.onNodeWithTag("reward-winner").assertExists()
        compose.onNodeWithTag("reward-confirm").performScrollTo().assertIsDisplayed()
        capture("reward-winner-test.png")
        compose.onNodeWithTag("reward-confirm").performClick()
        assertEquals(1, confirmations.get())
    }

    @Test fun dailyAndFeaturedCardsKeepEqualMetadataAndBannerPadding() {
        val now = Instant.parse("2026-10-28T12:00:00Z")
        val window = Window("2026-10-28T00:00:00Z", "2026-10-29T00:00:00Z")
        var featured by mutableStateOf(false)
        compose.setContent { MaterialTheme { Surface(Modifier.width(360.dp)) {
            ChallengeCardItem(Challenge("spacing", "Reward", "Claim your shield", Condition(60, 60, "watch_time_minutes"),
                featured = featured, recurrence = "daily", status = "completed", window = window,
                activePeriod = if (featured) window else null), 4, now, loader, {})
        } } }
        repeat(2) {
            val card = compose.onNodeWithTag("challenge-summary-spacing").fetchSemanticsNode().boundsInRoot
            val metadata = compose.onNodeWithTag("challenge-metadata-spacing").fetchSemanticsNode().boundsInRoot
            val banner = compose.onNodeWithTag("challenge-preview-spacing").fetchSemanticsNode().boundsInRoot
            val lower = card.bottom - banner.bottom
            val gap = banner.top - metadata.bottom
            assertTrue("Banner needs visible bottom padding", lower > 0)
            assertEquals("Metadata gap and bottom padding should match", lower, gap, 2f)
            if (it == 0) capture("reward-spacing-test.png")
            compose.runOnIdle { featured = true }
        }
    }
}
