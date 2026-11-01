package com.lechixy.kick.integration

import com.lechixy.kick.data.account.remote.string

import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.geometry.Offset
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

import com.lechixy.kick.R
import com.lechixy.kick.data.player.remote.ViewerSession
import com.lechixy.kick.data.player.remote.ViewerTrackingDiagnostics
import com.lechixy.kick.data.player.remote.ViewerTrackingState
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.SettingsRepository
import com.lechixy.kick.screens.settings.DeveloperToolsOverlay
import com.lechixy.kick.util.findActivity

@RunWith(AndroidJUnit4::class)
class DeveloperToolsOverlayTest {
    @get:Rule val compose = createComposeRule()
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val file by lazy { File(context.cacheDir, "developer-test-${System.nanoTime()}.preferences_pb") }
    @After fun cleanup() { storeScope.cancel(); file.delete(); ViewerTrackingDiagnostics.mutableState.value = ViewerTrackingState() }

    @Test fun overlayPreservesLayoutTouchesAndDialogFocus() {
        val repository = SettingsRepository(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { file }))
        var dialog by mutableStateOf(false)
        var taps by mutableIntStateOf(0)
        compose.setContent {
            val activity = androidx.compose.ui.platform.LocalContext.current.findActivity()
            SideEffect {
                activity?.setShowWhenLocked(true)
                activity?.setTurnScreenOn(true)
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            CompositionLocalProvider(LocalSettingsRepository provides repository) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().testTag("screen")) {
                        Button(onClick = { dialog = true }, modifier = Modifier.align(Alignment.TopCenter)) { Text("Open test dialog") }
                        Button(onClick = { taps++ }, modifier = Modifier.align(Alignment.BottomCenter)) { Text("Underlying action") }
                        if (dialog) Dialog(onDismissRequest = { dialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                            Box(Modifier.fillMaxSize()) {
                                Text("Dialog is open", Modifier.align(Alignment.TopCenter))
                                Button(onClick = { dialog = false }, modifier = Modifier.align(Alignment.BottomCenter)) { Text("Dismiss test dialog") }
                            }
                        }
                        DeveloperToolsOverlay(false, {}, false)
                    }
                }
            }
        }
        fun panelAttachedToFocusedWindow(): Boolean {
            var found = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val roots = WindowInspector.getGlobalWindowViews()
                val panel = roots.firstOrNull { (it.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL }
                val focused = roots.lastOrNull { it.hasWindowFocus() }
                found = panel != null && focused != null &&
                    (panel.layoutParams as WindowManager.LayoutParams).token == focused.windowToken
            }
            return found
        }
        try { compose.waitUntil(5_000) { panelAttachedToFocusedWindow() } }
        catch (failure: Throwable) {
            var roots = ""
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                roots = WindowInspector.getGlobalWindowViews().joinToString { "${it.javaClass.simpleName}: type=${(it.layoutParams as? WindowManager.LayoutParams)?.type}, focus=${it.hasWindowFocus()}, attached=${it.isAttachedToWindow}" }
            }
            throw AssertionError("Panel not attached to focused window: $roots", failure)
        }
        fun panelFrame(): List<Int>? {
            var result: List<Int>? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val params = WindowInspector.getGlobalWindowViews().firstOrNull {
                    (it.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
                }?.layoutParams as? WindowManager.LayoutParams
                result = params?.let { listOf(it.x, it.y, it.width, it.height) }
            }
            return result
        }
        val collapsedHeight = panelFrame()!![3]
        repeat(3) {
            // Exercise the real pointer route; semantic clicks alone skip native hit testing.
            compose.onNodeWithContentDescription(context.getString(R.string.developer_expand)).performTouchInput { click() }
            compose.waitUntil(5_000) { (panelFrame()?.get(3) ?: 0) > collapsedHeight }
            compose.waitForIdle()
            compose.onNodeWithText(context.getString(R.string.developer_trigger_live)).assertIsDisplayed()
            val expandedFrame = panelFrame()
            val started = android.os.SystemClock.elapsedRealtime()
            compose.waitUntil(1_500) {
                assertEquals("Native panel bounds changed during expansion", expandedFrame, panelFrame())
                android.os.SystemClock.elapsedRealtime() - started >= 350
            }
            compose.onNodeWithContentDescription(context.getString(R.string.developer_minimize)).performTouchInput { click() }
            compose.waitUntil(5_000) { panelFrame()?.get(3) == collapsedHeight }
            assertEquals(expandedFrame!!.take(2), panelFrame()!!.take(2))
        }
        val bounds = compose.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription(context.getString(R.string.developer_expand)).performTouchInput { click() }
        compose.onNodeWithText(context.getString(R.string.developer_trigger_live)).assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle {
            ViewerTrackingDiagnostics.mutableState.value = ViewerTrackingState(status = "retrying",
                session = ViewerSession(1, 2, "long-vod-id-".repeat(50)), error = "Developer scroll target")
        }
        compose.onNodeWithText("Developer scroll target").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.developer_minimize)).performClick()
        compose.onNodeWithText("Developer scroll target").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.developer_expand)).performClick()
        compose.onNodeWithText("Underlying action").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, taps) }
        compose.onNodeWithText("Open test dialog").performTouchInput { click() }
        compose.onNodeWithText("Dialog is open").assertIsDisplayed()
        compose.waitUntil(5_000) { panelAttachedToFocusedWindow() }
        compose.onNodeWithText("Dismiss test dialog").performTouchInput { click() }
        compose.onNodeWithText("Dialog is open").assertDoesNotExist()
        compose.waitUntil(5_000) { panelAttachedToFocusedWindow() }
        compose.onNodeWithText(context.getString(R.string.developer_tools)).performTouchInput {
            swipe(center, center + Offset(100f, 70f), durationMillis = 400)
        }
        compose.waitUntil(5_000) { runBlocking { repository.get(SettingsKeys.DEVELOPER_MENU_X).first() } != 0.5f }
        fun panelPosition(): Pair<Int, Int>? {
            var result: Pair<Int, Int>? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val params = WindowInspector.getGlobalWindowViews().firstOrNull {
                    (it.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
                }?.layoutParams as? WindowManager.LayoutParams
                result = params?.let { it.x to it.y }
            }
            return result
        }
        val draggedPosition = panelPosition()!!
        runBlocking { repository.set(SettingsKeys.DEVELOPER_FLOATING_MENU, false) }
        compose.waitUntil(5_000) { panelPosition() == null }
        runBlocking { repository.set(SettingsKeys.DEVELOPER_FLOATING_MENU, true) }
        compose.waitUntil(5_000) { panelAttachedToFocusedWindow() }
        compose.waitForIdle()
        val restoredPosition = panelPosition()!!
        assertEquals(draggedPosition.first.toFloat(), restoredPosition.first.toFloat(), 2f)
        assertEquals(draggedPosition.second.toFloat(), restoredPosition.second.toFloat(), 2f)
        // No input anywhere for seven seconds hides the native panel and releases its touch region.
        fun panelVisible(): Boolean {
            var visible = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                visible = WindowInspector.getGlobalWindowViews().any {
                    (it.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL && it.isShown
                }
            }
            return visible
        }
        compose.waitUntil(9_000) { !panelVisible() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_SHIFT_LEFT)
        compose.waitUntil(2_000) { panelVisible() }
        runBlocking { repository.set(SettingsKeys.DEVELOPER_FLOATING_MENU, false) }
        compose.waitUntil(5_000) { panelPosition() == null }
        assertEquals(bounds, compose.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Underlying action").performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, taps) }
    }
}
