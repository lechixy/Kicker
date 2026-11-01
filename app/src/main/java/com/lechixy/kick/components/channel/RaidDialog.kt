package com.lechixy.kick.components.channel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lechixy.kick.R
import com.lechixy.kick.components.StreamCard
import com.lechixy.kick.data.chat.model.ChannelRaid
import com.lechixy.kick.data.channel.model.toLivestream
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RaidDialog(sourceName: String, raid: ChannelRaid, onStay: () -> Unit, onJoin: () -> Unit) {
    var remaining by remember(raid) { mutableIntStateOf(10) }
    var stream by remember(raid) { mutableStateOf(raid.toLivestream()) }
    var resolved by remember(raid) { mutableStateOf(false) }
    val accept = {
        if (!resolved) { resolved = true; onJoin() }
    }
    val stay = {
        if (!resolved) { resolved = true; onStay() }
    }
    val join by rememberUpdatedState(accept)
    LaunchedEffect(raid) {
        repeat(10) {
            delay(1000)
            remaining--
        }
        join()
    }
    LaunchedEffect(raid) {
        try {
            stream = KickRepository(KickNetwork.api).getChannel(raid.targetSlug).toLivestream()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep the event's preview when metadata is unavailable. */ }
    }
    com.lechixy.kick.components.PredictiveDialog(onDismissRequest = stay, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.raid_title, sourceName), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StreamCard(stream, onClick = accept, onCategoryClick = {})
                    LinearWavyProgressIndicator(progress = { remaining / 10f }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.raid_countdown, remaining), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = stay, colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface)) { Text(stringResource(R.string.raid_stay)) }
                    TextButton(onClick = accept, colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary)) { Text(stringResource(R.string.raid_join)) }
                }
            }
        }
    }
}
