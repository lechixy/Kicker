package com.lechixy.kick.components.account

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

import com.lechixy.kick.R
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.model.SessionStatus
import com.lechixy.kick.data.account.remote.string
import com.lechixy.kick.data.account.remote.userMessage

@Composable
fun AccountPanel(showTitle: Boolean = true) {
    val auth = AccountGraph.auth
    val session by auth.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profileError by remember(session.generation) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showTitle) Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleLarge)
        session.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (session.status) {
            SessionStatus.Authenticated -> {
                session.profile?.let { profile ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccountAvatar(profile.avatar, profile.name, Modifier.size(48.dp))
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    }
                } ?: Text(stringResource(R.string.account_profile_missing))
                profileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (session.profile == null) TextButton(onClick = {
                    scope.launch {
                        profileError = null
                        try { auth.loadProfile() }
                        catch (error: Exception) { if (error is CancellationException) throw error; profileError = error.userMessage() }
                    }
                }) { Text(stringResource(R.string.account_reload_profile)) }
                Button(onClick = { scope.launch { auth.signOut() } }) { Text(stringResource(R.string.account_logout)) }
            }
            else -> AccountSignInState()
        }
    }
}
