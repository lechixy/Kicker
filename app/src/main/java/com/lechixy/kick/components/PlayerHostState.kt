package com.lechixy.kick.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json

import com.lechixy.kick.KickApp
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.player.model.RecordedPlayback

/**
 * Hidden     : oturum yok (player hiç yok, PiP kapalı, normal uygulama)
 * Expanded   : ChannelScreen açık, player sayfanın üstünde (16:9)
 * Fullscreen : ChannelScreen açık, player tam ekran
 * Mini       : ChannelScreen kapalı, player herhangi bir ekranın üstünde yüzen mini pencere
 */
enum class PlayerMode { Hidden, Expanded, Fullscreen, Mini }

/**
 * Player oturumunun TEK doğruluk kaynağı. KickApp'te yaşar, NavHost'un ÜSTÜNDEKİ PlayerHost'u besler.
 * Player artık ChannelScreen'in içinde değil; bu yüzden ekran değişse de yayın durmaz.
 */
@Stable
class PlayerHostState(
    initialMode: PlayerMode = PlayerMode.Hidden,
    initialSlug: String? = null,
    initialChatWidthFraction: Float = 0.35f
) {
    // Read by PlayerHost during layout so returning live shares the page's height animation.
    var offlineCollapseProgress by mutableStateOf<androidx.compose.runtime.State<Float>?>(null)
    var mode by mutableStateOf(initialMode)
        private set
    var slug by mutableStateOf(initialSlug)
        private set
    var pageSlug by mutableStateOf(initialSlug)   // sayfada gösterilen kanal (oturumdan farklı olabilir)
        private set

    /** Son yüklenen kanal detayı: ChannelScreen kapansa (VM ölse) bile mini player bununla yaşar. */
    var channel by mutableStateOf<ChannelDetail?>(null)
        private set

    var recording by mutableStateOf<RecordedPlayback?>(null)
        private set

    var recordingPositionMs by androidx.compose.runtime.mutableLongStateOf(0L)
        private set

    fun updateRecordingPosition(id: String, positionMs: Long) {
        if (recording?.id == id) recordingPositionMs = positionMs
    }

    fun playRecording(recording: RecordedPlayback) {
        recordingPositionMs = 0L
        this.recording = recording
        activeLiveAnnouncementId = null
        pendingAutoFullscreen = false
        if (mode == PlayerMode.Mini) mode = PlayerMode.Expanded
    }

    fun updateRecordingMetadata(id: String, title: String, durationMs: Long) {
        val current = recording?.takeIf { it.id == id } ?: return
        recording = current.copy(title = title, durationMs = durationMs)
    }

    fun playLive() {
        recording = null
        activeLiveAnnouncementId = null
    }

    var isExiting by mutableStateOf(false)
        private set

    val isChannelVisible get() = (mode == PlayerMode.Expanded || mode == PlayerMode.Fullscreen) && !isExiting

    var dragOffsetY by mutableFloatStateOf(0f)

    var pendingAutoFullscreen by mutableStateOf(false)
        private set

    var isTheaterMode by mutableStateOf(false)

    var isChatVisible by mutableStateOf(true)

    var isFullscreenNavigationVisible by mutableStateOf(false)
        private set
    var isTheaterNavigationVisible by mutableStateOf(false)
        private set
    val isPlayerNavigationVisible: Boolean
        get() = if (mode == PlayerMode.Fullscreen) isFullscreenNavigationVisible
        else mode == PlayerMode.Expanded && isTheaterMode && isTheaterNavigationVisible

    val navigationOpenProgress = Animatable(1f, visibilityThreshold = 0.0005f)
    var isDirectNavigationDrag by mutableStateOf(false)
    var navigationWidthPx by mutableIntStateOf(0)
    var navigationReleaseVelocity = 0f
    private var inlineChatVisible = true
    private var inlineTheaterMode = false

    fun updateFullscreenNavigationVisibility(visible: Boolean) {
        if (mode == PlayerMode.Fullscreen) isFullscreenNavigationVisible = visible
    }

    fun updatePlayerNavigationVisibility(visible: Boolean) {
        when (mode) {
            PlayerMode.Fullscreen -> isFullscreenNavigationVisible = visible
            PlayerMode.Expanded -> if (isTheaterMode) isTheaterNavigationVisible = visible
            else -> Unit
        }
    }

    suspend fun snapNavigationOpenProgress(progress: Float) {
        navigationOpenProgress.snapTo(progress.coerceIn(0f, 1f))
    }

    suspend fun animateNavigationOpenProgressTo(target: Float, initialVelocity: Float = 0f) {
        navigationOpenProgress.animateTo(target.coerceIn(0f, 1f), spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.0005f
        ), initialVelocity)
    }

    var chatWidthFraction by mutableFloatStateOf(initialChatWidthFraction.coerceIn(0.18f, 0.60f))

    /**
     * Yan sohbet panelinin açılma oranı (0f = tamamen kapalı, 1f = tamamen açık).
     * Sadece layout ve draw fazlarında okunur -> sürükleme/animasyon sırasında recomposition olmaz,
     * PlayerHost ve ChannelScreen aynı karede aynı değeri görür (1 karelik SideEffect gecikmesi yok).
     */
    val chatOpenProgress = Animatable(0f, visibilityThreshold = 0.0005f)

    var isDirectChatDrag by mutableStateOf(false)

    var channelHeaderHeightPx by mutableIntStateOf(0)

    var isControlsVisible by mutableStateOf(false)

    fun effectiveChatFraction(hasSideChat: Boolean): Float {
        if (!hasSideChat) return 0f
        return (chatWidthFraction * chatOpenProgress.value.coerceIn(0f, 1f)).coerceIn(0f, 0.60f)
    }

    fun sideChatWidthPx(totalWidthPx: Int, hasSideChat: Boolean): Int {
        if (!hasSideChat || totalWidthPx <= 0) return 0
        return (totalWidthPx * effectiveChatFraction(hasSideChat = true)).roundToInt()
    }

    suspend fun snapChatOpenProgress(progress: Float) {
        chatOpenProgress.snapTo(progress.coerceIn(0f, 1f))
    }

    suspend fun animateChatOpenProgressTo(target: Float, initialVelocity: Float = 0f) {
        chatOpenProgress.animateTo(
            targetValue = target.coerceIn(0f, 1f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.0005f
            ),
            initialVelocity = initialVelocity
        )
    }

    fun isSideChatOpen(isWide: Boolean): Boolean {
        return if (mode == PlayerMode.Fullscreen) {
            isTheaterMode && isChatVisible
        } else {
            isWide && isChatVisible
        }
    }

    fun updateChatVisibility(visible: Boolean) {
        if (mode == PlayerMode.Fullscreen) {
            if (visible) {
                if (!isTheaterMode || !isChatVisible) {
                    isTheaterMode = true
                    isChatVisible = true
                }
            } else {
                if (isTheaterMode || isChatVisible) {
                    isChatVisible = false
                    isTheaterMode = false
                }
            }
        } else isChatVisible = visible
    }

    fun toggleTheaterMode() {
        isTheaterMode = !isTheaterMode
        isTheaterNavigationVisible = false
        if (isTheaterMode) {
            isChatVisible = true
        }
    }

    fun toggleChatVisibility() {
        if (mode == PlayerMode.Fullscreen) {
            if (isTheaterMode && isChatVisible) {
                isChatVisible = false
                isTheaterMode = false
            } else {
                isTheaterMode = true
                isChatVisible = true
            }
        } else {
            isChatVisible = !isChatVisible
        }
    }

    fun updateChatWidthFraction(fraction: Float) {
        chatWidthFraction = fraction.coerceIn(0.18f, 0.60f)
    }

    fun channelMediaHeightLimit(viewportHeight: Int, tabSpace: Int): Int {
        val headerHeight = channelHeaderHeightPx.takeIf { it > 0 } ?: (viewportHeight * 0.24f).roundToInt()
        return (viewportHeight - headerHeight - tabSpace).coerceAtLeast(0)
    }

    var expansionSource by mutableStateOf<com.lechixy.kick.components.ExpansionSource?>(null)
        private set

    var activeLiveAnnouncementId by mutableStateOf<Long?>(null)
        private set

    fun triggerLiveAnnouncement(announcementId: Long) {
        if (recording == null) activeLiveAnnouncementId = announcementId
    }

    fun clearLiveAnnouncement(announcementId: Long) {
        if (activeLiveAnnouncementId == announcementId) {
            activeLiveAnnouncementId = null
        }
    }

    fun open(slug: String, source: com.lechixy.kick.components.ExpansionSource? = null) {
        if (mode == PlayerMode.Fullscreen) setFullscreen(false)
        if (this.slug != slug) {
            recording = null
            channel = null
            activeLiveAnnouncementId = null
        }
        this.slug = slug
        pageSlug = slug
        this.expansionSource = if (mode == PlayerMode.Hidden) source else null
        isExiting = false
        mode = PlayerMode.Expanded
        dragOffsetY = 0f
        pendingAutoFullscreen = true
    }

    fun clearExpansionSource() {
        expansionSource = null
    }

    fun onChannelLoaded(slug: String, detail: ChannelDetail) {
        if (slug != this.slug) return
        if (channel != detail) {
            channel = detail
        }
        if (detail.livestream == null && recording == null) {            // Canlı veya kayıt yoksa player/mini olmasın
            pendingAutoFullscreen = false
            activeLiveAnnouncementId = null
            when (mode) {
                PlayerMode.Fullscreen -> setFullscreen(false)
                PlayerMode.Mini -> close()
                else -> Unit
            }
            isTheaterMode = false
            isFullscreenNavigationVisible = false
        }
    }

    fun consumeAutoFullscreen(enterFullscreen: Boolean) {
        if (!pendingAutoFullscreen) return
        pendingAutoFullscreen = false
        if (enterFullscreen && mode == PlayerMode.Expanded && channel?.livestream != null) {
            setFullscreen(true)
        }
    }

    fun setFullscreen(enabled: Boolean) {
        pendingAutoFullscreen = false
        isDirectNavigationDrag = false
        isTheaterNavigationVisible = false
        if (enabled && mode == PlayerMode.Expanded) {
            inlineChatVisible = isChatVisible
            inlineTheaterMode = isTheaterMode
            isFullscreenNavigationVisible = false
            isDirectChatDrag = false
            mode = PlayerMode.Fullscreen
        } else if (!enabled && mode == PlayerMode.Fullscreen) {
            mode = PlayerMode.Expanded
            isChatVisible = inlineChatVisible
            isTheaterMode = inlineTheaterMode
            isFullscreenNavigationVisible = false
            isDirectChatDrag = false
        }
    }

    fun requestClose() {
        if (mode == PlayerMode.Hidden || isExiting) return
        isExiting = true
    }

    fun finishClose() {
        isExiting = false
        close()
    }

    fun minimize() {
        pendingAutoFullscreen = false
        isDirectNavigationDrag = false
        isTheaterNavigationVisible = false
        if (slug == null || mode == PlayerMode.Hidden) return
        if (mode == PlayerMode.Fullscreen) setFullscreen(false)
        clearExpansionSource()
        isDirectChatDrag = false
        val offline = recording == null && (channel == null || channel?.livestream == null)
        if (offline) requestClose() else mode = PlayerMode.Mini   // Oynatılacak içerik yoksa kapat
    }

    fun expand() {
        clearExpansionSource()
        if (slug != null) mode = PlayerMode.Expanded
        dragOffsetY = 0f
    }

    fun close() {
        isExiting = false
        dragOffsetY = 0f
        pendingAutoFullscreen = false
        activeLiveAnnouncementId = null
        mode = PlayerMode.Hidden
        slug = null
        channel = null
        recording = null
        pageSlug = null
        isTheaterMode = false
        isFullscreenNavigationVisible = false
        isTheaterNavigationVisible = false
        isDirectNavigationDrag = false
        isChatVisible = true
        isDirectChatDrag = false
        expansionSource = null
    }

    companion object {
        val Saver = listSaver<PlayerHostState, Any?>(
            save = {
                listOf(it.mode.name, it.slug, it.chatWidthFraction,
                    it.recording?.let { recording -> Json.encodeToString(recording) })
            },
            restore = {
                PlayerHostState(
                    initialMode = PlayerMode.valueOf(it[0] as String),
                    initialSlug = it[1] as String?,
                    initialChatWidthFraction = (it.getOrNull(2) as? Float) ?: 0.35f
                ).apply {
                    recording = (it.getOrNull(3) as? String)?.let { saved ->
                        runCatching { Json.decodeFromString<RecordedPlayback>(saved) }.getOrNull()
                    }
                }
            }
        )
    }
}

@Composable
fun rememberPlayerHostState(): PlayerHostState =
    rememberSaveable(saver = PlayerHostState.Saver) { PlayerHostState() }
