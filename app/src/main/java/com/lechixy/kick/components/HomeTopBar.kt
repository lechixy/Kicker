package com.lechixy.kick.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.lechixy.kick.components.account.AccountButton
import com.lechixy.kick.components.account.ChallengesButton

internal val LocalRailBrandVisible = compositionLocalOf { false }

@Composable
fun HomeTopBar(
    onSearchClick: (() -> Unit)? = null,
    onChannelClick: (String) -> Unit = LocalContentNavigation.current.channel,
    showAccount: Boolean = true,
    centerContent: (@Composable () -> Unit)? = null,
    forceVisible: Boolean = false
) {
    if (LocalSharedTopBar.current && !forceVisible) return
    val navigation = LocalContentNavigation.current
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val center: (@Composable () -> Unit)? = centerContent ?: if (wide) ({
        TopBarSearchField(onClick = onSearchClick ?: { navigation.search("") })
    }) else null
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(if (center == null) Modifier.weight(1f) else Modifier.widthIn(max = 180.dp)) {
            FadingAppBrand(visible = !LocalRailBrandVisible.current)
        }
        if (center != null) Box(Modifier.weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { center() }
        Row(Modifier.widthIn(max = 232.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ChallengesButton()
            if (showAccount) AccountButton(onChannelClick = onChannelClick, modifier = Modifier.weight(1f, fill = false))
        }
    }
}
