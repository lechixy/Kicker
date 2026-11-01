package com.lechixy.kick.integration

import com.lechixy.kick.data.account.remote.string

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

import com.lechixy.kick.KickNavScaffold
import com.lechixy.kick.R
import com.lechixy.kick.components.ConnectionNoticeBar
import com.lechixy.kick.components.channel.ExpandableProfilePhoto
import com.lechixy.kick.data.common.remote.ConnectionNotice

@RunWith(AndroidJUnit4::class)
class PlayerFeaturesUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun scaffoldUpdatesKeepPlayerContentAlive() {
        var hidden by mutableStateOf(false)
        var revision by mutableStateOf(0)
        val disposals = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                KickNavScaffold(hidden = hidden, currentRoute = "home", onNavigate = {}) {
                    DisposableEffect(Unit) { onDispose { disposals.incrementAndGet() } }
                    Text("Player revision $revision")
                }
            }
        }
        repeat(3) { index ->
            compose.runOnIdle { hidden = !hidden; revision++ }
            compose.onNodeWithText("Player revision ${index + 1}").assertIsDisplayed()
            compose.runOnIdle { assertEquals(0, disposals.get()) }
        }
    }

    @Test fun avatarLongPressOpensAndClosesConnectedPreview() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    ExpandableProfilePhoto("android.resource://com.lechixy.kick/drawable/ic_launcher_foreground",
                        "Test avatar", Modifier.size(56.dp))
                }
            }
        }
        compose.onNodeWithContentDescription("Test avatar").performTouchInput { longClick() }
        compose.waitForIdle()
        val close = context.getString(R.string.close_profile_photo)
        compose.onNodeWithText(close).assertDoesNotExist()
        compose.onNode(isDialog()).performTouchInput { click(Offset(4f, center.y)) }
        compose.waitForIdle()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Test avatar").assertIsDisplayed()
    }

    @Test fun networkNoticeUsesLocalizedMessageAndFullAvailableWidth() {
        compose.setContent {
            MaterialTheme { Box(Modifier.fillMaxSize().testTag("screen")) {
                ConnectionNoticeBar(ConnectionNotice.Restored, Modifier.testTag("notice"))
            } }
        }
        compose.onNodeWithText(context.getString(R.string.connection_restored)).assertIsDisplayed()
        val screen = compose.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
        val notice = compose.onNodeWithTag("notice").fetchSemanticsNode().boundsInRoot
        assertEquals(screen.width, notice.width, 1f)
    }
}
