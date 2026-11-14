package com.lechixy.kick.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import com.lechixy.kick.R
import com.lechixy.kick.components.CategoryRow
import com.lechixy.kick.components.ContentGrid
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.components.ContinueWatchingCard
import com.lechixy.kick.components.ExpansionSource
import com.lechixy.kick.components.HomeTopBar
import com.lechixy.kick.components.LocalContentNavigation
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.SkeletonLoadingScope
import com.lechixy.kick.components.StreamCard
import com.lechixy.kick.components.StreamCardSkeleton
import com.lechixy.kick.components.cardSection
import com.lechixy.kick.components.home.LiveTotalsButton
import com.lechixy.kick.data.player.local.WatchHistoryEntry
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository

private data class ExitingHistoryCard(
    val index: Int,
    val entry: WatchHistoryEntry,
    val scale: Animatable<Float, AnimationVector1D> = Animatable(1f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onStreamClick: (channelSlug: String, source: ExpansionSource?) -> Unit,
    onCategoryClick: (String) -> Unit = com.lechixy.kick.components.LocalContentNavigation.current.category,
    onFollowingClick: () -> Unit
) {
    val followingTitle = stringResource(R.string.home_following_live)
    val recommendedTitle = stringResource(R.string.home_recommended_live)
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val navigation = LocalContentNavigation.current
    val state by viewModel.uiState.collectAsState()
    val history by viewModel.watchHistory.collectAsState()
    val exitingHistory = remember { mutableStateListOf<ExitingHistoryCard>() }
    val displayedHistory = remember(history, exitingHistory.toList()) {
        val exitingKeys = exitingHistory.map { it.entry.key }.toSet()
        history.filterNot { it.key in exitingKeys }.toMutableList().apply {
            exitingHistory.sortedBy { it.index }.forEach { add(it.index.coerceAtMost(size), it.entry) }
        }
    }
    val hasHistory = displayedHistory.isNotEmpty()
    val showHistoryHeader = history.isNotEmpty()
    var keepHistoryHeader by remember { mutableStateOf(showHistoryHeader) }
    LaunchedEffect(showHistoryHeader) {
        if (showHistoryHeader) keepHistoryHeader = true
        else if (keepHistoryHeader) {
            delay(220)
            keepHistoryHeader = false
        }
    }
    val sectionOrder by LocalSettingsRepository.current.get(SettingsKeys.HOME_SECTION_ORDER)
        .collectAsStateWithLifecycle(SettingsKeys.HOME_SECTION_ORDER.defaultValue)
    val sections = remember(sectionOrder) { orderedHomeSections(sectionOrder) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var openingHistory by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var totalsVisible by remember { mutableStateOf(true) }
    val atTop by remember { derivedStateOf { !gridState.canScrollBackward } }
    val scrollThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    val totalsScrollConnection = remember(scrollThreshold) {
        object : NestedScrollConnection {
            private var distance = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || consumed.y == 0f) return Offset.Zero
                if (distance * consumed.y < 0f) distance = 0f
                distance += consumed.y
                if (distance <= -scrollThreshold) totalsVisible = false
                if (distance >= scrollThreshold) totalsVisible = true
                return Offset.Zero
            }
        }
    }

    SkeletonLoadingScope(active = state.isLoading || state.isRefreshing ||
        (state.following.signedIn && state.following.loading && state.following.streams.isEmpty())) {
        Scaffold(
            topBar = {
                HomeTopBar(onSearchClick = { navigation.search("") })
            }
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                state = pullState,
                indicator = {
                    Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.isRefreshing,
                        containerColor = MaterialTheme.colorScheme.onPrimary,
                        color = MaterialTheme.colorScheme.primary,
                        state = pullState
                    )
                }
            ) {
                val streams = remember(state.streams) { state.streams.distinctBy { it.id } }
                ContentGrid(
                    state = gridState,
                    modifier = Modifier.fillMaxSize().nestedScroll(totalsScrollConnection),
                    minCardWidth = 200.dp
                ) { columns ->
                    val loading = state.isLoading || state.isRefreshing
                    sections.forEach { section ->
                        when (section) {
                            HomeSection.CONTINUE_WATCHING -> {
                                if (hasHistory || keepHistoryHeader) {
                                    item(key = "continue_header", span = { GridItemSpan(maxLineSpan) }) {
                                        AnimatedVisibility(visible = showHistoryHeader,
                                            exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Top) + fadeOut(tween(150))) {
                                            SectionHeader(stringResource(R.string.home_continue_watching),
                                                leadingIcon = R.drawable.motion_play_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                                                compact = true)
                                        }
                                    }
                                    itemsIndexed(displayedHistory.take(columns), key = { _, entry -> "history_${entry.key}" }) { index, entry ->
                                        val exit = exitingHistory.firstOrNull { it.entry.key == entry.key }
                                        AnimatedVisibility(
                                            visible = exit == null || history.isNotEmpty(),
                                            modifier = Modifier.animateItem(fadeOutSpec = null),
                                            exit = shrinkVertically(tween(220), shrinkTowards = Alignment.Top),
                                        ) {
                                            Box(Modifier.graphicsLayer {
                                                val scale = exit?.scale?.value ?: 1f
                                                scaleX = scale
                                                scaleY = scale
                                                alpha = scale
                                            }) {
                                                ContinueWatchingCard(entry, enabled = !openingHistory && exit == null, onRemove = {
                                                    val removal = ExitingHistoryCard(index, entry)
                                                    exitingHistory.add(removal)
                                                    viewModel.removeHistory(entry.key)
                                                    scope.launch {
                                                        removal.scale.animateTo(0f, tween(220))
                                                        exitingHistory.remove(removal)
                                                    }
                                                }) { source ->
                                                    scope.launch {
                                                        openingHistory = true
                                                        try {
                                                            val recording = viewModel.resolveHistoryRecording(entry)
                                                            if (recording != null) navigation.recording(entry.channelSlug, recording, source)
                                                            else android.widget.Toast.makeText(context, R.string.home_recording_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                                                        } catch (error: kotlinx.coroutines.CancellationException) {
                                                            throw error
                                                        } catch (_: Exception) {
                                                            android.widget.Toast.makeText(context, R.string.home_recording_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                                                        } finally { openingHistory = false }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            HomeSection.FOLLOWING -> {
                                if (state.following.signedIn) {
                                    cardSection("following", followingTitle, state.following.streams,
                                        columns, loading = state.following.loading, previewRows = if (wide) 2 else 1,
                                        onMore = onFollowingClick, itemKey = { it.id }, skeleton = { StreamCardSkeleton() }) { stream ->
                                        StreamCard(stream, onClick = { stream.channel?.slug?.let { onStreamClick(it, null) } },
                                            onClickWithSource = { source -> stream.channel?.slug?.let { onStreamClick(it, source) } },
                                            onCategoryClick = onCategoryClick)
                                    }
                                    if (!state.following.loading) {
                                        state.following.error?.let { message -> item(key = "following_error", span = { GridItemSpan(maxLineSpan) }) {
                                            ContentState(message, viewModel::loadFollowing)
                                        } }
                                        if (state.following.loaded && state.following.streams.isEmpty() && state.following.error == null)
                                            item(key = "following_empty", span = { GridItemSpan(maxLineSpan) }) { ContentState(stringResource(R.string.home_no_following_live)) }
                                    }
                                }
                            }
                            HomeSection.CATEGORIES -> {
                                item(key = "categories_header", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.section_categories), onMore = { onCategoryClick("") }, compact = true) }
                                item(key = "categories_row", span = { GridItemSpan(maxLineSpan) }) { CategoryRow(state.categories, loading, onCategoryClick) }
                                state.categoriesError?.let { message -> item(key = "categories_error", span = { GridItemSpan(maxLineSpan) }) { ContentState(message, viewModel::loadStreams) } }
                            }
                            HomeSection.RECOMMENDED -> {
                                state.error?.let { message -> item(key = "recommended_error", span = { GridItemSpan(maxLineSpan) }) { ContentState(message, viewModel::loadStreams) } }
                                cardSection("recommended", recommendedTitle, streams, columns,
                                    loading = loading, itemKey = { it.id }, skeleton = { StreamCardSkeleton() }) { stream ->
                                    StreamCard(stream, onClick = { stream.channel?.slug?.let { onStreamClick(it, null) } },
                                        onClickWithSource = { source -> stream.channel?.slug?.let { onStreamClick(it, source) } },
                                        onCategoryClick = onCategoryClick)
                                }
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 80.dp)) }
                }
                AnimatedVisibility(
                    visible = state.liveTotals != null && (totalsVisible || atTop),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 12.dp),
                    enter = scaleIn(
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    ) + fadeIn(),
                    exit = scaleOut(
                        animationSpec = spring(dampingRatio = 1f, stiffness = 650f),
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    ) + fadeOut()
                ) {
                    state.liveTotals?.let { totals ->
                        LiveTotalsButton(totals = totals, onClick = {
                            totalsVisible = true
                            scope.launch { gridState.animateScrollToItem(0) }
                        })
                    }
                }
            }
        }
    }
}
