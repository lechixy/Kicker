package com.lechixy.kick.integration

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

import com.lechixy.kick.KickWebLoginActivity
import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.AccountChatComposer
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.local.SecureSessionStore
import com.lechixy.kick.data.account.model.SessionStatus
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.screens.following.FollowingScreen
import com.lechixy.kick.screens.you.YouScreen
import com.lechixy.kick.screens.you.DownloadsScreen

@RunWith(AndroidJUnit4::class)
class AccountUiTest {
    @get:Rule val compose = createComposeRule()
    @Before fun guestSession() { runBlocking { AccountGraph.auth.signOut() } }

    @Test fun guestFollowingShowsLoginInsteadOfLocalChannels() {
        compose.setContent { MaterialTheme { FollowingScreen(onStreamClick = { _, _ -> }) } }
        compose.onNodeWithText("Takip etmek için giriş yapın.").assertIsDisplayed()
        compose.onNodeWithText("Kick ile giriş yap").assertIsDisplayed()
    }

    @Test fun guestComposerOffersLoginAndDoesNotOfferSend() {
        compose.setContent { MaterialTheme {
            AccountChatComposer(ChannelDetail(id = 1, slug = "test"), ChatConnectionState.CONNECTED)
        } }
        compose.onNodeWithText("Sohbete katılmak için giriş yapın.").assertIsDisplayed()
        compose.onNodeWithText("Gönder").assertDoesNotExist()
    }

    @Test fun guestYouScreenShowsAccountAndSignIn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { MaterialTheme { YouScreen(onChannelClick = {}) } }
        compose.onNodeWithText(context.getString(R.string.account_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.account_login)).assertIsDisplayed()
    }

    @Test fun youScreenListsPublishedDownloadsAndDeletesFromLibrary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val title = "You screen test ${System.nanoTime()}"
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$title.mp4")
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Kicker")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }))
        try {
            // MediaStore creates the file when the download writes its output.
            requireNotNull(resolver.openOutputStream(uri)).use { it.write(0) }
            compose.setContent {
                MaterialTheme {
                    var showDownloads by remember { mutableStateOf(false) }
                    if (showDownloads) DownloadsScreen(onBack = { showDownloads = false })
                    else YouScreen(onChannelClick = {}, onDownloads = { showDownloads = true })
                }
            }
            val loading = context.getString(R.string.you_downloads_loading)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(loading).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText(title).assertDoesNotExist()
            assertEquals(1, resolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null))
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.you_downloads)).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            val delete = context.getString(R.string.you_download_delete)
            compose.onNodeWithContentDescription(delete).performClick()
            compose.onNode(hasText(delete) and hasClickAction()).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test fun guestCanOpenSingleLoginAndClosingReturnsToGuest() {
        ActivityScenario.launch(KickWebLoginActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(SessionStatus.Connecting, AccountGraph.auth.session.value.status)
                assertFalse(AccountGraph.auth.session.value.signedIn)
            }
        }
        assertEquals(SessionStatus.Guest, AccountGraph.auth.session.value.status)
    }

    @Test fun keystoreRoundTripUsesNoBackupDirectoryAndNoPlaintext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureSessionStore(context)
        try {
            store.write("test_session", "sensitive-token-example")
            assertEquals("sensitive-token-example", SecureSessionStore(context).read("test_session"))
            val file = File(context.noBackupFilesDir, "kick_account/test_session")
            assertTrue(file.exists())
            assertFalse(file.readText().contains("sensitive-token-example"))
        } finally { store.write("test_session", null) }
        assertNull(store.read("test_session"))
    }
}
