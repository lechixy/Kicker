package com.lechixy.kick.components

import android.content.pm.ActivityInfo
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lechixy.kick.components.AppLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.lechixy.kick.R
import com.lechixy.kick.components.channel.OfflineBannerMinHeight
import com.lechixy.kick.data.channel.model.getBannerUrl
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.util.buildOptimizedImageRequest
import com.lechixy.kick.util.findActivity

internal const val WIDE_BREAKPOINT_DP = 700
internal const val CHAT_WIDTH_FRACTION = 0.35f
internal val ChannelTabSpace = 144.dp

private const val MINI_DEFAULT_WIDTH_DP = com.lechixy.kick.data.settings.model.MiniPlayerSize.DEFAULT_WIDTH_DP
private const val MINI_MIN_WIDTH_DP = com.lechixy.kick.data.settings.model.MiniPlayerSize.MIN_WIDTH_DP
private const val MINI_MAX_WIDTH_DP = com.lechixy.kick.data.settings.model.MiniPlayerSize.MAX_WIDTH_DP
private const val MINI_PADDING_DP = 16f

private const val NORM_THRESHOLD = 0.0005f
private const val PX_THRESHOLD = 0.5f

private class ViewportMetrics {
    var containerW: Int = 0
    var containerH: Int = 0
    var safeArea: IntRect = IntRect.Zero
    var lastPlayerFrame: IntRect = IntRect.Zero
    var lastPlayerViewport: IntSize = IntSize.Zero

    fun update(cw: Int, ch: Int, area: IntRect) {
        containerW = cw
        containerH = ch
        safeArea = area
    }
}

