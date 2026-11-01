package com.lechixy.kick.screens.channel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.lechixy.kick.R
import com.lechixy.kick.components.CHAT_WIDTH_FRACTION
import com.lechixy.kick.components.CardSection
import com.lechixy.kick.components.ChannelCardSkeleton
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.components.LiveBadge
import com.lechixy.kick.components.LocalContentNavigation
import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.components.PlayerMode
import com.lechixy.kick.components.SkeletonBlock
import com.lechixy.kick.components.SkeletonLoadingScope
import com.lechixy.kick.components.VerifiedBadge
import com.lechixy.kick.components.VideoCard
import com.lechixy.kick.components.VideoSkeletonGrid
import com.lechixy.kick.components.WIDE_BREAKPOINT_DP
import com.lechixy.kick.components.account.rememberAccountFollowAction
import com.lechixy.kick.components.channel.AnimatedCounterText
import com.lechixy.kick.components.channel.ChannelAboutTabContent
import com.lechixy.kick.components.channel.ChannelTabPager
import com.lechixy.kick.components.channel.OfflineBannerScrollConnection
import com.lechixy.kick.components.channel.OfflineBannerState
import com.lechixy.kick.components.channel.OfflineChannelBanner
import com.lechixy.kick.components.channel.TagChip
import com.lechixy.kick.components.channel.chat.ChatView
import com.lechixy.kick.components.channel.resolveEventChipColors
import com.lechixy.kick.components.channel.resolveLanguageChipColors
import com.lechixy.kick.data.channel.model.ChannelClip
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelEvent
import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.channel.model.VideoThumbnail
import com.lechixy.kick.data.channel.model.getBannerUrl
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.player.model.toRecording
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.util.FormatUtils

