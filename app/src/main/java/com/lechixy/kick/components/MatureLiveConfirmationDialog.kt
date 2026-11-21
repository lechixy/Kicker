package com.lechixy.kick.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R

@Composable
internal fun MatureLiveConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
) {
    var dontAskAgain by rememberSaveable { mutableStateOf(false) }
    PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_18_up_rating_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.mature_live_title),
                modifier = Modifier.semantics { heading() },
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.mature_live_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = dontAskAgain,
                        role = Role.Checkbox,
                        onValueChange = { dontAskAgain = it },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(checked = dontAskAgain, onCheckedChange = null)
                    Text(stringResource(R.string.mature_live_dont_ask))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.mature_live_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dontAskAgain) }) { Text(stringResource(R.string.mature_live_watch)) }
        },
    )
}
