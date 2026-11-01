package com.lechixy.kick.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.lechixy.kick.components.account.AccountButton
import com.lechixy.kick.components.account.ChallengesButton

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
        androidx.compose.material3.Surface(onClick = onSearchClick ?: { navigation.browse("") },
            shape = androidx.compose.foundation.shape.CircleShape,
            color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().height(56.dp)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.lechixy.kick.R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null)
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.lechixy.kick.R.string.search_placeholder))
            }
        }
    }) else null
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(if (center == null) Modifier.weight(1f) else Modifier.widthIn(max = 180.dp)) { ColoredAppText() }
        if (center != null) Box(Modifier.weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { center() }
        Row(Modifier.widthIn(max = 232.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ChallengesButton()
            if (showAccount) AccountButton(onChannelClick = onChannelClick, modifier = Modifier.weight(1f, fill = false))
        }
    }
}
