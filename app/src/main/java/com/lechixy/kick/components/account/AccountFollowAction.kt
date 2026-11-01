package com.lechixy.kick.components.account

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

import com.lechixy.kick.R
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.remote.AccountApiException
import com.lechixy.kick.data.account.remote.string

data class AccountFollowAction(val following: Boolean, val label: String, val enabled: Boolean, val onClick: () -> Unit)

@Composable
fun rememberAccountFollowAction(slug: String): AccountFollowAction {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var following by remember(slug, session.generation) { mutableStateOf<Boolean?>(null) }
    var busy by remember(slug, session.generation) { mutableStateOf(false) }
    var error by remember(slug, session.generation) { mutableStateOf<String?>(null) }
    var unsupported by remember(slug, session.generation) { mutableStateOf(false) }
    var dialog by remember(slug, session.generation) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(slug, session.signedIn, session.generation, refresh) {
        if (!session.signedIn) { following = null; return@LaunchedEffect }
        busy = true
        try {
            val me = AccountGraph.auth.request("https://kick.com/api/v2/channels/$slug/me")
            following = me["is_following"]?.jsonPrimitive?.booleanOrNull ?: error("Missing follow status")
            error = null; unsupported = false
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = context.getString(R.string.channel_follow_failed)
            unsupported = failure is AccountApiException && failure.status in listOf(401, 403, 404)
        } finally { busy = false }
    }
    if (dialog && !session.signedIn) AdaptiveModalSheet(onDismiss = { dialog = false }) { _ -> AccountPanel() }
    if (dialog && session.signedIn) AlertDialog(onDismissRequest = { dialog = false }, title = { Text(stringResource(R.string.channel_follow_title)) },
        text = {
            if (!session.signedIn) AccountPanel()
            else androidx.compose.foundation.layout.Column {
                Text(error ?: stringResource(R.string.channel_follow_unavailable))
            }
        },
        confirmButton = { TextButton(onClick = {
            dialog = false
            runCatching { uriHandler.openUri("https://kick.com/$slug") }
        }) { Text(stringResource(R.string.channel_open_kick)) } },
        dismissButton = { TextButton(onClick = { dialog = false; refresh++ }) { Text(stringResource(R.string.account_close)) } })
    val generation = session.generation
    return AccountFollowAction(following == true, when {
        busy -> stringResource(R.string.channel_follow_wait)
        unsupported -> stringResource(R.string.channel_open_kick)
        following == true -> stringResource(R.string.channel_unfollow)
        else -> stringResource(R.string.follow)
    }, !busy) {
        if (!session.signedIn || following == null || unsupported) dialog = true
        else {
            busy = true
            scope.launch {
                try {
                    val next = following != true
                    AccountGraph.following.setFollowing(slug, next)
                    if (generation == AccountGraph.auth.session.value.generation) following = next
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    if (generation == AccountGraph.auth.session.value.generation) {
                        error = context.getString(R.string.channel_follow_failed); dialog = true
                        unsupported = failure is AccountApiException && failure.status in listOf(401, 403, 404)
                    }
                } finally { busy = false }
            }
        }
    }
}