/**
 * NavHost'un ÜSTÜNDE duran tek player katmanı.
 *
 * Tüm görünüm TEK bir değerden türer: frameT  (-1 = mini, 0 = inline 16:9, 1 = tam ekran).
 * Çerçeve (konum+boyut) layout fazında hesaplanır -> animasyon/sürükleme sırasında recomposition yok,
 * KickPlayerView hiç yeniden oluşmaz, video durmaz.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerHost(
    host: PlayerHostState,
    isInPipMode: Boolean,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    pageContent: @Composable (String) -> Unit,
    modifier: Modifier = Modifier,
    navigationRailAvailable: Boolean = false,
    enteringPip: Boolean = false
) {
    if (host.slug == null || host.mode == PlayerMode.Hidden) return

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val mode = host.mode
    val pipVideoOnly = isInPipMode || enteringPip

    val expansion = host.expansionSource
    val expandProgress = remember { Animatable(0f, visibilityThreshold = NORM_THRESHOLD) }
    val isExpanding = expansion != null
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    val previewThumbnailUrl = remember(host.slug) { expansion?.thumbnailUrl }

    val exitAlpha = remember { Animatable(1f, visibilityThreshold = NORM_THRESHOLD) }
    val exitScale = remember { Animatable(1f, visibilityThreshold = NORM_THRESHOLD) }

    LaunchedEffect(host.isExiting) {
        if (host.isExiting) {
            kotlinx.coroutines.coroutineScope {
                launch { exitAlpha.animateTo(0f, tween(350, easing = FastOutSlowInEasing)) }
                launch { exitScale.animateTo(0.94f, tween(350, easing = FastOutSlowInEasing)) }
            }
            host.finishClose()
            exitAlpha.snapTo(1f)
            exitScale.snapTo(1f)
        } else {
            exitAlpha.snapTo(1f)
            exitScale.snapTo(1f)
        }
    }

    val frameT = remember { Animatable(0f, visibilityThreshold = NORM_THRESHOLD) }
    val theaterAnim = remember {
        Animatable(if (host.isTheaterMode) 1f else 0f, visibilityThreshold = NORM_THRESHOLD)
    }
    var releaseVelocity by remember { mutableFloatStateOf(0f) }
    var releaseChatVelocity by remember { mutableFloatStateOf(0f) }
    val settleJobs = remember { mutableListOf<Job>() }
    var pageAlpha by remember { mutableFloatStateOf(0f) }   // 0'dan başlar -> açılışta fade-in
    var fsScale by remember { mutableFloatStateOf(1f) }
    var fsDy by remember { mutableFloatStateOf(0f) }
    val showPage by remember {
        derivedStateOf {
            host.mode == PlayerMode.Expanded || host.mode == PlayerMode.Fullscreen || pageAlpha > 0.01f
        }
    }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backEdge by remember { mutableIntStateOf(0) }
    PredictiveBackHandler(enabled = !pipVideoOnly && !host.isExiting &&
        (mode == PlayerMode.Expanded || mode == PlayerMode.Fullscreen)) { events ->
        val startFrame = frameT.value
        val startAlpha = pageAlpha
        try {
            frameT.stop()
            events.collect { event ->
                backProgress = event.progress
                backEdge = event.swipeEdge
                if (mode == PlayerMode.Fullscreen) frameT.snapTo(1f - event.progress)
                else {
                    frameT.snapTo(-event.progress)
                    pageAlpha = 1f - event.progress
                }
            }
            if (mode == PlayerMode.Fullscreen) host.setFullscreen(false) else host.minimize()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                kotlinx.coroutines.coroutineScope {
                    launch { frameT.animateTo(startFrame, tween(200)) }
                    launch { animate(pageAlpha, startAlpha, animationSpec = tween(200)) { v, _ -> pageAlpha = v } }
                    launch { animate(backProgress, 0f, animationSpec = tween(200)) { v, _ -> backProgress = v } }
                }
            }
        } finally { backProgress = 0f }
    }
    val loaded = host.channel
    val playerVisible =
        host.recording != null || loaded == null || loaded.livestream != null   // yüklenirken shell, canlıysa player, offline'da yok
    var dragScale by remember { mutableFloatStateOf(1f) }   // madde 3
    var playerDy by remember { mutableFloatStateOf(0f) }    // madde 4
    val metrics = remember { ViewportMetrics() }
    val systemInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val cutoutInsets = WindowInsets.displayCutout

    // Mini pencere: konum, hareket aralığının oranı olarak tutulur (0..1) => boyut/rotasyon değişse de köşede kalır.
    val settingsRepository = LocalSettingsRepository.current
    var miniWidthDp by remember { mutableFloatStateOf(MINI_DEFAULT_WIDTH_DP) }
    var fx by remember { mutableFloatStateOf(1f) }
    var fy by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(settingsRepository) {
        settingsRepository.get(SettingsKeys.MINI_PLAYER_WIDTH_DP).collect { savedWidthDp ->
            miniWidthDp = savedWidthDp.coerceIn(MINI_MIN_WIDTH_DP, MINI_MAX_WIDTH_DP)
        }
    }

    LaunchedEffect(host.isTheaterMode) {
        theaterAnim.animateTo(
            targetValue = if (host.isTheaterMode) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = NORM_THRESHOLD
            )
        )
    }

    val isWideConfig = configuration.screenWidthDp >= WIDE_BREAKPOINT_DP
    val shouldOpenSideChat = host.isSideChatOpen(isWideConfig)

    LaunchedEffect(host.slug) {
        host.snapChatOpenProgress(if (host.isSideChatOpen(isWideConfig)) 1f else 0f)
    }

    LaunchedEffect(shouldOpenSideChat, host.isDirectChatDrag, pipVideoOnly) {
        if (!pipVideoOnly && !host.isDirectChatDrag) {
            val v = releaseChatVelocity
            releaseChatVelocity = 0f
            host.animateChatOpenProgressTo(
                target = if (shouldOpenSideChat) 1f else 0f,
                initialVelocity = v
            )
        }
    }

    val targetT = when {
        mode == PlayerMode.Fullscreen -> 1f
        mode == PlayerMode.Mini -> -1f
        else -> 0f
    }

    LaunchedEffect(mode, isExpanding) {
        if (isExpanding) {
            pageAlpha = 1f
            return@LaunchedEffect
        }
        animate(
            pageAlpha,
            if (mode == PlayerMode.Mini) 0f else 1f,
            animationSpec = tween(250)
        ) { v, _ -> pageAlpha = v }
    }

    LaunchedEffect(expansion) {
        if (expansion == null) return@LaunchedEffect
        expandProgress.snapTo(0f)
        expandProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        if (host.expansionSource === expansion) host.clearExpansionSource()
    }

    fun localSourceRect(rect: androidx.compose.ui.geometry.Rect): IntRect =
        rect.translate(-hostOrigin).roundToIntRect()

    var orientationBeforeFullscreen by remember { mutableStateOf<Int?>(null) }
    var enteredFullscreenFromPortrait by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val window = activity?.window
        val prevRotationAnim = window?.attributes?.rotationAnimation
        if (window != null) {
            val params = window.attributes
            params.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE
            window.attributes = params
        }
        onDispose {
            activity?.let { act ->
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                orientationBeforeFullscreen?.let { orientation ->
                    if (!act.isInPictureInPictureMode && !act.isFinishing && !act.isDestroyed) {
                        act.requestedOrientation = orientation
                    }
                }
                if (prevRotationAnim != null) {
                    val params = act.window.attributes
                    params.rotationAnimation = prevRotationAnim
                    act.window.attributes = params
                }
            }
        }
    }

    LaunchedEffect(targetT, pipVideoOnly, activity) {
        if (pipVideoOnly) return@LaunchedEffect
        val v = releaseVelocity
        releaseVelocity = 0f
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        val enteringFullscreen = targetT == 1f
        val isPhone = configuration.smallestScreenWidthDp in 1 until 600
        val isPortraitNow = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val allowLandscapeRequest = !isPhone || activity?.isInMultiWindowMode != true
        val priorOrientation = orientationBeforeFullscreen
        if (enteringFullscreen) {
            if (priorOrientation == null && activity != null) {
                orientationBeforeFullscreen = activity.requestedOrientation
                enteredFullscreenFromPortrait = isPortraitNow
            }
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            dragScale = 1f
            playerDy = 0f
            fsScale = 1f
            fsDy = 0f
        } else if (priorOrientation != null) {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }

        val rotates = if (enteringFullscreen) isPortraitNow else
            priorOrientation != null && enteredFullscreenFromPortrait && !isPortraitNow
        kotlinx.coroutines.coroutineScope {
            launch {
                // Start moving the video before Android takes its rotation snapshot on phones.
                if (isPhone && rotates && allowLandscapeRequest && ValueAnimator.areAnimatorsEnabled() &&
                    frameT.value != targetT) delay(120)
                if (activity != null && !activity.isInPictureInPictureMode &&
                    !activity.isFinishing && !activity.isDestroyed) {
                    val orientation = when {
                        !enteringFullscreen -> priorOrientation
                        allowLandscapeRequest -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else -> null
                    }
                    if (orientation != null && activity.requestedOrientation != orientation) {
                        activity.requestedOrientation = orientation
                    }
                }
                if (!enteringFullscreen) {
                    orientationBeforeFullscreen = null
                    enteredFullscreenFromPortrait = false
                }
            }
            if (rotates && !isPhone) {
                frameT.snapTo(targetT)
            } else if (v != 0f) {
                frameT.animateTo(targetT, spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = NORM_THRESHOLD
                ), initialVelocity = v)
            } else {
                frameT.animateTo(targetT, tween(300, easing = FastOutSlowInEasing))
            }
        }
        if (targetT == 0f) {
            fsScale = 1f
            fsDy = 0f
        }
    }

    fun px(dp: Float) = with(density) { dp.dp.toPx() }
    val wideBreakpointPx = with(density) { WIDE_BREAKPOINT_DP.dp.roundToPx() }

    fun MeasureScope.resolveSafeArea(cw: Int, ch: Int): IntRect {
        val left = systemInsets.getLeft(this, layoutDirection)
        val top = systemInsets.getTop(this)
        val right = (cw - systemInsets.getRight(this, layoutDirection)).coerceAtLeast(left)
        val bottom = (ch - systemInsets.getBottom(this)).coerceAtLeast(top)
        return IntRect(left, top, right, bottom)
    }

    fun MeasureScope.resolveFullscreenSafeArea(cw: Int, ch: Int): IntRect {
        val left = cutoutInsets.getLeft(this, layoutDirection)
        val top = cutoutInsets.getTop(this)
        val right = (cw - cutoutInsets.getRight(this, layoutDirection)).coerceAtLeast(left)
        val bottom = (ch - cutoutInsets.getBottom(this)).coerceAtLeast(top)
        return IntRect(left, top, right, bottom)
    }

    fun inlineRect(safeArea: IntRect): IntRect {
        val wide = safeArea.width >= wideBreakpointPx
        val chatW = host.sideChatWidthPx(safeArea.width, hasSideChat = wide)
        val w = (safeArea.width - chatW).coerceAtLeast(0)
        val naturalHeight = (w * 9f / 16f).roundToInt()
        val fullHeight = if (wide) {
            min(naturalHeight, host.channelMediaHeightLimit(
                safeArea.height, px(ChannelTabSpace.value).roundToInt()))
        } else naturalHeight
        val compactHeight = px(OfflineBannerMinHeight.value).roundToInt().coerceAtMost(fullHeight)
        val playerHeight = (fullHeight + (compactHeight - fullHeight) *
            (host.offlineCollapseProgress?.value ?: 0f)).roundToInt()
        val headerPx = if (host.channelHeaderHeightPx > 0) {
            host.channelHeaderHeightPx
        } else {
            (safeArea.height * 0.24f).roundToInt()
        }
        val theaterTargetHeight = (safeArea.height - headerPx).coerceIn(0, safeArea.height)
        val h = if (wide) {
            (playerHeight + (theaterTargetHeight - playerHeight) * theaterAnim.value).roundToInt()
        } else {
            playerHeight
        }
        return IntRect(safeArea.left, safeArea.top, safeArea.left + w, safeArea.top + h)
    }

    fun inlineDraggedRect(safeArea: IntRect): IntRect {
        val r = inlineRect(safeArea)
        val cx = (r.left + r.right) / 2f
        val cy = (r.top + r.bottom) / 2f
        val w = r.width * dragScale
        val h = r.height * dragScale
        val leftPx = (cx - w / 2f).roundToInt()
        val topFromBottom = r.bottom - h
        val topFromCenter = cy - h / 2f
        val topPx = (topFromBottom + (topFromCenter - topFromBottom) * theaterAnim.value).roundToInt()
        return IntRect(leftPx, topPx, leftPx + w.roundToInt(), topPx + h.roundToInt())
    }

    fun miniWidthPx(safeArea: IntRect): Float {
        val availableW = (safeArea.width - 2 * px(MINI_PADDING_DP)).coerceAtLeast(1f)
        val minW = min(px(MINI_MIN_WIDTH_DP), availableW)
        val maxW = max(minW, min(availableW, px(MINI_MAX_WIDTH_DP)))
        return px(miniWidthDp).coerceIn(minW, maxW)
    }

    fun miniRangeX(safeArea: IntRect) =
        (safeArea.width - miniWidthPx(safeArea) - 2 * px(MINI_PADDING_DP)).coerceAtLeast(1f)

    fun miniRangeY(safeArea: IntRect) =
        (safeArea.height - miniWidthPx(safeArea) * 9f / 16f - 2 * px(MINI_PADDING_DP)).coerceAtLeast(1f)

    fun miniRect(safeArea: IntRect): IntRect {
        val w = miniWidthPx(safeArea)
        val h = w * 9f / 16f
        val pad = px(MINI_PADDING_DP)
        val l = safeArea.left + pad + fx * miniRangeX(safeArea)
        val t = safeArea.top + pad + fy * miniRangeY(safeArea)
        val leftPx = l.roundToInt()
        val topPx = t.roundToInt()
        return IntRect(leftPx, topPx, leftPx + w.roundToInt(), topPx + h.roundToInt())
    }

    fun lerpRect(a: IntRect, b: IntRect, f: Float): IntRect {
        val l = a.left + (b.left - a.left) * f
        val t = a.top + (b.top - a.top) * f
        val w = a.width + (b.width - a.width) * f
        val h = a.height + (b.height - a.height) * f
        val leftPx = l.roundToInt()
        val topPx = t.roundToInt()
        return IntRect(leftPx, topPx, leftPx + w.roundToInt(), topPx + h.roundToInt())
    }

    fun currentT() = frameT.value

    fun fullRect(cw: Int, ch: Int, fsSafeArea: IntRect): IntRect {
        val chatOpen = host.chatOpenProgress.value.coerceIn(0f, 1f)
        val chatW = host.sideChatWidthPx(fsSafeArea.width, hasSideChat = true)
        val availableRight = cw + (fsSafeArea.right - cw) * chatOpen - chatW
        val availableLeft = fsSafeArea.left * chatOpen
        val availableW = (availableRight - availableLeft).coerceAtLeast(1f)
        val w = availableW * fsScale
        val h = ch * fsScale
        val l = availableLeft + (availableW - w) / 2f
        val t = (ch - h) / 2f + fsDy
        val leftPx = l.roundToInt()
        val topPx = t.roundToInt()
        return IntRect(leftPx, topPx, leftPx + w.roundToInt(), topPx + h.roundToInt())
    }

    fun frameAt(t: Float, cw: Int, ch: Int, safeArea: IntRect, fsSafeArea: IntRect): IntRect {
        val base = inlineDraggedRect(safeArea)
        return if (t >= 0f) lerpRect(base, fullRect(cw, ch, fsSafeArea), t.coerceAtMost(1f))
        else lerpRect(base, miniRect(safeArea), (-t).coerceAtMost(1f))
    }

    fun snapMini() {
        val safeArea = metrics.safeArea
        val rx = miniRangeX(safeArea)
        val ry = miniRangeY(safeArea)
        val dx = min(fx, 1f - fx) * rx
        val dy = min(fy, 1f - fy) * ry
        val cornerReach = miniWidthPx(safeArea) * 0.6f
        var tx = fx
        var ty = fy
        if (dx <= dy) {
            tx = fx.roundToInt().toFloat()
            if (dy < cornerReach) ty = fy.roundToInt().toFloat()
        } else {
            ty = fy.roundToInt().toFloat()
            if (dx < cornerReach) tx = fx.roundToInt().toFloat()
        }
        val sx = fx
        val sy = fy
        scope.launch {
            animate(
                sx,
                tx,
                animationSpec = spring(stiffness = 400f, visibilityThreshold = NORM_THRESHOLD)
            ) { v, _ ->
                fx = v
            }
        }
        scope.launch {
            animate(
                sy,
                ty,
                animationSpec = spring(stiffness = 400f, visibilityThreshold = NORM_THRESHOLD)
            ) { v, _ ->
                fy = v
            }
        }
        val currentMiniWidthDp = miniWidthDp
        scope.launch {
            settingsRepository.set(SettingsKeys.MINI_PLAYER_WIDTH_DP, currentMiniWidthDp)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .then(if (pipVideoOnly) Modifier.background(Color.Black) else Modifier)
            .onGloballyPositioned { hostOrigin = it.positionInRoot() }
            .layout { measurable, constraints ->
                val cw = constraints.maxWidth
                val ch = constraints.maxHeight
                metrics.update(cw, ch, resolveSafeArea(cw, ch))
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
            .graphicsLayer {
                translationY = if (!playerVisible && !pipVideoOnly) host.dragOffsetY else 0f
                scaleX = if (pipVideoOnly) 1f else exitScale.value
                scaleY = scaleX
                alpha = if (pipVideoOnly) 1f else exitAlpha.value
            }
    ) {
        if (showPage) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        // Keep page state through PiP without measuring a tablet layout at PiP sizes.
                        val page = if (pipVideoOnly) null else measurable.measure(constraints)
                        layout(constraints.maxWidth, constraints.maxHeight) { page?.place(0, 0) }
                    }
                    // Reveal a page measured at its final size. Resizing its constraints
                    // on every frame reflows chat and changes the adaptive layout mid-flight.
                    .graphicsLayer {
                        translationY = playerDy
                        scaleX = 1f - .04f * backProgress
                        scaleY = 1f - .04f * backProgress
                        translationX = (if (backEdge == 0) 1f else -1f) * 36.dp.toPx() * backProgress
                        alpha = pageAlpha
                        if (isExpanding) {
                            val p = expandProgress.value
                            val source = localSourceRect(expansion.cardRect)
                            val shell = lerpRect(source, IntRect(0, 0, size.width.roundToInt(), size.height.roundToInt()), p)
                            val radius = expansion.cornerRadius.toPx() * (1f - p)
                            shape = GenericShape { _, _ ->
                                addRoundRect(RoundRect(shell.left.toFloat(), shell.top.toFloat(),
                                    shell.right.toFloat(), shell.bottom.toFloat(), CornerRadius(radius)))
                            }
                            clip = true
                        } else {
                            clip = false
                        }
                    }
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isExpanding) {
                                val p = expandProgress.value
                                val destContentAlpha = ((p - 0.22f) / 0.78f).coerceIn(0f, 1f)
                                translationY = 18.dp.toPx() * (1f - destContentAlpha)
                                alpha = destContentAlpha
                            } else {
                                translationY = 0f
                                alpha = 1f
                            }
                        }
                ) {
                    host.pageSlug?.let { s -> key(s) { pageContent(s) } }
                }
            }
        }

        if (isExpanding && !pipVideoOnly) {
            // Card Source Details (Phase 1: quickly fades out in first ~70ms)
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val cw = constraints.maxWidth
                        val ch = constraints.maxHeight
                        val safeArea = resolveSafeArea(cw, ch)
                        metrics.update(cw, ch, safeArea)
                        val p = expandProgress.value
                        val destRect = IntRect(0, 0, cw, ch)
                        val sourceCardRect = localSourceRect(expansion.cardRect)
                        val sourceMediaRect =
                            expansion.mediaRect?.let(::localSourceRect) ?: sourceCardRect
                        val shellRect = lerpRect(sourceCardRect, destRect, p)
                        val currentMediaRect = lerpRect(sourceMediaRect, inlineRect(safeArea), p)
                        val mediaH =
                            if (expansion.isStreamCard) {
                                currentMediaRect.height
                            } else {
                                0
                            }
                        val detailsTop = shellRect.top + mediaH
                        val detailsHeight = (shellRect.height - mediaH).coerceAtLeast(0)
                        val pl = measurable.measure(
                            Constraints.fixed(shellRect.width.coerceAtLeast(0), detailsHeight)
                        )
                        layout(cw, ch) {
                            pl.place(shellRect.left, detailsTop)
                        }
                    }
                    .graphicsLayer {
                        val p = expandProgress.value
                        val radiusPx =
                            (expansion.cornerRadius.toPx() * (1f - p)).coerceAtLeast(0f)
                        shape = RoundedCornerShape(
                            bottomStart = radiusPx,
                            bottomEnd = radiusPx
                        )
                        clip = radiusPx > 0.5f
                        alpha = (1f - (p / 0.18f)).coerceIn(0f, 1f)
                    }
            ) {
                if (expansion.isStreamCard) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!expansion.avatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = remember(expansion.avatarUrl) {
                                    buildOptimizedImageRequest(
                                        context = context,
                                        data = expansion.avatarUrl,
                                        targetWidthDp = 44,
                                        targetHeightDp = 44
                                    )
                                },
                                contentDescription = expansion.username,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = expansion.title ?: "No Title",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = expansion.username ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            expansion.category?.let { cat ->
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!expansion.avatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = remember(expansion.avatarUrl) {
                                    buildOptimizedImageRequest(
                                        context = context,
                                        data = expansion.avatarUrl,
                                        targetWidthDp = 48,
                                        targetHeightDp = 48
                                    )
                                },
                                contentDescription = expansion.username,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = expansion.title ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = expansion.username ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        // Keep the real surface mounted throughout the connected transition so loading
        // starts immediately and the final frame never swaps to a newly created player.
        if (playerVisible) {
            Box(
                Modifier
                    .drawWithContent {
                        // The swipe preview grows inside the video slot, below surrounding UI.
                        if (!pipVideoOnly && mode == PlayerMode.Expanded && dragScale > 1f) {
                            val bounds = inlineRect(metrics.safeArea)
                            clipRect(
                                bounds.left.toFloat(), bounds.top.toFloat(),
                                bounds.right.toFloat(), bounds.bottom.toFloat()
                            ) { this@drawWithContent.drawContent() }
                        } else {
                            drawContent()
                        }
                    }
                    .layout { measurable, constraints ->
                        val cw = constraints.maxWidth
                        val ch = constraints.maxHeight
                        val safeArea = resolveSafeArea(cw, ch)
                        val fsSafeArea = resolveFullscreenSafeArea(cw, ch)
                        metrics.update(cw, ch, safeArea)
                        val viewport = IntSize(cw, ch)
                        val f = if (pipVideoOnly) {
                            // Keep the source position while the system starts the PiP crop.
                            if (enteringPip && !isInPipMode && viewport == metrics.lastPlayerViewport) metrics.lastPlayerFrame
                            else IntRect(0, 0, cw, ch)
                        } else {
                            val target = frameAt(currentT(), cw, ch, safeArea, fsSafeArea)
                            val frame = if (isExpanding) {
                                val source = if (expansion.isStreamCard) expansion.mediaRect ?: expansion.cardRect
                                    else expansion.cardRect
                                lerpRect(localSourceRect(source), target, expandProgress.value)
                            } else target
                            frame.translate(IntOffset(0, playerDy.roundToInt())).also {
                                metrics.lastPlayerFrame = it
                                metrics.lastPlayerViewport = viewport
                            }
                        }
                        val p = measurable.measure(
                            Constraints.fixed(
                                f.width.coerceAtLeast(0),
                                f.height.coerceAtLeast(0)
                            )
                        )
                        // The viewport stays fixed, including when the swipe preview is wider than it.
                        layout(cw, ch) {
                            p.place(f.left, f.top)
                        }
                    }
                    .graphicsLayer {
                        val m = if (pipVideoOnly) 0f else (-currentT()).coerceIn(0f, 1f)
                        alpha = if (!pipVideoOnly && isExpanding && !expansion.isStreamCard)
                            ((expandProgress.value - 0.18f) / 0.42f).coerceIn(0f, 1f) else 1f
                        val openingRadius = if (!pipVideoOnly && isExpanding)
                            expansion.cornerRadius.toPx() * (1f - expandProgress.value) else 0f
                        shape = RoundedCornerShape(max(openingRadius, 12.dp.toPx() * m))
                        clip = true
                        shadowElevation = 12.dp.toPx() * m
                    }
                    // --- Mini: sürükle + pinch ile boyutlandır + bırakınca kenara/köşeye oturt; tek tık -> genişlet
                    .pointerInput(mode, pipVideoOnly) {
                        if (mode != PlayerMode.Mini || pipVideoOnly) return@pointerInput
                        detectTapGestures(onTap = { onExpand() })
                    }
                    .pointerInput(mode, pipVideoOnly) {
                        if (mode != PlayerMode.Mini || pipVideoOnly) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var moved = false
                            var accum = Offset.Zero
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.fastAny { it.isConsumed }) break
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                accum += pan
                                if (!moved && (accum.getDistance() > viewConfiguration.touchSlop || zoom != 1f)) moved =
                                    true
                                if (moved) {
                                    val safeArea = metrics.safeArea
                                    miniWidthDp = (miniWidthDp * zoom).coerceIn(
                                        MINI_MIN_WIDTH_DP,
                                        MINI_MAX_WIDTH_DP
                                    )
                                    fx = (fx + pan.x / miniRangeX(safeArea)).coerceIn(0f, 1f)
                                    fy = (fy + pan.y / miniRangeY(safeArea)).coerceIn(0f, 1f)
                                    event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.fastAny { it.pressed })
                            if (moved) snapMini()
                        }
                    }
                    // --- Inline/Tam ekran: dikey sürükleme. Sürüklerken sadece biraz büyür/küçülür, bırakınca karar verilir.
                    .pointerInput(mode, pipVideoOnly) {
                        if (mode == PlayerMode.Mini || pipVideoOnly) return@pointerInput
                        var total = 0f
                        var committed = false
                        val tracker = VelocityTracker()
                        val commitPx = 72.dp.toPx()
                        val limitPx = 180.dp.toPx()
                        val travelPx = 240.dp.toPx()
                        val scaleDistPx = 160.dp.toPx()
                        val backNorm =
                            spring<Float>(stiffness = 300f, visibilityThreshold = NORM_THRESHOLD)
                        val backPx =
                            spring<Float>(stiffness = 300f, visibilityThreshold = PX_THRESHOLD)

                        fun fsDyMax() =
                            metrics.containerH / 2f - 0.2f * metrics.containerH * fsScale  // videonun %30'u dışarıda

                        fun cancelSettle() {
                            settleJobs.forEach { it.cancel() }; settleJobs.clear()
                        }

                        fun settle() {
                            cancelSettle()
                            settleJobs += scope.launch {
                                if (dragScale != 1f) {
                                    launch {
                                        animate(
                                            dragScale,
                                            1f,
                                            animationSpec = backNorm
                                        ) { v, _ -> dragScale = v }
                                    }
                                }
                                if (playerDy != 0f) {
                                    launch {
                                        animate(
                                            playerDy,
                                            0f,
                                            animationSpec = backPx
                                        ) { v, _ -> playerDy = v }
                                    }
                                }
                                if (pageAlpha != 1f) {
                                    launch {
                                        animate(
                                            pageAlpha,
                                            1f,
                                            animationSpec = backNorm
                                        ) { v, _ -> pageAlpha = v }
                                    }
                                }
                                if (fsScale != 1f) {
                                    launch {
                                        animate(
                                            fsScale,
                                            1f,
                                            animationSpec = backNorm
                                        ) { v, _ -> fsScale = v }
                                    }
                                }
                                if (fsDy != 0f) {
                                    launch {
                                        animate(fsDy, 0f, animationSpec = backPx) { v, _ ->
                                            fsDy = v
                                        }
                                    }
                                }
                            }
                        }

                        fun goMini() {
                            committed = true
                            scope.launch {
                                animate(
                                    playerDy,
                                    0f,
                                    animationSpec = backPx
                                ) { v, _ -> playerDy = v }
                            }
                            onMinimize()
                        }

                        detectVerticalDragGestures(
                            onDragStart = {
                                cancelSettle()
                                committed = false
                                tracker.resetTracking()
                                total = when (mode) {
                                    PlayerMode.Fullscreen -> fsDy
                                    PlayerMode.Expanded ->
                                        if (dragScale > 1f) {
                                            // dragScale = 1 + 0.2 * up / (up + travel)  ->  up'ı geri çöz
                                            val k = ((dragScale - 1f) / 0.2f).coerceIn(0f, 0.95f)
                                            -(travelPx * k / (1f - k))
                                        } else playerDy

                                    else -> 0f
                                }
                            },
                            onVerticalDrag = { change, dy ->
                                if (committed) return@detectVerticalDragGestures
                                total += dy
                                tracker.addPosition(change.uptimeMillis, change.position)
                                when (mode) {
                                    PlayerMode.Expanded -> {
                                        val up = (-total).coerceAtLeast(0f)
                                        dragScale = 1f + 0.2f * up / (up + travelPx)
                                        val down = total.coerceIn(0f, limitPx)
                                        playerDy = down
                                        pageAlpha =
                                            1f - down / limitPx          // kaydıkça fade, arkadaki ekran belirir
                                        if (total >= limitPx) goMini()
                                    }

                                    PlayerMode.Fullscreen -> {
                                        val y = total.coerceAtLeast(0f)
                                        fsScale =
                                            1f - 0.1f * (y / scaleDistPx).coerceAtMost(1f)   // scale burada durur
                                        fsDy =
                                            y.coerceAtMost(fsDyMax())                           // kayma devam eder
                                    }

                                    else -> Unit
                                }
                            },
                            onDragCancel = { settle() },
                            onDragEnd = {
                                if (committed) return@detectVerticalDragGestures
                                val vy = tracker.calculateVelocity().y
                                val tVel = -vy / metrics.containerH.coerceAtLeast(1)
                                when {
                                    mode == PlayerMode.Expanded && (total < -commitPx || (total < -commitPx * 0.5f && vy < -1800f)) -> scope.launch {
                                        releaseVelocity = tVel.coerceAtLeast(0.5f)
                                        launch {
                                            animate(
                                                dragScale,
                                                1f,
                                                animationSpec = backNorm
                                            ) { v, _ -> dragScale = v }
                                        }
                                        host.setFullscreen(true)
                                    }

                                    mode == PlayerMode.Expanded && (total > 0.6f * limitPx || vy > 1800f) -> goMini()
                                    mode == PlayerMode.Fullscreen && (fsDy > 0.45f * fsDyMax() || vy > 1800f) -> {
                                        releaseVelocity = tVel.coerceAtMost(-0.5f)
                                        host.setFullscreen(false)   // bulunduğu çerçeveden 16:9'a doğru devam eder
                                    }

                                    else -> settle()
                                }
                            }
                        )
                    }
                    // --- Inline (geniş ekran) / Tam ekran: yatay sürükleme ile sohbeti akıcı bir şekilde gizle/aç.
                    // Büyük ekranda sağa: sohbeti kapat, ardından gezinmeyi aç.
                    // Sola: gezinmeyi kapat, ardından sohbeti aç. Telefon akışı yalnızca sohbeti yönetir.
                    .pointerInput(mode, pipVideoOnly, navigationRailAvailable) {
                        if (mode == PlayerMode.Mini || pipVideoOnly) return@pointerInput
                        var totalDx = 0f
                        var startProgress = 0f
                        var startNavigationProgress = 0f
                        var navigationWasVisible = false
                        var chatWasOpen = false
                        var gestureActive = false
                        var theaterGesture = false
                        var navigationGesture = false
                        var gestureTargetChosen = false
                        val tracker = VelocityTracker()
                        val minCommitPx = 42.dp.toPx()

                        detectHorizontalDragGestures(
                            onDragStart = {
                                gestureActive = mode == PlayerMode.Fullscreen || mode == PlayerMode.Expanded
                                theaterGesture = mode == PlayerMode.Expanded && !host.isTheaterMode
                                navigationGesture = false
                                gestureTargetChosen = false
                                navigationWasVisible = host.isPlayerNavigationVisible
                                chatWasOpen = host.isSideChatOpen(metrics.safeArea.width >= wideBreakpointPx)
                                startNavigationProgress = host.navigationOpenProgress.value
                                totalDx = 0f
                                tracker.resetTracking()
                                if (gestureActive) {
                                    startProgress = host.chatOpenProgress.value
                                    host.isDirectChatDrag = !theaterGesture
                                }
                            },
                            onHorizontalDrag = { change, dx ->
                                if (!gestureActive) return@detectHorizontalDragGestures
                                totalDx += dx
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val canDragNavigation = navigationRailAvailable &&
                                    (mode == PlayerMode.Fullscreen || (mode == PlayerMode.Expanded && host.isTheaterMode))
                                if (!gestureTargetChosen) {
                                    navigationGesture = canDragNavigation &&
                                        (navigationWasVisible || (!chatWasOpen && totalDx > 0f))
                                    gestureTargetChosen = true
                                }
                                if (navigationGesture) {
                                    host.isDirectNavigationDrag = true
                                    host.isDirectChatDrag = false
                                    val railWidth = host.navigationWidthPx.coerceAtLeast(1)
                                    scope.launch {
                                        host.snapNavigationOpenProgress(startNavigationProgress + totalDx / railWidth)
                                    }
                                    return@detectHorizontalDragGestures
                                }
                                if (theaterGesture && totalDx < 0f) return@detectHorizontalDragGestures
                                host.isDirectChatDrag = true
                                val baseWidth = metrics.safeArea.width.coerceAtLeast(1)
                                val chatMaxPx =
                                    (baseWidth * host.chatWidthFraction).coerceAtLeast(1f)
                                val deltaProgress = -totalDx / chatMaxPx
                                val newProgress = (startProgress + deltaProgress).coerceIn(0f, 1f)
                                scope.launch {
                                    host.snapChatOpenProgress(newProgress)
                                }
                            },
                            onDragCancel = {
                                if (navigationGesture) {
                                    host.navigationReleaseVelocity = 0f
                                    host.isDirectNavigationDrag = false
                                } else if (gestureActive) {
                                    val wide = metrics.safeArea.width >= wideBreakpointPx
                                    val target = if (host.isSideChatOpen(wide)) 1f else 0f
                                    host.isDirectChatDrag = false
                                    scope.launch {
                                        host.animateChatOpenProgressTo(target)
                                    }
                                }
                                gestureActive = false
                            },
                            onDragEnd = {
                                if (!gestureActive) {
                                    host.isDirectChatDrag = false
                                    return@detectHorizontalDragGestures
                                }
                                val wide = metrics.safeArea.width >= wideBreakpointPx
                                val baseWidth = metrics.safeArea.width.coerceAtLeast(1)
                                val chatMaxPx =
                                    (baseWidth * host.chatWidthFraction).coerceAtLeast(1f)
                                val commitPx = min(minCommitPx, chatMaxPx * 0.25f)
                                val vx = tracker.calculateVelocity().x
                                if (navigationGesture) {
                                    val railWidth = host.navigationWidthPx.coerceAtLeast(1)
                                    val targetOpen = if (navigationWasVisible) {
                                        !(totalDx < 0f && (-totalDx >= minCommitPx || vx <= -550f) && vx < 350f)
                                    } else {
                                        totalDx > 0f && (totalDx >= minCommitPx || vx >= 550f) && vx > -350f
                                    }
                                    host.navigationReleaseVelocity = (vx / railWidth).coerceIn(-8f, 8f)
                                    host.updatePlayerNavigationVisibility(targetOpen)
                                    host.isDirectNavigationDrag = false
                                    host.isDirectChatDrag = false
                                    gestureActive = false
                                    return@detectHorizontalDragGestures
                                }
                                val progressVel = (-vx / chatMaxPx).coerceIn(-8f, 8f)
                                val startedOpen = startProgress >= 0.5f

                                val targetOpen = if (startedOpen) {
                                    val shouldHide = totalDx > 0f &&
                                        (totalDx >= commitPx || vx >= 550f) &&
                                        vx > -350f
                                    !shouldHide
                                } else {
                                    val shouldShow = totalDx < 0f &&
                                        (-totalDx >= commitPx || vx <= -550f) &&
                                        vx < 350f
                                    shouldShow
                                }
                                val shouldEnterTheater = theaterGesture && totalDx < 0f &&
                                    (-totalDx >= commitPx || vx <= -550f) && vx < 350f
                                if (shouldEnterTheater) {
                                    host.toggleTheaterMode()
                                }
                                releaseChatVelocity = progressVel
                                host.updateChatVisibility(visible = targetOpen || shouldEnterTheater)
                                host.isDirectChatDrag = false
                                scope.launch {
                                    host.animateChatOpenProgressTo(
                                        target = if (targetOpen || shouldEnterTheater) 1f else 0f,
                                        initialVelocity = progressVel
                                    )
                                }
                                gestureActive = false
                            }
                        )
                    }
            ) {
                PlayerSurface(
                    host = host,
                    mode = mode,
                    previewThumbnailUrl = previewThumbnailUrl,
                    isInPipMode = pipVideoOnly,
                    onMinimize = onMinimize,
                    onClose = onClose
                )
            }
        }
    }
}

/**
 * Player surface
 */
