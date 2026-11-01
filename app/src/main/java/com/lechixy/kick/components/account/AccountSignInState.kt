package com.lechixy.kick.components.account

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.lechixy.kick.R
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.model.SessionStatus

@Composable
fun AccountSignInState(compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    if (compact) {
        FilledTonalButton(onClick = { open = true }) { Text(stringResource(R.string.account_login)) }
        if (open) AdaptiveModalSheet(onDismiss = { open = false }) { _ -> AccountPanel() }
        return
    }
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ContentState(message = session.error ?: stringResource(
        if (session.status == SessionStatus.Connecting) R.string.account_waiting_login else R.string.account_login_optional),
        action = {
            if (session.status == SessionStatus.Connecting) {
                TextButton(onClick = AccountGraph.auth::cancelLogin) { Text(stringResource(R.string.account_cancel_login)) }
            } else Button(onClick = { AccountGraph.auth.beginLogin(context) }) { Text(stringResource(R.string.account_login)) }
        })
}
