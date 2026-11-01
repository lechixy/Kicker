package com.lechixy.kick.data.account.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

import com.lechixy.kick.data.account.model.AccountChannelInfo
import com.lechixy.kick.data.account.model.AccountDashboardSection
import com.lechixy.kick.data.account.model.AccountDashboardState
import com.lechixy.kick.data.account.model.AccountLevelResponse
import com.lechixy.kick.data.account.model.AccountStreakResponse

class AccountDashboardRepository(private val auth: AuthRepository, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow(AccountDashboardState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    init {
        scope.launch {
            auth.session.map { it.signedIn to it.generation }.distinctUntilChanged().collect {
                job?.cancel()
                mutableState.value = AccountDashboardState()
            }
        }
    }
    fun refresh() {
        val session = auth.session.value
        val profile = session.profile ?: return
        if (!session.signedIn) return
        job?.cancel()
        job = scope.launch {
            mutableState.update { it.copy(loading = true, failures = emptySet()) }
            supervisorScope {
                fun load(section: AccountDashboardSection, block: suspend () -> Unit) = launch {
                    try { block() }
                    catch (error: Exception) {
                        if (error is CancellationException) throw error
                        if (auth.session.value.generation == session.generation) {
                            mutableState.update { it.copy(failures = it.failures + section) }
                        }
                    }
                }
                load(AccountDashboardSection.Channel) {
                    val url = "https://kick.com/api/v2/channels".toHttpUrl().newBuilder()
                        .addPathSegment(profile.name).addPathSegment("info").build()
                    val channel = json.decodeFromJsonElement<AccountChannelInfo>(auth.request(url.toString()))
                    mutableState.update { it.copy(channel = channel) }
                }
                load(AccountDashboardSection.Level) {
                    val level = json.decodeFromJsonElement<AccountLevelResponse>(
                        auth.request("https://web.kick.com/api/v1/gamification/user/level")).data
                    mutableState.update { it.copy(level = level) }
                }
                load(AccountDashboardSection.Streak) {
                    val streak = json.decodeFromJsonElement<AccountStreakResponse>(
                        auth.request("https://web.kick.com/api/v1/gamification/users/${profile.userId}/streak")).data
                    mutableState.update { it.copy(streak = streak) }
                }
            }
            mutableState.update { it.copy(loading = false) }
        }
    }
}
