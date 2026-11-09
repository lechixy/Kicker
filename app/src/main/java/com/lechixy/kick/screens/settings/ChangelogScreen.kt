package com.lechixy.kick.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import com.lechixy.kick.components.AppLoadingIndicator
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.settings.SettingCard
import com.lechixy.kick.data.releases.GitHubRelease
import com.lechixy.kick.data.releases.ReleaseConfig

internal fun LazyListScope.releaseNotes(state: ReleaseHistoryState, onRetry: () -> Unit, onSelect: (GitHubRelease) -> Unit) {
    item("releases-header") { SectionHeader(stringResource(R.string.settings_changelog)) }
    if (state.loading) item("releases-loading") { AppLoadingIndicator(Modifier.padding(16.dp)) }
    if (state.failed) item("releases-error") {
        Column {
            Text(stringResource(R.string.changelog_failed))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
        }
    }
    if (state.loaded && state.releases.isEmpty()) item("releases-empty") { Text(stringResource(R.string.changelog_empty)) }
    items(state.releases, key = { "release:${it.tagName}" }) { release ->
        SettingCard(title = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
            description = release.body.orEmpty().lineSequence().firstOrNull { it.isNotBlank() }
                ?.trim()?.trimStart('#', '*', ' ')?.take(160),
            onClick = { onSelect(release) })
    }
    item("releases-github") {
        val uri = LocalUriHandler.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilledTonalButton(onClick = { runCatching { uri.openUri(ReleaseConfig.RELEASES_URL) } }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.github), null)
                    Text(stringResource(R.string.github_label))
                }
            }
        }
    }
}
