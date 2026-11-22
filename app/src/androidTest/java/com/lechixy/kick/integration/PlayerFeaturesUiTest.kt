package com.lechixy.kick.integration

import android.view.TextureView
import android.view.View
import com.lechixy.kick.data.account.remote.string

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

import com.lechixy.kick.KickNavScaffold
import com.lechixy.kick.R
import com.lechixy.kick.components.ConnectionNoticeBar
import com.lechixy.kick.components.FrostedPlayerControl
import com.lechixy.kick.components.FrostedPlayerRegions
import com.lechixy.kick.components.PlayerControlsOverlay
import com.lechixy.kick.components.PlayerSleepTimerSettings
import com.lechixy.kick.components.PlayerVideoSurface
import com.lechixy.kick.components.PlayerVideoZoom
import com.lechixy.kick.components.PlayerZoomResetButton
import com.lechixy.kick.components.detectPlayerPinch
import com.lechixy.kick.components.detectRecordingTaps
import com.lechixy.kick.components.channel.ExpandableProfilePhoto
import com.lechixy.kick.components.channel.ChannelMediaTags
import com.lechixy.kick.components.channel.ChannelMediaTitle
import com.lechixy.kick.data.channel.model.ChannelEvent
import com.lechixy.kick.data.common.remote.ConnectionNotice
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.LocalBlurEffectsEnabled
import com.lechixy.kick.data.settings.repository.SettingsRepository
import com.lechixy.kick.service.player.SleepTimerState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@RunWith(AndroidJUnit4::class)
class PlayerFeaturesUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun liveDurationSitsAboveProgressAndFullscreenViewerCountFollowsLiveBadge() {
        var fullscreen by mutableStateOf(false)
        val streamDuration = "01:23:45"
        compose.setContent {
            MaterialTheme {
                AnimatedVisibility(visible = true, modifier = Modifier.size(640.dp, 380.dp)) {
                    PlayerControlsOverlay(
                        isFullScreen = fullscreen,
                        isChatVisible = true,
                        isLive = true,
                        liveViewerCount = 42,
                        isPlaying = false,
                        isMuted = false,
                        volume = 0.5f,
                        isSettingsDialogVisible = false,
                        isCastDialogVisible = false,
                        qualityLabel = "720p",
                        sleepRemainingSeconds = 0,
                        streamDuration = streamDuration,
                        positionMs = 25_000L,
                        durationMs = 60_000L,
                        onTogglePlay = {},
                        onToggleMute = {},
                        onVolumeChange = {},
                        onOpenSettings = {},
                        onOpenQuality = {},
                        onToggleFullScreen = {},
                        onMinimize = {},
                    )
                }
            }
        }

