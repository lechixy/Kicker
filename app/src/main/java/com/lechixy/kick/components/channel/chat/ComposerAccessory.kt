package com.lechixy.kick.components.channel.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.SubscriberBadge
import com.lechixy.kick.data.chat.model.ChatMessage

@Composable
internal fun ComposerAccessory(visible: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    AnimatedVisibility(visible,
        enter = expandVertically(tween(210), expandFrom = Alignment.Bottom) + fadeIn(tween(160)) + slideInVertically(tween(210)) { it / 4 },
        exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Bottom) + fadeOut(tween(120)) + slideOutVertically(tween(180)) { it / 4 },
        content = content)
}

@Composable
fun ChatReplyPreview(message: ChatMessage?, onDismiss: () -> Unit, subscriberBadges: List<SubscriberBadge> = emptyList(),
    userColors: Map<String, String> = emptyMap(), streamerUsername: String? = null, streamerSlug: String? = null) {
    var previous by remember { mutableStateOf<ChatMessage?>(null) }
    SideEffect { if (message != null) previous = message }
    val displayed = message ?: previous
    ComposerAccessory(message != null) {
        if (displayed != null) Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.reply_24dp), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.chat_replying_to, displayed.sender?.username.orEmpty()),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24), stringResource(R.string.chat_cancel_reply))
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(max = 96.dp)) {
                    ChatMessageItem(displayed.copy(type = "message", metadata = null), fontSizeSp = 13,
                        subscriberBadges = subscriberBadges, userColors = userColors,
                        streamerUsername = streamerUsername, streamerSlug = streamerSlug)
                }
            }
        }
    }
}
