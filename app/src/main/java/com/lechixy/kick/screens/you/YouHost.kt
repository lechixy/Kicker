package com.lechixy.kick.screens.you

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lechixy.kick.components.account.AccountDashboard
import com.lechixy.kick.components.navigationEnter
import com.lechixy.kick.components.navigationExit
import com.lechixy.kick.components.navigationPopEnter
import com.lechixy.kick.components.navigationPopExit
import com.lechixy.kick.data.player.local.PlaybackUsageStore
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

internal enum class YouPage { OVERVIEW, WATCH_TIME, DATA_USAGE, DOWNLOADS }

@Composable
internal fun YouHost(onExit: () -> Unit, onChannelClick: (String) -> Unit, initialPage: YouPage = YouPage.OVERVIEW) {
    val nav = rememberNavController()
    val usage by PlaybackUsageStore.entries.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(UsagePeriod.WEEK) }
    var savedCustomDays by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var choosingCustomDays by rememberSaveable { mutableStateOf(false) }
    val customDays = savedCustomDays?.let { it[0]..it[1] }
    val selectPeriod: (UsagePeriod) -> Unit = { option ->
        if (option == UsagePeriod.CUSTOM) choosingCustomDays = true else period = option
    }
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            val current = ZonedDateTime.now()
            value = current
            delay(60_000 - Math.floorMod(current.toInstant().toEpochMilli(), 60_000L))
        }
    }
    val watch by rememberUpdatedState(remember(usage, period, now, customDays) {
        UsageReport(usage, UsageMetric.WATCH_TIME, period, now, customDays)
    })
    val data by rememberUpdatedState(remember(usage, period, now, customDays) {
        UsageReport(usage, UsageMetric.DATA, period, now, customDays)
    })
    val hasHistory by rememberUpdatedState(usage.isNotEmpty())
    val openChannel by rememberUpdatedState(onChannelClick)
    val exit by rememberUpdatedState(onExit)
    val back = { if (!nav.popBackStack()) exit() }
    val open: (YouPage) -> Unit = { page -> nav.navigate(page.name) { launchSingleTop = true } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 640.dp
        val accountWidth = (maxWidth * 0.36f).coerceIn(248.dp, 400.dp)
        val showAccountInOverview by rememberUpdatedState(!expanded)
        Row(Modifier.fillMaxSize()) {
            if (expanded) {
                Box(Modifier.width(accountWidth).fillMaxHeight()
                    .verticalScroll(rememberScrollState()).padding(16.dp)) {
                    AccountDashboard(openChannel)
                }
                VerticalDivider()
            }
            NavHost(
                navController = nav,
                startDestination = initialPage.name,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                enterTransition = { navigationEnter(initialState.destination.route, targetState.destination.route) },
                exitTransition = { navigationExit(initialState.destination.route, targetState.destination.route) },
                popEnterTransition = { navigationPopEnter(initialState.destination.route, targetState.destination.route) },
                popExitTransition = { navigationPopExit(initialState.destination.route, targetState.destination.route) },
            ) {
                composable(YouPage.OVERVIEW.name) {
                    YouScreen(openChannel, open, showAccountInOverview, watch, data, selectPeriod)
                }
                composable(YouPage.WATCH_TIME.name) {
                    UsageScreen(watch, selectPeriod, hasHistory, back, openChannel)
                }
                composable(YouPage.DATA_USAGE.name) {
                    UsageScreen(data, selectPeriod, hasHistory, back, openChannel)
                }
                composable(YouPage.DOWNLOADS.name) { DownloadsScreen(onBack = back) }
            }
        }
    }
    if (choosingCustomDays) {
        UsageDateRangeDialog(customDays, onDismiss = { choosingCustomDays = false }) { range ->
            savedCustomDays = longArrayOf(range.first, range.last)
            period = UsagePeriod.CUSTOM
            choosingCustomDays = false
        }
    }
}
