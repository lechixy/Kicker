package com.lechixy.kick.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.components.channel.AnimatedCounterText
import com.lechixy.kick.components.channel.ChannelMediaTags
import com.lechixy.kick.components.channel.ChannelMediaTitle
import com.lechixy.kick.components.channel.chat.FullscreenChatBackdrop
import com.lechixy.kick.components.channel.chat.FullscreenChatBackdropRegion
import com.lechixy.kick.components.channel.chat.FullscreenChatBlur
import com.lechixy.kick.components.channel.chat.FullscreenChatEdges
import com.lechixy.kick.components.channel.chat.FullscreenChatOverlay
import com.lechixy.kick.components.channel.chat.FullscreenChatSettings
import com.lechixy.kick.components.channel.chat.FullscreenChatVideoFrame
import com.lechixy.kick.components.channel.chat.VideoBackdropBlur
import com.lechixy.kick.components.channel.chat.rememberFullscreenChatPreferences
import com.lechixy.kick.components.channel.chat.rememberLegacyFullscreenChatFrame
import com.lechixy.kick.components.settings.AmbientModeSettings
import com.lechixy.kick.components.settings.SettingSwitch
import com.lechixy.kick.components.settings.titleResource
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.getBannerUrl
import com.lechixy.kick.data.chat.model.recordingStartInstant
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.player.local.PlaybackProgressStore
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.player.remote.ViewerSession
import com.lechixy.kick.data.settings.model.FullscreenChatBlurStyle
import com.lechixy.kick.data.settings.model.PlayerAmbientMode
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.screens.channel.ChannelViewModel
import com.lechixy.kick.screens.clip.CreateClipModal
import com.lechixy.kick.service.EXTRA_LIVE_DVR
import com.lechixy.kick.service.EXTRA_LOW_LATENCY
import com.lechixy.kick.service.PlaybackService
import com.lechixy.kick.service.buildPlaybackMediaItem
import com.lechixy.kick.service.player.StreamVideoQuality
import com.lechixy.kick.service.player.broadcastPositionMs
import com.lechixy.kick.service.player.distanceBehindLiveTargetMs
import com.lechixy.kick.service.player.isNearLiveTarget
import com.lechixy.kick.service.player.setPlaybackUsageChannel
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.FormatUtils.formatDuration
import com.lechixy.kick.util.FormatUtils.formatLiveOffset
import com.lechixy.kick.util.FormatUtils.parseUtcStartTimeToMillis
import com.lechixy.kick.util.buildOptimizedImageRequest
import com.lechixy.kick.util.findActivity
import com.lechixy.kick.util.player.CastUtils
import com.lechixy.kick.util.player.NotificationUtils
import com.lechixy.kick.util.player.PlayerUtils
import com.lechixy.kick.util.player.VideoQualityOption
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.geometry.Rect as ComposeRect

private data class ClipTarget(val videoId: String?, val positionSeconds: Int, val liveSession: ViewerSession?)

