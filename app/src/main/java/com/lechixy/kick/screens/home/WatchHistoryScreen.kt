package com.lechixy.kick.screens.home

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.ContentGrid
import com.lechixy.kick.components.ContentPageHeader
import com.lechixy.kick.components.ContinueWatchingCard
import com.lechixy.kick.components.ExpansionSource
import com.lechixy.kick.components.LocalContentNavigation
import com.lechixy.kick.components.settings.SettingsContentWidth
import com.lechixy.kick.data.player.local.WatchHistoryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val WatchHistoryRoute = "home/watch-history"

@Composable
internal fun WatchHistoryScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    val history by viewModel.watchHistory.collectAsStateWithLifecycle()
    val (openingHistory, playHistory) = rememberHistoryPlayback(viewModel)
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ContentPageHeader(
                title = stringResource(R.string.home_continue_watching),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty() && !openingHistory) {
                        Icon(
                            painterResource(R.drawable.delete_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            stringResource(R.string.home_clear_history),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.home_history_empty),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                ContentGrid(Modifier.widthIn(max = SettingsContentWidth).padding(top = 6.dp)) {
                    items(history, key = { it.key }) { entry ->
                        Box(Modifier.animateItem()) {
                            ContinueWatchingCard(
                                entry = entry,
                                enabled = !openingHistory,
                                onRemove = { viewModel.removeHistory(entry.key) },
                                onClick = { playHistory(entry, it) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.home_clear_history)) },
            text = { Text(stringResource(R.string.home_clear_history_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                }) {
                    Text(stringResource(R.string.home_clear_history_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun rememberHistoryPlayback(
    viewModel: HomeViewModel,
): Pair<Boolean, (WatchHistoryEntry, ExpansionSource?) -> Unit> {
    val navigation = LocalContentNavigation.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    val play: (WatchHistoryEntry, ExpansionSource?) -> Unit = { entry, source ->
        if (!opening) {
            opening = true
            scope.launch {
                try {
                    val recording = viewModel.resolveHistoryRecording(entry)
                    if (recording != null) {
                        navigation.recording(entry.channelSlug, recording, source)
                    } else {
                        Toast.makeText(context, R.string.home_recording_unavailable, Toast.LENGTH_SHORT).show()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.home_recording_unavailable, Toast.LENGTH_SHORT).show()
                } finally {
                    opening = false
                }
            }
        }
    }
    return opening to play
}
