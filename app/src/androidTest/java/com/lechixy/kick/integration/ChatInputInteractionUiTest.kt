package com.lechixy.kick.integration

import com.lechixy.kick.data.account.remote.string

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.SystemClock
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

import com.lechixy.kick.R
import com.lechixy.kick.components.PlayerHost
import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.components.PlayerMode
import com.lechixy.kick.components.channel.ChannelTabPager
import com.lechixy.kick.components.channel.chat.ChannelPointsPanel
import com.lechixy.kick.components.channel.chat.ChatCompletionPanel
import com.lechixy.kick.components.channel.chat.ChatComposerPanel
import com.lechixy.kick.components.channel.chat.chatRulesFooter
import com.lechixy.kick.components.channel.chat.ChatReplyPreview
import com.lechixy.kick.components.channel.chat.ChatView
import com.lechixy.kick.components.channel.chat.DropsCampaignDialog
import com.lechixy.kick.components.channel.chat.DropsRewardsButton
import com.lechixy.kick.components.channel.chat.SwipeToReply
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.KickGoalDto
import com.lechixy.kick.data.chat.model.ChatCelebration
import com.lechixy.kick.data.chat.model.ChatCompletionToken
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatMessageMetadata
import com.lechixy.kick.data.chat.model.ChatMessageType
import com.lechixy.kick.data.chat.model.ChatSender
import com.lechixy.kick.data.chat.model.ChatWebSocketEvent
import com.lechixy.kick.data.chat.model.CompletionKind
import com.lechixy.kick.data.chat.model.EmoteDto
import com.lechixy.kick.data.chat.model.GiftedSubscriptionsEvent
import com.lechixy.kick.data.chat.model.TimedChatEvent
import com.lechixy.kick.data.chat.model.toRecipientMessages
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.SettingsRepository
import com.lechixy.kick.screens.channel.ChannelTab
import com.lechixy.kick.screens.channel.DropCampaignUiModel
import com.lechixy.kick.screens.channel.DropRewardUiModel
import com.lechixy.kick.screens.channel.DropsUiState

class ChatInputInteractionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var settings: SettingsRepository
    private lateinit var settingsFile: File
    private lateinit var storeScope: CoroutineScope
    @Before fun prepare() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        compose.activityRule.scenario.onActivity {
            it.setShowWhenLocked(true); it.setTurnScreenOn(true)
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settingsFile = File(compose.activity.cacheDir, "chat-input-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { settingsFile }))
    }
    @After fun cleanup() { storeScope.cancel(); settingsFile.delete() }
    private fun show(content: @Composable () -> Unit) {
        compose.setContent { CompositionLocalProvider(LocalSettingsRepository provides settings) { MaterialTheme { content() } } }
        compose.waitUntil(20_000) { runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    @Test fun swipingRightRepliesAndSwipingLeftDoesNot() {
        var replies = 0
        show {
            SwipeToReply(true, { replies++ }, Modifier.width(360.dp).testTag("message")) {
                Text("Alice: Hello", Modifier.fillMaxWidth().height(60.dp))
            }
        }
        compose.onNodeWithTag("message").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, replies) }
        compose.onNodeWithTag("message").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, replies) }
    }
    @Test fun replyPreviewUsesTheMessageRendererAndCloses() {
        var reply by mutableStateOf<ChatMessage?>(ChatMessage(id = "id", content = "original content",
            sender = ChatSender(id = 42, username = "Alice", identity = ChatIdentity(color = "#6F87FF"))))
        show { Box(Modifier.width(360.dp)) { ChatReplyPreview(reply, { reply = null }) } }
        compose.onNodeWithText("original content", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_cancel_reply)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("original content", substring = true).assertDoesNotExist()
    }
    @Test fun colonResultsInsertARealEmoteToken() {
        var inserted = ""
        show {
            ChatCompletionPanel(ChatCompletionToken(CompletionKind.EMOTE, "trF", 0, 4), emptyList(), emptyList(),
                listOf(EmoteDto(42, name = "trFire")), { inserted = it })
        }
        compose.onNodeWithText("trFire").performClick()
        compose.runOnIdle { assertEquals("[emote:42:trFire]", inserted) }
    }
    @Test fun disabledChannelPointsExplainTheStateAndNeverShowZero() {
        show { ChannelPointsPanel("tester", emptyList(), false, 0, null, {}, {}, pointsEnabled = false) }
        compose.onNodeWithText(compose.activity.getString(R.string.points_disabled)).assertIsDisplayed()
        compose.onAllNodesWithText("0").assertCountEquals(0)
        compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.points_disabled)).assertCountEquals(1)
    }
    @Test fun tabPagesFollowTheFingerAndRespondToShortDragsAndFlicks() {
        lateinit var pager: PagerState
        show {
            pager = rememberPagerState { 2 }
            ChannelTabPager(pager, listOf(ChannelTab.HOME, ChannelTab.VIDEOS), Modifier.size(360.dp, 200.dp).testTag("pager")) {
                Box(Modifier.fillMaxSize().testTag(it.name)) { Text(it.name) }
            }
        }
        compose.mainClock.autoAdvance = false
        val width = compose.onNodeWithTag("pager").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithTag("pager").performTouchInput { down(center); moveBy(Offset(-width * .3f, 0f)) }
        compose.mainClock.advanceTimeByFrame()
        val home = compose.onNodeWithTag("HOME").fetchSemanticsNode().boundsInRoot
        val video = compose.onNodeWithTag("VIDEOS").fetchSemanticsNode().boundsInRoot
        // Semantics bounds are clipped by the pager; the outgoing page gets narrower while the next enters.
        assertTrue("Outgoing page should be partially visible: $home / $width", home.width > 0f && home.width < width)
        assertTrue("Incoming page should enter before release: $video / $width", video.left > 0f && video.left < width)
        // Holding before release removes fling velocity, so this checks the lower distance threshold.
        compose.onNodeWithTag("pager").performTouchInput { advanceEventTime(250); up() }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(1, pager.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput {
            down(center); moveBy(Offset(width * .3f, 0f)); advanceEventTime(250); up()
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(0, pager.settledPage) }
        // A quick flick can advance even below the positional threshold.
        compose.onNodeWithTag("pager").performTouchInput {
            swipe(center, center + Offset(-width * .18f, 0f), durationMillis = 80)
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(1, pager.settledPage) }
        compose.mainClock.autoAdvance = true
    }
    @Test fun predictiveBackCancellationRestoresTheChannelAndCommitClosesIt() {
        val host = PlayerHostState().apply {
            open("offline"); onChannelLoaded("offline", ChannelDetail(1, slug = "offline"))
        }
        show {
            PlayerHost(host, false, host::minimize, host::expand, host::close,
                pageContent = { Box(Modifier.fillMaxSize().testTag("channel-page")) })
        }
        val initial = compose.onNodeWithTag("channel-page").fetchSemanticsNode().boundsInRoot.width
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 100f, .6f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        val preview = compose.onNodeWithTag("channel-page").fetchSemanticsNode().boundsInRoot.width
        assertTrue(preview < initial)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(PlayerMode.Expanded, host.mode) }
        assertEquals(initial, compose.onNodeWithTag("channel-page").fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(200f, 100f, 1f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(PlayerMode.Hidden, host.mode) }
    }
    @Test fun theBundledRewardIsAnAnimatedImage() {
        val source = ImageDecoder.createSource(compose.activity.resources, R.drawable.reward_available)
        val drawable = ImageDecoder.decodeDrawable(source)
        assertTrue(drawable is AnimatedImageDrawable)
        assertTrue(drawable.intrinsicWidth > 0)
        assertEquals(drawable.intrinsicWidth, drawable.intrinsicHeight)
    }
    @Test fun rulesStayUntilAcceptedAndTheAcceptanceSurvivesReopeningThatChannel() {
        var channelId by mutableLongStateOf(101)
        var mounted by mutableStateOf(true)
        val rules = "Keep spoilers out of chat."
        val editor = "Message editor"
        show {
            if (mounted) key(channelId) {
                var expanded by remember { mutableStateOf(false) }
                val rulesFooter = chatRulesFooter(channelId, 42, rules) { expanded = true }
                ChatComposerPanel(compact = true, expanded = expanded, allowAutoHide = false,
                    onExpandedChange = { expanded = it }, onHide = {}, rulesFooter = rulesFooter,
                    chatContent = { Text("Latest message") }) {
                    Text(editor)
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(rules).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(20_000)
        compose.onNodeWithText(rules).assertIsDisplayed()
        compose.onNodeWithText("Latest message").assertIsDisplayed()
        compose.onNodeWithText(editor).assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_show_composer)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.chat_rules_accept)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(editor).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(rules).assertDoesNotExist()
        compose.runOnIdle { mounted = false }
        compose.runOnIdle { mounted = true }
        val restoreComposer = compose.activity.getString(R.string.chat_show_composer)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(restoreComposer).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(rules).assertDoesNotExist()
        compose.runOnIdle { channelId = 102 }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(rules).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(editor).assertDoesNotExist()
    }

    @Test fun subscriptionCelebrationStacksUsernameDescriptionAndMessageWithGoalAtTheRight() {
        val celebration = ChatMessage(
            id = "celebration", type = ChatMessageType.CELEBRATION, sender = ChatSender(id = 42, username = "Plevertk"),
            content = "nice aylara abim",
            metadata = ChatMessageMetadata(celebration = ChatCelebration(type = "subscription_renewed", totalMonths = 26))
        )
        show {
            Box(Modifier.size(360.dp, 320.dp)) {
                ChatView(listOf(celebration), ChatConnectionState.CONNECTED,
                    subscriptionGoal = KickGoalDto(id = "goal", channelId = "channel", type = "subscriptions",
                        targetValue = 4_000, currentValue = 3_600, status = "active"))
            }
        }
        val context = compose.activity
        val username = compose.onNodeWithText("Plevertk").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val description = compose.onNodeWithText(context.getString(R.string.chat_event_celebrates_months, 26))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val message = compose.onNodeWithText("Plevertk: ${celebration.content}")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(username.bottom <= description.top)
        assertTrue(description.bottom <= message.top)
        assertEquals(username.left, description.left, 1f)
        assertEquals(username.left, message.left, 1f)

        val locale = context.resources.configuration.locales[0]
        val goalLabel = listOf(3_600L, 4_000L).joinToString(" / ") {
            com.lechixy.kick.util.FormatUtils.formatCompactCount(it, locale)
        }
        val goal = compose.onNodeWithText(goalLabel).assertIsDisplayed()
        assertTrue(goal.fetchSemanticsNode().boundsInRoot.left >= message.right)
    }

    @Test fun giftedSubscriptionGoalAppearsOnceEvenWithTheGiftTickerVisible() {
        val event = GiftedSubscriptionsEvent(correlationId = "gift", gifterUsername = "Gifter", giftedTotal = 1)
        val messages = event.toRecipientMessages()
        val goal = KickGoalDto(id = "goal", channelId = "channel", type = "subscriptions",
            targetValue = 4_000, currentValue = 3_600, status = "active")
        var tickerEvents by mutableStateOf(emptyList<TimedChatEvent>())
        show {
            Box(Modifier.size(360.dp, 320.dp)) {
                ChatView(messages, ChatConnectionState.CONNECTED, subscriptionGoal = goal, tickerEvents = tickerEvents)
            }
        }
        val locale = compose.activity.resources.configuration.locales[0]
        val format = com.lechixy.kick.util.FormatUtils
        val label = "${format.formatCompactCount(3_600, locale)} / ${format.formatCompactCount(4_000, locale)}"
        compose.onNodeWithText(label).assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            val duration = 60_000L
            tickerEvents = listOf(TimedChatEvent(1, ChatWebSocketEvent.GiftedSubscriptions(event),
                duration, SystemClock.elapsedRealtime() + duration))
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.hide)).assertIsDisplayed()
        compose.onAllNodesWithText(label).assertCountEquals(1)
    }

    @Test fun celebrationTickerKeepsTheSingleLineAndUsesTheAdaptiveSenderColor() {
        val message = ChatMessage(
            id = "ticker", type = ChatMessageType.CELEBRATION, content = "sa",
            sender = ChatSender(username = "suyaprak", identity = ChatIdentity(color = "#FFFFFF")),
            metadata = ChatMessageMetadata(celebration = ChatCelebration(type = "subscription_renewed", totalMonths = 7)),
        )
        val event = TimedChatEvent(1, ChatWebSocketEvent.MessageReceived(message),
            60_000L, SystemClock.elapsedRealtime() + 60_000L)
        val goal = KickGoalDto(id = "goal", channelId = "channel", type = "subscriptions",
            targetValue = 6_000, currentValue = 5_800, status = "active")
        var tickerEvents by mutableStateOf(emptyList<TimedChatEvent>())
        show {
            Box(Modifier.size(360.dp, 320.dp)) {
                ChatView(emptyList(), ChatConnectionState.CONNECTED, tickerEvents = tickerEvents, subscriptionGoal = goal)
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { tickerEvents = listOf(event) }
        compose.mainClock.advanceTimeBy(500)
        val context = compose.activity
        val locale = context.resources.configuration.locales[0]
        val months = java.text.NumberFormat.getIntegerInstance(locale).format(7)
        val line = context.getString(R.string.chat_event_subscribed_months, "suyaprak", months)
        val ticker = compose.onAllNodesWithText(line).onFirst().assertIsDisplayed().fetchSemanticsNode()
        val tickerText = ticker.config[SemanticsProperties.Text].single()
        val username = tickerText.text.indexOf("suyaprak")
        assertTrue(tickerText.spanStyles.any {
            it.start == username && it.end == username + "suyaprak".length && it.item.color == Color.Black
        })
        compose.onNodeWithText("suyaprak: sa").assertDoesNotExist()
        val label = listOf(5_800L, 6_000L).joinToString(" / ") {
            com.lechixy.kick.util.FormatUtils.formatCompactCount(it, locale)
        }
        compose.onNodeWithText(label).assertDoesNotExist()
    }

    @Test fun dropsButtonShowsItsTooltipAndEnablesOnlyWithAnActiveCampaign() {
        var uiState by mutableStateOf<DropsUiState>(DropsUiState.Loading)
        var openCount = 0
        val label = compose.activity.getString(R.string.drops_and_rewards)
        show {
            Box(Modifier.padding(top = 80.dp)) { DropsRewardsButton(uiState) { openCount++ } }
        }
        val button = compose.onNodeWithContentDescription(label)
        button.assertIsNotEnabled()
        compose.runOnIdle { uiState = DropsUiState.Empty }
        button.assertIsNotEnabled().performTouchInput { longClick() }
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, openCount)
            uiState = DropsUiState.Success(listOf(
                DropCampaignUiModel("campaign", "Campaign", "", "Channel", "Category",
                    listOf(DropRewardUiModel("reward", "Reward", "", 0f, false)), "", "")
            ))
        }
        button.assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, openCount)
            uiState = DropsUiState.Error
        }
        button.assertIsNotEnabled()
    }

    @Test fun dropsDialogPagesThroughCampaignsAndKeepsTheirRewardsSeparate() {
        fun campaign(id: String, name: String, vararg rewards: String) =
            DropCampaignUiModel(id, name, "", "Channel", "Category",
                rewards.map { DropRewardUiModel(it, it, "", 0f, false) }, "Watch time remaining", "Campaign ends soon")

        show {
            DropsCampaignDialog(DropsUiState.Success(listOf(
                campaign("first", "First campaign", "Badge"),
                campaign("second", "Second campaign", "Emote", "Reward")
            )), onRetry = {}, onDismiss = {})
        }
        compose.onNodeWithText("First campaign").assertIsDisplayed()
        compose.onNodeWithText("Badge").assertIsDisplayed()
        compose.onNode(hasText("Close") or hasText("Kapat")).assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.drops_next_campaign)).performClick()
        compose.onNodeWithText("Second campaign").assertIsDisplayed()
        compose.onNodeWithText("Emote").assertIsDisplayed()
        compose.onNodeWithText("Reward").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.drops_previous_campaign)).performClick()
        compose.onNodeWithText("First campaign").assertIsDisplayed()
    }

    @Test fun bundledKicksArtworkDecodesOfflineAndAnimatesFromFiveHundred() {
        val assets = compose.activity.assets
        val tiers = listOf(1, 50, 100, 500, 1_000, 2_000, 5_000, 10_000, 50_000)
        for (tier in tiers) {
            val source = ImageDecoder.createSource(assets, "kicks/gifts/$tier-kick-bundle.webp")
            val drawable = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                decoder.setTargetSize(96, 96)
            }
            assertEquals("Animated artwork for $tier Kicks", tier >= 500, drawable is AnimatedImageDrawable)
        }
    }

    @Test fun initialHistoryDoesNotShowTheJumpToLatestButton() {
        var messages by mutableStateOf(emptyList<ChatMessage>())
        show { Box(Modifier.size(360.dp, 300.dp)) { ChatView(messages, ChatConnectionState.CONNECTED) } }
        compose.runOnUiThread {
            messages = (1..50).map { ChatMessage(id = "$it", content = "history $it", sender = ChatSender(username = "Alice"), isHistory = true) }
        }
        compose.waitForIdle()
        compose.onNodeWithText(compose.activity.getString(R.string.chat_scroll_bottom)).assertDoesNotExist()
        compose.onNodeWithText("history 50", substring = true).assertIsDisplayed()
    }
}
