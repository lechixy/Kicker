package com.lechixy.kick.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight

import com.lechixy.kick.R
import com.lechixy.kick.screens.settings.LocalSettingsSplitPane

internal val SettingsContentWidth = 840.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(
    title: String,
    onBackClick: () -> Unit
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    TopAppBar(
        modifier = Modifier.widthIn(max = SettingsContentWidth).fillMaxWidth(),
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = { Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        ) },
        navigationIcon = {
            if (!LocalSettingsSplitPane.current) IconButton(onClick = onBackClick) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.navigation_back)
                )
            }
        }
    )
    }
}
