package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lechixy.kick.R
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.components.PredictiveDialog
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.channel.model.SubscriberBadge
import com.lechixy.kick.data.chat.model.ActiveChatter
import com.lechixy.kick.data.chat.model.ChatSender
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SilencedUsersDialog(
    channelSlug: String?,
    subscriberBadges: List<SubscriberBadge>,
    onOpenChannel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val repository = AccountGraph.silencedUsers
    val community: ChatCommunityViewModel = viewModel(key = "silenced-profile-$channelSlug")
    val selectedProfile by community.profile.collectAsStateWithLifecycle()
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    val users by repository.silencedUsers.collectAsStateWithLifecycle()
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember(session.generation) { mutableStateOf(session.signedIn) }
    var loadFailed by remember(session.generation) { mutableStateOf(false) }
    var updating by remember(session.generation) { mutableStateOf(false) }
    var updateFailed by remember(session.generation) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(community) {
        onDispose { community.closeProfile() }
    }
    LaunchedEffect(session.generation, session.signedIn, retry) {
        if (!session.signedIn) return@LaunchedEffect
        loading = true
        loadFailed = false
        try {
            repository.refresh(session.generation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
        } finally {
            loading = false
        }
    }

    PredictiveDialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.chat_silenced_users), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    val countLabel = pluralStringResource(R.plurals.chat_silenced_total, users.size, users.size)
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            users.size.toString(),
                            Modifier.clearAndSetSemantics { contentDescription = countLabel }.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (loadFailed) {
                    Text(stringResource(R.string.chat_silenced_load_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.chat_retry)) }
                }
                if (updateFailed) Text(stringResource(R.string.chat_silenced_update_error), color = MaterialTheme.colorScheme.error)
                if (!loading && !loadFailed && users.isEmpty()) {
                    ContentState(stringResource(R.string.chat_silenced_empty),
                        icon = R.drawable.volume_off_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
                }
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 340.dp)) {
                    items(users, key = { it.username.lowercase(Locale.ROOT) }) { user ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val userId = user.id.takeIf { it > 0 }
                            Box(
                                Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button) {
                                    community.openProfile(
                                        channelSlug ?: user.username.lowercase(Locale.ROOT),
                                        ActiveChatter(username = user.username),
                                        ChatSender(id = userId, username = user.username),
                                        subscriberBadges,
                                    )
                                },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(user.username, Modifier.padding(end = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val label = stringResource(R.string.chat_silenced_unmute, user.username)
                            FilledTonalButton(
                                enabled = !loading && !updating,
                                modifier = Modifier.semantics { contentDescription = label },
                                onClick = {
                                    updating = true
                                    updateFailed = false
                                    scope.launch {
                                        try {
                                            repository.setMuted(userId, user.username, false)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            updateFailed = true
                                        } finally {
                                            updating = false
                                        }
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.chat_silenced_unmute_action))
                            }
                        }
                    }
                }
            }
        }
    }
    selectedProfile?.let { profile ->
        KickUserProfileDialog(profile, community::closeProfile, community::toggleFollow, community::toggleMute,
            onChannelClick = {
                community.closeProfile()
                onDismiss()
                onOpenChannel(profile.slug)
            })
    }
}
