package com.lechixy.kick.screens.channel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.LocalChatContentActive
import com.lechixy.kick.components.channel.chat.collectAsChatState
import com.lechixy.kick.components.CHAT_WIDTH_FRACTION
import com.lechixy.kick.components.CardSection
import com.lechixy.kick.components.ChannelTabSpace
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
import com.lechixy.kick.components.rememberRecordingDownloadAction
import com.lechixy.kick.components.channel.AnimatedCounterText
import com.lechixy.kick.components.channel.ChannelAboutTabContent
import com.lechixy.kick.components.channel.ChannelHeaderScrollConnection
import com.lechixy.kick.components.channel.ChannelTabPager
import com.lechixy.kick.components.channel.OfflineBannerState
import com.lechixy.kick.components.channel.OfflineChannelBanner
import com.lechixy.kick.components.channel.TagChip
import com.lechixy.kick.components.channel.chat.ChatView
import com.lechixy.kick.components.channel.chat.ChatRestoreButton
import com.lechixy.kick.components.channel.offlineBannerCompactHeight
import com.lechixy.kick.components.channel.resolveEventChipColors
import com.lechixy.kick.components.channel.resolveLanguageChipColors
import com.lechixy.kick.data.channel.model.ChannelClip
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelEvent
import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.channel.model.VideoThumbnail
import com.lechixy.kick.data.channel.model.getBannerUrl
import com.lechixy.kick.data.chat.model.ChatTimestampMode
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
    var videoSort by rememberSaveable(channelSlug) { mutableStateOf(ChannelMediaSort.RECENT) }
    val downloadRecording = rememberRecordingDownloadAction()
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

    val followAction = rememberAccountFollowAction(state.channel?.slug ?: channelSlug)

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
    LaunchedEffect(selectedTab, channel?.id, state.isLoading) {
        if (!state.isLoading && selectedTab == ChannelTab.ABOUT) viewModel.loadAbout()
    }
    val offline = channel != null && channel.livestream == null && playerHost?.recording == null
    val offlineCollapse = remember(channelSlug, scope) { OfflineBannerState(scope) }
    val offlineDismissReset = remember(channelSlug) { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    LaunchedEffect(offline) { if (!offline) offlineCollapse.animateTo(0f) }
    androidx.compose.runtime.DisposableEffect(playerHost, offlineCollapse) {
        playerHost?.offlineCollapseProgress = offlineCollapse
        onDispose {
            if (playerHost?.offlineCollapseProgress === offlineCollapse) playerHost.offlineCollapseProgress = null
        }
    }
    val compactTabScrollStates = remember(channelSlug) {
        ChannelTab.entries.associateWith { LazyListState() }
    }
    val wideTabScrollStates = remember(channelSlug) {
        ChannelTab.entries.associateWith { LazyListState() }
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
        movableContentOf<ChannelDetail, (() -> Unit)?, Modifier, Boolean> { ch, onHide, chatMod, autoHideHeader ->
            if (playerHost?.recording != null) {
                com.lechixy.kick.components.channel.chat.RecordedChatView(
                    ch, playerHost.recording!!, playerHost, modifier = chatMod,
                    onHideChat = onHide, onOpenChannel = { slug -> playerHost.open(slug) },
                    autoHideHeader = autoHideHeader)
            } else {
                ChannelChatSection(
                    viewModel = viewModel,
                    channel = ch,
                    onHideChat = onHide,
                    autoHideHeader = autoHideHeader,
                    playerControlsVisible = playerHost?.isControlsVisible == true,
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
                val screenHeightDp = maxHeight
                val isWide = screenWidthDp >= WIDE_BREAKPOINT_DP.dp
                val scrollWideHeader = isWide && !isFullScreen && !isTheaterMode &&
                    channel?.livestream != null && playerHost?.recording == null
                var headerOffsetPx by remember(channelSlug, scrollWideHeader) { mutableFloatStateOf(0f) }
                var headerHeightPx by remember(channelSlug, scrollWideHeader) { mutableFloatStateOf(0f) }
                val headerScroll = remember(offline, offlineCollapse, scrollWideHeader, channelSlug) {
                    when {
                        offline -> ChannelHeaderScrollConnection(offlineCollapse::drag, offlineCollapse::settle)
                        scrollWideHeader -> ChannelHeaderScrollConnection(drag = { delta ->
                            val previous = headerOffsetPx.coerceAtMost(headerHeightPx)
                            headerOffsetPx = (previous + delta).coerceIn(0f, headerHeightPx)
                            headerOffsetPx - previous
                        })
                        else -> null
                    }
                }
                val tabScrollStates = if (isWide) wideTabScrollStates else compactTabScrollStates
                val visibleTabs = remember(isWide) {
                    if (isWide) ChannelTab.entries.filter { it != ChannelTab.CHAT }
                    else ChannelTab.entries
                }
                val effectiveSelectedTab = if (isWide) wideSelectedTab else compactSelectedTab

                val pagerState = key(channelSlug, isWide) {
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
                LaunchedEffect(pagerState.currentPage, channel?.id, state.isLoading) {
                    if (!state.isLoading && visibleTabs[pagerState.currentPage] == ChannelTab.ABOUT) viewModel.loadAbout()
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
                // Keep chat active while the panel is visible during a drag or animation.
                val sideChatActive by remember(playerHost, isChatActuallyVisible) {
                    derivedStateOf {
                        playerHost?.chatOpenProgress?.value?.let { it > 0f } ?: isChatActuallyVisible
                    }
                }

                Layout(
                    contents = listOf(
                        {
                            Column(Modifier.fillMaxSize().then(
                                if (headerScroll != null) Modifier.nestedScroll(headerScroll) else Modifier
                            )) {
                                // Player'ın oturduğu yer (PlayerHost bu alanı işliyor)
                                if (offline) {
                                    val bannerUrl = remember(channel, bannerWidthPx) {
                                        channel.getBannerUrl(targetWidthPx = bannerWidthPx)
                                    }
                                    OfflineChannelBanner(
                                        channel = channel,
                                        bannerUrl = bannerUrl,
                                        modifier = if (isWide) Modifier.heightIn(max = with(density) {
                                            val viewportHeight = screenHeightDp.roundToPx()
                                            (playerHost?.channelMediaHeightLimit(viewportHeight, ChannelTabSpace.roundToPx())
                                                ?: (viewportHeight - ChannelTabSpace.roundToPx()).coerceAtLeast(0)).toDp()
                                        }) else Modifier,
                                        collapseProgress = offlineCollapse,
                                        onToggle = offlineCollapse::toggle,
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
                                        },
                                        isWide = isWide,
                                        targetWidthPx = bannerWidthPx
                                    )
                                } else {
                                    Spacer(
                                        Modifier
                                            .fillMaxWidth()
                                            .layout { measurable, constraints ->
                                                val colWidth = constraints.maxWidth
                                                val naturalHeightPx = (colWidth * 9f / 16f).roundToInt()
                                                val fullHeightPx = if (isWide) {
                                                    minOf(naturalHeightPx, playerHost?.channelMediaHeightLimit(
                                                        constraints.maxHeight, ChannelTabSpace.roundToPx()
                                                    ) ?: constraints.maxHeight)
                                                } else naturalHeightPx
                                                val compactHeightPx = offlineBannerCompactHeight(fullHeightPx)
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

                                if (channel != null || state.error == null) {
                                    val isLive = channel?.livestream != null && playerHost?.recording == null
                                    val hasPlayback = isLive || playerHost?.recording != null ||
                                        (channel == null && playerHost?.openingChannelIsLive != false)
                                    val lastLiveText = remember(state.videos, configuration) {
                                        val latestVideo = state.videos.firstOrNull()
                                        FormatUtils.formatLastLive(
                                            context, latestVideo?.startTime ?: latestVideo?.createdAt,
                                            true
                                        )
                                    }

                                    @Composable
                                    fun Header() {
                                        AnimatedVisibility(
                                            modifier = if (scrollWideHeader) Modifier.clipToBounds().layout { measurable, constraints ->
                                                val header = measurable.measure(constraints)
                                                val hidden = headerOffsetPx.roundToInt().coerceIn(0, header.height)
                                                layout(header.width, header.height - hidden) {
                                                    header.placeRelative(0, -hidden)
                                                }
                                            } else Modifier,
                                            visible = isProfilePhotoExpanded || if (isWide || !hasPlayback) true else isControlsVisible,
                                            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                                            exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                                        ) {
                                            val measureHeader: (Int) -> Unit = { heightPx ->
                                                if (scrollWideHeader) headerHeightPx = heightPx.toFloat()
                                                if (heightPx > 0 && playerHost?.channelHeaderHeightPx != heightPx) {
                                                    playerHost?.channelHeaderHeightPx = heightPx
                                                }
                                            }
                                            Box(
                                                Modifier.fillMaxWidth().onSizeChanged { measureHeader(it.height) }
                                                    .animateContentSize(tween(220))
                                            ) {
                                                if (channel == null) {
                                                    ChannelHeaderSkeleton(hasPlayback)
                                                } else ChannelHeaderInfo(
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
                                                    onProfilePhotoExpandedChange = { if (!isWide) isProfilePhotoExpanded = it },
                                                    isLive = isLive,
                                                    lastLiveText = lastLiveText,
                                                    isFollowing = followAction.following,
                                                    followLabel = followAction.label,
                                                    followEnabled = followAction.enabled,
                                                    onFollowClick = followAction.onClick,
                                                    context = context,
                                                )
                                            }
                                        }

                                    }
                                    @Composable
                                    fun Tabs() {
                                        ChannelTabsRow(
                                            tabs = visibleTabs,
                                            selectedTab = visibleTabs[pagerState.currentPage],
                                            onTabSelected = { tab -> scope.launch { pagerState.animateScrollToPage(visibleTabs.indexOf(tab)) } }
                                        )
                                    }
                                    val headerOnly = isWide && isTheaterMode && hasPlayback
                                    Header()
                                    if (!headerOnly) {
                                        Tabs()
                                        ChannelTabPager(state = pagerState, tabs = visibleTabs,
                                            modifier = Modifier.fillMaxWidth().weight(1f)) { pageTab ->
                                            if (pageTab == ChannelTab.CHAT && !hasSideChat && isChatVisible &&
                                                (channel == null || state.isLoading)
                                            ) {
                                                ChannelChatSkeleton(
                                                    canHideChat = playerHost != null,
                                                    isReplay = playerHost?.recording != null,
                                                    isLive = hasPlayback
                                                )
                                            } else if (pageTab != ChannelTab.CHAT && (channel == null || state.isLoading)) {
                                                ChannelTabSkeleton(pageTab) { target ->
                                                    scope.launch { pagerState.animateScrollToPage(visibleTabs.indexOf(target)) }
                                                }
                                            } else if (pageTab == ChannelTab.CHAT && !hasSideChat) {
                                                // Empty chat still needs to dispatch vertical swipes to the offline banner.
                                                val chatScroll = if (offline) {
                                                    Modifier.scrollable(rememberScrollableState { 0f }, Orientation.Vertical)
                                                } else Modifier
                                                Box(Modifier.fillMaxSize().then(chatScroll)) {
                                                    if (isChatVisible && channel != null) {
                                                        sharedChat(channel, playerHost?.let { host ->
                                                            { host.updateChatVisibility(visible = false) }
                                                        }, Modifier.fillMaxSize(), true)
                                                    }
                                                    androidx.compose.animation.AnimatedVisibility(
                                                        visible = !isChatVisible,
                                                        enter = slideInHorizontally(tween(250)) { it } + fadeIn(tween(200)),
                                                        exit = slideOutHorizontally(tween(180)) { it } + fadeOut(tween(150)),
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 10.dp)
                                                    ) {
                                                        ChatRestoreButton(
                                                            R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                                            stringResource(R.string.player_show_chat)
                                                        ) {
                                                            playerHost?.updateChatVisibility(visible = true)
                                                        }
                                                    }
                                                }
                                            } else if (channel != null) {
                                                LazyColumn(state = tabScrollStates.getValue(pageTab), modifier = Modifier.fillMaxSize()) {
                                                    item(key = "channel_content") {
                                                        ChannelTabContent(
                                                            tab = pageTab, channel = channel,
                                                            linksState = state.links, goalState = state.goals,
                                                            onRetryAbout = { viewModel.loadAbout(retry = true) },
                                                            videos = state.videos,
                                                            videoSort = videoSort, onVideoSort = { videoSort = it },
                                                            clipState = state, onClipFilters = viewModel::updateClipFilters,
                                                            onLoadMoreClips = viewModel::loadMoreClips,
                                                            popularClips = state.popularClips, recentCategories = state.recentCategories,
                                                            onRetryHome = viewModel::loadHomeSections,
                                                            onRetryClips = viewModel::retryClips,
                                                            onPlayRecording = { playerHost?.playRecording(it) },
                                                            onDownloadRecording = { recording ->
                                                                downloadRecording(recording.copy(channelName = channel.user?.username ?: channel.slug,
                                                                    channelSlug = channel.slug))
                                                            },
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
                                        Text(
                                            text = state.error ?: "Unknown error",
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                                        )
                                    }
                                }
                            }
                        },
                        {
                            if (hasSideChat && (channel != null || state.error == null)) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val openProgress = (playerHost?.chatOpenProgress?.value
                                                ?: if (isChatActuallyVisible) 1f else 0f).coerceIn(0f, 1f)
                                            alpha = openProgress
                                        }
                                ) {
                                    if (channel == null || state.isLoading) {
                                        ChannelChatSkeleton(
                                            canHideChat = true,
                                            isReplay = playerHost?.recording != null,
                                            isLive = channel?.livestream != null ||
                                                (channel == null && playerHost?.openingChannelIsLive != false)
                                        )
                                    } else CompositionLocalProvider(LocalChatContentActive provides sideChatActive) {
                                        sharedChat(
                                            channel,
                                            { playerHost?.updateChatVisibility(visible = false) },
                                            Modifier.fillMaxSize(),
                                            configuration.smallestScreenWidthDp < 600
                                        )
                                    }

                                    if (channel != null && (isChatActuallyVisible || isDraggingHandle)) {
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
                                                        playerHost?.updateChatVisibility(visible = false)
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
private fun ChannelHeaderSkeleton(hasPlayback: Boolean) {
    val titleHeight = with(LocalDensity.current) { MaterialTheme.typography.titleMedium.lineHeight.toDp() }
    val labelHeight = with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
    Column(Modifier.fillMaxWidth()) {
        if (hasPlayback) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
                Box(Modifier.height(titleHeight + 2.dp), contentAlignment = Alignment.CenterStart) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(16.dp))
                }
                Box(Modifier.height(labelHeight + 4.dp), contentAlignment = Alignment.CenterStart) {
                    SkeletonBlock(Modifier.width(72.dp).height(12.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(Modifier.size(56.dp), CircleShape)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(16.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.3f).height(12.dp))
            }
            SkeletonBlock(Modifier.width(72.dp).height(36.dp), RoundedCornerShape(8.dp))
        }
    }
}

@Composable
private fun ChannelChatSkeleton(canHideChat: Boolean, isReplay: Boolean, isLive: Boolean) {
    val settings = LocalSettingsRepository.current
    val fontSize by settings.get(SettingsKeys.CHAT_FONT_SIZE)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.CHAT_FONT_SIZE.defaultValue)
    val spacing by settings.get(SettingsKeys.CHAT_MESSAGE_SPACING)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.CHAT_MESSAGE_SPACING.defaultValue)
    val timestamps by settings.get(SettingsKeys.CHAT_TIMESTAMPS)
        .collectAsStateWithLifecycle(initialValue = SettingsKeys.CHAT_TIMESTAMPS.defaultValue)
    val showTimestamps = ChatTimestampMode.fromValue(timestamps).shows(isReplay)
    val lineHeight = with(LocalDensity.current) { (fontSize + 5).sp.toDp() }
    val barHeight = with(LocalDensity.current) { (fontSize - 4).sp.toDp() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canHideChat) SkeletonBlock(Modifier.size(20.dp))
            SkeletonBlock(Modifier.width(42.dp).height(12.dp))
            Spacer(Modifier.weight(1f))
            SkeletonBlock(Modifier.size(8.dp), CircleShape)
            SkeletonBlock(Modifier.width(32.dp).height(10.dp))
            repeat(2) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    SkeletonBlock(Modifier.size(20.dp))
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val rowCount = (maxHeight / (lineHeight + spacing.dp)).toInt() + 1
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                userScrollEnabled = false,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.dp),
            ) {
                items(rowCount) { index ->
                    ChatMessageSkeleton(index, lineHeight, barHeight, showTimestamps)
                }
            }
        }
        if (!isReplay) {
            val compact = LocalConfiguration.current.smallestScreenWidthDp < 600 && isLive
            val session by com.lechixy.kick.data.account.AccountGraph.auth.session.collectAsStateWithLifecycle()
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(if (compact) 48.dp else 64.dp),
                contentAlignment = if (compact) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                when {
                    compact -> SkeletonBlock(Modifier.width(116.dp).height(18.dp))
                    !session.signedIn -> SkeletonBlock(Modifier.width(96.dp).height(40.dp), CircleShape)
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBlock(Modifier.size(24.dp), CircleShape)
                        SkeletonBlock(Modifier.weight(1f).height(40.dp), RoundedCornerShape(12.dp))
                        SkeletonBlock(Modifier.size(24.dp), CircleShape)
                    }
                }
            }
        }
    }
}

private val chatSkeletonMessageWidths = listOf(0.28f, 0.7f, 0.46f, 1f, 0.56f, 0.18f, 0.84f, 0.36f)

@Composable
private fun ChatMessageSkeleton(
    index: Int,
    lineHeight: Dp,
    barHeight: Dp,
    showTimestamps: Boolean,
) {
    val pattern = index % chatSkeletonMessageWidths.size
    Column {
        Row(
            Modifier.fillMaxWidth().height(lineHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showTimestamps) SkeletonBlock(Modifier.width(barHeight * 3).height(barHeight))
            if (index % 3 == 0) SkeletonBlock(Modifier.size(barHeight * 1.2f))
            SkeletonBlock(Modifier.width(barHeight * (4 + index % 5)).height(barHeight))
            Box(Modifier.weight(1f)) {
                SkeletonBlock(Modifier.fillMaxWidth(chatSkeletonMessageWidths[pattern]).height(barHeight))
            }
        }
        if (pattern == 3) {
            Box(Modifier.fillMaxWidth().height(lineHeight), contentAlignment = Alignment.CenterStart) {
                SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(barHeight))
            }
        }
    }
}

@Composable
private fun ChannelTabSkeleton(tab: ChannelTab, onNavigate: (ChannelTab) -> Unit) {
    when (tab) {
        ChannelTab.CHAT -> Unit
        ChannelTab.HOME -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                CardSection<Unit>(stringResource(R.string.recent_broadcasts), emptyList(), Modifier.padding(16.dp),
                    previewRows = 1, onMore = { onNavigate(ChannelTab.VIDEOS) }, loading = true) { }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    com.lechixy.kick.components.SectionHeader(stringResource(R.string.channel_popular_clips),
                        onMore = { onNavigate(ChannelTab.CLIPS) }, compact = true)
                    CardSection<Unit>(null, emptyList(), previewRows = 1, loading = true) { }
                }
            }
        }
        ChannelTab.VIDEOS, ChannelTab.CLIPS -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.width(104.dp).height(32.dp), RoundedCornerShape(8.dp))
                    if (tab == ChannelTab.CLIPS) SkeletonBlock(Modifier.width(88.dp).height(32.dp), RoundedCornerShape(8.dp))
                }
            }
            item { VideoSkeletonGrid(Modifier.padding(16.dp)) }
        }
        ChannelTab.CATEGORIES, ChannelTab.ABOUT -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(96.dp), RoundedCornerShape(8.dp))
        }
    }
}

