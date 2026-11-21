package com.lechixy.kick.components.channel.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.model.AccountSession
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.chat.model.ChatAccess
import com.lechixy.kick.data.chat.policy.ChatSettingsUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

internal class ChatComposerAccessViewModel : ViewModel() {
    data class State(
        val channelId: Long? = null,
        val generation: Long? = null,
        val access: ChatAccess? = null,
        val failed: Boolean = false
    ) {
        fun matches(channel: ChannelDetail, session: AccountSession): Boolean =
            session.signedIn && channel.id == channelId && session.generation == generation
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var connected = false
    private var settingsPatch = JsonObject(emptyMap())

    fun observe(channel: ChannelDetail, session: AccountSession, connected: Boolean, patch: JsonObject) {
        if (!_state.value.matches(channel, session)) {
            job?.cancel()
            job = null
            this.connected = false
            settingsPatch = JsonObject(emptyMap())
            _state.value = if (session.signedIn) State(channel.id, session.generation) else State()
        }
        if (!session.signedIn) return

        if (settingsPatch != patch) {
            settingsPatch = patch
            val snapshot = _state.value
            val access = snapshot.access
            if (access != null) {
                _state.value = snapshot.copy(access = access.copy(settings = ChatSettingsUpdates.apply(access.settings, patch)))
            }
        }

        val reconnected = connected && !this.connected
        this.connected = connected
        if (reconnected) refresh(channel, session)
    }

    fun refresh(channel: ChannelDetail, session: AccountSession) {
        val snapshot = _state.value
        if (!connected || !snapshot.matches(channel, session) || job?.isActive == true) return
        if (snapshot.failed) _state.value = snapshot.copy(failed = false)

        job = viewModelScope.launch {
            try {
                val auth = AccountGraph.auth
                if (auth.session.value.profile == null) auth.loadProfile()
                val patchAtRequest = settingsPatch
                val fetched = AccountGraph.chat.access(channel)
                if (!_state.value.matches(channel, session) || auth.session.value.generation != session.generation ||
                    !auth.session.value.signedIn
                ) return@launch

                // The HTTP snapshot supersedes earlier events. Keep changes received during the request.
                val access = if (settingsPatch == patchAtRequest) fetched else fetched.copy(
                    settings = ChatSettingsUpdates.apply(fetched.settings, settingsPatch)
                )
                _state.value = _state.value.copy(access = access, failed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val current = _state.value
                if (current.matches(channel, session) && current.matches(channel, AccountGraph.auth.session.value) &&
                    current.access == null
                ) {
                    _state.value = current.copy(failed = true)
                }
            }
        }
    }
}
