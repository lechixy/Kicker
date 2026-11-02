package com.lechixy.kick.components.channel.chat

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChatActivityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun hiddenRetainedEmoteReleasesItsDecoderAndAnimation() {
        val context = compose.activity.applicationContext
        val fixture = File(context.cacheDir, "chat-activity-${System.nanoTime()}.gif")
        InstrumentationRegistry.getInstrumentation().context.assets.open("synchronized-emote.gif").use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
        val url = fixture.toURI().toString()
        var active by mutableStateOf(true)
        lateinit var entry: SharedEmote
        try {
            compose.setContent {
                CompositionLocalProvider(LocalChatContentActive provides active) {
                    MaterialTheme { SynchronizedEmote(url, "test", Modifier.size(32.dp)) }
                }
            }
            compose.runOnIdle {
                entry = VisibleEmotes.acquire(context, url)
                VisibleEmotes.release(url, entry)
            }
            compose.waitUntil(10_000) { entry.drawable != null || entry.failed }
            compose.runOnIdle {
                assertEquals(1, entry.readers)
                assertTrue("Fixture must animate before hiding", (entry.drawable as? android.graphics.drawable.Animatable)?.isRunning == true)
                active = false
            }
            compose.waitUntil(5_000) { entry.readers == 0 }
            compose.runOnIdle { assertNull("Hidden rows must release decoded images", entry.drawable) }
        } finally {
            fixture.delete()
        }
    }

    @Test fun hiddenChatKeepsItsRowsButStopsCollectingAndResumesWithLatestMessages() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(compose.activity.cacheDir, "chat-activity-${System.nanoTime()}.preferences_pb")
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        val messages = MutableStateFlow(listOf(ChatMessage(id = "first", content = "Retained row")))
        var active by mutableStateOf(true)
        var constructions = 0
        var disposals = 0
        try {
            compose.setContent {
                CompositionLocalProvider(LocalSettingsRepository provides settings, LocalChatContentActive provides active) {
                    MaterialTheme {
                        val current by messages.collectAsChatState()
                        DisposableEffect(Unit) { constructions++; onDispose { disposals++ } }
                        Box(Modifier.fillMaxSize()) {
                            ChatView(current, connectionState = ChatConnectionState.CONNECTED)
                        }
                    }
                }
            }
            compose.waitUntil(5_000) { messages.subscriptionCount.value == 1 }
            compose.onNodeWithText("Retained row", substring = true).assertExists()
            repeat(3) { cycle ->
                compose.runOnIdle { active = false }
                compose.waitUntil(5_000) { messages.subscriptionCount.value == 0 }
                compose.runOnIdle {
                    repeat(1_000) { index ->
                        messages.value = listOf(ChatMessage(id = "$cycle-$index", content = "Latest $cycle-$index"))
                    }
                }
                compose.onNodeWithText("Latest $cycle-999", substring = true).assertDoesNotExist()
                compose.runOnIdle { assertEquals(1, constructions); assertEquals(0, disposals); active = true }
                compose.waitUntil(5_000) { messages.subscriptionCount.value == 1 }
                compose.onNodeWithText("Latest $cycle-999", substring = true).assertExists()
            }
            compose.runOnIdle { assertEquals(1, constructions); assertEquals(0, disposals) }
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