@SuppressLint("UseKtx")
@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(FlowPreview::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KickPlayerView(
    playbackUrl: String,
    previewThumbnailUrl: String? = null,
    channelName: String = "",
    streamTitle: String = "",
    channel: ChannelDetail? = null,
    recording: RecordedPlayback? = null,
    isFullScreen: Boolean = false,
    isTheaterMode: Boolean = false,
    isChatVisible: Boolean = true,
    onToggleChat: () -> Unit = {},
    isInPipMode: Boolean = false,
    isMini: Boolean = false,
    hideBufferingIndicator: Boolean = false,
    onPlaybackPositionChanged: (Long) -> Unit = {},
    pendingAutoFullscreen: Boolean = false,
    onConsumeAutoFullscreen: (Boolean) -> Unit = {},
    onFullScreenChange: (Boolean) -> Unit = {},
    onToggleTheater: () -> Unit = {},
    onMinimize: () -> Unit = {},
    onClose: () -> Unit = {},
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    onPlayerCoordinatesChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
    onPlaybackSeek: (Long) -> Unit = {},
    playerHost: PlayerHostState? = null,
    liveChatViewModel: ChannelViewModel? = null,
    fullscreenChatOverlayEnabled: Boolean = false,
    onToggleFullscreenChatOverlay: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewersLocale = LocalConfiguration.current.locales[0]
    val activity = remember(context) { context.findActivity() }

    val player by rememberMediaController()

    val sessionId = remember(channel?.slug, playbackUrl, if (recording == null) channel?.livestream?.id else recording.id) {
        val baseId = channel?.slug ?: playbackUrl
        val liveId = if (recording == null) channel?.livestream?.id else null
        if (recording != null) "${baseId}_recording_${recording.id}" else if (liveId != null) "${baseId}_$liveId" else baseId
    }
    var dvrUrl by remember(sessionId) { mutableStateOf<String?>(null) }
    var dvrPlayback by remember(sessionId) { mutableStateOf<LiveDvrPlayback?>(null) }
    val playingDvr = dvrPlayback != null
    val stableId = if (playingDvr) "${sessionId}_dvr" else sessionId
    val activePlaybackUrl = dvrPlayback?.url ?: playbackUrl
    val liveStartMs = if (recording == null) {
        parseUtcStartTimeToMillis(channel?.livestream?.startTime ?: channel?.livestream?.createdAt)
    } else 0L
    LaunchedEffect(sessionId) {
        val detail = channel ?: return@LaunchedEffect
        val livestream = detail.livestream ?: return@LaunchedEffect
        if (recording != null || liveStartMs <= 0L) return@LaunchedEffect
        try {
            dvrUrl = com.lechixy.kick.data.common.repository.KickRepository(
                com.lechixy.kick.data.common.remote.KickNetwork.api,
            ).getLiveDvrUrl(detail.slug, livestream.vodId)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The primary live stream can still use its own seekable window.
        }
    }

    var hasRenderedFirstFrame by remember(stableId) { mutableStateOf(false) }
    var ambientFirstFrame by remember(stableId) { mutableIntStateOf(0) }
    var ambientSeek by remember(stableId) { mutableStateOf<AmbientSeek?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var loadedMediaId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember(player, stableId) { mutableStateOf(false) }
    val playOnLoad = dvrPlayback?.playWhenReady ?: true
    var playRequested by remember(player, stableId) { mutableStateOf(playOnLoad) }
    var playbackError by remember(stableId) { mutableStateOf<String?>(null) }
    var liveRetryTrigger by remember(stableId) { mutableIntStateOf(0) }
    var isSeeking by remember(stableId) { mutableStateOf(false) }
    var skipFeedback by remember(stableId, isMini, isInPipMode) { mutableStateOf<RecordingSkipFeedback?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var isAdjustingVolume by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var clipTarget by remember(stableId) { mutableStateOf<ClipTarget?>(null) }
    var settingsPage by remember { mutableStateOf(PlayerSettingsPage.MAIN) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var playerTexture by remember { mutableStateOf<TextureView?>(null) }
    var ambientCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val hostedAmbient = LocalPlayerAmbientState.current
    val ambient = hostedAmbient ?: remember { PlayerAmbientState() }
    val fullscreenProgress = LocalPlayerFullscreenProgress.current
    var chatBlurBounds by remember { mutableStateOf<FullscreenChatBackdropRegion?>(null) }
    val controlBlur = remember { FrostedPlayerRegions() }
    val updateChatBlurBounds: (FullscreenChatBackdropRegion?) -> Unit = remember { { chatBlurBounds = it } }
    val downloadRecording = rememberRecordingDownloadAction()
    val sleepTimer by com.lechixy.kick.service.player.SleepTimer.state.collectAsStateWithLifecycle()
    val sleepTimerMinutes = sleepTimer.selectedMinutes
    val remainingTimerSeconds = sleepTimer.remainingSeconds

    val settingsRepository = LocalSettingsRepository.current
    val ambientModeValue by remember(settingsRepository) { settingsRepository.get(SettingsKeys.AMBIENT_MODE) }
        .collectAsStateWithLifecycle(SettingsKeys.AMBIENT_MODE.defaultValue)
    val selectedAmbientMode = PlayerAmbientMode.fromValue(ambientModeValue)
    val ambientBatterySaver by remember(settingsRepository) { settingsRepository.get(SettingsKeys.AMBIENT_BATTERY_SAVER) }
        .collectAsStateWithLifecycle(SettingsKeys.AMBIENT_BATTERY_SAVER.defaultValue)
    val ambientMode = if (selectedAmbientMode == PlayerAmbientMode.Live &&
        ambientBatterySaver && !isInPipMode && rememberAmbientPowerSaveMode()
    ) PlayerAmbientMode.Dynamic else selectedAmbientMode
    val ambientFpsValue by remember(settingsRepository) { settingsRepository.get(SettingsKeys.AMBIENT_LIVE_FPS) }
        .collectAsStateWithLifecycle(SettingsKeys.AMBIENT_LIVE_FPS.defaultValue)
    val ambientFps = if (ambientFpsValue == 60) 60 else 30
    val ambientIntensity by remember(settingsRepository) { settingsRepository.get(SettingsKeys.AMBIENT_INTENSITY) }
        .collectAsStateWithLifecycle(SettingsKeys.AMBIENT_INTENSITY.defaultValue)
    val ambientSpread by remember(settingsRepository) { settingsRepository.get(SettingsKeys.AMBIENT_SPREAD) }
        .collectAsStateWithLifecycle(SettingsKeys.AMBIENT_SPREAD.defaultValue)
    val ambientFullscreen by remember(settingsRepository) { settingsRepository.get(SettingsKeys.AMBIENT_FULLSCREEN) }
        .collectAsStateWithLifecycle(SettingsKeys.AMBIENT_FULLSCREEN.defaultValue)
    val ambientThroughUi by remember(settingsRepository) { settingsRepository.get(SettingsKeys.AMBIENT_THROUGH_UI) }
        .collectAsStateWithLifecycle(SettingsKeys.AMBIENT_THROUGH_UI.defaultValue)
    val ambientChatOpacity by remember(settingsRepository) { settingsRepository.get(SettingsKeys.AMBIENT_CHAT_OPACITY) }
        .collectAsStateWithLifecycle(SettingsKeys.AMBIENT_CHAT_OPACITY.defaultValue)
    val ambientNavigationOpacity by remember(settingsRepository) { settingsRepository.get(SettingsKeys.AMBIENT_NAVIGATION_OPACITY) }
        .collectAsStateWithLifecycle(SettingsKeys.AMBIENT_NAVIGATION_OPACITY.defaultValue)
    val preserveBlackBars by remember(settingsRepository) { settingsRepository.get(SettingsKeys.AMBIENT_PRESERVE_BLACK_BARS) }
        .collectAsStateWithLifecycle(SettingsKeys.AMBIENT_PRESERVE_BLACK_BARS.defaultValue)
    androidx.compose.runtime.SideEffect {
        ambient.intensity = ambientIntensity.coerceIn(SettingsKeys.AMBIENT_INTENSITY_RANGE) / 100f
        ambient.spread = ambientSpread.coerceIn(SettingsKeys.AMBIENT_SPREAD_RANGE) / 100f
        ambient.fullscreenEnabled = ambientFullscreen
        ambient.throughUi = ambientThroughUi
        ambient.chatOpacity = ambientChatOpacity.coerceIn(0, 100) / 100f
        ambient.navigationOpacity = ambientNavigationOpacity.coerceIn(0, 100) / 100f
    }
    val overlaySupported = playerHost != null && channel != null && if (recording == null) {
        liveChatViewModel != null
    } else {
        recording.directUrl == null && recordingStartInstant(recording.startTime) != null
    }
    val fullscreenChatAvailable = overlaySupported && isFullScreen && !isInPipMode
    val overlayPreferences = if (fullscreenChatAvailable) rememberFullscreenChatPreferences() else null
    val editingChatPosition = fullscreenChatAvailable && playerHost.isEditingFullscreenChatPosition
    val overlayHasOpacity by remember(playerHost) {
        derivedStateOf { (playerHost?.fullscreenChatOverlayAlpha ?: 0f) > 0f }
    }
    LaunchedEffect(fullscreenChatAvailable) {
        if (!fullscreenChatAvailable) playerHost?.editFullscreenChatPosition(false)
    }
    val lastSleepTimerMinutes by settingsRepository.get(SettingsKeys.LAST_SLEEP_TIMER_MINUTES)
        .collectAsStateWithLifecycle(SettingsKeys.LAST_SLEEP_TIMER_MINUTES.defaultValue)
    val syncVolume by settingsRepository.get(SettingsKeys.SYNC_PLAYER_VOLUME_WITH_DEVICE)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.SYNC_PLAYER_VOLUME_WITH_DEVICE.defaultValue)
    val playerVolume = rememberPlayerVolume(syncVolume)
    val isMuted = playerVolume.muted
    val volume = playerVolume.volume
    val playerGain = playerVolume.playerGain
    val coroutineScope = rememberCoroutineScope()
    val preferredQuality by settingsRepository
        .get(SettingsKeys.VIDEO_QUALITY)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.VIDEO_QUALITY.defaultValue)

    var selectedQualityHeight by remember { mutableIntStateOf(preferredQuality) }
    val availableQualities = remember { mutableStateListOf<VideoQualityOption>() }
    var currentVideoHeight by remember(player, stableId) { mutableIntStateOf(0) }

    LaunchedEffect(preferredQuality) {
        selectedQualityHeight = preferredQuality
    }

    val compactQualityLimit = if (isMini || isInPipMode) {
        compactVideoHeightLimit(viewport.width, selectedQualityHeight)
    } else {
        null
    }
    LaunchedEffect(selectedQualityHeight, compactQualityLimit) {
        // Let the mini-player or PiP resize settle before requesting a new quality.
        if (compactQualityLimit != null) delay(150)
        StreamVideoQuality.current = StreamVideoQuality(selectedQualityHeight, compactQualityLimit)
    }

    val latestIsFullScreen by rememberUpdatedState(isFullScreen)
    val latestOnFullScreenChange by rememberUpdatedState(onFullScreenChange)
    val toggleFullScreen: () -> Unit = remember {
        { latestOnFullScreenChange(!latestIsFullScreen) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && com.lechixy.kick.service.player.SleepTimer.state.value.remainingSeconds > 0) {
            NotificationUtils.updateTimerNotification(context, com.lechixy.kick.service.player.SleepTimer.state.value.remainingSeconds)
        }
    }

    var streamDuration by remember { mutableStateOf("00:00:00") }
    val startTimeString = channel?.livestream?.startTime // "2026-10-09 16:29:59"

    LaunchedEffect(startTimeString) {
        val startMillis = parseUtcStartTimeToMillis(startTimeString)

        if (startMillis <= 0L) {
            streamDuration = "00:00:00"
            return@LaunchedEffect
        }

        while (isActive) {
            val currentMillis = System.currentTimeMillis()
            val elapsedMillis = (currentMillis - startMillis).coerceAtLeast(0L)

            streamDuration = formatDuration(elapsedMillis)

            delay(1000.milliseconds)
        }
    }

    val pipEnabled by settingsRepository
        .get(SettingsKeys.PIP_ENABLED)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.PIP_ENABLED.defaultValue)
    val lowLatencySetting by settingsRepository
        .get(SettingsKeys.LOW_LATENCY)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.LOW_LATENCY.defaultValue)
    val autoFullscreenLive by settingsRepository
        .get(SettingsKeys.AUTO_FULLSCREEN_LIVE)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.AUTO_FULLSCREEN_LIVE.defaultValue)
    var showCastDialog by remember { mutableStateOf(false) }
    var playerBounds by remember { mutableStateOf<Rect?>(null) }

    val clipOwnsAudio = com.lechixy.kick.service.ClipAudioHandoff.active
    val clipModalOpen = clipTarget != null
    LaunchedEffect(isPlaying, pipEnabled, activity, isInPipMode, clipOwnsAudio, clipModalOpen) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || activity == null || isInPipMode) {
            return@LaunchedEffect
        }
        if (clipOwnsAudio || clipModalOpen) {
            activity.updatePlayerPipParams(buildPipParams(autoEnter = false, sourceRect = null))
            return@LaunchedEffect
        }
        snapshotFlow { playerBounds }
            .debounce(150.milliseconds)
            .collect { bounds ->
                activity.updatePlayerPipParams(
                    buildPipParams(autoEnter = isPlaying && pipEnabled, sourceRect = bounds)
                )
            }
    }

    DisposableEffect(activity) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity?.updatePlayerPipParams(
                    buildPipParams(
                        autoEnter = false,
                        sourceRect = null
                    )
                )
            }
        }
    }

    DisposableEffect(isPlaying) {
        if (isPlaying) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(player, playerGain) {
        player?.volume = playerGain
    }

    LaunchedEffect(showControls, isAdjustingVolume, isSeeking) {
        if (showControls && !isAdjustingVolume && !isSeeking) {
            delay(3500.milliseconds)
            showControls = false
        }
    }

    LaunchedEffect(showControls) {
        onControlsVisibilityChanged(showControls)
    }

    LaunchedEffect(isMini) { if (isMini) showControls = false }

    var liveOffsetMs by remember(stableId) { mutableLongStateOf(C.TIME_UNSET) }
    var isAtLiveEdge by remember(stableId) { mutableStateOf(false) }
    val isLiveStream = recording == null && (channel?.livestream != null || player?.isCurrentMediaItemLive == true)
    val clipLiveSession = channel?.livestream?.takeIf { recording == null }?.let {
        ViewerSession(channel.id, it.id, it.vodId?.takeIf(String::isNotBlank), channel.slug)
    }
    val clipVideoId = recording?.vodId ?: clipLiveSession?.vodId
    val lowLatencyEnabled = isLiveStream && !playingDvr && lowLatencySetting
    val latestIsLiveStream by rememberUpdatedState(isLiveStream)
    var positionMs by remember(stableId) { mutableLongStateOf(0L) }
    var durationMs by remember(stableId) { mutableLongStateOf(recording?.durationMs ?: 0L) }
    var liveSeekWindow by remember(stableId) { mutableStateOf<LiveSeekWindow?>(null) }
    val positionCallback by rememberUpdatedState(onPlaybackPositionChanged)
    val seekCallback by rememberUpdatedState(onPlaybackSeek)
    val skipRecording: (Boolean, Boolean) -> Unit = { forward, continueSequence ->
        val active = player?.takeIf {
            it.currentMediaItem?.mediaId == stableId && it.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                it.isCurrentMediaItemSeekable && it.duration > 0L
        }
        if (active != null) {
            val delta = if (forward) 10_000L else -10_000L
            active.seekTo((active.currentPosition + delta).coerceIn(0L, active.duration))
            val now = SystemClock.uptimeMillis()
            val previous = skipFeedback?.takeIf {
                continueSequence && it.forward == forward && now - it.atMillis <= RECORDING_SKIP_SEQUENCE_TIMEOUT_MS
            }
            skipFeedback = RecordingSkipFeedback(forward, (previous?.seconds ?: 0) + 10, now)
        }
    }
    val onRecordingTap by rememberUpdatedState(skipRecording)
    val onLiveDoubleTap by rememberUpdatedState(toggleFullScreen)
    LaunchedEffect(player, stableId, isLiveStream) {
        if (isLiveStream) return@LaunchedEffect
        while (isActive) {
            player?.takeIf { it.currentMediaItem?.mediaId == stableId }?.let { current ->
                positionMs = current.currentPosition.coerceAtLeast(0)
                positionCallback(positionMs)
                if (current.duration > 0 && current.duration != C.TIME_UNSET) durationMs = current.duration
            }
            delay(250.milliseconds)
        }
    }

    LaunchedEffect(player, stableId, isLiveStream, dvrUrl, liveStartMs) {
        val p = player ?: return@LaunchedEffect
        if (!isLiveStream) {
            isAtLiveEdge = true
            liveOffsetMs = 0L
            return@LaunchedEffect
        }

        while (isActive) {
            val active = p.takeIf { it.currentMediaItem?.mediaId == stableId }
            val nativeOffset = active?.distanceBehindLiveTargetMs() ?: C.TIME_UNSET
            val elapsed = (System.currentTimeMillis() - liveStartMs).coerceAtLeast(0L)
            val dvrPosition = if (playingDvr) active?.currentPosition ?: dvrPlayback?.positionMs ?: 0L
                else (elapsed - nativeOffset.coerceAtLeast(0L)).coerceAtLeast(0L)
            val currentOffset = if (playingDvr) (elapsed - dvrPosition).coerceAtLeast(0L) else nativeOffset
            if (showControls || isSeeking) {
                liveSeekWindow = if (active != null && dvrUrl != null && elapsed > DVR_LIVE_GAP_MS) {
                    LiveSeekWindow(C.TIME_UNSET, elapsed, dvrPosition, usesDvr = true)
                } else active?.liveSeekWindow()
            }

            liveOffsetMs = currentOffset
            isAtLiveEdge = p.playWhenReady && p.playbackState == Player.STATE_READY &&
                isNearLiveTarget(currentOffset, isAtLiveEdge)

            delay(250.milliseconds)
        }
    }

    /**
     * Player listener
     */
    DisposableEffect(player, stableId) {
        val p = player
        if (p == null) {
            onDispose { }
        } else {
            fun refreshQualities(tracks: Tracks) {
                availableQualities.clear()
                availableQualities.addAll(tracks.toQualityOptions())
            }

            fun refreshPlaybackControls() {
                val isCurrentMedia = p.currentMediaItem?.mediaId == stableId
                isPlaying = isCurrentMedia && p.isPlaying
                playRequested = if (isCurrentMedia) p.playWhenReady else playOnLoad
            }

            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    // A new item can inherit playWhenReady without firing its change callback.
                    refreshPlaybackControls()
                    if (isPlaying) playbackError = null
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playingDvr && p.currentMediaItem?.mediaId == stableId && playbackState == Player.STATE_ENDED) {
                        dvrPlayback = null
                        return
                    }
                    if (p.currentMediaItem?.mediaId != stableId) return
                    val live = latestIsLiveStream
                    isBuffering = playbackState == Player.STATE_BUFFERING ||
                            (live && (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE))
                    isPlayerReady = playbackState == Player.STATE_READY
                    if (playbackState == Player.STATE_READY && p.playWhenReady) {
                        playbackError = null
                    } else if (live && playbackState == Player.STATE_ENDED) {
                        liveRetryTrigger++
                    }
                    loadedMediaId = p.currentMediaItem?.mediaId
                }

                // READY and isPlaying can arrive before the texture contains a frame.
                // Only the renderer event may uncover the video beneath the preview.
                override fun onRenderedFirstFrame() {
                    if (p.currentMediaItem?.mediaId != stableId) return
                    hasRenderedFirstFrame = true
                    ambientFirstFrame++
                    isPlayerReady = true
                    playbackError = null
                    loadedMediaId = p.currentMediaItem?.mediaId
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (!latestIsLiveStream && p.currentMediaItem?.mediaId == stableId &&
                        (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)
                    ) {
                        ambientSeek = AmbientSeek(
                            sequence = (ambientSeek?.sequence ?: 0) + 1,
                            previousTimestamp = playerTexture?.surfaceTexture?.timestamp ?: 0L,
                        )
                        positionMs = newPosition.positionMs.coerceAtLeast(0L)
                        seekCallback(positionMs)
                    }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (p.currentMediaItem?.mediaId == stableId) currentVideoHeight = videoSize.height
                }

                override fun onTracksChanged(tracks: Tracks) {
                    refreshQualities(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (p.currentMediaItem?.mediaId != stableId) return
                    if (playingDvr) {
                        dvrPlayback = null
                        dvrUrl = null
                        Toast.makeText(context, R.string.player_dvr_unavailable, Toast.LENGTH_SHORT).show()
                        return
                    }
                    val live = latestIsLiveStream
                    if (live) {
                        playbackError = null
                        isBuffering = true
                        isPlayerReady = false
                        liveRetryTrigger++
                    } else {
                        playbackError = error.message ?: "Oynatma hatası"
                        isBuffering = false
                        isPlayerReady = false
                    }
                }
            }

            val isSameMedia = p.currentMediaItem?.mediaId == stableId
            currentVideoHeight = if (isSameMedia) p.videoSize.height else 0
            refreshPlaybackControls()
            isBuffering = !isSameMedia || p.playbackState == Player.STATE_BUFFERING
            isPlayerReady = isSameMedia && p.playbackState == Player.STATE_READY
            loadedMediaId = p.currentMediaItem?.mediaId
            refreshQualities(p.currentTracks)

            p.addListener(listener)
            onDispose { p.removeListener(listener) }
        }
    }

    LaunchedEffect(player, stableId, playbackUrl, liveRetryTrigger, isLiveStream) {
        val p = player ?: return@LaunchedEffect
        if (liveRetryTrigger <= 0 || !isLiveStream) return@LaunchedEffect
        delay(2000.milliseconds)
        if (isActive && latestIsLiveStream && p.currentMediaItem?.mediaId == stableId && p.playbackState != Player.STATE_READY) {
            isBuffering = true
            playbackError = null
            p.seekToDefaultPosition()
            p.prepare()
            p.play()
        }
    }

    LaunchedEffect(player, stableId, activePlaybackUrl, lowLatencyEnabled) {
        val p = player ?: return@LaunchedEffect
        val requestedDvr = dvrPlayback
        if (activePlaybackUrl.isBlank()) return@LaunchedEffect

        playbackError = null

        val currentItem = p.currentMediaItem
        // Reloading channel metadata can rotate the signed HLS URL without changing
        // the broadcast. Keep a healthy session running when returning from mini mode.
        // A failed/idle session may still adopt the refreshed URL to recover playback.
        val keepLiveSession = isLiveStream && p.isCurrentMediaItemLive && p.playerError == null &&
                p.playbackState == Player.STATE_READY
        val alreadyLoaded = currentItem?.mediaId == stableId &&
                (keepLiveSession || currentItem.localConfiguration?.uri?.toString() == activePlaybackUrl) &&
                (!isLiveStream ||
                    (currentItem.mediaMetadata.extras?.getBoolean(EXTRA_LOW_LATENCY) == true) == lowLatencyEnabled)
        if (alreadyLoaded) {
            // ViewerTrackingClient resolves missing VOD metadata independently. Updating
            // tracking metadata must not replace the media item of an active HLS stream.
            loadedMediaId = stableId
            isPlayerReady = p.playbackState == Player.STATE_READY
            if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
                p.seekToDefaultPosition()
                p.prepare()
                p.play()
            }
            return@LaunchedEffect
        }

        hasRenderedFirstFrame = false
        isBuffering = true
        isPlayerReady = false
        loadedMediaId = stableId
        val pic: String = (recording?.thumbnail ?: channel?.getBannerUrl() ?: channel?.user?.profilePic
        ?: "https://placehold.co/600x400/EEE/31343C?font=open-sans&text=No%20banner")

        val isLive = isLiveStream
        val historyEntry = channel?.let { detail ->
            com.lechixy.kick.data.player.local.WatchHistoryEntry(
                key = recording?.let { "recording:${it.progressId}" } ?: "live:${detail.slug}:${detail.livestream?.id}",
                channelSlug = detail.slug, channelName = channelName, title = streamTitle,
                thumbnail = recording?.thumbnail ?: detail.livestream?.thumbnail?.url ?: pic,
                recording = recording?.copy(directUrl = null),
                livestreamId = if (recording == null) detail.livestream?.id else null,
                vodId = if (recording == null) detail.livestream?.vodId else null,
                startTime = recording?.startTime ?: detail.livestream?.startTime ?: detail.livestream?.createdAt)
        }
        val baseMediaItem = buildPlaybackMediaItem(
            playbackUrl = activePlaybackUrl,
            mediaId = stableId,
            isLive = isLive,
            title = streamTitle.ifBlank { if (isLiveStream) "Canlı Yayın" else "Kayıt" },
            channelName = channelName.ifBlank { "Kick" },
            viewersText = (if (isLiveStream) channel?.livestream?.viewerCount else null)
                ?.let {
                    "${FormatUtils.formatViewersCount(it, viewersLocale)} ${
                        resources.getString(
                            R.string.viewers
                        )
                    }"
                },
            artwork = pic.toUri(),
            startTimeMs = parseUtcStartTimeToMillis(if (recording != null) recording.startTime else channel?.livestream?.startTime ?: channel?.livestream?.createdAt),
            vodDurationMs = recording?.durationMs,
            recordingId = recording?.progressId,
            viewerSession = if (isLive && channel?.livestream != null)
                com.lechixy.kick.data.player.remote.ViewerSession(channel.id, channel.livestream.id, channel.livestream.vodId, channel.slug)
                else null,
            isLowLatency = lowLatencyEnabled
        )

        val extras = android.os.Bundle(baseMediaItem.mediaMetadata.extras ?: android.os.Bundle()).apply {
            if (playingDvr) putBoolean(EXTRA_LIVE_DVR, true)
            historyEntry?.let { setPlaybackUsageChannel(it.channelSlug, it.channelName, channel.user?.profilePic) }
            historyEntry?.let { putString(com.lechixy.kick.data.player.local.EXTRA_WATCH_HISTORY,
                kotlinx.serialization.json.Json.encodeToString(it)) }
        }
        val mediaItem = baseMediaItem.buildUpon().setMediaMetadata(
            baseMediaItem.mediaMetadata.buildUpon().setExtras(extras).build()).build()

        if (recording != null) {
            val resumePosition = PlaybackProgressStore.get(recording.progressId)?.let { saved ->
                saved.resumePosition(recording.durationMs.takeIf { it > 0 } ?: saved.durationMs)
            } ?: 0L
            p.setMediaItem(mediaItem, resumePosition)
        } else if (requestedDvr != null) {
            p.setMediaItem(mediaItem, requestedDvr.positionMs)
        } else {
            p.setMediaItem(mediaItem)
        }
        p.prepare()
        p.playWhenReady = playOnLoad
    }

    LaunchedEffect(
        pendingAutoFullscreen,
        autoFullscreenLive,
        isPlayerReady,
        loadedMediaId,
        stableId,
        channel?.livestream,
        isMini,
        isInPipMode
    ) {
        if (
            pendingAutoFullscreen &&
            isLiveStream &&
            isPlayerReady &&
            loadedMediaId == stableId &&
            !isMini &&
            !isInPipMode
        ) {
            onConsumeAutoFullscreen(autoFullscreenLive)
        }
    }

    val showPlaybackControls = !isInPipMode && showControls && playbackError == null && !isMini && !editingChatPosition
    val controlsTransition = updateTransition(showPlaybackControls, label = "playback controls")
    val controlsOpacity = controlsTransition.animateFloat(
        transitionSpec = { tween(if (targetState) 200 else 160) },
        label = "playback controls opacity",
    ) { if (it) 1f else 0f }
    val backdropTexture = playerTexture.takeIf {
        !isMini && !isInPipMode && hasRenderedFirstFrame && loadedMediaId == stableId && playbackError == null
    }
    val chatBlurStyle = overlayPreferences?.blurStyle.takeIf {
        overlayPreferences?.blur == true &&
            ((fullscreenChatOverlayEnabled && overlayHasOpacity) || editingChatPosition)
    }
    val visibleChatBlurStyle = chatBlurStyle.takeIf { chatBlurBounds != null }
    val legacyBackdrop = if (Build.VERSION.SDK_INT < 31) {
        rememberLegacyFullscreenChatFrame(
            backdropTexture,
            isPlaying,
            soft = visibleChatBlurStyle == FullscreenChatBlurStyle.Soft,
            frosted = controlBlur.regions.isNotEmpty() || visibleChatBlurStyle == FullscreenChatBlurStyle.FrostedGlass,
            mediaKey = stableId to activePlaybackUrl,
        )
    } else null
    val trackAmbient = ambientMode != PlayerAmbientMode.Off && !isMini && !isInPipMode
    if (trackAmbient) {
        TrackAmbientVideoLayout(ambient, playerTexture) { ambientCoordinates }
    }
    val fullscreenSettled by remember(isFullScreen, fullscreenProgress) {
        derivedStateOf { isFullScreen && (fullscreenProgress?.invoke() ?: 1f) >= 1f }
    }
    val ambientVisible = trackAmbient && hasRenderedFirstFrame && loadedMediaId == stableId &&
        playbackError == null && !clipModalOpen && (!fullscreenSettled || ambientFullscreen)
    val useHostBackdrop = hostedAmbient != null && playerHost?.expansionSource == null
    CapturePlayerAmbient(
        state = ambient,
        texture = playerTexture,
        mode = ambientMode,
        liveFps = ambientFps,
        visible = ambientVisible,
        playing = isPlaying,
        mediaId = stableId,
        mediaUrl = activePlaybackUrl,
        firstFrame = ambientFirstFrame,
        seek = ambientSeek,
        preserveBlackBars = preserveBlackBars,
        keepFrameWhenHidden = hostedAmbient != null && isMini && !isInPipMode,
    )
    Box(
        modifier = modifier
            .drawBehind {
                if (isMini || preserveBlackBars || !useHostBackdrop || !ambientVisible || ambient.frame == null) {
                    drawRect(Color.Black)
                }
            }
            .onGloballyPositioned { coordinates ->
                ambientCoordinates = coordinates
                if (trackAmbient) ambient.updateBounds(playerTexture, coordinates)
                val b = coordinates.boundsInWindow()
                val rect = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                if (!isInPipMode && !rect.isEmpty && playerBounds != rect) {
                    playerBounds = rect
                    onPlayerCoordinatesChanged(rect)
                }
            }
            .pointerInput(isInPipMode, isMini, editingChatPosition, stableId, recording != null) {
                if (!isInPipMode && !isMini && !editingChatPosition) {
                    if (recording != null) {
                        detectRecordingTaps(onTap = { showControls = !showControls }) { forward, continued ->
                            onRecordingTap(forward, continued)
                        }
                    } else {
                        detectTapGestures(
                            onDoubleTap = { onLiveDoubleTap() },
                            onTap = { showControls = !showControls },
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!isMini && !preserveBlackBars && !useHostBackdrop && ambientVisible && ambient.bounds?.hasLetterboxSpace == true) {
            PlayerAmbientBackdrop(ambient, modifier = Modifier.matchParentSize())
        }
        PlayerVideoSurface(
            player, isPlaying, Modifier.fillMaxSize().onSizeChanged { viewport = it },
            onTextureChanged = { playerTexture = it },
        )

        val overlayThumbnail = remember(previewThumbnailUrl, channel, recording?.id) {
            if (recording != null) recording.thumbnail ?: channel?.getBannerUrl()
            else previewThumbnailUrl ?: channel?.livestream?.thumbnail?.url ?: channel?.getBannerUrl()
        }

        AnimatedVisibility(
            visible = !hasRenderedFirstFrame && !overlayThumbnail.isNullOrEmpty(),
            enter = androidx.compose.animation.EnterTransition.None,
            exit = fadeOut(tween(180))
        ) {
            AsyncImage(
                model = remember(overlayThumbnail) {
                    buildOptimizedImageRequest(
                        context = context,
                        data = overlayThumbnail,
                        targetWidthDp = if (recording == null && previewThumbnailUrl != null) 400 else 640,
                        targetHeightDp = if (recording == null && previewThumbnailUrl != null) 225 else 360
                    )
                },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isBuffering && playbackError == null && !hideBufferingIndicator) {
            LoadingIndicator(
                modifier = Modifier.size(if (isMini) 32.dp else 56.dp)
            )
        }

        if (backdropTexture != null) {
            val chatRegion = chatBlurBounds.takeIf { chatBlurStyle != null }
            VideoBackdropBlur(
                backdropTexture,
                listOfNotNull(chatRegion) + controlBlur.regions.values,
                controlOpacity = { controlsOpacity.value },
                opacity = { if (editingChatPosition) 1f else playerHost?.fullscreenChatOverlayAlpha ?: 0f },
            )
        }
        if (overlayPreferences != null && playerHost != null && channel != null &&
            ((fullscreenChatOverlayEnabled && overlayHasOpacity) || editingChatPosition)
        ) {
            val blur = if (overlayPreferences.blur) {
                FullscreenChatBlur(updateChatBlurBounds, legacyBackdrop, overlayPreferences.blurStyle)
            } else null
            FullscreenChatOverlay(
                host = playerHost,
                channel = channel,
                liveChatViewModel = liveChatViewModel,
                preferences = overlayPreferences,
                playing = loadedMediaId == stableId && isPlaying,
                editing = editingChatPosition,
                onSavePosition = { horizontal, position ->
                    val key = if (horizontal) SettingsKeys.FULLSCREEN_CHAT_HORIZONTAL_POSITION
                    else SettingsKeys.FULLSCREEN_CHAT_VERTICAL_POSITION
                    coroutineScope.launch {
                        settingsRepository.set(key, position.encode())
                        playerHost.editFullscreenChatPosition(false)
                    }
                },
                onCancelEditing = { playerHost.editFullscreenChatPosition(false) },
                onOpenSettings = {
                    settingsPage = PlayerSettingsPage.CHAT_OVERLAY
                    showSettingsDialog = true
                    showControls = false
                },
                blur = blur,
                viewport = playerBounds?.takeUnless { it.isEmpty }?.let {
                    ComposeRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
                },
                modifier = Modifier.graphicsLayer {
                    alpha = if (editingChatPosition) 1f else playerHost.fullscreenChatOverlayAlpha
                },
                interactive = !playerHost.isDirectChatDrag && !playerHost.isSideChatOpen(isWide = true),
            )
        }

        val autoQualityLabel = stringResource(R.string.player_auto)
        val qualityLabel = remember(selectedQualityHeight, currentVideoHeight, autoQualityLabel) {
            when {
                selectedQualityHeight > 0 -> "${currentVideoHeight.takeIf { it > 0 } ?: selectedQualityHeight}p"
                currentVideoHeight > 0 -> "${currentVideoHeight}p ($autoQualityLabel)"
                else -> autoQualityLabel
            }
        }

        val handleTogglePlay = {
            val p = player?.takeIf { it.currentMediaItem?.mediaId == stableId }
            if (p != null) {
                if (p.playWhenReady) {
                    p.pause()
                } else {
                    // Live resumes are handled once in the playback service, including
                    // notification/PiP/headset commands while this UI is unmounted.
                    if (!isLiveStream && p.playbackState == Player.STATE_ENDED) {
                        p.seekToDefaultPosition()
                    }
                    p.play()
                }
            }
        }

        val handleGoToLive = {
            val p = player?.takeIf { it.currentMediaItem?.mediaId == stableId }
            if (playingDvr) {
                dvrPlayback = null
            } else if (p != null) {
                p.seekToDefaultPosition()
                p.play()
            }
        }

        if (!isInPipMode) controlsTransition.AnimatedVisibility(
            visible = { it },
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = controlsOpacity.value },
        ) {
            val hostStream = channel?.livestream
            val channelState = if (isFullScreen) liveChatViewModel?.uiState?.collectAsStateWithLifecycle()?.value else null
            val currentChannelState = channelState?.takeIf { it.channel?.id == channel?.id }
            val updatedStream = currentChannelState?.channel?.livestream
            val liveStream = updatedStream?.takeIf { it.id == hostStream?.id } ?: hostStream
            PlayerControlsOverlay(
                isFullScreen = isFullScreen,
                mediaInfo = if (isFullScreen) { modifier ->
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        Column(
                            modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            val title = recording?.title
                                ?: (liveStream?.sessionTitle ?: streamTitle)
                            if (title.isNotBlank()) ChannelMediaTitle(title)
                            ChannelMediaTags(
                                categories = recording?.categories?.map { it.name to it.slug }
                                    ?: liveStream?.categories.orEmpty().map { it.name to it.slug },
                                tags = recording?.tags ?: liveStream?.tags.orEmpty(),
                                event = currentChannelState?.event,
                                language = if (recording == null) liveStream?.language else recording.language,
                                isMature = recording == null && liveStream?.isMature == true,
                                contentColor = Color.White,
                            )
                        }
                    }
                } else null,
                isTheaterMode = isTheaterMode,
                isChatVisible = if (fullscreenChatAvailable) {
                    fullscreenChatOverlayEnabled && !playerHost.isSideChatOpen(isWide = true)
                } else isChatVisible,
                isClipModalVisible = clipTarget != null,
                onToggleChat = if (fullscreenChatAvailable) {
                    onToggleFullscreenChatOverlay
                } else onToggleChat,
                isPlaying = playRequested,
                isMuted = isMuted,
                volume = volume,
                isSettingsDialogVisible = showSettingsDialog,
                isCastDialogVisible = showCastDialog,
                qualityLabel = qualityLabel,
                controlFrame = legacyBackdrop,
                controlBlur = controlBlur,
                controlVideoReady = backdropTexture != null,
                sleepRemainingSeconds = if (sleepTimerMinutes > 0) remainingTimerSeconds else 0,
                streamDuration = streamDuration,
                positionMs = if (isLiveStream) liveSeekWindow?.positionMs ?: 0L else positionMs,
                durationMs = if (isLiveStream) liveSeekWindow?.edgeMs ?: 0L else durationMs,
                liveWindowStartMs = liveSeekWindow?.startMs ?: C.TIME_UNSET,
                onSeek = { target, originalWindowStart, atEnd ->
                    val active = player?.takeIf { it.currentMediaItem?.mediaId == stableId }
                    if (isLiveStream) {
                        val sliderWindow = liveSeekWindow
                        val source = dvrUrl
                        when {
                            atEnd -> handleGoToLive()
                            sliderWindow?.usesDvr == true && source != null && active != null -> {
                                if (playingDvr) {
                                    if (sliderWindow.edgeMs - target <= DVR_LIVE_GAP_MS) handleGoToLive()
                                    else if (active.isCurrentMediaItemSeekable && active.duration > 0L) {
                                        active.seekTo(target.coerceIn(0L, active.duration))
                                    }
                                } else {
                                    val dvrEnd = (sliderWindow.edgeMs - DVR_LIVE_GAP_MS).coerceAtLeast(0L)
                                    dvrPlayback = LiveDvrPlayback(source, target.coerceIn(0L, dvrEnd), active.playWhenReady)
                                }
                            }
                            else -> active?.liveSeekWindow()?.let { window ->
                                val shiftedTarget = if (originalWindowStart != C.TIME_UNSET && window.startMs != C.TIME_UNSET) {
                                    target + originalWindowStart - window.startMs
                                } else target
                                active.seekTo(shiftedTarget.coerceIn(0L, window.edgeMs))
                            }
                        }
                    } else if (active?.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) == true) {
                        active.seekTo(target.coerceIn(0L, durationMs.coerceAtLeast(0L)))
                    }
                },
                onSeekingChanged = { isSeeking = it },
                onSeekBack = { skipRecording(false, true) },
                onSeekForward = { skipRecording(true, true) },
                isLive = isLiveStream,
                liveViewerCount = liveStream?.viewerCount ?: hostStream?.viewerCount,
                isAtLiveEdge = isAtLiveEdge,
                behindLiveText = if (liveOffsetMs == C.TIME_UNSET) "" else formatLiveOffset(liveOffsetMs),
                onGoToLive = handleGoToLive,
                onTogglePlay = handleTogglePlay,
                onToggleMute = {
                    playerVolume.toggleMute()
                },
                onVolumeChange = { newVol ->
                    isAdjustingVolume = true
                    playerVolume.setVolume(newVol)
                },
                onVolumeChangeFinished = {
                    isAdjustingVolume = false
                },
                onOpenSettings = { showSettingsDialog = true },
                onOpenQuality = {
                    settingsPage = PlayerSettingsPage.QUALITY
                    showSettingsDialog = true
                },
                onOpenCastDialog = { showCastDialog = true },
                onToggleTheater = onToggleTheater,
                onCreateClip = if (clipVideoId != null || clipLiveSession != null) {
                    {
                        val active = player?.takeIf {
                            it.currentMediaItem?.mediaId == stableId && it.playbackState != Player.STATE_IDLE
                        }
                        val position = active?.let { current ->
                            if (clipLiveSession == null || playingDvr) current.currentPosition else {
                                val timeline = current.currentTimeline
                                val window = current.currentMediaItemIndex.takeIf { it in 0 until timeline.windowCount }
                                    ?.let { timeline.getWindow(it, Timeline.Window()) }
                                val livestream = channel.livestream
                                val start = parseUtcStartTimeToMillis(livestream.startTime ?: livestream.createdAt)
                                window?.let { broadcastPositionMs(it.windowStartTimeMs, current.currentPosition, start) }
                            }
                        }
                        if (position == null || position < 0) {
                            Toast.makeText(context, R.string.clip_position_unavailable, Toast.LENGTH_SHORT).show()
                        } else {
                            clipTarget = ClipTarget(clipVideoId, (position / 1000L).toInt(), clipLiveSession)
                        }
                    }
                } else null,
                onToggleFullScreen = toggleFullScreen,
                // Tam ekrandayken önce tam ekrandan çık, normalde ise mini player'a geç.
                onMinimize = { if (isFullScreen) onFullScreenChange(false) else onMinimize() }
            )
        }

        if (!isInPipMode && !isMini && !editingChatPosition) {
            skipFeedback?.let { feedback ->
                RecordingSkipOverlay(feedback, Modifier.matchParentSize()) { skipFeedback = null }
            }
        }

        if (!isInPipMode && !isMini) {
            clipTarget?.let { target ->
                CreateClipModal(
                    videoId = target.videoId,
                    positionSeconds = target.positionSeconds,
                    liveSession = target.liveSession,
                    onDismiss = { clipTarget = null },
                )
            }
        }

        if (isMini && !isInPipMode) {
            MiniControlsOverlay(
                isPlaying = playRequested,
                texture = playerTexture.takeIf { hasRenderedFirstFrame },
                videoPlaying = isPlaying,
                onTogglePlay = handleTogglePlay,
                onClose = onClose
            )
        }

        if (showStats && !isMini && !isInPipMode) {
            PlayerStatsOverlay(
                videoId = recording?.progressId ?: channel?.livestream?.id?.toString(),
                viewport = viewport,
                volumePercent = if (isMuted) 0 else (volume * 100).roundToInt(),
                onClose = { showStats = false },
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).widthIn(max = 540.dp).fillMaxWidth(.86f)
            )
        }

        if (showSettingsDialog && !isInPipMode) {
            val sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
            var dismissing by remember { mutableStateOf(false) }
            val closeSettings: () -> Unit = {
                showSettingsDialog = false
                settingsPage = PlayerSettingsPage.MAIN
            }
            fun dismissSettings(afterDismiss: () -> Unit = {}) {
                if (dismissing) return
                dismissing = true
                coroutineScope.launch {
                    try {
                        sheetState.hide()
                        closeSettings()
                        afterDismiss()
                    } finally {
                        dismissing = false
                    }
                }
            }

            val selectSleepTimer: (Int) -> Unit = { mins ->
                dismissSettings {
                    // Playback expiry never depends on the notification permission answer.
                    com.lechixy.kick.service.player.SleepTimer.start(mins)
                    AccountGraph.notifications.postSleepTimer(mins)
                    if (mins > 0) {
                        coroutineScope.launch { settingsRepository.set(SettingsKeys.LAST_SLEEP_TIMER_MINUTES, mins) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }

            ModalBottomSheet(
                onDismissRequest = { if (!dismissing) closeSettings() },
                sheetState = sheetState,
                sheetMaxWidth = 640.dp,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    AnimatedContent(
                        targetState = settingsPage,
                        transitionSpec = {
                            val transition = if (initialState == PlayerSettingsPage.MAIN) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(
                                    slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()).togetherWith(
                                    slideOutHorizontally { it } + fadeOut())
                            }
                            transition.using(SizeTransform { _, _ -> tween(260) })
                        },
                        label = "PlayerSettingsPageTransition"
                    ) { targetPage ->
                        Column(Modifier.fillMaxWidth()) {
                            if (targetPage != PlayerSettingsPage.MAIN) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            settingsPage = PlayerSettingsPage.MAIN
                                        },
                                        modifier = Modifier.size(36.dp),
                                        enabled = !dismissing
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                            contentDescription = stringResource(R.string.player_back)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when (targetPage) {
                                            PlayerSettingsPage.QUALITY -> stringResource(R.string.player_quality)
                                            PlayerSettingsPage.SLEEP_TIMER -> stringResource(R.string.player_sleep_timer)
                                            PlayerSettingsPage.CHAT_OVERLAY -> stringResource(R.string.fullscreen_chat_title)
                                            PlayerSettingsPage.MORE -> stringResource(R.string.player_more)
                                            PlayerSettingsPage.AMBIENT -> stringResource(R.string.player_ambient_mode)
                                            PlayerSettingsPage.MAIN -> stringResource(R.string.settings)
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            when (targetPage) {
                                PlayerSettingsPage.MAIN -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        PlayerSettingsCategoryRow(
                                            icon = R.drawable.hd_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                            title = stringResource(R.string.player_quality),
                                            value = qualityLabel,
                                            onClick = { settingsPage = PlayerSettingsPage.QUALITY }
                                        )
                                        PlayerSettingsCategoryRow(
                                            icon = R.drawable.timer_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                            title = stringResource(R.string.player_sleep_timer),
                                            value = if (sleepTimerMinutes > 0) stringResource(R.string.player_minutes, (remainingTimerSeconds + 59) / 60) else stringResource(R.string.player_off),
                                            onClick = { settingsPage = PlayerSettingsPage.SLEEP_TIMER }
                                        )
                                        PlayerSettingsCategoryRow(
                                            icon = R.drawable.fluorescent_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                            title = stringResource(R.string.player_ambient_mode),
                                            value = stringResource(ambientMode.titleResource),
                                            onClick = { settingsPage = PlayerSettingsPage.AMBIENT },
                                        )
                                        if (overlayPreferences != null) {
                                            PlayerSettingsCategoryRow(
                                                icon = R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                                title = stringResource(R.string.fullscreen_chat_title),
                                                value = stringResource(if (overlayPreferences.horizontal) {
                                                    R.string.fullscreen_chat_horizontal
                                                } else R.string.fullscreen_chat_vertical),
                                                onClick = { settingsPage = PlayerSettingsPage.CHAT_OVERLAY }
                                            )
                                        }
                                        if (recording != null) {
                                            PlayerSettingsCategoryRow(
                                                icon = R.drawable.download_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                                title = stringResource(R.string.recording_download),
                                                value = null,
                                                showChevron = false,
                                                onClick = {
                            dismissSettings {
                                downloadRecording(recording.copy(
                                    channelName = channelName,
                                    channelSlug = channel?.slug,
                                    channelAvatar = channel?.user?.profilePic,
                                ))
                            }
                                                }
                                            )
                                        }
                                        PlayerSettingsCategoryRow(
                                            icon = R.drawable.tune_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                            title = stringResource(R.string.player_more),
                                            value = null,
                                            onClick = { settingsPage = PlayerSettingsPage.MORE }
                                        )
                                    }
                                }

                                PlayerSettingsPage.QUALITY -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        PlayerSettingsOptionRow(
                                            title = stringResource(R.string.player_auto),
                                            subtitle = stringResource(R.string.player_auto_network),
                                            selected = selectedQualityHeight == -1,
                                            onClick = {
                                                selectedQualityHeight = -1
                                                coroutineScope.launch {
                                                    settingsRepository.set(SettingsKeys.VIDEO_QUALITY, -1)
                                                }
                                                dismissSettings()
                                            }
                                        )

                                        availableQualities.forEach { quality ->
                                            PlayerSettingsOptionRow(
                                                title = quality.label,
                                                subtitle = quality.dataPerHourText.replace("/saat", stringResource(R.string.player_per_hour)),
                                                selected = selectedQualityHeight == quality.height,
                                                onClick = {
                                                    selectedQualityHeight = quality.height
                                                    coroutineScope.launch {
                                                        settingsRepository.set(
                                                            SettingsKeys.VIDEO_QUALITY,
                                                            quality.height
                                                        )
                                                    }
                                                    dismissSettings()
                                                }
                                            )
                                        }
                                    }
                                }

                                PlayerSettingsPage.SLEEP_TIMER -> {
                                    PlayerSleepTimerSettings(
                                        timer = sleepTimer,
                                        lastMinutes = lastSleepTimerMinutes,
                                        enabled = !dismissing,
                                        onApply = selectSleepTimer
                                    )
                                }

                                PlayerSettingsPage.CHAT_OVERLAY -> {
                                    if (overlayPreferences != null && playerHost != null) {
                                        val connection = if (recording == null) {
                                            liveChatViewModel?.chatConnectionState?.collectAsStateWithLifecycle()?.value
                                        } else null
                                        val canConnect = connection == ChatConnectionState.DISCONNECTED || connection == ChatConnectionState.ERROR
                                        FullscreenChatSettings(
                                            horizontal = overlayPreferences.horizontal,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            onEditPosition = {
                                                dismissSettings {
                                                    playerHost.editFullscreenChatPosition(true)
                                                    showControls = false
                                                }
                                            },
                                            onConnect = liveChatViewModel?.takeIf { canConnect }?.let { it::connectChat },
                                        )
                                    }
                                }

                                PlayerSettingsPage.MORE -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        SettingSwitch(
                                            title = stringResource(R.string.player_stats_for_nerds),
                                            checked = showStats,
                                            onCheckedChange = { checked -> dismissSettings { showStats = checked } },
                                            icon = R.drawable.eyeglasses_3_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                                        )

                                        SettingSwitch(
                                            key = SettingsKeys.SYNC_PLAYER_VOLUME_WITH_DEVICE,
                                            title = stringResource(R.string.player_sync_volume),
                                            icon = R.drawable.volume_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                                        )

                                        SettingSwitch(
                                            key = SettingsKeys.PIP_ENABLED,
                                            title = stringResource(R.string.player_pip),
                                            description = stringResource(R.string.player_pip_description),
                                            icon = R.drawable.picture_in_picture_alt_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                                        )

                                        SettingSwitch(
                                            key = SettingsKeys.LOW_LATENCY,
                                            title = stringResource(R.string.player_low_latency),
                                            description = stringResource(R.string.player_low_latency_description),
                                            icon = R.drawable.bolt_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                                        )

                                        SettingSwitch(
                                            key = SettingsKeys.AUTO_FULLSCREEN_LIVE,
                                            title = stringResource(R.string.player_auto_fullscreen),
                                            description = stringResource(R.string.player_auto_fullscreen_description),
                                            icon = R.drawable.fullscreen_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                                        )
                                    }
                                }

                                PlayerSettingsPage.AMBIENT -> Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AmbientModeSettings()
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }

        if (showCastDialog && !isInPipMode) {
            PlayerCastDialog(
                onDismiss = { showCastDialog = false },
                onGoogleCast = {
                    showCastDialog = false
                    runCatching {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure {
                        android.widget.Toast.makeText(context, R.string.player_cast_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onWebVideoCaster = {
                    showCastDialog = false
                    CastUtils.launchWebVideoCaster(context = context, playbackUrl = playbackUrl,
                        streamTitle = streamTitle, channelName = channelName,
                        posterUrl = recording?.thumbnail ?: channel?.getBannerUrl() ?: channel?.user?.profilePic)
                }
            )
        }
    }
}

@Composable
private fun rememberMediaController(): State<Player?> {
    val context = LocalContext.current
    val playerState = remember { mutableStateOf<Player?>(null) }

    DisposableEffect(context) {
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            { playerState.value = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(appContext)
        )
        onDispose {
            val p = playerState.value
            val changing = context.findActivity()?.isChangingConfigurations == true
            if (!changing) {
                p?.pause()
                p?.stop()
                p?.clearMediaItems()      // session/bildirim tamamen temizlensin
            }
            // Application context kullanıldığı için Activity leak oluşmaz.
            Handler(Looper.getMainLooper()).postDelayed(
                { MediaController.releaseFuture(future) },
                if (changing) 0L else 500L
            )
            playerState.value = null
        }
    }
    return playerState
}

internal fun compactVideoHeightLimit(widthPx: Int, preferredHeight: Int): Int {
    val limit = when {
        widthPx <= 0 -> 360
        widthPx <= 256 -> 144
        widthPx <= 426 -> 240
        widthPx <= 640 -> 360
        else -> 480
    }
    return if (preferredHeight > 0) minOf(limit, preferredHeight) else limit
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Activity.updatePlayerPipParams(params: PictureInPictureParams) {
    if (isFinishing || isDestroyed || isInPictureInPictureMode ||
        !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    try {
        setPictureInPictureParams(params)
    } catch (error: IllegalStateException) {
        Log.w("KickPlayerView", "PiP parameters rejected during activity transition", error)
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun buildPipParams(autoEnter: Boolean, sourceRect: Rect?): PictureInPictureParams =
    PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setAutoEnterEnabled(autoEnter)
        .setSeamlessResizeEnabled(true)
        .apply {
            sourceRect?.let { bounds ->
                val width = minOf(bounds.width(), (bounds.height() * 16f / 9f).roundToInt())
                val height = minOf(bounds.height(), (width * 9f / 16f).roundToInt())
                val left = bounds.left + (bounds.width() - width) / 2
                val top = bounds.top + (bounds.height() - height) / 2
                if (width > 0 && height > 0) setSourceRectHint(Rect(left, top, left + width, top + height))
            }
        }
        .build()

@androidx.annotation.OptIn(UnstableApi::class)
private fun Tracks.toQualityOptions(): List<VideoQualityOption> =
    groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
        .filter { it.height > 0 }
        .map { format ->
            VideoQualityOption(
                label = "${format.height}p",
                height = format.height,
                bitrate = format.bitrate,
                dataPerHourText = PlayerUtils.estimateDataUsagePerHour(
                    format.bitrate,
                    format.height
                )
            )
        }
        .distinctBy { it.height }
        .sortedByDescending { it.height }
        .toList()

/**
 * Player ayarları dialogundaki sayfalar (YouTube tarzı kategoriler).
 */
private enum class PlayerSettingsPage { MAIN, QUALITY, SLEEP_TIMER, CHAT_OVERLAY, MORE, AMBIENT }

@Composable
private fun PlayerSettingsCategoryRow(
    icon: Int,
    title: String,
    value: String?,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (showChevron) {
            Icon(
                painter = painterResource(id = R.drawable.arrow_forward_ios_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun PlayerSettingsOptionRow(
    title: String,
    selected: Boolean,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
internal fun AnimatedVisibilityScope.PlayerControlsOverlay(
    isFullScreen: Boolean,
    mediaInfo: (@Composable (Modifier) -> Unit)? = null,
    isTheaterMode: Boolean = false,
    isChatVisible: Boolean = true,
    isClipModalVisible: Boolean = false,
    onToggleChat: () -> Unit = {},
    isPlaying: Boolean,
    isMuted: Boolean,
    volume: Float,
    isSettingsDialogVisible: Boolean,
    isCastDialogVisible: Boolean,
    qualityLabel: String,
    controlFrame: State<FullscreenChatVideoFrame?>? = null,
    controlBlur: FrostedPlayerRegions? = null,
    controlVideoReady: Boolean = false,
    sleepRemainingSeconds: Int,
    streamDuration: String,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    liveWindowStartMs: Long = C.TIME_UNSET,
    onSeek: (Long, Long, Boolean) -> Unit = { _, _, _ -> },
    onSeekingChanged: (Boolean) -> Unit = {},
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    isLive: Boolean = false,
    liveViewerCount: Int? = null,
    isAtLiveEdge: Boolean = true,
    behindLiveText: String = "",
    onGoToLive: () -> Unit = {},
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenCastDialog: () -> Unit = {},
    onToggleTheater: () -> Unit = {},
    onCreateClip: (() -> Unit)? = null,
    onToggleFullScreen: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labelStyle = MaterialTheme.typography.labelSmall

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val height = size.height.coerceAtLeast(1f)
                val top = minOf(0.30f, 152.dp.toPx() / height)
                val bottom = minOf(0.36f, 196.dp.toPx() / height)
                val scrim = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.68f),
                    top * 0.45f to Color.Black.copy(alpha = 0.38f),
                    top to Color.Transparent,
                    1f - bottom to Color.Transparent,
                    1f - bottom * 0.45f to Color.Black.copy(alpha = 0.45f),
                    1f to Color.Black.copy(alpha = 0.76f),
                )
                onDrawBehind { drawRect(scrim) }
            }
            .let {
                if (isFullScreen) it.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                else it
            }
    ) {
        if (sleepRemainingSeconds > 0) {
            Row(Modifier.align(Alignment.TopCenter).offset(y = maxHeight / 4).offset(y = (-12).dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActiveSleepTimerIcon()
                PlayerSleepTimerRemainingLabel(sleepRemainingSeconds)
            }
        }
        val isCompact = !isFullScreen
        val configuration = LocalConfiguration.current
        val isWide = configuration.screenWidthDp >= WIDE_BREAKPOINT_DP
        val largeControls = configuration.smallestScreenWidthDp >= 600 && maxWidth >= 480.dp
        val chromeButtonModifier = when {
            largeControls -> Modifier.size(48.dp)
            isCompact -> Modifier.size(36.dp)
            else -> Modifier
        }
        val chromeIconModifier = if (largeControls) Modifier.size(28.dp) else Modifier
        val showChatButton = isFullScreen || isTheaterMode || isWide
        val mediaInfoBesideActions = maxWidth >= 560.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .animateEnterExit(
                    enter = slideInVertically(tween(260)) { -it / 2 },
                    exit = slideOutVertically(tween(180)) { -it / 2 }
                )
                .padding(
                    start = if (isCompact) 2.dp else 4.dp,
                    top = if (isCompact) 2.dp else 8.dp,
                    end = if (isCompact) 6.dp else 12.dp,
                    bottom = if (isCompact) 2.dp else 8.dp
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = if (isFullScreen) Alignment.Top else Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp),
                ) {
                    IconButton(onClick = onMinimize, modifier = chromeButtonModifier) {
                        Icon(
                            painter = painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = "Minimize",
                            modifier = chromeIconModifier,
                            tint = Color.White,
                        )
                    }
                }

                if (mediaInfoBesideActions) {
                    mediaInfo?.invoke(Modifier.weight(1f).padding(horizontal = 12.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp),
                ) {
                    PlayerQualityBadge(
                        qualityLabel, controlFrame, controlBlur, onOpenQuality, controlVideoReady,
                        modifier = Modifier.heightIn(min = if (isCompact && !largeControls) 36.dp else 48.dp),
                    )
                    IconButton(
                        onClick = onOpenCastDialog,
                        modifier = chromeButtonModifier
                    ) {
                        Icon(
                            painter = painterResource(id = if (isCastDialogVisible) R.drawable.cast_24dp_e3e3e3_fill1_wght400_grad0_opsz24 else R.drawable.cast_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = stringResource(R.string.player_cast),
                            modifier = chromeIconModifier,
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = chromeButtonModifier
                    ) {
                        Icon(
                            painter = painterResource(id = if (isSettingsDialogVisible) R.drawable.settings_24dp_e3e3e3_fill1_wght400_grad0_opsz24 else R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = "Settings",
                            modifier = chromeIconModifier,
                            tint = Color.White
                        )
                    }
                }
            }
            if (!mediaInfoBesideActions) {
                mediaInfo?.invoke(Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            }
        }

        /**
         * Play Icon / Center
         */
        Row(
            modifier = Modifier.align(Alignment.Center).animateEnterExit(
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium), initialScale = 0.5f),
                exit = scaleOut(tween(160), targetScale = 0.65f)
            ),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isLive) {
                FrostedPlaybackButton(
                    onClick = onSeekBack,
                    frame = controlFrame,
                    backdrop = controlBlur,
                    videoReady = controlVideoReady,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(painterResource(R.drawable.replay_10_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = stringResource(R.string.player_skip_back), tint = Color.White)
                }
            }
            FrostedPlaybackButton(
                onClick = onTogglePlay,
                frame = controlFrame,
                backdrop = controlBlur,
                videoReady = controlVideoReady,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    painter = rememberPlayPausePainter(isPlaying),
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            if (!isLive) {
                FrostedPlaybackButton(onSeekForward, controlFrame, Modifier.size(48.dp), controlBlur, controlVideoReady) {
                    Icon(painterResource(R.drawable.forward_10_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = stringResource(R.string.player_skip_forward), tint = Color.White)
                }
            }
        }
        /**
         * Bottom
         */
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().animateEnterExit(
            enter = slideInVertically(tween(260)) { it / 2 },
            exit = slideOutVertically(tween(180)) { it / 2 }
        )) {
            key(isLive) {
                var remaining by remember { mutableStateOf(false) }
                var scrubPosition by remember { mutableStateOf<Float?>(null) }
                var scrubWindowStart by remember { mutableLongStateOf(C.TIME_UNSET) }
                var scrubDuration by remember { mutableLongStateOf(0L) }
                val sliderDuration = if (isLive && scrubPosition != null) scrubDuration else durationMs
                val sliderMax = sliderDuration.coerceAtLeast(1).toFloat()
                val shownPosition = scrubPosition?.toLong() ?: positionMs
                val sliderPosition = (scrubPosition ?: positionMs.toFloat()).coerceIn(0f, sliderMax)
                DisposableEffect(Unit) { onDispose { onSeekingChanged(false) } }
                Column(Modifier.fillMaxWidth().padding(horizontal = if (isCompact) 12.dp else 20.dp)) {
                    if (isLive) {
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isCompact) 8.dp else 10.dp)
                                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
                            )
                            Text(
                                text = streamDuration,
                                color = Color.White,
                                style = labelStyle,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    } else {
                        Text(
                            text = (if (remaining) "-" + FormatUtils.formatDuration((durationMs - shownPosition).coerceAtLeast(0))
                                else FormatUtils.formatDuration(shownPosition)) + " / " + FormatUtils.formatDuration(durationMs),
                            color = Color.White,
                            style = labelStyle,
                            modifier = Modifier.clickable { remaining = !remaining }.padding(vertical = 2.dp)
                        )
                    }
                    CompactPlayerSlider(
                        value = if (isLive) sliderPosition / sliderMax else sliderPosition,
                        valueRange = 0f..(if (isLive) 1f else sliderMax),
                        enabled = durationMs > 0,
                        wavyTrack = true,
                        animateWave = isPlaying && durationMs > 0L,
                        onValueChange = {
                            if (scrubPosition == null) {
                                scrubWindowStart = liveWindowStartMs
                                scrubDuration = durationMs
                            }
                            scrubPosition = if (isLive) it * sliderMax else it
                            onSeekingChanged(true)
                        },
                        onValueChangeFinished = {
                            scrubPosition?.let { onSeek(it.toLong(), scrubWindowStart, it >= sliderDuration.toFloat()) }
                            scrubPosition = null
                            onSeekingChanged(false)
                        },
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isCompact) 8.dp else 16.dp,
                        vertical = if (isCompact) 4.dp else 8.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                /**
                 * Bottom Start/Left
                 */
                val itemSpacing = if (isCompact) 4.dp else 8.dp
                val targetSliderWidth = when {
                    isCompact -> 52.dp
                    isChatVisible -> 88.dp
                    else -> 115.dp
                }
                val sliderWidth by animateDpAsState(
                    targetValue = targetSliderWidth,
                    animationSpec = tween(durationMillis = 250),
                    label = "volumeSliderWidth"
                )

                Row(
                    modifier = Modifier.weight(1f, fill = false).playerControlsScroll(isFullScreen),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(if (largeControls) 44.dp else if (isCompact) 28.dp else 32.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isMuted || volume == 0f) R.drawable.volume_off_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                                else R.drawable.volume_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                            ),
                            contentDescription = "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(if (largeControls) 26.dp else if (isCompact) 18.dp else 20.dp)
                        )
                    }
                    CompactPlayerSlider(
                        value = if (isMuted) 0f else volume,
                        onValueChange = onVolumeChange,
                        onValueChangeFinished = onVolumeChangeFinished,
                        valueRange = 0f..1f,
                        modifier = Modifier.width(sliderWidth).height(28.dp)
                    )
                    Text(
                        text = "${(if (isMuted) 0f else volume * 100).toInt()}%",
                        color = Color.White,
                        style = labelStyle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (isLive) {
                        Separator()
                        Surface(
                            onClick = onGoToLive,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(start = 2.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (isCompact) 5.dp else 6.dp,
                                    vertical = 2.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isAtLiveEdge) {
                                        stringResource(R.string.live_badge)
                                    } else {
                                        "${stringResource(R.string.live_badge)} $behindLiveText"
                                    },
                                    style = labelStyle,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!isAtLiveEdge) {
                                    Text(
                                        text = "• ${stringResource(R.string.go_to_live)}",
                                        style = labelStyle,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (isFullScreen && liveViewerCount != null) {
                            Separator()
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painterResource(R.drawable.group_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    contentDescription = stringResource(R.string.viewers),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                AnimatedCounterText(
                                    count = liveViewerCount,
                                    style = labelStyle,
                                    animateEntrance = false,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }

                /**
                 * Bottom End/Right
                 */
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 8.dp)
                ) {
                    if (onCreateClip != null) {
                        IconButton(onClick = onCreateClip, modifier = chromeButtonModifier) {
                            Icon(
                                painterResource(
                                    if (isClipModalVisible) R.drawable.movie_edit_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                                    else R.drawable.movie_edit_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                                ),
                                contentDescription = stringResource(R.string.clip_create),
                                modifier = chromeIconModifier,
                                tint = Color.White,
                            )
                        }
                    }
                    Separator()
                    if (showChatButton) IconButton(
                        onClick = onToggleChat,
                        modifier = chromeButtonModifier
                    ) {
                        Icon(painterResource(R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = stringResource(if (isChatVisible) R.string.player_hide_chat else R.string.player_show_chat),
                            modifier = chromeIconModifier,
                            tint = if (isChatVisible) MaterialTheme.colorScheme.primary else Color.White)
                    }
                    IconButton(
                        onClick = onToggleTheater,
                        modifier = chromeButtonModifier
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isTheaterMode) R.drawable.theaters_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                                else R.drawable.theaters_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                            ),
                            contentDescription = "Theater Mode",
                            modifier = chromeIconModifier,
                            tint = if (isTheaterMode) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(
                        onClick = onToggleFullScreen,
                        modifier = chromeButtonModifier
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isFullScreen) R.drawable.fullscreen_exit_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                                else R.drawable.fullscreen_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                            ),
                            contentDescription = "Fullscreen",
                            modifier = chromeIconModifier,
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

private const val DVR_LIVE_GAP_MS = 30_000L

@Composable
private fun Modifier.playerControlsScroll(isFullScreen: Boolean): Modifier {
    val scroll = key(isFullScreen) { rememberScrollState() }
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fadeWidth = minOf(20.dp.toPx(), size.width / 2)
            val rtl = layoutDirection == LayoutDirection.Rtl
            onDrawWithContent {
                drawContent()
                val before = scroll.value
                val after = scroll.maxValue - before
                val left = minOf(fadeWidth, (if (rtl) after else before).toFloat())
                val right = minOf(fadeWidth, (if (rtl) before else after).toFloat())
                if (left > 0f) {
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), endX = left),
                        size = size.copy(width = left),
                        blendMode = BlendMode.DstIn,
                    )
                }
                if (right > 0f) {
                    val start = size.width - right
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = start, endX = size.width),
                        topLeft = Offset(start, 0f),
                        size = size.copy(width = right),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
        }
        .horizontalScroll(scroll)
}

private data class LiveDvrPlayback(val url: String, val positionMs: Long, val playWhenReady: Boolean)
private data class LiveSeekWindow(val startMs: Long, val edgeMs: Long, val positionMs: Long, val usesDvr: Boolean = false)

private fun Player.liveSeekWindow(): LiveSeekWindow? {
    if (!isCurrentMediaItemLive || !isCurrentMediaItemSeekable ||
        !isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    ) return null
    val timeline = currentTimeline
    val index = currentMediaItemIndex
    if (index !in 0 until timeline.windowCount) return null
    val window = timeline.getWindow(index, Timeline.Window())
    val edge = window.defaultPositionMs.coerceAtMost(window.durationMs)
    if (edge <= 0) return null
    return LiveSeekWindow(window.windowStartTimeMs, edge, currentPosition.coerceIn(0L, edge))
}

@Composable
private fun PlayerQualityBadge(
    label: String,
    frame: State<FullscreenChatVideoFrame?>?,
    blur: FrostedPlayerRegions?,
    onClick: () -> Unit,
    videoReady: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clip(CircleShape)
            .clickable(
                onClickLabel = stringResource(R.string.player_quality),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        FrostedPlayerControl(frame, blur, videoReady)
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MiniControlsOverlay(
    isPlaying: Boolean,
    texture: TextureView?,
    videoPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
) {
    var playRegion by remember { mutableStateOf<FullscreenChatBackdropRegion?>(null) }
    var closeRegion by remember { mutableStateOf<FullscreenChatBackdropRegion?>(null) }
    val legacyFrame = if (Build.VERSION.SDK_INT < 31 && texture != null) {
        rememberLegacyFullscreenChatFrame(texture, videoPlaying)
    } else null
    val playBlur = FullscreenChatBlur(remember { { playRegion = it } }, legacyFrame)
    val closeBlur = FullscreenChatBlur(remember { { closeRegion = it } }, legacyFrame)
    VideoBackdropBlur(texture, listOfNotNull(playRegion, closeRegion))

    Box(Modifier.fillMaxSize()) {
        MiniCornerButton(
            painter = painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
            contentDescription = stringResource(R.string.player_close),
            edges = FullscreenChatEdges(top = true, right = true),
            blur = closeBlur,
            onClick = onClose,
            modifier = Modifier.align(AbsoluteAlignment.TopRight),
        )
        MiniCornerButton(
            painter = rememberPlayPausePainter(isPlaying),
            contentDescription = "Play/Pause",
            edges = FullscreenChatEdges(left = true, bottom = true),
            blur = playBlur,
            onClick = onTogglePlay,
            modifier = Modifier.align(AbsoluteAlignment.BottomLeft),
        )
    }
}

@Composable
private fun MiniCornerButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    edges: FullscreenChatEdges,
    onClick: () -> Unit,
    modifier: Modifier,
    blur: FullscreenChatBlur? = null,
) {
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        FullscreenChatBackdrop(opacity = 0.28f, blur = blur, edges = edges)
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(painter, contentDescription, Modifier.size(24.dp), tint = Color.White)
        }
    }
}

@Composable
internal fun ShellChrome(mode: PlayerMode, onMinimize: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (mode == PlayerMode.Mini) {
            MiniCornerButton(
                painter = painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = stringResource(R.string.player_close),
                edges = FullscreenChatEdges(top = true, right = true),
                onClick = onClose,
                modifier = Modifier.align(AbsoluteAlignment.TopRight),
            )
        } else {
            PlayerChromeButton(
                painter = painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = "Minimize",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                onClick = onMinimize
            )
        }
    }
}

@Composable
internal fun PlayerChromeButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Composable
internal fun Separator() {
    Text(
        text = " | ",
        color = Color.White.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactPlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    wavyTrack: Boolean = false,
    animateWave: Boolean = true
) {
    val sliderState = remember(valueRange) { SliderState(value = value, trackRange = valueRange) }
    sliderState.value = value
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Slider(
            state = sliderState,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            thumb = {
                Box(Modifier.size(width = 4.dp, height = 14.dp)
                    .background(if (enabled) Color.White else Color.White.copy(alpha = 0.38f), CircleShape))
            },
            track = { state ->
                if (wavyTrack) {
                    // Keep Slider's gestures, thumb, RTL handling and accessibility actions.
                    PlayerProgressTrack(
                        progress = {
                            val range = valueRange.endInclusive - valueRange.start
                            if (range > 0f) ((state.value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
                        },
                        playing = animateWave, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().height(12.dp).clearAndSetSemantics {}
                    )
                } else {
                    SliderDefaults.Track(
                        sliderState = state,
                        modifier = Modifier.height(4.dp),
                        enabled = enabled,
                        drawStopIndicator = null,
                        thumbTrackGapSize = 3.dp,
                        trackInsideCornerSize = 0.dp
                    )
                }
            }
        )
    }
}
