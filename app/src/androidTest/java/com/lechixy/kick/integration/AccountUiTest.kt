package com.lechixy.kick.integration

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

import com.lechixy.kick.KickWebLoginActivity
import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.AccountChatComposer
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.local.SecureSessionStore
import com.lechixy.kick.data.account.model.SessionStatus
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.player.local.RECORDING_DOWNLOAD_DIRECTORY
import com.lechixy.kick.data.player.local.RECORDING_DOWNLOAD_MIME_TYPE
import com.lechixy.kick.screens.following.FollowingScreen
import com.lechixy.kick.screens.you.YouHost

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
        compose.setContent { MaterialTheme { YouHost(onExit = {}, onChannelClick = {}) } }
        compose.onNodeWithText(context.getString(R.string.account_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.account_login)).assertIsDisplayed()
    }

    @Test fun youWatchTimeAndDataUsageShareSelectedPeriod() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val month = context.getString(R.string.you_usage_month)
        val allTime = context.getString(R.string.you_usage_all)
        val watchTime = context.getString(R.string.you_data_usage_watch_time)
        val dataUsage = context.getString(R.string.you_data_usage)
        val live = context.getString(R.string.you_usage_live)
        val recordings = context.getString(R.string.you_usage_vod_clips)
        compose.setContent { MaterialTheme { YouHost(onExit = {}, onChannelClick = {}) } }

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(month))
        compose.onNodeWithText(month).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(watchTime))
        compose.onNode(hasText(watchTime) and hasClickAction()).performClick()
        compose.onNodeWithText(month).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.you_usage_total_watch_time)).assertIsDisplayed()
        compose.onNodeWithText(live).assertIsDisplayed()
        compose.onNodeWithText(recordings).assertIsDisplayed()
        val account = compose.onNodeWithText(context.getString(R.string.account_title))
        if (context.resources.configuration.screenWidthDp >= 640) account.assertIsDisplayed()
        else account.assertDoesNotExist()
        compose.onNodeWithText(allTime).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.navigation_back)).performClick()

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(dataUsage))
        compose.onNode(hasText(dataUsage) and hasClickAction()).performClick()
        compose.onNodeWithText(allTime).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.you_usage_total_transferred)).assertIsDisplayed()
        compose.onNodeWithText(live).assertIsDisplayed()
        compose.onNodeWithText(recordings).assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun youAppliesCustomDaysAndKeepsThemAvailableAfterChangingPeriods() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val custom = context.getString(R.string.you_usage_custom)
        val apply = context.getString(R.string.you_usage_apply_range)
        val recent = context.getString(R.string.you_usage_last_24_hours)
        val dataUsage = context.getString(R.string.you_data_usage)
        val month = context.getString(R.string.you_usage_month)
        val dateFormatter = DatePickerDefaults.dateFormatter()
        val today = LocalDate.now()
        val locale = context.resources.configuration.locales[0]
        fun selectDay(day: Int) {
            val utcDate = today.withDayOfMonth(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val description = requireNotNull(dateFormatter.formatDate(utcDate, locale, forContentDescription = true))
            compose.onNode(hasText(description, substring = true) and hasClickAction()).performClick()
        }
        compose.setContent { MaterialTheme { YouHost(onExit = {}, onChannelClick = {}) } }

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(custom))
        compose.onNodeWithText(custom).performClick()
        compose.onNodeWithText(apply).assertIsNotEnabled()
        selectDay(10)
        selectDay(12)
        compose.onNodeWithText(apply).performClick()
        compose.onNodeWithText(custom).assertIsSelected()

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(dataUsage))
        compose.onNode(hasText(dataUsage) and hasClickAction()).performClick()
        compose.onNodeWithText(custom).assertIsSelected()
        compose.onNodeWithText(recent).performClick().assertIsSelected()
        compose.onNodeWithText(custom).performClick()
        compose.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
        compose.onNodeWithText(recent).assertIsSelected()
        compose.onNodeWithText(custom).performClick()
        compose.onNodeWithText(apply).performClick()
        compose.onNodeWithText(custom).assertIsSelected()
        compose.onNodeWithText(month).performClick().assertIsSelected()
    }

    @Test fun youScreenListsPublishedDownloadsAndDeletesFromLibrary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val title = "You screen test ${System.nanoTime()}"
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$title.mp4")
            put(MediaStore.Downloads.MIME_TYPE, RECORDING_DOWNLOAD_MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, RECORDING_DOWNLOAD_DIRECTORY)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }))
        try {
            // MediaStore creates the file when the download writes its output.
            requireNotNull(resolver.openOutputStream(uri)).use { it.write(0) }
            compose.setContent { MaterialTheme { YouHost(onExit = {}, onChannelClick = {}) } }
            val downloads = context.getString(R.string.you_downloads)
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(downloads))
            val loading = context.getString(R.string.you_downloads_loading)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(loading).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText(title).assertDoesNotExist()
            assertEquals(1, resolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null))
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(title))
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(downloads))
            compose.onNodeWithText(downloads).performClick()
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
