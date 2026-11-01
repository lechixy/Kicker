package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import kotlinx.coroutines.delay

@Composable
internal fun ChatComposerPanel(
    compact: Boolean,
    expanded: Boolean,
    allowAutoHide: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onHide: () -> Unit,
    content: @Composable () -> Unit
) {
    LaunchedEffect(compact, expanded, allowAutoHide) {
        if (compact && expanded && allowAutoHide) {
            delay(5_000)
            onExpandedChange(false)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (compact) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    if (expanded) onHide()
                    onExpandedChange(!expanded)
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null,
                        Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (expanded) R.string.chat_hide_composer else R.string.chat_show_composer))
                }
            }
        }
        if (!compact || expanded) content()
    }
}