enum class ChannelTab(
    @StringRes val label: Int,
) {
    CHAT(R.string.chat),
    HOME(R.string.home),
    ABOUT(R.string.about),
    VIDEOS(R.string.videos),
    CLIPS(R.string.clips),
    CATEGORIES(R.string.channel_categories);

    fun getLabel(context: Context): String {
        return context.getString(this.label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelScreen(
    channelSlug: String,
    modifier: Modifier = Modifier,
    playerHost: PlayerHostState? = null,
    viewModel: ChannelViewModel = viewModel(),
    onChannelLoaded: (ChannelDetail) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val raidNavigation = LocalContentNavigation.current
    state.raid?.let { raid ->
        com.lechixy.kick.components.channel.RaidDialog(
            sourceName = state.channel?.user?.username ?: channelSlug,
            raid = raid,
            onStay = viewModel::dismissRaid,
            onJoin = {
                viewModel.dismissRaid()
                raidNavigation.channel(raid.targetSlug)
            }
        )
    }


    val settingsRepository = LocalSettingsRepository.current
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val initialIsWide = configuration.screenWidthDp >= WIDE_BREAKPOINT_DP
    var compactSelectedTab by remember(channelSlug) { mutableStateOf(ChannelTab.CHAT) }
    var wideSelectedTab by remember(channelSlug) { mutableStateOf(ChannelTab.HOME) }
    val selectedTab = if (initialIsWide) wideSelectedTab else compactSelectedTab

    val followAction = rememberAccountFollowAction(channelSlug)

    var isProfilePhotoExpanded by remember(channelSlug) { mutableStateOf(false) }
    val isControlsVisible = playerHost?.isControlsVisible ?: true
    val isChatVisible = playerHost?.isChatVisible ?: true
    val chatWidthFraction = playerHost?.chatWidthFraction ?: CHAT_WIDTH_FRACTION
    val isTheaterMode = playerHost?.isTheaterMode ?: false

    val theaterAnim = remember {
        Animatable(if (isTheaterMode) 1f else 0f, visibilityThreshold = 0.0005f)
    }
    LaunchedEffect(isTheaterMode) {
        theaterAnim.animateTo(
            targetValue = if (isTheaterMode) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.0005f
            )
        )
    }

    LaunchedEffect(channelSlug) {
        viewModel.setRecordingChat(playerHost?.recording != null)
        val autoConnect = settingsRepository.get(SettingsKeys.AUTO_CONNECT_CHAT).first()
        viewModel.loadChannel(channelSlug, autoConnectChat = autoConnect)
    }

    LaunchedEffect(state.channel) {
        state.channel?.let(onChannelLoaded)
    }

    LaunchedEffect(state.liveAnnouncementId) {
        val announcementId = state.liveAnnouncementId ?: return@LaunchedEffect
        playerHost?.triggerLiveAnnouncement(announcementId)
        viewModel.consumeLiveAnnouncement(announcementId)
    }

    val isFullScreen = playerHost?.mode == PlayerMode.Fullscreen
    val inlineInsets = (if (isFullScreen) WindowInsets.systemBars else WindowInsets.systemBarsIgnoringVisibility)
        .union(WindowInsets.displayCutout)
    val density = LocalDensity.current

    val channel = state.channel
    LaunchedEffect(selectedTab, channel?.id) {
        if (selectedTab == ChannelTab.ABOUT) viewModel.loadAbout()
    }
    val offline = channel != null && channel.livestream == null && playerHost?.recording == null
    val offlineCollapse = remember(channelSlug, scope) { OfflineBannerState(scope) }
    val offlineDismissReset = remember(channelSlug) { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    LaunchedEffect(offline) { if (!offline) offlineCollapse.animateTo(0f) }
    androidx.compose.runtime.DisposableEffect(playerHost, offlineCollapse) {
        playerHost?.offlineCollapseProgress = offlineCollapse
        onDispose {
            if (playerHost?.offlineCollapseProgress === offlineCollapse) playerHost?.offlineCollapseProgress = null
        }
    }
    val tabScrollStates = remember(channelSlug) { ChannelTab.entries.associateWith { LazyListState() } }
    val channelContentState = tabScrollStates.getValue(selectedTab)
    val offlineScroll = remember(offline, channelContentState) {
        OfflineBannerScrollConnection(
            enabled = { offline },
            atTop = { !channelContentState.canScrollBackward },
            state = offlineCollapse
        )
    }
    var wasRecording by remember(viewModel) { mutableStateOf(false) }
    LaunchedEffect(playerHost?.recording?.id) {
        val replay = playerHost?.recording != null
        val returningLive = wasRecording && !replay
        wasRecording = replay
        viewModel.setRecordingChat(replay)
        if (returningLive && settingsRepository.get(SettingsKeys.AUTO_CONNECT_CHAT).first()) viewModel.connectChat()
    }
    val sharedChat = remember(viewModel, channel?.id, playerHost?.recording?.id) {
        movableContentOf<ChannelDetail, (() -> Unit)?, Modifier> { ch, onHide, chatMod ->
            if (playerHost?.recording != null) {
                com.lechixy.kick.components.channel.chat.RecordedChatView(
                    ch, playerHost.recording!!, playerHost, modifier = chatMod,
                    onHideChat = onHide, onOpenChannel = { slug -> playerHost.open(slug) })
            } else {
                ChannelChatSection(
                    viewModel = viewModel,
                    channel = ch,
                    onHideChat = onHide,
                    modifier = chatMod,
                    onOpenChannel = { slug -> playerHost?.open(slug) }
                )
            }
        }
    }

    SkeletonLoadingScope(active = state.isLoading ||
        (selectedTab == ChannelTab.CLIPS && state.isClipsLoading && state.clips.isEmpty()) ||
        (selectedTab == ChannelTab.ABOUT && (state.links.loading || state.goals.loading ||
            (!state.links.loaded && !state.links.failed)))) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(inlineInsets)
            ) {
                val screenWidthDp = maxWidth
                val isWide = screenWidthDp >= WIDE_BREAKPOINT_DP.dp
                val visibleTabs = remember(isWide) {
                    if (isWide) ChannelTab.entries.filter { it != ChannelTab.CHAT }
                    else ChannelTab.entries
                }
                val effectiveSelectedTab = if (isWide) wideSelectedTab else compactSelectedTab

                val pagerState = key(channelSlug, isWide, isFullScreen) {
                    rememberPagerState(initialPage = visibleTabs.indexOf(effectiveSelectedTab).coerceAtLeast(0)) { visibleTabs.size }
                }
                LaunchedEffect(pagerState, isFullScreen) {
                    snapshotFlow { pagerState.settledPage }.collect {
                        if (!isFullScreen) {
                            if (isWide) wideSelectedTab = visibleTabs[it]
                            else compactSelectedTab = visibleTabs[it]
                        }
                    }
                }
                LaunchedEffect(pagerState.currentPage, channel?.id) {
                    if (visibleTabs[pagerState.currentPage] == ChannelTab.ABOUT) viewModel.loadAbout()
                }
                val bannerWidthPx = remember(screenWidthDp, density) {
                    with(density) { screenWidthDp.toPx().toInt() }
                }

                var isDraggingHandle by remember { mutableStateOf(false) }
                var dragAccumulatedPx by remember { mutableFloatStateOf(0f) }
                var dragStartFraction by remember { mutableFloatStateOf(chatWidthFraction) }

                val hasSideChat = isWide || isFullScreen
                val isChatActuallyVisible =
                    playerHost?.isSideChatOpen(isWide) ?: (isWide && isChatVisible)

                Layout(
                    contents = listOf(
                        {
                            Column(
                                Modifier.fillMaxSize().nestedScroll(offlineScroll)
                            ) {
                                // Player'ın oturduğu yer (PlayerHost bu alanı işliyor)
                                if (channel != null && channel.livestream == null && playerHost?.recording == null) {
                                    val bannerUrl = remember(channel, bannerWidthPx) {
                                        channel.getBannerUrl(targetWidthPx = bannerWidthPx)
                                    }
                                    OfflineChannelBanner(
                                        channel = channel,
                                        bannerUrl = bannerUrl,
                                        collapseProgress = offlineCollapse,
                                        onCollapse = { offlineCollapse.animateTo(1f) },
                                        onBackClick = onBackClick,
                                        onTravelChanged = { offlineCollapse.travelPx = it },
                                        onDrag = { offlineCollapse.drag(it) },
                                        onDragFinished = offlineCollapse::settle,
                                        onDismissDrag = playerHost?.let { host -> { amount ->
                                            offlineDismissReset[0]?.cancel()
                                            host.dragOffsetY = (host.dragOffsetY + amount).coerceAtLeast(0f)
                                        } },
                                        onDismissDragFinished = { velocity, cancelled ->
                                            playerHost?.let { host ->
                                                val threshold = with(density) { 72.dp.toPx() }
                                                if (!cancelled && (host.dragOffsetY > threshold || velocity > 1800f)) onBackClick()
                                                else offlineDismissReset[0] = scope.launch {
                                                    androidx.compose.animation.core.animate(host.dragOffsetY, 0f,
                                                        animationSpec = tween(300)) { value, _ -> host.dragOffsetY = value }
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    Spacer(
                                        Modifier
                                            .fillMaxWidth()
                                            .layout { measurable, constraints ->
                                                val colWidth = constraints.maxWidth
                                                val fullHeightPx = (colWidth * 9f / 16f).roundToInt()
                                                val compactHeightPx = 84.dp.roundToPx().coerceAtMost(fullHeightPx)
                                                val normalHeightPx = (fullHeightPx + (compactHeightPx - fullHeightPx) * offlineCollapse.value).roundToInt()
                                                val hPx = if (isWide) {
                                                    val totalH = constraints.maxHeight
                                                    val headerPx =
                                                        if ((playerHost?.channelHeaderHeightPx ?: 0) > 0) {
                                                            playerHost!!.channelHeaderHeightPx
                                                        } else {
                                                            (totalH * 0.24f).roundToInt()
                                                        }
                                                    val theaterTargetPx =
                                                        (totalH - headerPx).coerceIn(0, totalH)
                                                    (normalHeightPx + (theaterTargetPx - normalHeightPx) * theaterAnim.value).roundToInt()
                                                } else {
                                                    normalHeightPx
                                                }
                                                val placeable = measurable.measure(
                                                    Constraints.fixed(colWidth, hPx.coerceAtLeast(0))
                                                )
                                                layout(placeable.width, placeable.height) {
                                                    placeable.place(0, 0)
                                                }
                                            }
                                    )
                                }

                                if (channel != null) {
                                    val isLive = channel.livestream != null && playerHost?.recording == null
                                    val hasPlayback = isLive || playerHost?.recording != null
                                    val lastLiveText = remember(state.videos, configuration) {
                                        val latestVideo = state.videos.firstOrNull()
                                        FormatUtils.formatLastLive(
                                            context, latestVideo?.startTime ?: latestVideo?.createdAt,
                                            true
                                        )
                                    }

                                    @Composable
                                    fun Header() {
                                        // ChannelHeaderInfo: geniş ekranda (tablet) theater mod dahil her zaman görünür, mobilde kontrollerle senkronize
                                        AnimatedVisibility(
                                            visible = isProfilePhotoExpanded || if (isWide || !hasPlayback) true else isControlsVisible,
                                            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                                            exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                                        ) {
                                            ChannelHeaderInfo(
                                                channel = channel,
                                                event = state.event,
                                                lastHourGiftedSubsRank = state.lastHourGiftedSubsRank,
                                                recording = playerHost?.recording,
                                                onPlayLive = {
                                                    val requestedRecording = playerHost?.recording?.id
                                                    viewModel.refreshLivePlayback { fresh ->
                                                        if (playerHost?.recording?.id == requestedRecording) {
                                                            playerHost?.onChannelLoaded(channelSlug, fresh)
                                                            playerHost?.playLive()
                                                        }
                                                    }
                                                },
                                                onProfilePhotoExpandedChange = { isProfilePhotoExpanded = it },
                                                isLive = isLive,
                                                lastLiveText = lastLiveText,
                                                isFollowing = followAction.following,
                                                followLabel = followAction.label,
                                                followEnabled = followAction.enabled,
                                                onFollowClick = followAction.onClick,
                                                context = context,
                                                onHeightMeasured = { heightPx ->
                                                    if (heightPx > 0 && playerHost?.channelHeaderHeightPx != heightPx) {
                                                        playerHost?.channelHeaderHeightPx = heightPx
                                                    }
                                                }
                                            )
                                        }

                                    }
                                    @Composable
                                    fun Tabs() {
                                        // Tabs remain available while scrolling; wide theater mode shows only the header.
                                        AnimatedVisibility(
                                            visible = !isWide || !isTheaterMode || !hasPlayback,
                                            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                                            exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                                        ) {
                                            ChannelTabsRow(
                                                tabs = visibleTabs,
                                                selectedTab = visibleTabs[pagerState.currentPage],
                                                onTabSelected = { tab -> scope.launch { pagerState.animateScrollToPage(visibleTabs.indexOf(tab)) } }
                                            )
                                        }

                                    }
                                    Header()
                                    if (!(isWide && isTheaterMode && hasPlayback)) {
                                        Tabs()
                                        ChannelTabPager(state = pagerState, tabs = visibleTabs,
                                            modifier = Modifier.fillMaxWidth().weight(1f)) { pageTab ->
                                            if (pageTab == ChannelTab.CHAT && !hasSideChat) {
                                                sharedChat(channel, null, Modifier.fillMaxSize())
                                            } else {
                                                LazyColumn(state = tabScrollStates.getValue(pageTab), modifier = Modifier.fillMaxSize()) {
                                                    item(key = "channel_content") {
                                                        ChannelTabContent(
                                                            tab = pageTab, channel = channel,
                                                            linksState = state.links, goalState = state.goals,
                                                            onRetryAbout = { viewModel.loadAbout(retry = true) },
                                                            videos = state.videos, clips = state.clips,
                                                            popularClips = state.popularClips, recentCategories = state.recentCategories,
                                                            onRetryHome = viewModel::loadHomeSections,
                                                            clipsError = state.clipsError, isClipsLoading = state.isClipsLoading,
                                                            onRetryClips = viewModel::retryClips,
                                                            onPlayRecording = { playerHost?.playRecording(it) },
                                                            onNavigateToTab = { target -> scope.launch {
                                                                pagerState.animateScrollToPage(visibleTabs.indexOf(target))
                                                            } }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        if (state.error != null) {
                                            Text(
                                                text = state.error ?: "Unknown error",
                                                color = MaterialTheme.colorScheme.error,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .padding(16.dp)
                                            )
                                        } else {
                                            androidx.compose.foundation.lazy.LazyColumn(
                                                Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                item { SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(22.dp)) }
                                                item { ChannelCardSkeleton() }
                                                item { SkeletonBlock(Modifier.fillMaxWidth().height(48.dp)) }
                                                item { VideoSkeletonGrid(Modifier.fillMaxWidth()) }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        {
                            // Yan sohbet paneli: hasSideChat aktifken her zaman compose'lu kalır,
                            // böylece sola kaydırmanın ilk karesinde sıfırdan oluşturulmaz, GPU'da kayarak açılır.
                            if (hasSideChat && channel != null) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val openProgress = (playerHost?.chatOpenProgress?.value
                                                ?: if (isChatActuallyVisible) 1f else 0f).coerceIn(0f, 1f)
                                            alpha = openProgress
                                        }
                                ) {
                                    sharedChat(
                                        channel,
                                        { playerHost?.setSideChatVisible(visible = false, isWide = isWide) },
                                        Modifier.fillMaxSize()
                                    )

                                    if (isChatActuallyVisible || isDraggingHandle) {
                                        ChatResizeHandle(
                                            isDragging = isDraggingHandle,
                                            onDragStart = {
                                                isDraggingHandle = true
                                                dragAccumulatedPx = 0f
                                                dragStartFraction = chatWidthFraction
                                            },
                                            onDragEnd = {
                                                isDraggingHandle = false
                                                val currentFraction = playerHost?.chatWidthFraction
                                                if (currentFraction != null && playerHost.isSideChatOpen(isWide)) {
                                                    scope.launch {
                                                        settingsRepository.set(
                                                            SettingsKeys.CHAT_WIDTH_FRACTION,
                                                            currentFraction
                                                        )
                                                    }
                                                }
                                            },
                                            onDrag = { dragAmountPx ->
                                                dragAccumulatedPx += dragAmountPx
                                                val screenWidthPx = with(density) { screenWidthDp.toPx() }
                                                if (screenWidthPx > 0f) {
                                                    val deltaFraction = -dragAccumulatedPx / screenWidthPx
                                                    val newFraction = dragStartFraction + deltaFraction
                                                    if (newFraction < 0.18f) {
                                                        playerHost?.updateChatWidthFraction(dragStartFraction)
                                                        playerHost?.setSideChatVisible(
                                                            visible = false,
                                                            isWide = isWide
                                                        )
                                                        isDraggingHandle = false
                                                    } else {
                                                        playerHost?.updateChatWidthFraction(newFraction)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .fillMaxHeight()
                                                .width(24.dp)
                                                .offset(x = (-12).dp)
                                        )
                                    }
                                }
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxSize()
                ) { (mainMeasurables, sideChatMeasurables), constraints ->
                    val totalW = constraints.maxWidth
                    val totalH = constraints.maxHeight
                    val openProgress = if (hasSideChat) {
                        (playerHost?.chatOpenProgress?.value
                            ?: if (isChatActuallyVisible) 1f else 0f).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val targetChatW = (totalW * chatWidthFraction).roundToInt().coerceAtLeast(1)
                    val currentChatW = if (hasSideChat) (targetChatW * openProgress).roundToInt() else 0

                    // Tam ekranda sol sütun player'ın altında kaldığı için sabit genişlikte tutulur ->
                    // tam ekranda chat kaydırılırken arkadaki sayfa yeniden ölçülmez.
                    val mainColW = if (!hasSideChat || isFullScreen) {
                        totalW
                    } else {
                        (totalW - currentChatW).coerceAtLeast(0)
                    }

                    val mainPlaceable = mainMeasurables.firstOrNull()?.measure(
                        Constraints.fixed(mainColW, totalH)
                    )

                    val sideChatMeasureW = if (isDraggingHandle) {
                        currentChatW.coerceAtLeast(1)
                    } else {
                        targetChatW
                    }
                    val sideChatPlaceable = sideChatMeasurables.firstOrNull()?.measure(
                        Constraints.fixed(sideChatMeasureW, totalH)
                    )

                    layout(totalW, totalH) {
                        // Keep inline measurements for the return transition, but never draw
                        // channel details behind the fullscreen player or fading side chat.
                        if (!isFullScreen) mainPlaceable?.place(0, 0)
                        if (sideChatPlaceable != null && currentChatW > 0) {
                            sideChatPlaceable.place(totalW - currentChatW, 0)
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun ChannelChatSection(
    viewModel: ChannelViewModel,
    channel: ChannelDetail,
    onHideChat: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenChannel: (String) -> Unit = {}
) {
    val community: com.lechixy.kick.components.channel.chat.ChatCommunityViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(key = "community-${channel.slug}")
    val chatMessages by viewModel.chatMessages.collectAsState()
    val chatConnectionState by viewModel.chatConnectionState.collectAsState()
    val pinnedMessage by viewModel.pinnedMessage.collectAsState()
    val userColors by viewModel.userColors.collectAsState()
    val chatSettings by viewModel.chatSettings.collectAsState()
    val pointsUpdated by viewModel.pointsUpdated.collectAsState()
    val emotes by viewModel.emotes.collectAsState()
    val replySession by com.lechixy.kick.data.account.AccountGraph.auth.session.collectAsState()
    var replyTarget by remember(channel.id, replySession.generation) { mutableStateOf<com.lechixy.kick.data.chat.model.ChatMessage?>(null) }
    LaunchedEffect(chatMessages) {
        if (chatMessages.any { it.id == replyTarget?.id && it.isDeleted }) replyTarget = null
    }

    ChatView(
        messages = chatMessages,
        onReplyTo = { replyTarget = it },
        connectionState = chatConnectionState,
        subscriberBadges = channel.subscriberBadges,
        userColors = userColors,
        pinnedMessage = pinnedMessage,
        chatEvents = viewModel.chatEvents,
        streamerUsername = channel.user?.username,
        streamerSlug = channel.slug,
        channelId = channel.id,
        onConnectClick = viewModel::connectChat,
        onDisconnectClick = viewModel::disconnectChat,
        onHideChat = onHideChat,
        onOpenChannel = onOpenChannel,
        composer = { com.lechixy.kick.components.channel.chat.AccountChatComposer(
            channel, chatConnectionState, chatSettings, pointsUpdated,
            rulesSeen = { generation -> viewModel.rulesSeen(generation, channel.id) },
            markRulesSeen = { generation -> viewModel.markRulesSeen(generation, channel.id) },
            replyTo = replyTarget, onCancelReply = { replyTarget = null }, recentMessages = chatMessages, userColors = userColors,
            onUserCommand = { username -> community.openProfile(channel.slug,
                com.lechixy.kick.data.chat.model.ActiveChatter(username = username)) },
            emotes = emotes, onRetryEmotes = { viewModel.loadEmotes(retry = true) },
            onIdentityPreviewClick = { identity ->
                val account = com.lechixy.kick.data.account.AccountGraph.auth.session.value.profile
                if (account != null) community.openProfile(channel.slug,
                    com.lechixy.kick.data.chat.model.ActiveChatter(username = account.name, profilePicture = account.avatar),
                    com.lechixy.kick.data.chat.model.ChatSender(id = account.userId, username = account.name, identity = identity))
            }) },
        modifier = modifier
    )
}

/**
 * Components
 */

@Composable
private fun ChannelHeaderInfo(
    channel: ChannelDetail,
    event: ChannelEvent?,
    lastHourGiftedSubsRank: Int?,
    isLive: Boolean,
    lastLiveText: String,
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    followLabel: String,
    followEnabled: Boolean,
    context: Context,
    recording: RecordedPlayback? = null,
    onPlayLive: () -> Unit = {},
    onProfilePhotoExpandedChange: (Boolean) -> Unit = {},
    onHeightMeasured: (Int) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .onSizeChanged { onHeightMeasured(it.height) }
    ) {
        val liveAvailable = isLive && channel.livestream != null
        val recordingAvailable = recording != null
        if ((liveAvailable) || recordingAvailable) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 16.dp, 16.dp, 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when(liveAvailable) {
                            true -> channel.livestream.sessionTitle ?: "No title"
                            false -> recording?.title ?: "No title"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    if (liveAvailable) LiveBadge()
                    if (recordingAvailable) {
                        Text(
                            text = FormatUtils.formatLastLive(context, recording.startTime),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val categories = if (liveAvailable) {
                            channel.livestream.categories.map { it.name to it.slug }
                        } else {
                            recording?.categories?.map { it.name to it.slug }
                        }
                        val navigation = LocalContentNavigation.current
                        categories?.forEach { category ->
                            val name = category.first
                            val translatedName = when (name) {
                                "Just Chatting" -> stringResource(R.string.category_just_chatting)
                                else -> name
                            }
                            TagChip(tag = translatedName, onClick = { navigation.category(category.second) })
                        }
                        // Event
                        event?.title?.takeIf { it.isNotBlank() }?.let { eventTitle ->
                            val defaultContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            val defaultContentColor = MaterialTheme.colorScheme.primary
                            val (eventContainerColor, eventContentColor) = remember(
                                event.palette?.accentPromoPoster,
                                defaultContainerColor,
                                defaultContentColor
                            ) {
                                resolveEventChipColors(
                                    accentHex = event.palette?.accentPromoPoster,
                                    defaultContainerColor = defaultContainerColor,
                                    defaultContentColor = defaultContentColor
                                )
                            }
                            TagChip(
                                tag = eventTitle,
                                icon = R.drawable.local_activity_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                containerColor = eventContainerColor,
                                contentColor = eventContentColor
                            )
                        }
                        if (liveAvailable) {
                            channel.livestream.tags.forEach { tag ->
                                TagChip(tag = tag)
                            }
                        }

                        val languageLabel = if (liveAvailable) {
                            channel.livestream.language?.takeIf { it.isNotBlank() }
                        } else {
                            recording?.language?.takeIf { it.isNotBlank() }
                        }
                        if (!languageLabel.isNullOrBlank()) {
                            val defaultContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            val defaultContentColor = MaterialTheme.colorScheme.primary
                            val (langContainerColor, langContentColor) = remember(
                                languageLabel,
                                defaultContainerColor,
                                defaultContentColor
                            ) {
                                resolveLanguageChipColors(
                                    language = languageLabel,
                                    defaultContainerColor = defaultContainerColor,
                                    defaultContentColor = defaultContentColor
                                )
                            }
                            val translatedLabel = when (languageLabel) {
                                "English" -> stringResource(R.string.language_english)
                                "Turkish" -> stringResource(R.string.language_turkish)
                                else -> languageLabel
                            }
                            TagChip(
                                tag = translatedLabel,
                                containerColor = langContainerColor,
                                contentColor = langContentColor
                            )
                        }
                    }

                    val viewerCount = if (liveAvailable) {
                        channel.livestream.viewerCount
                    } else {
                        recording?.views?.toInt()
                    }

                    if (viewerCount != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = if (liveAvailable) painterResource(R.drawable.group_24dp_e3e3e3_fill0_wght400_grad0_opsz24) else painterResource(R.drawable.visibility_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                contentDescription = if (liveAvailable) stringResource(R.string.viewers) else stringResource(R.string.views),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            androidx.compose.runtime.key(channel.id) {
                                AnimatedCounterText(
                                    count = viewerCount,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = if (liveAvailable) stringResource(R.string.viewers) else stringResource(R.string.views),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.lechixy.kick.components.channel.ExpandableProfilePhoto(
                url = channel.user?.profilePic,
                username = channel.user?.username,
                onExpandedChange = onProfilePhotoExpandedChange,
                modifier = Modifier.size(56.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = channel.user?.username ?: channel.slug, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (channel.verified) VerifiedBadge(modifier = Modifier.size(16.dp), colors = listOf(
                            MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimaryContainer))
                    }
                    lastHourGiftedSubsRank?.let { com.lechixy.kick.components.channel.LastHourGiftedSubsChip(it) }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = FormatUtils.formatFollowersCount(channel.followersCount, context.resources.configuration.locales[0]),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.followers),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (!isLive && recording == null) {
                    Text(
                        text = lastLiveText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (recordingAvailable) {
                Button(
                    onClick = onPlayLive,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.play_live),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            com.lechixy.kick.components.FavoriteUserButton(
                channel.userId ?: channel.user?.id, channel.user?.username ?: channel.slug, channel.slug,
                matchButtonSize = true)

            Button(
                onClick = onFollowClick,
                enabled = followEnabled,
                shape = RoundedCornerShape(8.dp),
                colors = if (isFollowing) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = followLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ChannelTabsRow(
    tabs: List<ChannelTab>,
    selectedTab: ChannelTab,
    onTabSelected: (ChannelTab) -> Unit
) {
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    PrimaryScrollableTabRow(
        edgePadding = 8.dp,
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.getLabel(LocalContext.current),
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelTabContent(
    tab: ChannelTab,
    channel: ChannelDetail,
    linksState: com.lechixy.kick.data.channel.model.ChannelSectionState<List<com.lechixy.kick.data.channel.model.KickChannelLinkDto>>,
    goalState: com.lechixy.kick.data.channel.model.ChannelSectionState<List<com.lechixy.kick.data.channel.model.KickGoalDto>>,
    onRetryAbout: () -> Unit,
    videos: List<ChannelVideo>,
    clips: List<ChannelClip>,
    clipsError: String?,
    isClipsLoading: Boolean,
    onRetryClips: () -> Unit,
    onPlayRecording: (RecordedPlayback) -> Unit,
    popularClips: com.lechixy.kick.data.channel.model.ChannelSectionState<List<ChannelClip>>,
    recentCategories: com.lechixy.kick.data.channel.model.ChannelSectionState<List<com.lechixy.kick.data.browse.model.BrowseCategory>>,
    onRetryHome: () -> Unit,
    onNavigateToTab: (ChannelTab) -> Unit
) {
    when (tab) {
        ChannelTab.CHAT -> {
            // Managed in ChannelScreen main layout when selected
        }

        ChannelTab.HOME -> Column {
            if (videos.isNotEmpty()) CardSection(
                title = stringResource(R.string.recent_broadcasts), data = videos,
                modifier = Modifier.padding(16.dp), previewRows = 1,
                onMore = { onNavigateToTab(ChannelTab.VIDEOS) }) { video ->
                VideoCard(video = video, onClick = { video.toRecording()?.let(onPlayRecording) })
            } else ContentState(stringResource(R.string.no_recent_broadcasts))

            Column(Modifier.padding(16.dp)) {
                com.lechixy.kick.components.SectionHeader(stringResource(R.string.channel_popular_clips),
                    onMore = { onNavigateToTab(ChannelTab.CLIPS) })
                when {
                    popularClips.failed -> ContentState(stringResource(R.string.channel_section_error), onRetryHome)
                    popularClips.loaded && popularClips.value.orEmpty().isEmpty() -> ContentState(stringResource(R.string.media_no_clips))
                    else -> CardSection(title = null, data = popularClips.value.orEmpty(), previewRows = 1,
                        loading = popularClips.loading) { clip -> ChannelClipCard(clip, onPlayRecording) }
                }
            }
            ChannelCategoriesSection(recentCategories, onRetryHome, preview = true,
                onMore = { onNavigateToTab(ChannelTab.CATEGORIES) })
        }

        ChannelTab.CATEGORIES -> ChannelCategoriesSection(recentCategories, onRetryHome)

        ChannelTab.ABOUT -> ChannelAboutTabContent(channel, linksState, goalState, onRetryAbout)

        ChannelTab.VIDEOS -> {
            if (videos.isEmpty()) ContentState(stringResource(R.string.media_no_videos))
            else CardSection(title = null, data = videos, modifier = Modifier.padding(16.dp)) { video ->
                VideoCard(video = video, onClick = { video.toRecording()?.let(onPlayRecording) })
            }
        }
        ChannelTab.CLIPS -> {
            when {
                clipsError != null -> ContentState(stringResource(R.string.channel_clips_error), onRetryClips)
                !isClipsLoading && clips.isEmpty() -> ContentState(stringResource(R.string.media_no_clips))
                else -> CardSection(title = null, data = clips, modifier = Modifier.padding(16.dp),
                    loading = isClipsLoading, skeleton = { com.lechixy.kick.components.VideoCardSkeleton() }) { clip ->
                    ChannelClipCard(clip, onPlayRecording)
                }
            }
        }
    }
}

@Composable
private fun ChannelClipCard(clip: ChannelClip, onPlayRecording: (RecordedPlayback) -> Unit) {
    VideoCard(video = ChannelVideo(id = 0, sessionTitle = clip.title, duration = (clip.duration * 1000).toLong(),
        views = clip.views, thumbnail = VideoThumbnail(src = clip.thumbnailUrl)), progressId = clip.id,
        onClick = { onPlayRecording(clip.toRecording()) })
}

@Composable
private fun ChannelCategoriesSection(
    state: com.lechixy.kick.data.channel.model.ChannelSectionState<List<com.lechixy.kick.data.browse.model.BrowseCategory>>,
    onRetry: () -> Unit, preview: Boolean = false, onMore: (() -> Unit)? = null
) {
    val navigation = LocalContentNavigation.current
    Column(Modifier.padding(16.dp)) {
        if (preview) com.lechixy.kick.components.SectionHeader(
            stringResource(R.string.channel_recent_categories), onMore)
        when {
            state.failed -> ContentState(stringResource(R.string.channel_section_error), onRetry)
            state.loaded && state.value.orEmpty().isEmpty() -> ContentState(stringResource(R.string.channel_no_categories))
            else -> com.lechixy.kick.components.CategoryRow(
                categories = state.value.orEmpty(), loading = state.loading,
                onCategoryClick = navigation.category, showTag = true)

        }
    }
}

@Composable
private fun ChatResizeHandle(
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(150),
        label = "handleGlow"
    )
    val handleColor = if (isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (glowAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f * glowAlpha),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.5.dp)
                .background(handleColor)
        )

        if (glowAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

