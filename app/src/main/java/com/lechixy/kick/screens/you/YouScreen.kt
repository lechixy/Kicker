package com.lechixy.kick.screens.you

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.CardSection
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.account.AccountDashboard
import com.lechixy.kick.components.settings.SettingsCardSurface
import com.lechixy.kick.components.settings.SettingsContentWidth
import com.lechixy.kick.data.favorites.local.FavoriteUser
import com.lechixy.kick.data.favorites.local.FavoriteUsersStore
import com.lechixy.kick.data.player.local.observeRecordingDownloads
import java.util.Locale

@Composable
internal fun YouScreen(
    onChannelClick: (String) -> Unit,
    onOpenPage: (YouPage) -> Unit,
    showAccount: Boolean,
    watch: UsageReport,
    data: UsageReport,
    onPeriodChange: (UsagePeriod) -> Unit,
) {
    val context = LocalContext.current
    val downloads by remember(context) { observeRecordingDownloads(context) }.collectAsStateWithLifecycle(null)
    val favorites by FavoriteUsersStore.favorites.collectAsStateWithLifecycle()
    val recordings = downloads?.getOrNull().orEmpty()
    var playingUri by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = SettingsContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showAccount) item("account") { AccountDashboard(onChannelClick) }
            item("activity") {
                Column {
                    SectionHeader(stringResource(R.string.you_usage_activity))
                    UsagePeriodButtons(watch, onPeriodChange)
                }
            }
            item("watch-time") { UsageOverviewCard(watch) { onOpenPage(YouPage.WATCH_TIME) } }
            item("data-usage") { UsageOverviewCard(data) { onOpenPage(YouPage.DATA_USAGE) } }
            item("downloads") {
                Column {
                    CardSection(
                        title = stringResource(R.string.you_downloads),
                        data = recordings,
                        previewRows = 1,
                        onMore = { onOpenPage(YouPage.DOWNLOADS) },
                    ) { recording ->
                        DownloadCard(recording, onClick = { playingUri = recording.uri.toString() })
                    }
                    DownloadListMessage(downloads)
                }
            }
            item("favorites") {
                SectionHeader(stringResource(R.string.you_favorite_channels),
                    leadingIcon = R.drawable.favorite_24dp_e3e3e3_fill1_wght400_grad0_opsz24)
            }
            if (favorites.users.isEmpty()) item("favorites-empty") {
                SettingsCardSurface {
                    Text(stringResource(R.string.you_favorite_channels_empty), Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(favorites.users) { FavoriteChannelCard(it, onChannelClick) }
        }
    }
    recordings.firstOrNull { it.uri.toString() == playingUri }?.let { recording ->
        DownloadPlaybackDialog(recording, onDismiss = { playingUri = null })
    }
}

@Composable
private fun FavoriteChannelCard(favorite: FavoriteUser, onChannelClick: (String) -> Unit) {
    val slug = favorite.slug.ifBlank { favorite.username.trim().lowercase(Locale.ROOT) }
    val name = favorite.username.ifBlank { slug }
    SettingsCardSurface {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = slug.isNotBlank(), role = Role.Button,
                onClickLabel = stringResource(R.string.you_data_usage_open_channel)) { onChannelClick(slug) }.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UsageChannelAvatar(slug, name, avatar = null, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (slug.isNotBlank()) {
                    Text(stringResource(R.string.you_channel_handle, slug), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ChannelChevron()
        }
    }
}

@Composable
internal fun YouSectionMessage(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
