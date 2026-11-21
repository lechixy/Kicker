package com.lechixy.kick.components.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R

@Composable
internal fun SettingResetButton(title: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        Icon(
            painterResource(R.drawable.restart_alt_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
            contentDescription = stringResource(R.string.settings_reset_value, title),
            modifier = Modifier.size(18.dp)
        )
    }
}