        val duration = compose.onNodeWithText(streamDuration)
        val viewers = compose.onNodeWithContentDescription(context.getString(R.string.viewers))
        val liveBadge = compose.onNodeWithText(context.getString(R.string.live_badge))
        val sliders = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        viewers.assertDoesNotExist()
        for (mode in listOf(false, true)) {
            compose.runOnIdle { fullscreen = mode }
            val progress = sliders.fetchSemanticsNodes().minBy { it.boundsInRoot.top }.boundsInRoot
            val timestamp = duration.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(timestamp.bottom <= progress.top)
            assertTrue(timestamp.left in progress.left..progress.center.x)
        }
        compose.onNodeWithContentDescription("42").performScrollTo().assertIsDisplayed()
        val badgeBounds = liveBadge.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val viewerBounds = viewers.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(badgeBounds.right <= viewerBounds.left)
        assertEquals(badgeBounds.center.y, viewerBounds.center.y, 1f)
    }

    @Test fun qualityAppearsImmediatelyBeforeCastInFullscreenAndInline() {
        var width by mutableStateOf(640.dp)
        var fullscreen by mutableStateOf(true)
        var isMature by mutableStateOf(true)
        var qualityOpens = 0
        var castOpens = 0
        compose.setContent {
            MaterialTheme {
                AnimatedVisibility(visible = true, modifier = Modifier.size(width, 380.dp)) {
                    PlayerControlsOverlay(
                        isFullScreen = fullscreen,
                        mediaInfo = { modifier ->
                            Column(modifier) {
                                ChannelMediaTitle("Stream title")
                                ChannelMediaTags(
                                    categories = listOf("Game" to "game"),
                                    tags = listOf("Ranked"),
                                    event = ChannelEvent(title = "Tournament"),
                                    language = "English",
                                    isMature = isMature,
                                )
                            }
                        },
                        isPlaying = true,
                        isMuted = false,
                        volume = 1f,
                        isSettingsDialogVisible = false,
                        isCastDialogVisible = false,
                        qualityLabel = "720p",
                        sleepRemainingSeconds = 0,
                        streamDuration = "",
                        onTogglePlay = {},
                        onToggleMute = {},
                        onVolumeChange = {},
                        onOpenSettings = {},
                        onOpenQuality = { qualityOpens++ },
                        onOpenCastDialog = { castOpens++ },
                        onToggleFullScreen = {},
                        onMinimize = {},
                    )
                }
            }
        }
        val title = compose.onNodeWithText("Stream title")
        val matureBadge = compose.onNodeWithText("+18")
        val category = compose.onNodeWithText("Game")
        val quality = compose.onNodeWithText("720p")
        val cast = compose.onNodeWithContentDescription(context.getString(R.string.player_cast))
        for (layoutWidth in listOf(640.dp, 340.dp)) {
            compose.runOnIdle { width = layoutWidth }
            title.assertIsDisplayed()
            listOf("Game", "Ranked", "Tournament", context.getString(R.string.language_english)).forEach {
                compose.onNodeWithText(it).assertIsDisplayed()
            }
            matureBadge.assertIsDisplayed()
            assertTrue(matureBadge.fetchSemanticsNode().boundsInRoot.right <= category.fetchSemanticsNode().boundsInRoot.left)
            val qualityBounds = quality.fetchSemanticsNode().boundsInRoot
            val castBounds = cast.fetchSemanticsNode().boundsInRoot
            assertTrue(qualityBounds.right <= castBounds.left)
            assertEquals(qualityBounds.center.y, castBounds.center.y, 1f)
            if (layoutWidth == 340.dp) assertTrue(title.fetchSemanticsNode().boundsInRoot.top >= castBounds.bottom)
            quality.performClick()
            cast.performClick()
        }
        compose.runOnIdle { isMature = false }
        matureBadge.assertDoesNotExist()
        compose.runOnIdle { fullscreen = false }
        val inlineQuality = quality.fetchSemanticsNode().boundsInRoot
        val inlineCast = cast.fetchSemanticsNode().boundsInRoot
        assertTrue(inlineQuality.right <= inlineCast.left)
        assertEquals(inlineQuality.center.y, inlineCast.center.y, 1f)
        quality.performClick()
        cast.performClick()
        compose.runOnIdle {
            assertEquals(3, qualityOpens)
            assertEquals(3, castOpens)
        }
    }

    @Test fun recordingSkipContinuesForEveryTapAfterTheSecond() {
        val skips = mutableListOf<Pair<Boolean, Boolean>>()
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("recording").pointerInput(Unit) {
                detectRecordingTaps(onTap = { error("Tap sequence toggled controls") }) { forward, continued ->
                    skips += forward to continued
                }
            })
        }
        compose.onNodeWithTag("recording").performTouchInput {
            repeat(4) {
                click(Offset(width * 0.75f, center.y))
                advanceEventTime(70)
            }
        }
        compose.runOnIdle { assertEquals(listOf(true to false, true to true, true to true), skips) }
    }

    @Test fun recordingPinchConsumesTheGestureAndReportsItsOffCenterFocusAndPan() {
        var zoom = 1f
        var focus = Offset.Zero
        var pan = Offset.Zero
        var pinchesEnded = 0
        compose.setContent {
            Box(
                Modifier.fillMaxSize().testTag("recording")
                    .pointerInput(Unit) {
                        detectPlayerPinch({ centroid, movement, factor ->
                            zoom *= factor
                            focus = Offset(centroid.x / size.width, centroid.y / size.height)
                            pan += Offset(movement.x / size.width, movement.y / size.height)
                        }, { pinchesEnded++ })
                    }
                    .pointerInput(Unit) {
                        detectRecordingTaps(
                            onTap = { error("Pinch toggled controls") },
                            onHoldStart = { error("Pinch started fast-forward") },
                        ) { _, _ -> error("Pinch skipped playback") }
                    },
            )
        }
        compose.onNodeWithTag("recording").performTouchInput {
            down(0, Offset(width * 0.4f, height * 0.33f))
            down(1, Offset(width * 0.8f, height * 0.33f))
            updatePointerTo(0, Offset(width * 0.3f, height * 0.38f))
            updatePointerTo(1, Offset(width * 0.9f, height * 0.38f))
            move()
            up(1)
            up(0)
        }
        compose.runOnIdle {
            assertEquals(1.5f, zoom, 0.01f)
            assertEquals(0.6f, focus.x, 0.01f)
            assertEquals(0.33f, focus.y, 0.01f)
            assertEquals(0f, pan.x, 0.01f)
            assertEquals(0.05f, pan.y, 0.01f)
            assertEquals(1, pinchesEnded)
        }
    }

    @Test fun zoomResetAppearsWhenZoomedAndRetainsItsLabelWhileExiting() {
        var zoom by mutableFloatStateOf(1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                PlayerZoomResetButton(
                    label = if (zoom > 1f) "x2" else null,
                    onReset = { zoom = 1f },
                    frame = null,
                    backdrop = null,
                    videoReady = false,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val resetButton = compose.onNodeWithText("x2")
        resetButton.assertDoesNotExist()
        compose.runOnIdle { zoom = 2f }
        compose.mainClock.advanceTimeBy(500)
        resetButton.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1f, zoom, 0f) }
        compose.mainClock.advanceTimeBy(64)
        resetButton.assertIsDisplayed()
        compose.onNodeWithText("x1").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(240)
        resetButton.assertDoesNotExist()
    }

    @Test fun recordingHoldStartsOnEitherSideAndEndsOnRelease() {
        val holds = AtomicInteger()
        val releases = AtomicInteger()
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("recording").pointerInput(Unit) {
                detectRecordingTaps(
                    onTap = { error("Hold toggled controls") },
                    onHoldStart = { holds.incrementAndGet() },
                    onHoldEnd = { releases.incrementAndGet() },
                ) { _, _ -> error("Hold skipped playback") }
            })
        }
        val recording = compose.onNodeWithTag("recording")
        for ((index, side) in listOf(0.2f, 0.8f).withIndex()) {
            recording.performTouchInput { down(Offset(width * side, center.y)) }
            compose.waitUntil(timeoutMillis = 5_000) { holds.get() == index + 1 }
            assertEquals(index, releases.get())
            recording.performTouchInput { up() }
            compose.runOnIdle { assertEquals(index + 1, releases.get()) }
        }
    }

    @Test fun sleepTimerOffersStopAndUpdatesOnlyWhenTheNewDurationIsApplied() {
        var timer by mutableStateOf(SleepTimerState())
        val start = context.getString(R.string.player_sleep_timer_start)
        val stop = context.getString(R.string.player_sleep_timer_stop)
        val update = context.getString(R.string.player_sleep_timer_update)
        compose.setContent {
            MaterialTheme {
                PlayerSleepTimerSettings(timer, lastMinutes = 15, enabled = true) { minutes ->
                    timer = SleepTimerState(minutes, minutes * 60)
                }
            }
        }
        compose.onNodeWithText(start).performClick()
        compose.onNodeWithText(stop).assertIsDisplayed()
        compose.onNodeWithText(start).assertDoesNotExist()
        compose.onNodeWithText(update).assertDoesNotExist()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(25f) }
        compose.onNodeWithText(update).assertIsDisplayed()
        compose.runOnIdle { assertEquals(15, timer.selectedMinutes) }
        compose.onNodeWithText(update).performClick()
        compose.runOnIdle { assertEquals(25, timer.selectedMinutes) }
        compose.onNodeWithText(stop).performClick()
        compose.onNodeWithText(start).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, timer.remainingSeconds) }
    }

    @Test fun endOfRecordingRemainsASeparateClickableOptionBesideTheMinutes() {
        val end = context.getString(R.string.player_sleep_end_recording)
        val duration = context.getString(R.string.player_minutes, 30)
        var selected = false
        compose.setContent {
            MaterialTheme {
                PlayerSleepTimerSettings(SleepTimerState(), lastMinutes = 30, enabled = true,
                    mediaEndLabel = end, onMediaEnd = { selected = true }, onApply = {})
            }
        }
        val minutes = compose.onNodeWithContentDescription(duration).assertIsDisplayed()
        val button = compose.onNodeWithText(end).assertIsDisplayed()
        assertTrue(button.fetchSemanticsNode().boundsInRoot.left > minutes.fetchSemanticsNode().boundsInRoot.right)
        button.performClick()
        compose.runOnIdle { assertTrue(selected) }
    }

    @Test fun togglingBlurRestoresTheControlRegionWithoutMovingTheControl() {
        val backdrop = FrostedPlayerRegions()
        var enabled by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalBlurEffectsEnabled provides enabled) {
                MaterialTheme {
                    Box(Modifier.size(48.dp)) {
                        FrostedPlayerControl(frame = null, backdrop = backdrop, videoReady = true)
                    }
                }
            }
        }
        val initial = compose.runOnIdle { backdrop.regions.values.single().bounds }
        compose.runOnIdle { enabled = false }
        compose.runOnIdle { assertTrue(backdrop.regions.isEmpty()) }
        compose.runOnIdle { enabled = true }
        compose.runOnIdle { assertEquals(initial, backdrop.regions.values.single().bounds) }
    }

    @Test fun zoomScalesTheViewWithoutResizingItsTexture() {
        var texture: TextureView? = null
        var zoom by mutableStateOf(PlayerVideoZoom())
        compose.setContent {
            Box(Modifier.size(200.dp).clipToBounds()) {
                PlayerVideoSurface(null, false, Modifier.fillMaxSize(), zoom = { zoom }) { texture = it }
            }
        }
        val originalWidth = compose.runOnIdle { requireNotNull(texture).width }
        compose.runOnIdle { zoom = PlayerVideoZoom(4f) }
        compose.runOnIdle {
            val surface = requireNotNull(texture)
            assertTrue(originalWidth > 0)
            assertEquals(originalWidth, surface.width)
            assertTrue(generateSequence(surface as View) { it.parent as? View }.any { it.scaleX == 4f })
        }
    }

    @Test fun scaffoldUpdatesKeepPlayerContentAlive() {
        var hidden by mutableStateOf(false)
        var revision by mutableStateOf(0)
        val disposals = AtomicInteger()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(context.cacheDir, "player-scaffold-${System.nanoTime()}.preferences_pb")
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        try {
            compose.setContent {
                CompositionLocalProvider(LocalSettingsRepository provides settings) {
                    MaterialTheme {
                        KickNavScaffold(hidden = hidden, currentRoute = "home", onNavigate = {}) {
                            DisposableEffect(Unit) { onDispose { disposals.incrementAndGet() } }
                            Text("Player revision $revision")
                        }
                    }
                }
            }
            repeat(3) { index ->
                compose.runOnIdle { hidden = !hidden; revision++ }
                compose.onNodeWithText("Player revision ${index + 1}").assertIsDisplayed()
                compose.runOnIdle { assertEquals(0, disposals.get()) }
            }
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test fun avatarTapRunsItsActionAndLongPressOpensConnectedPreview() {
        var taps = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    ExpandableProfilePhoto("android.resource://com.lechixy.kick/drawable/ic_launcher_foreground",
                        "Test avatar", Modifier.size(56.dp), imagePadding = 4.dp, onTap = { taps++ })
                }
            }
        }
        compose.onNodeWithContentDescription("Test avatar").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, taps) }
        compose.onNodeWithContentDescription("Test avatar").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, taps) }
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
