package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.launch

@Composable
internal fun chatRulesFooter(
    channelId: Long,
    userId: Long?,
    rules: String?,
    onAccepted: () -> Unit
): (@Composable () -> Unit)? {
    val rulesText = rules?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val repository = LocalSettingsRepository.current
    val version = remember(rulesText) {
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(rulesText.encodeToByteArray()))
    }
    val setting = remember(userId, channelId) { userId?.let { SettingsKeys.chatRulesAcceptance(it, channelId) } }
    val saved = key(setting) {
        setting?.let { remember(repository, it) { repository.get(it) }.collectAsStateWithLifecycle(initialValue = null).value }
    }
    val scope = rememberCoroutineScope()
    if (saved == version) return null

    var saving by remember(setting, version) { mutableStateOf(false) }
    var saveFailed by remember(setting, version) { mutableStateOf(false) }
    return {
        if (setting == null || saved != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.chat_rules), style = MaterialTheme.typography.titleSmall)
                    Text(rulesText, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false).heightIn(max = 144.dp).verticalScroll(rememberScrollState()))
                    if (saveFailed) {
                        Text(stringResource(R.string.chat_rules_save_failed), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        enabled = setting != null && !saving,
                        modifier = Modifier.align(Alignment.End),
                        onClick = {
                            if (setting != null) {
                                saving = true
                                saveFailed = false
                                scope.launch {
                                    try {
                                        repository.set(setting, version)
                                        onAccepted()
                                    } catch (_: IOException) {
                                        saveFailed = true
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.chat_rules_accept)) }
                }
            }
        }
    }
}
