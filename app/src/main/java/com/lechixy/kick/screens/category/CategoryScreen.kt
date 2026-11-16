package com.lechixy.kick.screens.category

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.components.ClipVideoCard
import com.lechixy.kick.components.ContentPageHeader
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.components.ExpansionSource
import com.lechixy.kick.components.FilterDropdown
import com.lechixy.kick.components.SkeletonBlock
import com.lechixy.kick.components.SkeletonLoadingScope
import com.lechixy.kick.components.StreamCard
import com.lechixy.kick.components.StreamCardSkeleton
import com.lechixy.kick.components.VideoCardSkeleton
import com.lechixy.kick.components.channel.MetadataPill
import com.lechixy.kick.components.rememberStreamLanguageCodes
import com.lechixy.kick.components.streamLanguageName
import com.lechixy.kick.components.streamLanguageOptions
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.browse.model.BrowseCategory
import com.lechixy.kick.data.channel.model.ChannelEvent
import com.lechixy.kick.screens.events.EventsViewModel
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.buildOptimizedImageRequest
import com.lechixy.kick.util.getResponsiveImageUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryScreen(
    viewModel: CategoryViewModel,
    eventsViewModel: EventsViewModel,
    onBack: () -> Unit,
    onStreamClick: (String, ExpansionSource?) -> Unit,
    onEventClick: (String) -> Unit,
    onClipClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val eventState by eventsViewModel.uiState.collectAsStateWithLifecycle()
    val category = state.category
    val events = remember(category?.id, category?.slug, eventState.liveEvents) {
        if (category == null) emptyList() else eventState.liveEvents.filter {
            it.categoryId == category.id || it.category?.id == category.id || it.category?.slug == category.slug
        }
    }
    val refresh = {
        viewModel.refresh()
        eventsViewModel.refresh()
    }
    val codes by rememberStreamLanguageCodes()
    val languages = remember(codes) { streamLanguageOptions(codes).map { it.first } }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
        viewModel.snackbarMessages.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            val title = when (category?.slug) {
                "just-chatting" -> stringResource(R.string.category_just_chatting)
                else -> category?.name.orEmpty()
            }
            ContentPageHeader(title, onBack)
        },
    ) { insets ->
        SkeletonLoadingScope(
            active = state.loadingCategory || eventState.isLoading || state.loadingContent && category != null,
            modifier = Modifier.padding(insets),
        ) {
            PullToRefreshBox(
                isRefreshing = state.loadingCategory && state.category != null,
                onRefresh = refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (category == null) {
                        if (state.categoryFailed) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ContentState(stringResource(R.string.category_load_failed), refresh)
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }) { CategoryHeroSkeleton() }
                            item(span = { GridItemSpan(maxLineSpan) }) { CategoryEvents(emptyList(), true, onEventClick) }
                            items(6) { StreamCardSkeleton() }
                        }
                        return@LazyVerticalGrid
                    }

                    item(key = "category", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            CategoryHero(category, canFollow = !state.loadingCategory && !state.followingPending) {
                                if (session.signedIn) viewModel.toggleFollow() else AccountGraph.auth.beginLogin(context)
                            }
                        }
                    }
                    if (events.isNotEmpty() || eventState.isLoading) {
                        item(key = "events", span = { GridItemSpan(maxLineSpan) }) {
                            CategoryEvents(events, eventState.isLoading, onEventClick)
                        }
                    }
                    item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            CategoryTabs(state.tab, viewModel::selectTab)
                            if (state.tab == CategoryTab.LIVESTREAMS) {
                                CategoryFilters(
                                    state, languages, viewModel::selectLanguage, viewModel::selectSort,
                                )
                            }
                        }
                    }

                    if (state.loadingContent) {
                        items(6) {
                            if (state.tab == CategoryTab.LIVESTREAMS) StreamCardSkeleton() else VideoCardSkeleton()
                        }
                    } else if (state.tab == CategoryTab.LIVESTREAMS) {
                        items(state.streams, key = { it.id }) { stream ->
                            val slug = stream.channel?.slug?.takeIf(String::isNotBlank)
                            StreamCard(
                                stream = stream,
                                onClick = { slug?.let { onStreamClick(it, null) } },
                                onClickWithSource = { source -> slug?.let { onStreamClick(it, source) } },
                                compact = true,
                            )
                        }
                    } else {
                        items(state.clips, key = { it.id }) { clip ->
                            ClipVideoCard(clip, onClick = { onClipClick(clip.id) })
                        }
                    }

                    val livestreams = state.tab == CategoryTab.LIVESTREAMS
                    val empty = if (livestreams) state.streams.isEmpty() else state.clips.isEmpty()
                    when {
                        state.contentFailed || state.categoryFailed -> {
                            item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                                val message = when {
                                    state.categoryFailed -> R.string.category_load_failed
                                    livestreams -> R.string.category_streams_load_failed
                                    else -> R.string.channel_clips_error
                                }
                                ContentState(
                                    stringResource(message),
                                    if (state.categoryFailed) refresh else viewModel::retryContent,
                                )
                            }
                        }
                        !state.loadingContent && empty -> {
                            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                                ContentState(stringResource(
                                    if (livestreams) R.string.category_no_live_streams else R.string.category_no_clips
                                ))
                            }
                        }
                        state.nextCursor != null -> {
                            item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                                LaunchedEffect(state.tab, state.nextCursor) { viewModel.loadMore() }
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHero(category: BrowseCategory, canFollow: Boolean, onFollow: () -> Unit) {
    val density = LocalDensity.current
    val bannerWidth = with(density) { 104.dp.roundToPx() }
    val banner = remember(category.banner, bannerWidth) {
        category.banner?.url?.takeIf(String::isNotBlank)
            ?: getResponsiveImageUrl(category.banner?.responsive, targetWidthPx = bannerWidth)
    }
    val description = category.description?.takeIf(String::isNotBlank)
    val descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val descriptionBeside = description != null && maxWidth >= 560.dp * density.fontScale.coerceAtLeast(1f)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryImage(banner, 104, 139, 8)
                CategoryInfo(category, canFollow, onFollow, Modifier.weight(1f))
                if (descriptionBeside) {
                    Text(
                        description, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        color = descriptionColor,
                    )
                }
            }
            if (!descriptionBeside && description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = descriptionColor)
            }
        }
    }
}

