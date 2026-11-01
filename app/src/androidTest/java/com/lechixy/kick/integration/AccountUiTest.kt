package com.lechixy.kick.integration

import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import com.lechixy.kick.components.account.AccountButton
import com.lechixy.kick.components.channel.chat.AccountChatComposer
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.local.SecureSessionStore
import com.lechixy.kick.data.account.model.SessionStatus
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.screens.following.FollowingScreen

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

    @Test fun accountDialogIsOptionalAndDismissible() {
        compose.setContent { MaterialTheme { AccountButton() } }
        compose.onNodeWithText("Giriş yap").performClick()
        compose.onNodeWithText("Kick hesabı").assertIsDisplayed()
        compose.onNodeWithText("Kapat").performClick()
        compose.onNodeWithText("Kick hesabı").assertDoesNotExist()
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