@Composable
private fun PlayerSurface(
    host: PlayerHostState,
    mode: PlayerMode,
    previewThumbnailUrl: String? = null,
    isInPipMode: Boolean,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val channel = host.channel
    val recording = host.recording
    val isLive = recording == null && channel?.livestream != null
    var resolvedUrl by rememberSaveable(recording?.id) { mutableStateOf(recording?.directUrl) }
    var resolvedDuration by rememberSaveable(recording?.id) { mutableStateOf(recording?.durationMs ?: 0L) }
    var resolvedTitle by rememberSaveable(recording?.id) { mutableStateOf(recording?.title.orEmpty()) }
    var loadError by remember(recording?.id) { mutableStateOf<String?>(null) }
    var retry by remember(recording?.id) { mutableStateOf(0) }
    LaunchedEffect(recording?.id, retry) {
        if (recording == null || !resolvedUrl.isNullOrBlank()) return@LaunchedEffect
        loadError = null
        try {
            val repository = KickRepository(
                KickNetwork.api
            )
            val resolved = repository.resolveRecording(recording)
            resolvedUrl = resolved.directUrl
            resolvedDuration = resolved.durationMs
            resolvedTitle = resolved.title
            host.updateRecordingMetadata(recording.id, resolvedTitle, resolvedDuration)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadError = "Kayıt yüklenemedi. Tekrar dene"
        }
    }
    val playbackUrl = if (recording != null) resolvedUrl else channel?.playbackUrl
    val announcementId = host.activeLiveAnnouncementId

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black), contentAlignment = Alignment.Center
    ) {
        if (channel != null && (isLive || recording != null) && !playbackUrl.isNullOrBlank()) {
            // PiP parametreleri (sourceRectHint, autoEnter) KickPlayerView içinde yönetiliyor.
            KickPlayerView(
                playbackUrl = playbackUrl,
                previewThumbnailUrl = previewThumbnailUrl,
                channelName = channel.user?.username ?: channel.slug,
                streamTitle = if (recording != null) resolvedTitle else channel.livestream?.sessionTitle ?: "Canlı Yayın",
                recording = recording?.copy(title = resolvedTitle, durationMs = resolvedDuration),
                onPlaybackPositionChanged = { position -> recording?.let { host.updateRecordingPosition(it.id, position) } },
                channel = channel,
                isFullScreen = mode == PlayerMode.Fullscreen,
                isTheaterMode = host.isTheaterMode,
                isChatVisible = if (mode == PlayerMode.Fullscreen) (host.isTheaterMode && host.isChatVisible) else host.isChatVisible,
                onToggleChat = host::toggleChatVisibility,
                isInPipMode = isInPipMode,
                isMini = mode == PlayerMode.Mini,
                hideBufferingIndicator = announcementId != null || host.expansionSource != null,
                pendingAutoFullscreen = host.pendingAutoFullscreen && host.expansionSource == null,
                onConsumeAutoFullscreen = host::consumeAutoFullscreen,
                onFullScreenChange = host::setFullscreen,
                onToggleTheater = host::toggleTheaterMode,
                onMinimize = onMinimize,
                onClose = onClose,
                onControlsVisibilityChanged = { host.isControlsVisible = it },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val bannerUrl = remember(channel) { channel?.getBannerUrl(targetWidthPx = 1080) }
            val displayImage = previewThumbnailUrl ?: bannerUrl
            if (!displayImage.isNullOrEmpty()) {
                AsyncImage(
                    model = remember(displayImage) {
                        buildOptimizedImageRequest(
                            context = context,
                            data = displayImage,
                            targetWidthDp = 400,
                            targetHeightDp = 225
                        )
                    },
                    contentDescription = "Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (channel != null) {
                Text(
                    stringResource(R.string.channel_offline),
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recording != null) {
                if (loadError == null) AppLoadingIndicator()
                else TextButton(onClick = { retry++ }) { Text(loadError.orEmpty()) }
            }
            if (!isInPipMode && host.expansionSource == null) {
                ShellChrome(mode = mode, onMinimize = {
                    if (mode == PlayerMode.Fullscreen) host.setFullscreen(false) else onMinimize()
                }, onClose = onClose)
            }
        }

        if (announcementId != null && isLive && !isInPipMode) {
            val streamerName = channel.user?.username?.takeIf { it.isNotBlank() } ?: channel.slug
            StreamerIsLiveOverlay(
                username = streamerName,
                announcementId = announcementId,
                onFinished = { host.clearLiveAnnouncement(announcementId) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