@Composable
private fun CategoryInfo(category: BrowseCategory, canFollow: Boolean, onFollow: () -> Unit, modifier: Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CategoryStat(FormatUtils.formatCompactCount(category.viewers.toLong(), locale), stringResource(R.string.viewers))
            CategoryStat(FormatUtils.formatCompactCount(category.followersCount, locale), stringResource(R.string.followers))
        }
        if (category.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                category.tags.forEach { MetadataPill(it) }
            }
        }
        Button(
            onClick = onFollow,
            enabled = canFollow,
            modifier = Modifier.height(34.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (category.followed) scheme.surfaceContainerHighest else scheme.primary,
                contentColor = if (category.followed) scheme.onSurface else scheme.onPrimary,
                disabledContainerColor = if (category.followed) scheme.surfaceContainerHighest else scheme.primary,
                disabledContentColor = if (category.followed) scheme.onSurface else scheme.onPrimary,
            ),
            border = if (category.followed) BorderStroke(1.dp, scheme.outlineVariant) else null,
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Icon(
                painterResource(
                    if (category.followed) R.drawable.favorite_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                    else R.drawable.favorite_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                ),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(if (category.followed) R.string.following else R.string.follow),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun CategoryStat(value: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = scheme.primary, fontWeight = FontWeight.Bold)) { append(value) }
            append(' ')
            append(label)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
    )
}

@Composable
private fun CategoryHeroSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SkeletonBlock(Modifier.size(104.dp, 139.dp), RoundedCornerShape(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.size(150.dp, 24.dp))
            SkeletonBlock(Modifier.size(184.dp, 16.dp))
            SkeletonBlock(Modifier.size(112.dp, 16.dp))
            SkeletonBlock(Modifier.size(82.dp, 34.dp))
        }
    }
}

@Composable
private fun CategoryEvents(events: List<ChannelEvent>, loading: Boolean, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.events),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (events.isEmpty() && loading) {
                items(3) {
                    Surface(
                        modifier = Modifier.size(180.dp, 56.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SkeletonBlock(Modifier.size(38.dp), RoundedCornerShape(6.dp))
                            Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SkeletonBlock(Modifier.size(92.dp, 14.dp))
                                SkeletonBlock(Modifier.size(56.dp, 10.dp))
                            }
                        }
                    }
                }
            } else {
                items(events, key = { checkNotNull(it.id) }) { event ->
                    CategoryEventCard(event, onOpen)
                }
            }
        }
    }
}

@Composable
private fun CategoryEventCard(event: ChannelEvent, onOpen: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val scheme = MaterialTheme.colorScheme
    val slug = event.slug?.takeIf(String::isNotBlank) ?: event.id
    Surface(
        onClick = { slug?.let(onOpen) },
        enabled = slug != null,
        modifier = Modifier.size(180.dp, 56.dp),
        shape = RoundedCornerShape(8.dp),
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryImage(event.assets?.thumbnail ?: event.assets?.promoPoster ?: event.assets?.banner, 38, 38, 6)
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    event.title.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.category_event_streaming,
                        FormatUtils.formatCompactCount(event.numLivestreams.toLong(), locale),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryImage(url: String?, width: Int, height: Int, corners: Int) {
    val context = LocalContext.current
    val request = remember(context, url, width, height) {
        buildOptimizedImageRequest(context, url, targetWidthDp = width, targetHeightDp = height)
    }
    Surface(
        modifier = Modifier.size(width.dp, height.dp),
        shape = RoundedCornerShape(corners.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTabs(selected: CategoryTab, onSelect: (CategoryTab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SecondaryTabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = scheme.background,
        divider = { HorizontalDivider(color = scheme.outlineVariant) },
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selected.ordinal),
                height = 2.dp,
                color = scheme.primary,
            )
        },
    ) {
        CategoryTab.entries.forEach { tab ->
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                text = { Text(stringResource(tab.label), maxLines = 1) },
                selectedContentColor = scheme.onSurface,
                unselectedContentColor = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryFilters(
    state: CategoryUiState,
    languages: List<String>,
    onLanguageSelected: (String?) -> Unit,
    onSortSelected: (CategoryStreamSort) -> Unit,
) {
    val allLanguages = stringResource(R.string.category_all_languages)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.category_filter_by), style = MaterialTheme.typography.labelMedium, color = labelColor)
            FilterDropdown(
                title = state.language?.let(::streamLanguageName) ?: stringResource(R.string.category_languages),
                options = listOf(null) + languages,
                label = { it?.let(::streamLanguageName) ?: allLanguages },
                selected = state.language != null,
                compact = true,
                onSelect = onLanguageSelected,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.category_sort_by), style = MaterialTheme.typography.labelMedium, color = labelColor)
            FilterDropdown(
                title = stringResource(state.sort.label),
                options = CategoryStreamSort.entries,
                label = { stringResource(it.label) },
                compact = true,
                onSelect = onSortSelected,
            )
        }
    }
}
