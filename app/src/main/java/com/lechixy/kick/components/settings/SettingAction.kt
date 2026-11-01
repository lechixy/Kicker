package com.lechixy.kick.components.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

import com.lechixy.kick.R

@Composable
fun SettingAction(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Int? = null,
    enabled: Boolean = true
) {
    SettingCard(
        title = title,
        description = description,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        action = {
            Icon(
                painter = painterResource(R.drawable.arrow_forward_ios_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(2.dp)
            )
        }
    )
}