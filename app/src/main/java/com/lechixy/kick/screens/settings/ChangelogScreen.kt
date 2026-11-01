package com.lechixy.kick.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.BuildConfig
import com.lechixy.kick.R
import com.lechixy.kick.components.AppLoadingIndicator
import com.lechixy.kick.components.ReleaseDialog
import com.lechixy.kick.components.settings.SettingCard
import com.lechixy.kick.components.settings.SettingsTopBar
import com.lechixy.kick.data.releases.*

val LocalOpenChangelog = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun ChangelogScreen(viewModel: ReleaseViewModel, onBackClick: () -> Unit) {
    val state by viewModel.history.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.loadHistory() }
    ChangelogContent(state, { viewModel.loadHistory(retry = true) }, onBackClick)
}

@Composable
internal fun ChangelogContent(state: ReleaseHistoryState, onRetry: () -> Unit, onBackClick: () -> Unit) {
    var selected by remember { mutableStateOf<GitHubRelease?>(null) }
    val uri = LocalUriHandler.current
    Scaffold(topBar = { SettingsTopBar(stringResource(R.string.settings_changelog), onBackClick) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium) }
                if (state.loading) item { AppLoadingIndicator(Modifier.padding(16.dp)) }
                if (state.failed) item {
                    Column {
                        Text(stringResource(R.string.changelog_failed))
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
                    }
                }
                if (state.loaded && state.releases.isEmpty()) item { Text(stringResource(R.string.changelog_empty)) }
                items(state.releases, key = { it.tagName }) { release ->
                    SettingCard(title = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
                        description = release.body.orEmpty().lineSequence().firstOrNull { it.isNotBlank() }
                            ?.trim()?.trimStart('#', '*', ' ')?.take(160),
                        onClick = { selected = release })
                }
                item { TextButton(onClick = { runCatching { uri.openUri(ReleaseConfig.RELEASES_URL) } }) {
                    Text(stringResource(R.string.release_on_github))
                } }
            }
        }
    }
    selected?.let { ReleaseDialog(ReleaseDialogRequest(ReleaseDialogMode.WhatsNew, it), { selected = null }) }
}
