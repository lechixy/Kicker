package com.lechixy.kick.screens.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.lechixy.kick.R
import com.lechixy.kick.components.CategoryRow
import com.lechixy.kick.components.ChannelCard
import com.lechixy.kick.components.ChannelCardSkeleton
import com.lechixy.kick.components.ContentGrid
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.components.ExpansionSource
import com.lechixy.kick.components.HomeTopBar
import com.lechixy.kick.components.LocalContentNavigation
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.SkeletonLoadingScope
import com.lechixy.kick.components.StreamCard
import com.lechixy.kick.components.StreamCardSkeleton
import com.lechixy.kick.components.cardSection
import com.lechixy.kick.data.browse.model.BrowseCategory
import com.lechixy.kick.data.browse.model.CategoryBanner
import com.lechixy.kick.data.browse.model.toChannelDetail
import com.lechixy.kick.data.common.model.Channel
import com.lechixy.kick.data.common.model.Livestream
import com.lechixy.kick.data.common.model.Thumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(viewModel: BrowseViewModel = viewModel(), onCategoryClick: (String) -> Unit = com.lechixy.kick.components.LocalContentNavigation.current.category,
    initialQuery: String = "",
    onChannelClick: (channelSlug: String, source: ExpansionSource?) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) viewModel.onQueryChange(initialQuery) }
    val channelsTitle = stringResource(R.string.section_channels)
    val streamsTitle = stringResource(R.string.section_livestreams)
    SkeletonLoadingScope(state.isLoading) {
        Scaffold(topBar = {
            Column {
                HomeTopBar(onChannelClick = { onChannelClick(it, null) }, centerContent = if (wide) ({ BrowseSearchBar(viewModel) }) else null)
                if (!wide) Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) { BrowseSearchBar(viewModel) }
            }
        }) { padding ->
            ContentGrid(Modifier.padding(padding)) { columns ->
                val data = state.searchData
                if (state.isLoading || data?.channels?.isNotEmpty() == true) {
                    cardSection("channels", channelsTitle, data?.channels.orEmpty(), columns, loading = state.isLoading,
                        itemSpan = if (wide) 1 else columns, itemKey = { it.slug }, skeleton = { ChannelCardSkeleton() }) { channel ->
                        ChannelCard(channel.toChannelDetail(), onChannelClick = { onChannelClick(it, null) },
                            onChannelClickWithSource = onChannelClick)
                    }
                }
                if (state.isLoading || data?.categories?.isNotEmpty() == true) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.section_categories)) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CategoryRow(data?.categories.orEmpty().map { BrowseCategory(name = it.name, slug = it.slug,
                            banner = CategoryBanner(it.thumbnail?.src)) }, state.isLoading, onCategoryClick, showTag = false)
                    }
                }
                if (state.isLoading || data?.livestreams?.isNotEmpty() == true) {
                    cardSection("streams", streamsTitle, data?.livestreams.orEmpty(), columns, loading = state.isLoading,
                        itemKey = { it.slug }, skeleton = { StreamCardSkeleton() }) { stream ->
                        StreamCard(Livestream(id = stream.slug, title = stream.title,
                            thumbnail = Thumbnail(stream.thumbnail?.src), showViewCount = false,
                            channel = Channel(0, slug = stream.slug, username = stream.slug)),
                            onClick = { onChannelClick(stream.slug, null) },
                            onClickWithSource = { onChannelClick(stream.slug, it) }, onCategoryClick = onCategoryClick)
                    }
                }
                if (!state.isLoading) item(span = { GridItemSpan(maxLineSpan) }) {
                    when {
                        state.error != null -> ContentState(stringResource(R.string.search_failed), { viewModel.onQueryChange(state.query) })
                        data == null -> ContentState(stringResource(R.string.search_hint))
                        data.channels.isEmpty() && data.categories.isEmpty() && data.livestreams.isEmpty() -> ContentState(stringResource(R.string.search_empty))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseSearchBar(viewModel: BrowseViewModel) {
    val state by viewModel.uiState.collectAsState()
    Surface(modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().height(56.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            SearchBarDefaults.InputField(query = state.query, onQueryChange = viewModel::onQueryChange,
                onSearch = {}, expanded = false, onExpandedChange = {},
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(painterResource(R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null) },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.onQueryChange("") }) {
                    Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24), stringResource(R.string.search_clear))
                } })
    }
    }
