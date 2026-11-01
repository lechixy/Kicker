package com.lechixy.kick.data.account.repository

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

import com.lechixy.kick.data.account.model.Challenge
import com.lechixy.kick.data.account.model.ChallengeLoadFailure
import com.lechixy.kick.data.account.model.ChallengesState
import com.lechixy.kick.data.account.model.WatchTimeGoalReached
import com.lechixy.kick.data.account.model.decodeChallengeClaim
import com.lechixy.kick.data.account.model.decodeChallenges
import com.lechixy.kick.data.account.remote.AccountApiException
import com.lechixy.kick.data.account.remote.accountDiagnostic

class ChallengesRepository(private val auth: AuthRepository, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow(ChallengesState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var claimJob: Job? = null
    init {
        scope.launch {
            auth.session.map { Triple(it.signedIn, it.generation, it.profile?.userId) }.distinctUntilChanged().collect { (signedIn, _, _) ->
                job?.cancel()
                claimJob?.cancel()
                mutableState.value = ChallengesState()
                if (signedIn) refresh()
            }
        }
    }
    fun refresh() {
        if (!auth.session.value.signedIn) return
        val generation = auth.session.value.generation
        while (true) {
            val current = state.value
            if (current.loading) return
            if (mutableState.compareAndSet(current, current.copy(loading = true, failed = false, failure = null, httpStatus = null))) break
        }
        job = scope.launch {
            try {
                val response = auth.requestElement("https://web.kick.com/api/v1/gamification/challenges")
                val shape = when (response) {
                    is JsonArray -> "array(size=${response.size})"
                    is JsonObject -> "object(keys=${response.keys.joinToString()})"
                    else -> "${response.javaClass.simpleName}"
                }
                accountDiagnostic("KickChallenges", "Challenges response: $shape")
                val challenges = decodeChallenges(response, json)
                accountDiagnostic("KickChallenges", "Loaded ${challenges.size} challenges")
                if (generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.update { previous ->
                        val old = previous.challenges.associateBy { it.id }
                        previous.copy(challenges = challenges.map { challenge ->
                            val prior = old[challenge.id]
                            val sameWindow = prior != null && prior.window == challenge.window && prior.activePeriod == challenge.activePeriod
                            if (sameWindow && prior.claimed) return@map challenge.copy(status = "claimed")
                            // Watch time is monotonic within a window; an in-flight fetch may predate the goal event.
                            if (prior != null && challenge.window != null && prior.window == challenge.window &&
                                prior.condition.type == challenge.condition.type && challenge.condition.type == "watch_time_minutes" &&
                                prior.condition.threshold == challenge.condition.threshold) {
                                challenge.copy(condition = challenge.condition.copy(progress = maxOf(prior.condition.progress, challenge.condition.progress)))
                            } else challenge
                        }, loaded = true, loading = false, failed = false, failure = null, httpStatus = null)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val failure = when (error) {
                    is AccountApiException -> ChallengeLoadFailure.Http
                    is java.io.IOException -> ChallengeLoadFailure.Network
                    is SerializationException, is IllegalArgumentException -> ChallengeLoadFailure.Format
                    else -> ChallengeLoadFailure.Other
                }
                val status = (error as? AccountApiException)?.status
                accountDiagnostic("KickChallenges", "Load failed: kind=$failure status=$status type=${error.javaClass.simpleName}")
                if (generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.update { it.copy(loading = false, failed = true, failure = failure, httpStatus = status) }
                }
            }
        }
    }
    /** One POST per user action; polling and reveal acknowledgement never repeat the mutation. */
    fun claim(challenge: Challenge) {
        val session = auth.session.value
        if (!session.signedIn) return
        while (true) {
            val current = state.value
            val eligible = current.challenges.firstOrNull { it.id == challenge.id } ?: return
            if (current.claimingId != null || current.reward != null || !eligible.canClaim(Instant.now())) return
            if (mutableState.compareAndSet(current, current.copy(claimingId = eligible.id, claimFailed = false, claimHttpStatus = null))) break
        }
        claimJob = scope.launch {
            var accepted = false
            try {
                val id = java.net.URLEncoder.encode(challenge.id, Charsets.UTF_8.name())
                val response = auth.request("https://web.kick.com/api/v1/gamification/challenges/$id/claim", "POST")
                accepted = true
                val reward = decodeChallengeClaim(response, challenge.id, json)
                if (session.generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.update { it.copy(claimingId = null, reward = reward,
                        challenges = it.challenges.map { entry -> if (entry.id == challenge.id) entry.copy(status = "claimed") else entry }) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (session.generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.update { it.copy(claimingId = null, claimFailed = true,
                        claimHttpStatus = (error as? AccountApiException)?.status,
                        challenges = if (accepted) it.challenges.map { entry ->
                            if (entry.id == challenge.id) entry.copy(status = "claimed") else entry
                        } else it.challenges) }
                    refresh()
                }
            }
        }
    }

    fun acknowledgeReward() {
        mutableState.update { it.copy(reward = null) }
        refresh()
    }
    /** Receives only the current user's private publication; never derives watch time locally. */
    fun onGoalReached(channel: String?, payload: JsonElement) {
        val session = auth.session.value
        val user = session.profile?.userId ?: return
        if (!session.signedIn || channel != "private-$user") return
        val event = runCatching {
            val element = if (payload is JsonPrimitive && payload.isString) json.parseToJsonElement(payload.content) else payload
            json.decodeFromJsonElement<WatchTimeGoalReached>(element)
        }.getOrNull() ?: return
        if (event.userId != user || event.thresholdMinutes <= 0) return
        val now = Instant.now()
        mutableState.update { state -> state.copy(challenges = state.challenges.map { challenge ->
            if (challenge.id == event.challengeId && challenge.recurrence == event.recurrence &&
                challenge.condition.type == "watch_time_minutes" && challenge.condition.threshold == event.thresholdMinutes &&
                !challenge.claimed && challenge.window?.contains(now) != false && challenge.activePeriod?.contains(now) != false) {
                challenge.copy(condition = challenge.condition.copy(progress = maxOf(challenge.condition.progress, challenge.condition.threshold)))
            } else challenge
        }) }
        refresh()
    }
}
