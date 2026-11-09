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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
import com.lechixy.kick.components.channel.chat.FullscreenChatBlur
import com.lechixy.kick.components.channel.chat.FullscreenChatBackdropRegion
import com.lechixy.kick.components.channel.chat.FullscreenChatOverlay
import com.lechixy.kick.components.channel.chat.FullscreenChatSettings
import com.lechixy.kick.components.channel.chat.FullscreenChatVideoBlur
import com.lechixy.kick.components.channel.chat.LocalChatContentActive
import com.lechixy.kick.components.channel.chat.rememberFullscreenChatPreferences
import com.lechixy.kick.components.channel.chat.rememberLegacyFullscreenChatFrame
import com.lechixy.kick.components.settings.SettingSwitch
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.getBannerUrl
import com.lechixy.kick.data.chat.model.recordingStartInstant
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.player.local.PlaybackProgressStore
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.player.remote.ViewerSession
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.screens.channel.ChannelViewModel
import com.lechixy.kick.screens.clip.CreateClipModal
import com.lechixy.kick.service.EXTRA_LOW_LATENCY
import com.lechixy.kick.service.PlaybackService
import com.lechixy.kick.service.buildPlaybackMediaItem
import com.lechixy.kick.service.player.setPlaybackUsageChannel
import com.lechixy.kick.service.player.broadcastPositionMs
import com.lechixy.kick.service.player.distanceBehindLiveTargetMs
import com.lechixy.kick.service.player.isNearLiveTarget
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

    val stableId = remember(channel?.slug, playbackUrl, if (recording == null) channel?.livestream?.id else recording.id) {
        val baseId = channel?.slug ?: playbackUrl
        val liveId = if (recording == null) channel?.livestream?.id else null
        if (recording != null) "${baseId}_recording_${recording.id}" else if (liveId != null) "${baseId}_$liveId" else baseId
    }

    var hasRenderedFirstFrame by remember(stableId) { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var loadedMediaId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var playRequested by remember(stableId) { mutableStateOf(true) }
    var playbackError by remember(stableId) { mutableStateOf<String?>(null) }
    var liveRetryTrigger by remember(stableId) { mutableIntStateOf(0) }
    var isSeeking by remember(stableId) { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var isAdjustingVolume by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var clipTarget by remember(stableId) { mutableStateOf<ClipTarget?>(null) }
    var settingsPage by remember { mutableStateOf(PlayerSettingsPage.MAIN) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var playerTexture by remember { mutableStateOf<TextureView?>(null) }
    var chatBlurBounds by remember { mutableStateOf<FullscreenChatBackdropRegion?>(null) }
    val updateChatBlurBounds: (FullscreenChatBackdropRegion?) -> Unit = remember { { chatBlurBounds = it } }
    val downloadRecording = rememberRecordingDownloadAction()
    val sleepTimer by com.lechixy.kick.service.player.SleepTimer.state.collectAsStateWithLifecycle()
    val sleepTimerMinutes = sleepTimer.selectedMinutes
    val remainingTimerSeconds = sleepTimer.remainingSeconds

    val settingsRepository = LocalSettingsRepository.current
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
    var currentVideoHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(preferredQuality) {
        selectedQualityHeight = preferredQuality
    }

    LaunchedEffect(player, selectedQualityHeight, availableQualities.toList()) {
        applyQualityToPlayer(player, selectedQualityHeight, availableQualities)
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
    val lowLatencyEnabled = isLiveStream && lowLatencySetting
    val latestIsLiveStream by rememberUpdatedState(isLiveStream)
    var positionMs by remember(stableId) { mutableLongStateOf(0L) }
    var durationMs by remember(stableId) { mutableLongStateOf(recording?.durationMs ?: 0L) }
    val positionCallback by rememberUpdatedState(onPlaybackPositionChanged)
    val seekCallback by rememberUpdatedState(onPlaybackSeek)
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

    LaunchedEffect(player, stableId, isLiveStream, lowLatencyEnabled) {
        val p = player ?: return@LaunchedEffect
        if (!isLiveStream) {
            isAtLiveEdge = true
            liveOffsetMs = 0L
            return@LaunchedEffect
        }

        while (isActive) {
            val currentOffset = if (p.currentMediaItem?.mediaId == stableId) {
                p.distanceBehindLiveTargetMs()
            } else C.TIME_UNSET

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

            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
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
                    isPlayerReady = true
                    playbackError = null
                    loadedMediaId = p.currentMediaItem?.mediaId
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (p.currentMediaItem?.mediaId == stableId) playRequested = playWhenReady
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    if (p.currentMediaItem?.mediaId != stableId) return
                    isPlaying = playing
                    if (playing) {
                        playbackError = null
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (!latestIsLiveStream && p.currentMediaItem?.mediaId == stableId &&
                        (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)
                    ) {
                        positionMs = newPosition.positionMs.coerceAtLeast(0L)
                        seekCallback(positionMs)
                    }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.height > 0) currentVideoHeight = videoSize.height
                }

                override fun onTracksChanged(tracks: Tracks) {
                    refreshQualities(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (p.currentMediaItem?.mediaId != stableId) return
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
            isPlaying = isSameMedia && p.isPlaying
            playRequested = isSameMedia && p.playWhenReady
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

    LaunchedEffect(player, stableId, playbackUrl, lowLatencyEnabled) {
        val p = player ?: return@LaunchedEffect
        if (playbackUrl.isBlank()) return@LaunchedEffect

        playbackError = null

        val currentItem = p.currentMediaItem
        // Reloading channel metadata can rotate the signed HLS URL without changing
        // the broadcast. Keep a healthy session running when returning from mini mode.
        // A failed/idle session may still adopt the refreshed URL to recover playback.
        val keepLiveSession = isLiveStream && p.isCurrentMediaItemLive && p.playerError == null &&
                p.playbackState == Player.STATE_READY
        val alreadyLoaded = currentItem?.mediaId == stableId &&
                (keepLiveSession || currentItem.localConfiguration?.uri?.toString() == playbackUrl) &&
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
            playbackUrl = playbackUrl,
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
            historyEntry?.let { setPlaybackUsageChannel(it.channelSlug, it.channelName) }
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
        } else {
            p.setMediaItem(mediaItem)
        }
        p.prepare()
        p.play()
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

    Box(
        modifier = modifier
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                val b = coordinates.boundsInWindow()
                val rect = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                if (!isInPipMode && !rect.isEmpty && playerBounds != rect) {
                    playerBounds = rect
                    onPlayerCoordinatesChanged(rect)
                }
            }
            .pointerInput(isInPipMode, isMini, editingChatPosition) {
                if (!isInPipMode && !isMini && !editingChatPosition) {
                    detectTapGestures(
                        onDoubleTap = { toggleFullScreen() },
                        onTap = { showControls = !showControls }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
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

        if (overlayPreferences != null && playerHost != null && channel != null &&
            (fullscreenChatOverlayEnabled || editingChatPosition)
        ) {
            val overlayActive = overlayHasOpacity || editingChatPosition
            val blur = if (overlayPreferences.blur) {
                val legacyFrame = if (Build.VERSION.SDK_INT < 31 && hasRenderedFirstFrame && overlayActive) {
                    rememberLegacyFullscreenChatFrame(playerTexture, isPlaying)
                } else null
                FullscreenChatVideoBlur(playerTexture, chatBlurBounds.takeIf { hasRenderedFirstFrame }) {
                    if (editingChatPosition) 1f else playerHost.fullscreenChatOverlayAlpha
                }
                FullscreenChatBlur(updateChatBlurBounds, legacyFrame)
            } else null
            CompositionLocalProvider(LocalChatContentActive provides overlayActive) {
                FullscreenChatOverlay(
                    host = playerHost,
                    channel = channel,
                    liveChatViewModel = liveChatViewModel,
                    preferences = overlayPreferences,
                    playing = overlayActive && loadedMediaId == stableId && isPlaying,
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
                    }.semantics { if (!overlayActive) hideFromAccessibility() },
                    interactive = overlayActive && !playerHost.isDirectChatDrag && !playerHost.isSideChatOpen(isWide = true),
                )
            }
        }

        val autoQualityLabel = stringResource(R.string.player_auto)
        val qualityLabel = remember(selectedQualityHeight, currentVideoHeight, autoQualityLabel) {
            when {
                selectedQualityHeight > 0 -> "${selectedQualityHeight}p"
                currentVideoHeight > 0 -> "${currentVideoHeight}p ($autoQualityLabel)"
                else -> autoQualityLabel
            }
        }

        val viewersText = remember(channel?.livestream?.viewerCount, viewersLocale, resources) {
            "${FormatUtils.formatViewersCount(channel?.livestream?.viewerCount, viewersLocale)} ${
                resources.getString(
                    R.string.viewers
                )
            }"
        }

        val handleTogglePlay = {
            val p = player
            if (p != null) {
                val shouldPlay = !playRequested
                playRequested = shouldPlay
                if (!shouldPlay) {
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
            val p = player
            if (p != null) {
                // A paused player's play transition already seeks in LiveResumeListener.
                if (p.playWhenReady) p.seekToDefaultPosition()
                p.play()
            }
        }

        if (!isInPipMode) AnimatedVisibility(
            visible = showControls && playbackError == null && !isMini && !editingChatPosition,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControlsOverlay(
                isFullScreen = isFullScreen,
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
                sleepRemainingSeconds = if (sleepTimerMinutes > 0) remainingTimerSeconds else 0,
                streamDuration = streamDuration,
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = { target -> player?.seekTo(target.coerceIn(0L, durationMs.coerceAtLeast(0L))) },
                onSeekingChanged = { isSeeking = it },
                onSeekBack = { player?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) } },
                onSeekForward = { player?.let { it.seekTo((it.currentPosition + 10_000).coerceIn(0, durationMs.coerceAtLeast(0))) } },
                isLive = isLiveStream,
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
                onOpenCastDialog = { showCastDialog = true },
                onToggleTheater = onToggleTheater,
                onCreateClip = if (clipVideoId != null || clipLiveSession != null) {
                    {
                        val active = player?.takeIf {
                            it.currentMediaItem?.mediaId == stableId && it.playbackState != Player.STATE_IDLE
                        }
                        val position = active?.let { current ->
                            if (clipLiveSession == null) current.currentPosition else {
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
//                    // Header
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(horizontal = 16.dp, vertical = 4.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        AnimatedVisibility(
//                            visible = settingsPage != PlayerSettingsPage.MAIN
//                        ) {
//                            IconButton(
//                                onClick = { settingsPage = PlayerSettingsPage.MAIN },
//                                modifier = Modifier.size(36.dp)
//                            ) {
//                                Icon(
//                                    painter = painterResource(id = R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
//                                    contentDescription = stringResource(R.string.player_back)
//                                )
//                            }
//                            Spacer(modifier = Modifier.width(16.dp))
//                        }
//                        Text(
//                            text = when (settingsPage) {
//                                PlayerSettingsPage.MAIN -> stringResource(R.string.settings)
//                                PlayerSettingsPage.QUALITY -> stringResource(R.string.player_quality)
//                                PlayerSettingsPage.SLEEP_TIMER -> stringResource(R.string.player_sleep_timer)
//                                PlayerSettingsPage.MORE -> stringResource(R.string.player_more)
//                            },
//                            style = MaterialTheme.typography.titleLarge,
//                            fontWeight = FontWeight.Bold,
//                            modifier = Modifier.weight(1f)
//                        )
//                        IconButton(
//                            onClick = closeSettings,
//                            modifier = Modifier.size(36.dp)
//                        ) {
//                            Icon(
//                                painter = painterResource(id = R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
//                                contentDescription = stringResource(R.string.player_close)
//                            )
//                        }
//                    }

//                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

                    AnimatedVisibility(
                        visible = settingsPage != PlayerSettingsPage.MAIN,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                                text = when (settingsPage) {
                                    PlayerSettingsPage.MAIN -> stringResource(R.string.settings)
                                    PlayerSettingsPage.QUALITY -> stringResource(R.string.player_quality)
                                    PlayerSettingsPage.SLEEP_TIMER -> stringResource(R.string.player_sleep_timer)
                                    PlayerSettingsPage.CHAT_OVERLAY -> stringResource(R.string.fullscreen_chat_title)
                                    PlayerSettingsPage.MORE -> stringResource(R.string.player_more)
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = settingsPage,
                        transitionSpec = {
                            if (initialState == PlayerSettingsPage.MAIN) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(
                                    slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()).togetherWith(
                                    slideOutHorizontally { it } + fadeOut())
                            }
                        },
                        label = "PlayerSettingsPageTransition"
                    ) { targetPage ->
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
                                                dismissSettings { downloadRecording(recording.copy(channelName = channelName, channelSlug = channel?.slug)) }
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
                                            applyQualityToPlayer(player, -1, availableQualities)
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
                                                applyQualityToPlayer(
                                                    player,
                                                    quality.height,
                                                    availableQualities
                                                )
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

@androidx.annotation.OptIn(UnstableApi::class)
private fun applyQualityToPlayer(
    player: Player?,
    selectedHeight: Int,
    availableQualities: List<VideoQualityOption>
) {
    val p = player ?: return
    if (selectedHeight == -1) {
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMinVideoSize(0, 0)
            .build()
    } else {
        val resolvedHeight = if (availableQualities.isNotEmpty()) {
            availableQualities.firstOrNull { it.height == selectedHeight }?.height
                ?: availableQualities.firstOrNull { it.height <= selectedHeight }?.height
                ?: availableQualities.lastOrNull()?.height
                ?: selectedHeight
        } else {
            selectedHeight
        }

        val targetWidth = resolvedHeight * 16 / 9
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(targetWidth, resolvedHeight)
            .setMinVideoSize(targetWidth, resolvedHeight)
            .build()
    }
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
private enum class PlayerSettingsPage { MAIN, QUALITY, SLEEP_TIMER, CHAT_OVERLAY, MORE }

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
    sleepRemainingSeconds: Int,
    streamDuration: String,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
    onSeekingChanged: (Boolean) -> Unit = {},
    onSeekBack: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    isLive: Boolean = false,
    isAtLiveEdge: Boolean = true,
    behindLiveText: String = "",
    onGoToLive: () -> Unit = {},
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit = {},
    onOpenSettings: () -> Unit,
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
            .background(Color.Black.copy(alpha = 0.5f))
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

        /**
         * Top
         */
        Row(
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            /**
             * Top Start/Left
             */
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
            ) {
                IconButton(
                    onClick = onMinimize,
                    modifier = chromeButtonModifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = "Minimize",
                        modifier = chromeIconModifier,
                        tint = Color.White,
                    )
                }
                Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = qualityLabel,
                        color = Color.White,
                        style = labelStyle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

            }
            /**
             * Top End/Right
             */
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
                ) {
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
                IconButton(
                    onClick = onSeekBack,
                    modifier = Modifier
                        .size(48.dp)
                ) {
                    Icon(painterResource(R.drawable.replay_10_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = "10 saniye geri", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(60.dp)
//                    .background(Color.Black.copy(alpha = 0.25f), CircleShape)
            ) {
                Icon(
                    painter = rememberPlayPausePainter(isPlaying),
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(60.dp)
                )
            }

            if (!isLive) {
                IconButton(onClick = onSeekForward, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.forward_10_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = "10 saniye ileri", tint = MaterialTheme.colorScheme.primary)
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
            if (!isLive) {
                var remaining by remember { mutableStateOf(false) }
                var scrubPosition by remember { mutableStateOf<Float?>(null) }
                val shownPosition = scrubPosition?.toLong() ?: positionMs
                Column(Modifier.fillMaxWidth().padding(horizontal = if (isCompact) 12.dp else 20.dp)) {
                    Text(
                        text = (if (remaining) "-" + FormatUtils.formatDuration((durationMs - shownPosition).coerceAtLeast(0))
                            else FormatUtils.formatDuration(shownPosition)) + " / " + FormatUtils.formatDuration(durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable { remaining = !remaining }.padding(vertical = 2.dp)
                    )
                    CompactPlayerSlider(
                        value = (scrubPosition ?: positionMs.toFloat()).coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()),
                        valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                        enabled = durationMs > 0,
                        wavyTrack = true,
                        animateWave = isPlaying,
                        onValueChange = { scrubPosition = it; onSeekingChanged(true) },
                        onValueChangeFinished = {
                            scrubPosition?.let { onSeek(it.toLong()) }
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
                    modifier = Modifier.weight(1f, fill = false),
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
                            softWrap = false
                        )

                    }
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
                    Spacer(modifier = Modifier.width(if (isCompact) 2.dp else 8.dp))
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

/**
 * Miniplayer
 */
@Composable
internal fun MiniControlsOverlay(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0f))
    ) {
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            PlayerChromeButton(
                painter = painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = stringResource(R.string.player_close),
                size = 48.dp,
                onClick = onClose
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            PlayerChromeButton(
                painter = rememberPlayPausePainter(isPlaying),
                contentDescription = "Play/Pause",
                size = 48.dp,
                onClick = onTogglePlay
            )
        }
    }
}

@Composable
internal fun ShellChrome(mode: PlayerMode, onMinimize: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (mode == PlayerMode.Mini) {
            PlayerChromeButton(
                painter = painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = stringResource(R.string.player_close),
                size = 48.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                onClick = onClose
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
