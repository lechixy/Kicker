package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

import com.lechixy.kick.R
import com.lechixy.kick.components.account.AccountAvatar
import com.lechixy.kick.data.chat.model.ActiveChatter
import com.lechixy.kick.data.chat.model.ChatCompletionToken
import com.lechixy.kick.data.chat.model.CompletionKind
import com.lechixy.kick.data.chat.model.EmoteDto
import com.lechixy.kick.data.chat.policy.ChatSlashCommand

@Composable
internal fun ChatCompletionPanel(token: ChatCompletionToken?, users: List<ActiveChatter>,
    commands: List<ChatSlashCommand>, emotes: List<EmoteDto>, onInsert: (String) -> Unit) {
    ComposerAccessory(token != null) {
        if (token != null) Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                when (token.kind) {
                    CompletionKind.MENTION -> {
                        if (users.isEmpty()) item { Text(stringResource(R.string.mention_no_results), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
                        items(users, key = { it.username }) { user ->
                            Row(Modifier.fillMaxWidth().clickable { onInsert("@${user.username}") }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AccountAvatar(user.profilePicture, user.username, Modifier.size(28.dp))
                                Text(user.username, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    CompletionKind.COMMAND -> {
                        if (commands.isEmpty()) item { Text(stringResource(R.string.command_no_results), Modifier.padding(16.dp)) }
                        items(commands, key = { it.name }) { command ->
                            Text(command.usage, Modifier.fillMaxWidth().clickable { onInsert("/${command.name}") }.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    CompletionKind.EMOTE -> {
                        if (emotes.isEmpty()) item { Text(stringResource(R.string.emote_no_results), Modifier.padding(16.dp)) }
                        items(emotes, key = { it.id }) { emote ->
                            Row(Modifier.fillMaxWidth().clickable { onInsert(emote.messageToken) }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AsyncImage(emote.imageUrl, null, Modifier.size(32.dp))
                                Text(emote.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
