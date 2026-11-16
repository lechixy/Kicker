package com.lechixy.kick.screens.events

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.components.ExpansionSource
import com.lechixy.kick.components.FilterDropdown
import com.lechixy.kick.components.InteractiveCard
import com.lechixy.kick.components.LocalContentNavigation
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.SkeletonBlock
import com.lechixy.kick.components.SkeletonLoadingScope
import com.lechixy.kick.components.StreamCard
import com.lechixy.kick.components.StreamCardSkeleton
import com.lechixy.kick.components.cardSection
import com.lechixy.kick.data.channel.model.ChannelEvent
import com.lechixy.kick.data.channel.model.EventClip
import com.lechixy.kick.data.chat.model.recordingStartInstant
import com.lechixy.kick.util.FormatUtils
import java.time.Instant
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventDetailScreen(
    viewModel: EventDetailViewModel,
    onBack: () -> Unit,
    onStreamClick: (String, ExpansionSource?) -> Unit,
    onClipClick: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAllStreams by rememberSaveable { mutableStateOf(false) }
    val event = state.event
    val streams = remember(event, state.streams) {
        if (event?.phase != null && event.phase != "live") emptyList()
        else (state.streams ?: event?.defaultStreams.orEmpty())
            .mapNotNull { it.toLivestream(event?.category) }
            .distinctBy { it.id }
    }
    val streamsTitle = stringResource(R.string.events_live_streams)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painterResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        stringResource(R.string.navigation_back)
                    )
                }
                Text(stringResource(R.string.events), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        SkeletonLoadingScope(
            (state.loadingEvent && event == null) ||
                (state.loadingStreams && streams.isEmpty()) ||
                (state.loadingClips && state.clips.isEmpty())
        ) {
            PullToRefreshBox(
                state.isLoading && event != null, viewModel::refresh,
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            ) {
                EventContentGrid { columns, heroMaxWidth ->
                    if (event != null || state.loadingEvent) {
                        item(key = "event-banner", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                                val modifier = Modifier.widthIn(max = heroMaxWidth).fillMaxWidth()
                                if (event != null) EventIdentityCard(event, viewModel.eventSlug, modifier)
                                else FeaturedEventSkeleton(modifier)
                            }
                        }
                    }
                    if (state.eventFailed) {
                        item(key = "event-error", span = { GridItemSpan(maxLineSpan) }) {
                            ContentState(stringResource(R.string.events_detail_load_failed), viewModel::refresh)
                        }
                    }
                    if (event?.phase == "live" || streams.isNotEmpty()) {
                        if (streams.isNotEmpty() || state.loadingStreams) {
                            cardSection(
                                key = "event_stream", title = streamsTitle, data = streams, columns = columns,
                                loading = state.loadingStreams && streams.isEmpty(),
                                previewRows = 2.takeUnless { showAllStreams }, itemKey = { it.id },
                                skeleton = { StreamCardSkeleton() }
                            ) { stream ->
                                val slug = requireNotNull(stream.channel).slug
                                StreamCard(
                                    stream,
                                    onClick = { onStreamClick(slug, null) },
                                    onClickWithSource = { source -> onStreamClick(slug, source) }
                                )
                            }
                            if (!showAllStreams && streams.size > columns * 2) {
                                item(key = "show-all-streams", span = { GridItemSpan(maxLineSpan) }) {
                                    TextButton(onClick = { showAllStreams = true }) {
                                        Text(stringResource(R.string.events_show_all_streams))
                                    }
                                }
                            }
                        } else if (!state.streamsFailed) {
                            item(key = "no-streams", span = { GridItemSpan(maxLineSpan) }) {
                                ContentState(stringResource(R.string.events_no_streams))
                            }
                        }
                    }
                    if (state.streamsFailed && (event?.phase == null || event.phase == "live")) {
                        item(key = "streams-error", span = { GridItemSpan(maxLineSpan) }) {
                            ContentState(stringResource(R.string.events_streams_load_failed), viewModel::refresh)
                        }
                    }
                    if (state.clips.isNotEmpty() || state.loadingClips || state.clipsFailed) {
                        item(key = "key-moments", span = { GridItemSpan(maxLineSpan) }) {
                            KeyMomentsSection(state, onClipClick, viewModel::refresh)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventIdentityCard(event: ChannelEvent, slug: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navigation = LocalContentNavigation.current
    val scheme = MaterialTheme.colorScheme
    val accent = eventSurfaceColor(event.palette?.accentPromoPoster)
    val locale = LocalConfiguration.current.locales[0]
    val date = eventDate(event, compact = false)
    val start = remember(event.startTime) { recordingStartInstant(event.startTime) }
    val upcoming = event.phase in setOf("pre", "upcoming")
    val countdown by produceState<String?>(null, context, upcoming, start) {
        if (!upcoming || start == null) {
            value = null
            return@produceState
        }
        while (start > Instant.now()) {
            value = FormatUtils.formatRelativeTime(context, event.startTime)
            delay(30_000)
        }
        value = null
    }
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = scheme.surfaceContainer) {
        Column {
            Box {
                EventPromoBanner(event, Modifier.fillMaxWidth())
                if (event.phase == "live") {
                    Box(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                        EventBrandBadge(stringResource(R.string.live_badge))
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.44f), Color.Transparent))
                ).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    event.assets?.thumbnail?.takeIf(String::isNotBlank)?.let {
                        EventImage(it, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            event.title.orEmpty(), Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                        )
                        EventBrandBadge(stringResource(R.string.events_only_on_kick))
                    }
                    FilledIconButton(onClick = {
                        val link = "https://kick.com/events/${Uri.encode(event.slug?.takeIf(String::isNotBlank) ?: slug)}"
                        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                        context.startActivity(Intent.createChooser(intent, null))
                    }) {
                        Icon(
                            painterResource(R.drawable.share_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            stringResource(R.string.events_share)
                        )
                    }
                }
                if (event.phase == "live") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(
                                painterResource(R.drawable.visibility_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                null, Modifier.size(16.dp), tint = scheme.primary
                            )
                            Text(
                                stringResource(R.string.events_viewer_badge, FormatUtils.formatCompactCount(event.viewCount.toLong(), locale)),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Text(stringResource(R.string.events_streaming_count, event.numLivestreams), style = MaterialTheme.typography.labelMedium)
                    }
                }
                event.description?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                EventOrganiserLabel(event, avatarSize = 28.dp, showHostLabel = true)
                if (date != null) EventDateLabel(date, scheme.onSurfaceVariant, maxLines = 2)
                countdown?.let {
                    Text(stringResource(R.string.events_starts_in, it), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                }
                val categorySlug = event.category?.slug
                val categoryName = event.category?.name
                if (!categorySlug.isNullOrBlank() && !categoryName.isNullOrBlank()) {
                    AssistChip(onClick = { navigation.category(categorySlug) }, label = { Text(categoryName) })
                }
            }
        }
    }
}

private enum class KeyMomentsSort(val label: Int) {
    Latest(R.string.events_clips_latest),
    MostViewed(R.string.events_clips_most_viewed)
}

@Composable
private fun KeyMomentsSection(state: EventDetailUiState, onClipClick: (String) -> Unit, onRetry: () -> Unit) {
    var sort by rememberSaveable { mutableStateOf(KeyMomentsSort.Latest) }
    val rowState = rememberLazyListState()
    LaunchedEffect(sort) { rowState.scrollToItem(0) }
    val clips = remember(state.clips, sort) {
        when (sort) {
            KeyMomentsSort.Latest -> state.clips.sortedByDescending { recordingStartInstant(it.createdAt) }
            KeyMomentsSort.MostViewed -> state.clips.sortedByDescending { it.views }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SectionHeader(stringResource(R.string.events_key_moments), modifier = Modifier.weight(1f), compact = true)
            if (clips.size > 1) {
                FilterDropdown(
                    title = stringResource(sort.label), options = KeyMomentsSort.entries,
                    label = { stringResource(it.label) }, onSelect = { sort = it }
                )
            }
        }
        LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loadingClips && clips.isEmpty()) {
                items(3) { SkeletonBlock(Modifier.width(180.dp).aspectRatio(16f / 9f), RoundedCornerShape(12.dp)) }
            } else {
                items(clips, key = { it.id }, contentType = { "event_clip" }) { clip ->
                    EventClipCard(clip) { onClipClick(clip.id) }
                }
            }
        }
        if (state.clipsFailed) ContentState(stringResource(R.string.events_clips_load_failed), onRetry)
    }
}

@Composable
private fun EventClipCard(clip: EventClip, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    InteractiveCard(
        onClick = onClick, modifier = Modifier.width(180.dp), color = MaterialTheme.colorScheme.surfaceContainerLow
    ) { mediaZoom ->
        Column {
            Box {
                EventImage(clip.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f), imageModifier = mediaZoom)
                Surface(
                    Modifier.align(Alignment.TopStart).padding(6.dp),
                    color = Color.Black.copy(alpha = 0.72f), contentColor = Color.White,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(painterResource(R.drawable.visibility_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null, Modifier.size(12.dp))
                        Text(
                            FormatUtils.formatCompactCount(clip.views, locale),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Text(
                clip.title, Modifier.padding(8.dp),
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
