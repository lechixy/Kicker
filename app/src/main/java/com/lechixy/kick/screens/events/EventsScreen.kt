package com.lechixy.kick.screens.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

import com.lechixy.kick.R
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.components.HomeTopBar
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.SkeletonBlock
import com.lechixy.kick.components.SkeletonLoadingScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen() {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { HomeTopBar() }) { padding ->
        PullToRefreshBox(refreshing, { scope.launch { refreshing = true; delay(500.milliseconds); refreshing = false } },
            Modifier.fillMaxSize().padding(padding)) {
            SkeletonLoadingScope(refreshing) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    item { SectionHeader(stringResource(R.string.events)) }
                    item {
                        if (refreshing) SkeletonBlock(Modifier.fillMaxWidth().height(180.dp))
                        else ContentState(stringResource(R.string.events_coming_soon))
                    }
                }
            }
        }
    }
}
