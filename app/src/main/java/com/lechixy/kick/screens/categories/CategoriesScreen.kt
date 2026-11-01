package com.lechixy.kick.screens.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

import com.lechixy.kick.R
import com.lechixy.kick.components.CategoryCard
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.components.HomeTopBar
import com.lechixy.kick.components.LocalContentNavigation
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.SkeletonLoadingScope
import com.lechixy.kick.data.browse.model.BrowseCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = viewModel(), onChannelClick: (String) -> Unit = {},
    selectedSlug: String? = null) {
    val navigation = LocalContentNavigation.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    var selectionRevealed by remember(selectedSlug) { mutableStateOf(false) }
    LaunchedEffect(selectedSlug, state.categories) {
        val index = state.categories.indexOfFirst { it.slug == selectedSlug }
        if (index >= 0 && !selectionRevealed) { grid.animateScrollToItem(index + 1); selectionRevealed = true }
    }
    SkeletonLoadingScope(state.loading || state.loadingMore) {
        Scaffold(topBar = { HomeTopBar(onChannelClick = onChannelClick) }) { padding ->
            PullToRefreshBox(state.loading, viewModel::refresh, Modifier.fillMaxSize().padding(padding)) {
                LazyVerticalGrid(GridCells.Adaptive(150.dp), state = grid, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.section_categories)) }
                    if (state.loading) items(12) { CategoryCard(BrowseCategory(name = "", slug = ""), {}, loading = true) }
                    else items(state.categories, key = { it.slug }) { CategoryCard(it, { navigation.browse(it.name) }) }
                    state.error?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) {
                        ContentState(message, if (state.categories.isEmpty()) viewModel::refresh else viewModel::more)
                    } }
                    if (!state.loading && state.page < state.lastPage && state.error == null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LaunchedEffect(state.page) { viewModel.more() }
                            if (state.loadingMore) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
