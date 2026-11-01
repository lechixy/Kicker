package com.lechixy.kick.components.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.following.model.FollowedChannel

@Composable
fun FollowedChannelCard(channel: FollowedChannel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = modifier) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = channel.avatar, contentDescription = null, modifier = Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.username, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (channel.live) "Canlı" else "Çevrimdışı", color = MaterialTheme.colorScheme.primary)
                channel.title?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                channel.category?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (channel.live && channel.showViewers && channel.viewers != null) Text("${channel.viewers} izleyici", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AccountFollowingSection(onChannelClick: (String) -> Unit) {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    val state by AccountGraph.following.state.collectAsStateWithLifecycle()
    if (!session.signedIn) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Takip edilen kanallar",
            onMore = if (state.nextCursor != null) AccountGraph.following::loadMore else null,
            moreEnabled = !state.loading)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = AccountGraph.following::refresh) { Text("Yeniden dene") }
        }
        if (state.loaded && state.channels.isEmpty()) Text("Henüz takip ettiğin bir kanal yok.")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.channels.sortedByDescending { it.live }, key = { it.slug }) { channel ->
                FollowedChannelCard(channel, Modifier.width(280.dp)) { onChannelClick(channel.slug) }
            }
        }
    }
}
