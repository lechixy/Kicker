package com.lechixy.kick.integration

import com.lechixy.kick.components.channel.chat.ChatMessageItem
import com.lechixy.kick.components.channel.chat.SynchronizedEmote
import com.lechixy.kick.data.account.remote.string
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatMessageMetadata
import com.lechixy.kick.data.chat.model.ChatOriginalMessage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.BadgeTooltip
import com.lechixy.kick.components.channel.chat.ChannelPointsPanel
import com.lechixy.kick.components.channel.chat.ChatIdentityDialog
import com.lechixy.kick.components.channel.chat.ChatTextParser
import com.lechixy.kick.components.channel.chat.ParsedChatMessage
import com.lechixy.kick.components.channel.chat.ResolvedChatBadge
import com.lechixy.kick.data.chat.model.ChatBadge
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.ChatSenderBadge

@RunWith(AndroidJUnit4::class)
class ChatIdentityUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun identityLoadRetainsDialogAndEarlyColorSelection() {
        val loaded = androidx.compose.runtime.mutableStateOf<ChatIdentity?>(null)
        var saved: ChatIdentity? = null
        compose.setContent { MaterialTheme {
            ChatIdentityDialog(loaded.value, "MyAccount", emptyList(), onPreviewClick = {},
                isLoading = loaded.value == null) { saved = it }
        } }
        val dialogNodeId = compose.onNode(isDialog()).fetchSemanticsNode().id
        compose.onNodeWithContentDescription("#00F1FF").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle {
            loaded.value = ChatIdentity(color = "#FFFFFF", badges = listOf(ChatSenderBadge(type = "moderator")))
        }
        assertEquals(dialogNodeId, compose.onNode(isDialog()).fetchSemanticsNode().id)
        compose.onNodeWithContentDescription("#00F1FF").performScrollTo().assertIsSelected()
        compose.onNodeWithText("MyAccount").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("#00F1FF", saved?.color)
            assertEquals("moderator", saved?.badges?.single()?.type)
        }
    }

    @Test fun previewOpensOwnProfileWithPendingIdentityAndDismissesEditor() {
        var dismissed = false
        var opened: ChatIdentity? = null
        compose.setContent { MaterialTheme {
            ChatIdentityDialog(ChatIdentity(color = "#FFFFFF"), "MyAccount", emptyList(),
                onPreviewClick = { opened = it }) { dismissed = true }
        } }
        compose.onNodeWithText("MyAccount").performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertEquals("#FFFFFF", opened?.color)
        }
    }

    @Test fun replyHeaderPassesOriginalMessageIdToNavigation() {
        var target: String? = null
        val message = com.lechixy.kick.data.chat.model.ChatMessage(id = "reply", content = "response",
            metadata = com.lechixy.kick.data.chat.model.ChatMessageMetadata(
                originalMessage = com.lechixy.kick.data.chat.model.ChatOriginalMessage(id = "cached-original", content = "original body")))
        compose.setContent { MaterialTheme {
            com.lechixy.kick.components.channel.chat.ChatMessageItem(message, onReplyClick = { target = it })
        } }
        compose.onNode(hasText("original body", substring = true) and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals("cached-original", target) }
    }

    @Test fun emoteTooltipShowsColonWrappedName() {
        compose.setContent { MaterialTheme {
            com.lechixy.kick.components.channel.chat.SynchronizedEmote("https://files.kick.com/emotes/1/fullsize", "trFlag", Modifier.size(32.dp))
        } }
        compose.onNodeWithContentDescription("trFlag").performClick()
        compose.onNodeWithText(":trFlag:").assertIsDisplayed()
    }

    @Test fun levelTooltipUsesTheUsersMetadataLevel() {
        val identity = ChatIdentity(badgesV2 = listOf(ChatBadge(name = "level", selected = true,
            imageUrl = "https://example.com/badge.png", metadata = Json.parseToJsonElement("""{"level":22}"""))))
        val badge = ChatTextParser.resolveBadges(identity, emptyList()).single()
        compose.setContent { MaterialTheme { BadgeTooltip(badge) { Text("level badge") } } }
        compose.onNodeWithText("level badge").performClick()
        compose.onNodeWithText(context.getString(R.string.badge_level, "22")).assertIsDisplayed()
    }

    @Test fun pointsUpdateTimeAppearsNextToDialogBalances() {
        compose.setContent { MaterialTheme {
            ChannelPointsPanel("tester", emptyList(), false, 100, 0, {}, {},
                lastPointUpdate = java.time.Instant.parse("2026-10-29T12:00:00Z"))
        } }
        val label = context.getString(R.string.points_last_update, "").trim()
        compose.onNodeWithText(label, substring = true).assertIsDisplayed()
    }

    @Test fun paletteChangesAreLocalUntilDismiss() {
        var dismissed = false
        var saved: ChatIdentity? = null
        compose.setContent { MaterialTheme {
            ChatIdentityDialog(ChatIdentity(color = "#DEB2FF"), "tester", emptyList(), onPreviewClick = {}) {
                dismissed = true; saved = it
            }
        } }
        compose.onNodeWithContentDescription("#00F1FF").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertFalse(dismissed); assertNull(saved) }
        compose.onNodeWithText("tester").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(dismissed); assertEquals("#00F1FF", saved?.color) }
    }

    @Test fun revertingBadgeSelectionDoesNotSave() {
        var dismissed = false
        var saved: ChatIdentity? = ChatIdentity()
        compose.setContent { MaterialTheme {
            ChatIdentityDialog(ChatIdentity(color = "#00F1FF", badges = listOf(ChatSenderBadge(type = "moderator"))),
                "tester", emptyList(), onPreviewClick = {}) { dismissed = true; saved = it }
        } }
        val badge = compose.onNode(
            hasContentDescription(context.getString(R.string.badge_moderator)) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        )
        badge.performScrollTo().performClick().assertIsNotSelected()
        badge.performClick().assertIsSelected()
        compose.onNodeWithText("tester").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(dismissed); assertNull(saved) }
    }

    @Test fun onlyDeletedBodyUsesItalic() {
        lateinit var deleted: ParsedChatMessage
        lateinit var normal: ParsedChatMessage
        compose.setContent { MaterialTheme {
            deleted = ChatTextParser.parseMessage("tester", Color.Red, "Message deleted by moderator", 14, deleted = true)
            normal = ChatTextParser.parseMessage("tester", Color.Red, "hello", 14)
            Text(deleted.annotatedString)
        } }
        compose.runOnIdle {
            val italic = deleted.annotatedString.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
            assertEquals(1, italic.size)
            assertEquals("Message deleted by moderator", deleted.annotatedString.text.substring(italic.single().start, italic.single().end))
            assertTrue(normal.annotatedString.spanStyles.none { it.item.fontStyle == FontStyle.Italic })
        }
    }

    @Test fun badgeClicksReplaceThePreviousTooltip() {
        compose.setContent { MaterialTheme {
            Column {
                BadgeTooltip(ResolvedChatBadge("badge_local_moderator", "Moderator", sortOrder = 0)) { Text("first badge") }
                BadgeTooltip(ResolvedChatBadge("badge_local_broadcaster", "Broadcaster", sortOrder = 0)) { Text("second badge") }
            }
        } }
        compose.onNodeWithText("first badge").performClick()
        compose.onNodeWithText(context.getString(R.string.badge_moderator)).assertIsDisplayed()
        compose.onNodeWithText("second badge").performClick()
        compose.onNodeWithText(context.getString(R.string.badge_broadcaster)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.badge_moderator)).assertDoesNotExist()
    }

    @Test fun ownChannelPointsUseInfinityInsteadOfTheBalance() {
        compose.setContent { MaterialTheme {
            ChannelPointsPanel("tester", emptyList(), false, 56085, 0, {}, {}, ownChannel = true)
        } }
        compose.onNodeWithContentDescription(context.getString(R.string.points_unlimited)).assertIsDisplayed()
        compose.onNodeWithText("56,085").assertDoesNotExist()
    }
}
