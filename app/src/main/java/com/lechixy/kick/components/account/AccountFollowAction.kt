package com.lechixy.kick.components.account

import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.PredictiveAlertDialog
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.remote.AccountApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class AccountFollowAction(val following: Boolean, val label: String, val enabled: Boolean, val onClick: () -> Unit)

@Composable
fun rememberAccountFollowAction(slug: String): AccountFollowAction {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    val repository = AccountGraph.following
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val generation = session.generation
    var following by remember(slug, generation) { mutableStateOf<Boolean?>(null) }
    var busy by remember(slug, generation) { mutableStateOf(session.signedIn) }
    var blocked by remember(slug, generation) { mutableStateOf(false) }
    var dialog by remember(slug, generation) { mutableStateOf(false) }
    var refresh by remember(slug, generation) { mutableIntStateOf(0) }
    var openedKick by rememberSaveable(slug, generation) { mutableStateOf(false) }

    fun recordFailure(failure: Exception) {
        if (failure is CancellationException) throw failure
        following = null
        blocked = failure is AccountApiException && failure.status in setOf(401, 403, 404, 419)
    }

    fun retryStatus() {
        dialog = false
        busy = true
        refresh++
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (openedKick) {
            openedKick = false
            refresh++
            repository.refreshAfterFollowChange()
        }
    }
    LaunchedEffect(slug, session.signedIn, generation, refresh) {
        if (!session.signedIn) return@LaunchedEffect
        busy = true
        try {
            following = repository.isFollowing(slug)
            blocked = false
        } catch (failure: Exception) {
            recordFailure(failure)
        } finally { busy = false }
    }

    if (dialog) {
        if (!session.signedIn) {
            AdaptiveModalSheet(onDismiss = { dialog = false }) { _ -> AccountPanel() }
        } else {
            PredictiveAlertDialog(
                onDismissRequest = { dialog = false },
                title = { Text(stringResource(R.string.channel_follow_title)) },
                text = { Text(stringResource(
                    if (blocked) R.string.channel_follow_unavailable else R.string.channel_follow_failed
                )) },
                confirmButton = {
                    if (blocked) {
                        TextButton(onClick = {
                            dialog = false
                            openedKick = runCatching { uriHandler.openUri("https://kick.com/${Uri.encode(slug)}") }.isSuccess
                        }) { Text(stringResource(R.string.channel_open_kick)) }
                    } else {
                        TextButton(onClick = ::retryStatus) {
                            Text(stringResource(R.string.chat_retry))
                        }
                    }
                },
                dismissButton = {
                    if (blocked) {
                        TextButton(onClick = ::retryStatus) { Text(stringResource(R.string.chat_retry)) }
                    } else {
                        TextButton(onClick = { dialog = false }) { Text(stringResource(R.string.account_close)) }
                    }
                }
            )
        }
    }
    return AccountFollowAction(following == true, when {
        busy -> stringResource(R.string.channel_follow_wait)
        blocked -> stringResource(R.string.channel_open_kick)
        session.signedIn && following == null -> stringResource(R.string.chat_retry)
        following == true -> stringResource(R.string.channel_unfollow)
        else -> stringResource(R.string.follow)
    }, !busy) {
        when {
            !session.signedIn || blocked -> dialog = true
            following == null && !busy -> retryStatus()
            !busy -> {
                val next = following != true
                busy = true
                scope.launch {
                    try {
                        repository.setFollowing(slug, next)
                        if (generation == AccountGraph.auth.session.value.generation) following = next
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        if (generation == AccountGraph.auth.session.value.generation) {
                            recordFailure(failure)
                            dialog = true
                        }
                    } finally { busy = false }
                }
            }
        }
    }
}
