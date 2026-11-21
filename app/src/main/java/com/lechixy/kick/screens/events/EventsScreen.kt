package com.lechixy.kick.screens.events

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.ContentGrid
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.components.SkeletonBlock
import com.lechixy.kick.components.SkeletonLoadingScope
import com.lechixy.kick.components.cardSection
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.channel.model.ChannelEvent

private enum class EventFeed(val title: Int) {
    Live(R.string.events_live_now),
    Upcoming(R.string.events_upcoming)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventsScreen(onEventClick: (String) -> Unit, viewModel: EventsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expandedFeed by rememberSaveable { mutableStateOf<EventFeed?>(null) }
    val feedScrollState = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val loginLabel = stringResource(R.string.account_login)
    LaunchedEffect(viewModel, context, loginLabel) {
        viewModel.snackbarMessages.collect { message ->
            val signIn = message == R.string.events_reminder_sign_in
            val result = snackbar.showSnackbar(context.getString(message), actionLabel = loginLabel.takeIf { signIn })
            if (signIn && result == SnackbarResult.ActionPerformed) AccountGraph.auth.beginLogin(context)
        }
    }
    BackHandler(expandedFeed != null) { expandedFeed = null }
    val expanded = expandedFeed
    val initialLoading = state.isLoading && !state.hasEvents
    val openEvent: (ChannelEvent) -> Unit = { event ->
        val slug = event.slug?.takeIf(String::isNotBlank) ?: event.id
        if (!slug.isNullOrBlank()) onEventClick(slug)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = { EventsHeaderBar(expanded?.title, onBack = { expandedFeed = null }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && !initialLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        ) {
            SkeletonLoadingScope(initialLoading) {
                if (!initialLoading && !state.hasEvents) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContentState(
                            stringResource(when (state.failedFeeds) {
                                0 -> R.string.events_empty
                                3 -> R.string.events_load_failed
                                else -> R.string.events_partial_error
                            }),
                            viewModel::refresh
                        )
                    }
                } else {
                    feedScrollState.SaveableStateProvider(expanded?.name ?: "browse") {
                        val liveTitle = stringResource(R.string.events_live_now)
                        val upcomingTitle = stringResource(R.string.events_upcoming)
                        ContentGrid(minCardWidth = 200.dp) { columns ->
                            if (expanded == null && (initialLoading || state.featuredEvents.isNotEmpty())) {
                                item(key = "featured-event", span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                                        val width = Modifier.widthIn(max = 960.dp).fillMaxWidth()
                                        if (state.featuredEvents.isNotEmpty()) {
                                            FeaturedEventsCarousel(state.featuredEvents, width, openEvent)
                                        } else {
                                            SkeletonBlock(width.height(288.dp), RoundedCornerShape(12.dp))
                                        }
                                    }
                                }
                            }
                            if (expanded != EventFeed.Upcoming && (initialLoading || state.liveEvents.isNotEmpty())) {
                                cardSection(
                                    key = "live_event", title = liveTitle, data = state.liveEvents, columns = columns,
                                    loading = initialLoading, previewRows = 2.takeIf { expanded == null },
                                    onMore = if (expanded == null && state.liveEvents.size > columns * 2) {
                                        { expandedFeed = EventFeed.Live }
                                    } else null,
                                    itemKey = { requireNotNull(it.id) }, skeleton = { LiveEventSkeleton() }
                                ) { event -> LiveEventCard(event) { openEvent(event) } }
                            }
                            if (expanded != EventFeed.Live && (initialLoading || state.upcomingEvents.isNotEmpty())) {
                                val cardSpan = minOf(columns, 2)
                                cardSection(
                                    key = "upcoming_event", title = upcomingTitle, data = state.upcomingEvents, columns = columns,
                                    loading = initialLoading, previewRows = 2.takeIf { expanded == null }, itemSpan = cardSpan,
                                    onMore = if (expanded == null && state.upcomingEvents.size > (columns / cardSpan) * 2) {
                                        { expandedFeed = EventFeed.Upcoming }
                                    } else null,
                                    itemKey = { requireNotNull(it.id) }, skeleton = { UpcomingEventSkeleton() }
                                ) { event ->
                                    val id = requireNotNull(event.id)
                                    UpcomingEventCard(
                                        event = event,
                                        isReminderSet = id in state.reminderIds,
                                        isReminderPending = id in state.pendingReminderIds,
                                        onClick = { openEvent(event) },
                                        onReminderClick = { viewModel.toggleReminder(id) }
                                    )
                                }
                            }
                            val expandedIsEmpty = when (expanded) {
                                EventFeed.Live -> state.liveEvents.isEmpty()
                                EventFeed.Upcoming -> state.upcomingEvents.isEmpty()
                                null -> false
                            }
                            if (!initialLoading && (state.failedFeeds > 0 || expandedIsEmpty)) {
                                item(key = "feed-status", span = { GridItemSpan(maxLineSpan) }) {
                                    ContentState(
                                        stringResource(if (state.failedFeeds > 0) R.string.events_partial_error else R.string.events_empty),
                                        viewModel::refresh
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsHeaderBar(feedTitle: Int?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (feedTitle != null) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    stringResource(R.string.events_back)
                )
            }
        }
        Text(
            stringResource(feedTitle ?: R.string.events),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (feedTitle == null) EventBrandBadge(stringResource(R.string.events_only_on_kick))
    }
}

@Composable
internal fun FeaturedEventSkeleton(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(12.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(24.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.75f).height(16.dp))
    }
}

@Composable
private fun LiveEventSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(12.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.65f).height(20.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(20.dp))
    }
}

@Composable
private fun UpcomingEventSkeleton() {
    SkeletonBlock(Modifier.fillMaxWidth().height(190.dp), RoundedCornerShape(12.dp))
}
