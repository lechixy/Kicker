package com.lechixy.kick.screens.you

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.CardSection
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.account.AccountDashboard
import com.lechixy.kick.data.favorites.local.FavoriteUsersStore
import com.lechixy.kick.data.player.local.PlaybackUsageStore
import com.lechixy.kick.data.player.local.observeRecordingDownloads
import java.util.Locale

@Composable
internal fun YouScreen(
    onChannelClick: (String) -> Unit,
    onDownloads: () -> Unit = {},
    onDataUsage: () -> Unit = {},
) {
    val context = LocalContext.current
    val downloads by remember(context) { observeRecordingDownloads(context) }
        .collectAsStateWithLifecycle(initialValue = null)
    val favorites by FavoriteUsersStore.favorites.collectAsStateWithLifecycle()
    val usage by PlaybackUsageStore.entries.collectAsStateWithLifecycle()
    val recordings = downloads?.getOrNull().orEmpty()
    var playingUri by rememberSaveable { mutableStateOf<String?>(null) }

    val sections: LazyListScope.() -> Unit = {
        item("downloads") {
            Column {
                CardSection(
                    title = stringResource(R.string.you_downloads),
                    data = recordings,
                    previewRows = 1,
                    onMore = onDownloads,
                ) { recording ->
                    DownloadCard(recording, onClick = { playingUri = recording.uri.toString() })
                }
                DownloadListMessage(downloads)
            }
        }
        item("data-usage") {
            Column {
                SectionHeader(stringResource(R.string.you_data_usage), onMore = onDataUsage)
                YouSectionMessage(if (usage.isEmpty()) stringResource(R.string.you_data_usage_empty) else
                    usageSummary(usage.sumOf { it.watchedMs }, usage.sumOf { it.bytes }))
            }
        }
        item("favorites") { SectionHeader(stringResource(R.string.you_favorite_channels)) }
        if (favorites.users.isEmpty()) item("favorites-empty") {
            YouSectionMessage(stringResource(R.string.you_favorite_channels_empty))
        }
        items(favorites.users) { favorite ->
            val slug = favorite.slug.ifBlank { favorite.username.trim().lowercase(Locale.ROOT) }
            ListItem(
                leadingContent = {
                    Icon(painterResource(R.drawable.favorite_24dp_e3e3e3_fill1_wght400_grad0_opsz24), null)
                },
                modifier = Modifier.clickable(enabled = slug.isNotBlank(), role = Role.Button) {
                    onChannelClick(slug)
                },
            ) { Text(favorite.username.ifBlank { slug }) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(460.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    AccountDashboard(onChannelClick)
                }
                VerticalDivider()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = sections,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item("account") { AccountDashboard(onChannelClick) }
                sections()
            }
        }
    }
    recordings.firstOrNull { it.uri.toString() == playingUri }?.let { recording ->
        DownloadPlaybackDialog(recording, onDismiss = { playingUri = null })
    }
}

@Composable
internal fun YouSectionMessage(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
