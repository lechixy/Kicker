package com.lechixy.kick.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

import com.lechixy.kick.R
import com.lechixy.kick.components.account.ChallengesButton

internal val LocalRailBrandVisible = compositionLocalOf { false }

@Composable
fun HomeTopBar(
    onSearchClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    settingsSelected: Boolean = false,
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
            if (onSettingsClick != null) {
                TopBarActionButton(
                    onClick = onSettingsClick,
                    label = stringResource(R.string.settings),
                    selected = settingsSelected,
                ) {
                    Icon(painterResource(if (settingsSelected)
                        R.drawable.settings_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                    else R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null)
                }
            }
        }
    }
}

@Composable
internal fun TopBarActionButton(onClick: () -> Unit, label: String, selected: Boolean? = null, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.size(46.dp).semantics {
            contentDescription = label
            if (selected != null) this.selected = selected
        },
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}
