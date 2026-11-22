package com.lechixy.kick.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import com.lechixy.kick.data.settings.repository.WinterWelcome

@Composable
internal fun WinterWelcomeDialog(
    welcome: WinterWelcome,
    onDismiss: () -> Unit,
    onTurnOff: () -> Unit,
    onCelebrate: () -> Unit,
) {
    val seasonal = welcome == WinterWelcome.Seasonal
    PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painterResource(if (seasonal) R.drawable.snowflake_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                    else R.drawable.calendar_clock_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        title = { Text(stringResource(if (seasonal) R.string.winter_welcome_title else R.string.winter_offseason_title)) },
        text = { Text(stringResource(if (seasonal) R.string.winter_welcome_text else R.string.winter_offseason_text)) },
        dismissButton = {
            TextButton(onClick = onTurnOff) { Text(stringResource(R.string.winter_turn_off)) }
        },
        confirmButton = {
            TextButton(onClick = onCelebrate) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("❄️", Modifier.clearAndSetSemantics {})
                    Text(stringResource(R.string.winter_celebrate))
                }
            }
        },
    )
}
