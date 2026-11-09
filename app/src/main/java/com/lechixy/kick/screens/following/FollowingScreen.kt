package com.lechixy.kick.screens.following

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lechixy.kick.R
import com.lechixy.kick.components.ChannelCard
import com.lechixy.kick.components.ChannelCardSkeleton
import com.lechixy.kick.components.ContentGrid
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.components.ExpansionSource
import com.lechixy.kick.components.HomeTopBar
import com.lechixy.kick.components.SkeletonLoadingScope
import com.lechixy.kick.components.StreamCard
import com.lechixy.kick.components.StreamCardSkeleton
import com.lechixy.kick.components.account.AccountSignInState
import com.lechixy.kick.components.cardSection
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelUser

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FollowingScreen(viewModel: FollowingViewModel = viewModel(),
    onCategoryClick: (String) -> Unit = com.lechixy.kick.components.LocalContentNavigation.current.category,
    onStreamClick: (channelSlug: String, source: ExpansionSource?) -> Unit) {
    val liveTitle = stringResource(R.string.following_online)
    val offlineTitle = stringResource(R.string.following_offline)
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val offline = remember(state.pageChannels) { state.pageChannels.filterNot { it.live } }
    val refreshing = state.loading || state.streamsLoading
    SkeletonLoadingScope(session.signedIn && refreshing) {
        Scaffold(topBar = { HomeTopBar() }) { padding ->
            PullToRefreshBox(refreshing, viewModel::refresh, Modifier.fillMaxSize().padding(padding)) {
                ContentGrid { columns ->
                    if (!session.signedIn) {
                        item(span = { GridItemSpan(maxLineSpan) }) { AccountSignInState() }
                    } else {
                        cardSection("live", liveTitle, state.livestreams, columns,
                            loading = state.streamsLoading, itemKey = { it.id }, skeleton = { StreamCardSkeleton() }) { stream ->
                            StreamCard(stream, onClick = { stream.channel?.slug?.let { onStreamClick(it, null) } },
                                onClickWithSource = { source -> stream.channel?.slug?.let { onStreamClick(it, source) } },
                                onCategoryClick = onCategoryClick)
                        }
                        if (!state.streamsLoading) {
                            state.streamsError?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) { ContentState(message, viewModel::retryStreams) } }
                            if (state.streamsLoaded && state.livestreams.isEmpty() && state.streamsError == null)
                                item(span = { GridItemSpan(maxLineSpan) }) { ContentState(stringResource(R.string.following_no_online)) }
                        }
                        cardSection("offline", offlineTitle, offline, columns,
                            loading = state.loading, itemSpan = if (wide) 1 else columns, itemKey = { it.slug }, skeleton = { ChannelCardSkeleton() }) { channel ->
                            ChannelCard(ChannelDetail(id = 0, slug = channel.slug,
                                user = ChannelUser(id = 0, username = channel.username, profilePic = channel.avatar)),
                                onChannelClick = { onStreamClick(it, null) }, onChannelClickWithSource = onStreamClick)
                        }
                        if (!state.loading) {
                            state.error?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) { ContentState(message, viewModel::retryChannels) } }
                            if (state.loaded && offline.isEmpty() && state.error == null)
                                item(span = { GridItemSpan(maxLineSpan) }) { ContentState(stringResource(R.string.following_no_offline)) }
                        }
                        if (state.loaded && (state.pageIndex > 0 || state.nextCursor != null)) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                val previous = stringResource(R.string.following_previous)
                                val page = stringResource(R.string.following_page, state.pageIndex + 1)
                                val next = stringResource(R.string.following_next)
                                Row(
                                    Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                                        .widthIn(max = 480.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                ) {
                                    Button(onClick = viewModel::previousPage,
                                        enabled = state.pageIndex > 0 && !state.loading,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        shapes = ButtonDefaults.shapes(
                                            shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                                            pressedShape = ButtonGroupDefaults.connectedLeadingButtonPressShape)) {
                                        Text(previous, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Button(onClick = {}, enabled = false,
                                        modifier = Modifier.weight(.8f).height(48.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        shapes = ButtonDefaults.shapes(shape = MaterialTheme.shapes.small),
                                        colors = ButtonDefaults.buttonColors(
                                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                                        Text(page, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Button(onClick = viewModel::loadMore,
                                        enabled = state.nextCursor != null && !state.loading,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        shapes = ButtonDefaults.shapes(
                                            shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                                            pressedShape = ButtonGroupDefaults.connectedTrailingButtonPressShape)) {
                                        Text(next, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