@Composable
private fun ChannelChatSection(
    viewModel: ChannelViewModel,
    channel: ChannelDetail,
    onHideChat: (() -> Unit)?,
    autoHideHeader: Boolean,
    playerControlsVisible: Boolean,
    modifier: Modifier = Modifier,
    onOpenChannel: (String) -> Unit = {}
) {
    val community: com.lechixy.kick.components.channel.chat.ChatCommunityViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(key = "community-${channel.slug}")
    val highTraffic by viewModel.highTraffic.collectAsChatState()
    val chatMessages by viewModel.chatMessages.collectAsChatState()
    val chatConnectionState by viewModel.chatConnectionState.collectAsChatState()
    val pinnedMessage by viewModel.pinnedMessage.collectAsChatState()
    val userColors by viewModel.userColors.collectAsChatState()
    val chatSettings by viewModel.chatSettings.collectAsChatState()
    val pointsUpdated by viewModel.pointsUpdated.collectAsChatState()
    val emotes by viewModel.emotes.collectAsChatState()
    val replySession by com.lechixy.kick.data.account.AccountGraph.auth.session.collectAsChatState()
    var replyTarget by remember(channel.id, replySession.generation) { mutableStateOf<com.lechixy.kick.data.chat.model.ChatMessage?>(null) }
    LaunchedEffect(chatMessages) {
        if (chatMessages.any { it.id == replyTarget?.id && it.isDeleted }) replyTarget = null
    }

    ChatView(
        messages = chatMessages,
        highTraffic = highTraffic,
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
        autoHideHeader = autoHideHeader,
        playerControlsVisible = playerControlsVisible,
        onOpenChannel = onOpenChannel,
        composer = { com.lechixy.kick.components.channel.chat.AccountChatComposer(
            channel, chatConnectionState, chatSettings, pointsUpdated,
            rulesSeen = { generation -> viewModel.rulesSeen(generation, channel.id) },
            markRulesSeen = { generation -> viewModel.markRulesSeen(generation, channel.id) },
            replyTo = replyTarget, onCancelReply = { replyTarget = null }, recentMessages = chatMessages, userColors = userColors,
            onUserCommand = { username -> community.openProfile(channel.slug,
                com.lechixy.kick.data.chat.model.ActiveChatter(username = username), subscriberBadges = channel.subscriberBadges) },
            emotes = emotes, onRetryEmotes = { viewModel.loadEmotes(retry = true) },
            onIdentityPreviewClick = { identity ->
                val account = com.lechixy.kick.data.account.AccountGraph.auth.session.value.profile
                if (account != null) community.openProfile(channel.slug,
                    com.lechixy.kick.data.chat.model.ActiveChatter(username = account.name, profilePicture = account.avatar),
                    com.lechixy.kick.data.chat.model.ChatSender(id = account.userId, username = account.name, identity = identity), channel.subscriberBadges)
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(Alignment.Top, unbounded = true)
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
                modifier = Modifier.size(56.dp).then(
                    if (channel.livestream != null) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(4.dp)
                    } else Modifier
                )
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = channel.user?.username ?: channel.slug, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                        text = FormatUtils.formatFollowersCount(channel.followersCount, LocalConfiguration.current.locales[0]),
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
                icon = if (tab == ChannelTab.CHAT) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = stringResource(tab.label),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else null,
                text = if (tab != ChannelTab.CHAT) {
                    {
                        Text(
                            text = stringResource(tab.label),
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                } else null
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
    videoSort: ChannelMediaSort,
    onVideoSort: (ChannelMediaSort) -> Unit,
    clipState: ChannelUiState,
    onClipFilters: (ChannelMediaSort, ChannelClipTime) -> Unit,
    onLoadMoreClips: () -> Unit,
    onRetryClips: () -> Unit,
    onPlayRecording: (RecordedPlayback) -> Unit,
    onDownloadRecording: (RecordedPlayback) -> Unit,
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
                ChannelRecordingCard(video, onPlayRecording, onDownloadRecording)
            } else ContentState(stringResource(R.string.no_recent_broadcasts))

            Column(Modifier.padding(16.dp)) {
                com.lechixy.kick.components.SectionHeader(stringResource(R.string.channel_popular_clips),
                    onMore = { onNavigateToTab(ChannelTab.CLIPS) }, compact = true)
                when {
                    popularClips.failed -> ContentState(stringResource(R.string.channel_section_error), onRetryHome)
                    popularClips.loaded && popularClips.value.orEmpty().isEmpty() -> ContentState(stringResource(R.string.media_no_clips))
                    else -> CardSection(title = null, data = popularClips.value.orEmpty(), previewRows = 1,
                        loading = popularClips.loading) { clip -> ChannelClipCard(clip, onPlayRecording, onDownloadRecording) }
                }
            }
            ChannelCategoriesSection(recentCategories, onRetryHome, preview = true,
                onMore = { onNavigateToTab(ChannelTab.CATEGORIES) })
        }

        ChannelTab.CATEGORIES -> ChannelCategoriesSection(recentCategories, onRetryHome)

        ChannelTab.ABOUT -> ChannelAboutTabContent(channel, linksState, goalState, onRetryAbout)

        ChannelTab.VIDEOS -> Column {
            Row(Modifier.padding(horizontal = 16.dp)) {
                ChannelMediaFilter(ChannelMediaSort.entries, videoSort, { it.label }, onVideoSort)
            }
            val sortedVideos = remember(videos, videoSort) {
                if (videoSort == ChannelMediaSort.VIEWS) videos.sortedByDescending { it.views } else videos
            }
            if (videos.isEmpty()) ContentState(stringResource(R.string.media_no_videos))
            else CardSection(title = null, data = sortedVideos, modifier = Modifier.padding(16.dp)) { video ->
                ChannelRecordingCard(video, onPlayRecording, onDownloadRecording)
            }
        }
        ChannelTab.CLIPS -> Column {
            val clips = clipState.clips
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelMediaFilter(ChannelMediaSort.entries, clipState.clipSort, { it.label }) {
                    onClipFilters(it, clipState.clipTime)
                }
                ChannelMediaFilter(ChannelClipTime.entries, clipState.clipTime, { it.label }) {
                    onClipFilters(clipState.clipSort, it)
                }
            }
            if (clips.isNotEmpty() || clipState.isClipsLoading) {
                CardSection(title = null, data = clips, modifier = Modifier.padding(16.dp),
                    loading = clipState.isClipsLoading && clips.isEmpty(),
                    skeleton = { com.lechixy.kick.components.VideoCardSkeleton() }) { clip ->
                    ChannelClipCard(clip, onPlayRecording, onDownloadRecording)
                }
            }
            when {
                clipState.clipsError != null -> ContentState(stringResource(R.string.channel_clips_error), onRetryClips)
                clipState.isClipsLoading && clips.isEmpty() -> Unit
                clips.isEmpty() -> ContentState(stringResource(R.string.media_no_clips))
                clipState.clipCursor != null -> TextButton(
                    onClick = onLoadMoreClips,
                    enabled = !clipState.isClipsLoading,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(stringResource(R.string.channel_clips_load_more)) }
            }
        }
    }
}

@Composable
private fun <T> ChannelMediaFilter(
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(stringResource(label(selected))) },
            trailingIcon = {
                Icon(painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = null, modifier = Modifier.size(18.dp))
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(stringResource(label(option))) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}

@Composable
private fun ChannelRecordingCard(
    video: ChannelVideo,
    onPlayRecording: (RecordedPlayback) -> Unit,
    onDownloadRecording: (RecordedPlayback) -> Unit
) {
    val recording = remember(video) { video.toRecording() }
    VideoCard(video = video, onDownload = recording?.let { { onDownloadRecording(it) } },
        onClick = { recording?.let(onPlayRecording) })
}

@Composable
private fun ChannelClipCard(
    clip: ChannelClip,
    onPlayRecording: (RecordedPlayback) -> Unit,
    onDownloadRecording: (RecordedPlayback) -> Unit
) {
    val recording = remember(clip) { clip.toRecording() }
    VideoCard(video = ChannelVideo(id = 0, sessionTitle = clip.title, duration = recording.durationMs,
        createdAt = clip.createdAt, views = clip.views, thumbnail = VideoThumbnail(src = clip.thumbnailUrl)), progressId = clip.id,
        onDownload = { onDownloadRecording(recording) }, onClick = { onPlayRecording(recording) })
}

@Composable
private fun ChannelCategoriesSection(
    state: com.lechixy.kick.data.channel.model.ChannelSectionState<List<com.lechixy.kick.data.browse.model.BrowseCategory>>,
    onRetry: () -> Unit, preview: Boolean = false, onMore: (() -> Unit)? = null
) {
    val navigation = LocalContentNavigation.current
    Column(Modifier.padding(16.dp)) {
        if (preview) com.lechixy.kick.components.SectionHeader(
            stringResource(R.string.channel_recent_categories), onMore, compact = true)
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

