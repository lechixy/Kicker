package com.lechixy.kick.data.chat.model

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

import com.lechixy.kick.data.common.remote.KickApi
import com.lechixy.kick.data.common.repository.KickRepository

class EmotePickerTest {
    @Test fun mixedCategoryIdsAndNullableChannelFieldsAreSupported() {
        val categories = parseEmoteCategories(Json.parseToJsonElement("""[
            {"id":26239555,"slug":"rraenee","user":{"id":27259381,"username":"RRaenee","profile_pic":"avatar.webp"},
             "emotes":[{"id":2930809,"channel_id":26239555,"name":"rraeneearti","subscribers_only":true}]},
            {"id":"Global","name":"Global","emotes":[{"id":3753119,"channel_id":null,"name":"asmonSmash"}]},
            {"id":"Emoji","name":"Emojis","emotes":[{"id":1730752,"name":"emojiAngel"}]},
            {"id":"Collectibles","name":"Collectibles","emotes":[]}
        ]"""))
        assertEquals(listOf("26239555", "Global", "Emoji", "Collectibles"), categories.map { it.id })
        assertEquals(listOf(EmoteCategoryKind.CHANNEL, EmoteCategoryKind.GLOBAL, EmoteCategoryKind.EMOJIS, EmoteCategoryKind.COLLECTIBLES), categories.map { it.kind })
        assertEquals("RRaenee", categories.first().title)
        assertEquals("avatar.webp", categories.first().profilePicUrl)
        assertTrue(categories.first().emotes.single().subscribersOnly)
        assertNull(categories[1].emotes.single().channelId)
        assertEquals("https://files.kick.com/emotes/2930809/fullsize", categories.first().emotes.single().imageUrl)
    }

    @Test fun searchIgnoresCaseAndDeduplicatesFrequentEmotes() {
        val emote = EmoteDto(1, name = "emojiAngel")
        val groups = listOf(EmoteCategory("recent", "Recent", EmoteCategoryKind.FREQUENT, listOf(emote)),
            EmoteCategory("emoji", "Emojis", EmoteCategoryKind.EMOJIS, listOf(emote, EmoteDto(2, name = "other"))))
        assertEquals(listOf(emote), filterEmotes(groups, " ANGEL "))
        assertTrue(filterEmotes(groups, "missing").isEmpty())
    }

    @Test fun insertionReplacesSelectionAndKeepsSurroundingText() {
        val emote = EmoteDto(1, name = "Smile")
        val inserted = insertEmote("hello old world", 9, 6, emote)
        assertEquals("hello [emote:1:Smile]  world", inserted.text)
        assertEquals("hello [emote:1:Smile] ".length, inserted.cursor)
        assertEquals("hello [emote:1:Smile] ", insertEmote("hello", 5, 5, emote).text)
        assertEquals("[emote:1:Smile] ", insertEmote("", 10, 10, emote).text)
    }

    @Test fun repositoryUsesOnlyTheEmotesEndpoint() = runBlocking {
        val calls = mutableListOf<String>()
        val api = Proxy.newProxyInstance(KickApi::class.java.classLoader, arrayOf(KickApi::class.java)) { _, method, args ->
            calls += method.name
            assertEquals("rraenee", args?.first())
            Json.parseToJsonElement("""[{"id":"Global","emotes":[{"id":1,"name":"Smile"}]}]""")
        } as KickApi
        assertEquals("Smile", KickRepository(api).getEmotes("rraenee").single().emotes.single().name)
        assertEquals(listOf("getEmotes"), calls)
    }
}
