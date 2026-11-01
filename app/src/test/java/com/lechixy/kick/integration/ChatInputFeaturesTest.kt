package com.lechixy.kick.integration

import com.lechixy.kick.data.channel.model.ChannelDetail

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.data.chat.model.ActiveChatter
import com.lechixy.kick.data.chat.model.ActiveChatters
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatSender
import com.lechixy.kick.data.chat.model.CompletionKind
import com.lechixy.kick.data.chat.model.EmoteCategoryKind
import com.lechixy.kick.data.chat.model.EmoteDto
import com.lechixy.kick.data.chat.model.chatCompletionToken
import com.lechixy.kick.data.chat.model.chatSendPayload
import com.lechixy.kick.data.chat.model.filterEmotes
import com.lechixy.kick.data.chat.model.insertCompletion
import com.lechixy.kick.data.chat.model.insertEmote
import com.lechixy.kick.data.chat.model.mentionMatches
import com.lechixy.kick.data.chat.model.parseEmoteCategories
import com.lechixy.kick.data.chat.policy.ChatCommandAction
import com.lechixy.kick.data.chat.policy.ChatCommandException
import com.lechixy.kick.data.chat.policy.ChatCommands

class ChatInputFeaturesTest {
    private val channel = ChannelDetail(id = 100, slug = "tester")
    @Test fun completionTracksCaretAndNeverTreatsEmailsAsMentions() {
        assertNull(chatCompletionToken("mail@example.com", 16))
        assertNull(chatCompletionToken("https://kick.com", 10))
        val token = chatCompletionToken("hello @al later", 9)!!
        assertEquals(CompletionKind.MENTION, token.kind)
        assertEquals("al", token.query)
        assertEquals("hello @Alice later", insertCompletion("hello @al later", token, "@Alice").text)
        assertEquals("hello @Alice ".length, insertCompletion("hello @al later", token, "@Alice").cursor)
    }
    @Test fun colonCompletionReplacesOnlyTheTypedFragmentWithEmoteToken() {
        val text = "hi :trF there"
        val token = chatCompletionToken(text, 7)!!
        assertEquals(CompletionKind.EMOTE, token.kind)
        assertEquals("trF", token.query)
        assertEquals("hi [emote:42:trFire] there", insertCompletion(text, token, EmoteDto(42, name = "trFire").messageToken).text)
        assertNull(chatCompletionToken("[emote:42:trFire]", 12))
    }
    @Test fun mentionMatchingDeduplicatesAndRanksPrefixesBeforeSubstrings() {
        val result = mentionMatches(listOf(ActiveChatter("Bala"), ActiveChatter("Alice"), ActiveChatter("ALICE")), "al")
        assertEquals(listOf("Alice", "Bala"), result.map { it.username })
        assertTrue(mentionMatches(result, "missing").isEmpty())
    }
    @Test fun commandsFilterPrivilegesAndNeverFallThroughToChat() {
        assertEquals(listOf("help", "user"), ChatCommands.available(false, false).map { it.name })
        assertFalse(ChatCommands.available(true, false).any { it.name == "mod" })
        assertTrue(ChatCommands.available(false, true).any { it.name == "mod" })
        assertTrue(runCatching { ChatCommands.parse(channel, "/ban alice", false, false) }.exceptionOrNull() is ChatCommandException)
        assertTrue(runCatching { ChatCommands.parse(channel, "/unknown", true, true) }.isFailure)
    }
    @Test fun slashCommandsUseTheirOwnEndpointsAndPreserveMultiwordReasons() {
        val action = ChatCommands.parse(channel, "/timeout alice 5 repeated spam", true, false) as ChatCommandAction.Request
        assertEquals("https://kick.com/api/v2/channels/tester/bans", action.url)
        assertEquals("POST", action.method)
        assertEquals(5, action.body!!["duration"]!!.jsonPrimitive.int)
        assertEquals("repeated spam", action.body["reason"]!!.jsonPrimitive.content)
        assertFalse(action.body["permanent"]!!.jsonPrimitive.boolean)
        val slow = ChatCommands.parse(channel, "/slow on 30", true, false) as ChatCommandAction.Request
        assertEquals("https://web.kick.com/api/v1/channels/100/chat/settings", slow.url)
        assertEquals(30, slow.body!!["slow_mode"]!!.jsonObject["duration_seconds"]!!.jsonPrimitive.int)
        assertTrue(runCatching { ChatCommands.parse(channel, "/slow on nope", true, false) }.isFailure)
    }
    @Test fun replyPayloadMatchesTheSuppliedKickContractAndNormalPayloadHasNoReplyMetadata() {
        val original = ChatMessage(id = "original-id", content = "original content", sender = ChatSender(id = 42, username = "alice"))
        val payload = chatSendPayload("my reply", original, "ref")
        assertEquals("reply", payload["type"]!!.jsonPrimitive.content)
        assertEquals("ref", payload["message_ref"]!!.jsonPrimitive.content)
        val metadata = payload["metadata"]!!.jsonObject
        assertEquals("original-id", metadata["original_message"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("alice", metadata["original_sender"]!!.jsonObject["username"]!!.jsonPrimitive.content)
        assertEquals(42L, metadata["original_sender"]!!.jsonObject["id"]!!.jsonPrimitive.long)
        assertNull(chatSendPayload("plain")["metadata"])
    }
    @Test fun collectiblesCanUseNamedCategoriesWithoutIdsAndStaySearchableAndInsertable() {
        val result = parseEmoteCategories(Json.parseToJsonElement("""[{"name":"Collectibles","emotes":[{"id":1200,"name":"collectiblesFire"}]}]"""))
        assertEquals(EmoteCategoryKind.COLLECTIBLES, result.single().kind)
        val emote = filterEmotes(result, "fire").single()
        assertEquals("[emote:1200:collectiblesFire]", emote.messageToken)
        assertEquals("[emote:1200:collectiblesFire] ", insertEmote("", 0, 0, emote).text)
    }
    @Test fun serverTotalAndTheLimitedChatterListRemainDistinct() {
        val data = ActiveChatters(chatters = (1..100).map { ActiveChatter("user$it") },
            moderators = listOf(ActiveChatter("mod")), totalCount = 126)
        assertTrue(data.isPartial)
        assertEquals(101, data.listedCount)
        assertEquals(126, data.totalCount)
    }
}
