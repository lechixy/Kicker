package com.lechixy.kick.components

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchSplashUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun realGifWaitsForThemeThenRevealsPreparedContent() {
        val visible = mutableStateOf(true)
        val ready = mutableStateOf(false)
        compose.activityRule.scenario.onActivity {
            it.setShowWhenLocked(true)
            it.setTurnScreenOn(true)
        }
        compose.setContent {
            MaterialTheme {
                LaunchSplashHost(
                    visible = visible.value,
                    appReady = ready.value,
                    playAnimation = true,
                    onFinished = { visible.value = false }
                ) { Text("Prepared home") }
            }
        }
        // Let the actual native GIF finish without advancing the Compose failsafe timer.
        SystemClock.sleep(1_600)
        compose.onNodeWithText("Prepared home").assertDoesNotExist()
        compose.runOnIdle { ready.value = true }
        compose.waitUntil(5_000) { !visible.value }
        compose.onNodeWithText("Prepared home").assertIsDisplayed()
        compose.runOnIdle { assertFalse(visible.value) }
    }
}
