package com.lechixy.kick.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.lechixy.kick.R
import com.lechixy.kick.components.settings.SettingCard

@Composable
internal fun PlayerCastDialog(onDismiss: () -> Unit, onGoogleCast: () -> Unit, onWebVideoCaster: () -> Unit) {
    com.lechixy.kick.components.PredictiveDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 640.dp).dialogEntrance(),
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.cast_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null,
                        Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.player_cast), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.player_cast_choose_method), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingCard(title = "Google Cast / Chromecast", description = stringResource(R.string.player_cast_google_description),
                    icon = R.drawable.cast_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = onGoogleCast)
                SettingCard(title = "Web Video Caster", description = stringResource(R.string.player_cast_web_description),
                    image = R.drawable.webvideocasterlogo, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = onWebVideoCaster)
            }
        }
    }
}
