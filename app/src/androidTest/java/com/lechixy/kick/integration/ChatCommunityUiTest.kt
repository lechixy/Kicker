package com.lechixy.kick.integration

import com.lechixy.kick.data.account.remote.string

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.ActiveChattersContent
import com.lechixy.kick.components.channel.chat.ChatMessageItem
import com.lechixy.kick.components.channel.chat.ChatTextParser
import com.lechixy.kick.components.channel.chat.ContinuousTickerRow
import com.lechixy.kick.components.channel.chat.KickUserProfileCard
import com.lechixy.kick.components.channel.chat.UserProfileCardUiState
import com.lechixy.kick.data.chat.model.ActiveChatter
import com.lechixy.kick.data.chat.model.ActiveChatters
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatSender

@RunWith(AndroidJUnit4::class)
class ChatCommunityUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun filteringPreservesGroupsAndClickTargetsSelectedUser() {
        var selected: ActiveChatter? = null
        compose.setContent { MaterialTheme {
            ActiveChattersContent(ActiveChatters(
                moderators = listOf(ActiveChatter("ModeratorOne", "mod-one")),
                chatters = listOf(ActiveChatter("ViewerOne", "viewer-one")), totalCount = 402), null, {}, { selected = it })
        } }
        compose.onNodeWithText(context.getString(R.string.active_chatters_search)).performTextInput("moderator")
        compose.onNodeWithText(context.getString(R.string.active_chatters_moderators)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.active_chatters_ogs)).assertIsDisplayed()
        compose.onNodeWithText("ViewerOne").assertDoesNotExist()
        compose.onNodeWithText("ModeratorOne").performClick()
        compose.runOnIdle { assertEquals("mod-one", selected?.slug) }
        compose.onNodeWithText(context.getString(R.string.active_chatters_moderators)).performClick()
        compose.onNodeWithText("ModeratorOne").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.chatters_partial, 2, 402)).assertIsDisplayed()
    }

    @Test fun usernameLinkOpensSenderWithoutMakingMessageBodyClickable() {
        var selected: ChatSender? = null
        val sender = ChatSender(id = 42, username = "Tester", slug = "tester", identity = ChatIdentity(color = "#01D7E5"))
        compose.setContent { MaterialTheme {
            ChatMessageItem(ChatMessage(id = "message", sender = sender, content = "hello"), onUserClick = { selected = it })
        } }
        compose.onNodeWithText("Tester: hello").performFirstLinkClick()
        compose.runOnIdle { assertEquals(sender, selected) }
        val text = compose.onNodeWithText("Tester: hello").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val styles = text.getLinkAnnotations(0, text.length).single().item.styles!!
        assertEquals(Color(0xFF01D7E5), styles.style?.color)
        listOf(styles.style, styles.pressedStyle, styles.focusedStyle, styles.hoveredStyle).forEach {
            assertEquals(TextDecoration.None, it?.textDecoration)
            assertEquals(Color(0xFF01D7E5), it?.color)
        }
    }

    @Test fun mentionOpensMentionedUserWhileUrlKeepsItsOwnLink() {
        var selected: ChatSender? = null
        val content = "hello @Target_one! https://example.com/@Other"
        compose.setContent { MaterialTheme {
            ChatMessageItem(ChatMessage(id = "mention", sender = ChatSender(username = "Tester"), content = content),
                onUserClick = { selected = it })
        } }
        val node = compose.onNodeWithText("Tester: $content")
        val text = node.fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals(3, links.size)
        assertEquals(1, links.count { it.item is LinkAnnotation.Url })
        val mention = links.single { (it.item as? LinkAnnotation.Clickable)?.tag == "mention:Target_one" }
        assertEquals("@Target_one", text.text.substring(mention.start, mention.end))
        assertEquals(TextDecoration.None, mention.item.styles?.style?.textDecoration)
        node.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "mention:Target_one" }
        compose.runOnIdle { assertEquals("Target_one", selected?.username) }
    }

    @Test fun tickerLinksOnlyUserSegmentsAndPreservesTheirColor() {
        var selected: String? = null
        val segments = listOf(
            ChatTextParser.EventTextSegment("Gifter", true, Color.Red, username = "Gifter"),
            ChatTextParser.EventTextSegment(" gifted "),
            ChatTextParser.EventTextSegment("5 subs", true),
            ChatTextParser.EventTextSegment(" to "),
            ChatTextParser.EventTextSegment("Recipient", true, Color.Cyan, username = "Recipient"))
        compose.setContent { MaterialTheme {
            ContinuousTickerRow(R.drawable.gifter_1_badge, Color.Green, segments, 14, Color.Green, Color.White,
                onUsernameClick = { selected = it }, speedDpPerSecond = 0f)
        } }
        val node = compose.onAllNodesWithText("Gifter gifted 5 subs to Recipient").onFirst()
        val text = node.fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals(2, links.size)
        assertEquals(Color.Red, links.first().item.styles?.style?.color)
        assertEquals(Color.Cyan, links.last().item.styles?.style?.color)
        assertTrue(links.all { it.item.styles?.style?.textDecoration == TextDecoration.None })
        node.performFirstLinkClick { (it.item as? LinkAnnotation.Clickable)?.tag == "chatter:Recipient" }
        compose.runOnIdle { assertEquals("Recipient", selected) }
    }

    @Test fun profileDisplaysUnknownValuesWithoutInventingLevelOrSubscription() {
        compose.setContent { MaterialTheme {
            KickUserProfileCard(UserProfileCardUiState(username = "Tester", isLoading = false),
                onFollowClick = {}, onMuteClick = {}, onChannelClick = {})
        } }
        compose.onNodeWithText("Tester").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.chatter_joined)).assertIsDisplayed()
        compose.onNodeWithText("40").assertDoesNotExist()
        compose.onNodeWithText("0 months").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.follow)).assertIsNotEnabled()
    }

    @Test fun chattersAreNotComposedUntilExpandedAndRowsAreLazy() {
        val users = (1..500).map { ActiveChatter("Viewer$it", "viewer$it") }
        compose.setContent { MaterialTheme { ActiveChattersContent(ActiveChatters(chatters = users), null, {}, {}) } }
        compose.onNodeWithText("Viewer1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Viewer1").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.active_chatters_viewers)).performClick()
        compose.onNodeWithText("Viewer1").assertIsDisplayed()
        compose.onNodeWithText("Viewer500").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.active_chatters_viewers)).performClick()
        compose.onNodeWithText("Viewer1").assertDoesNotExist()
    }
}
