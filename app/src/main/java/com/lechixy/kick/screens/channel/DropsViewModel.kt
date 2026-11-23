package com.lechixy.kick.screens.channel

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.R
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.DropCampaignDto
import com.lechixy.kick.data.channel.model.DropCampaignProgressDto
import com.lechixy.kick.data.channel.model.resolveDropsImageUrl
import com.lechixy.kick.data.channel.repository.getDropsProgressSummary
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.player.remote.ViewerTrackingDiagnostics
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DropCampaignUiModel(
    val id: String,
    val title: String,
    val organizationLogoUrl: String,
    val channelName: String,
    val categoryName: String,
    val rewards: List<DropRewardUiModel>,
    val remainingDurationText: String,
    val endsAtFormatted: String,
    val banner: DropBannerUiModel? = null
)

data class DropBannerUiModel(
    val id: String,
    val title: String,
    val rewardCountSubtitle: String,
    val organizationLogoUrl: String,
    val eligibilityDescription: String,
    val rewards: List<DropRewardThumbnailUiModel>,
    val endsAtFormatted: String,
    val overallProgressFraction: Float
)

data class DropRewardThumbnailUiModel(
    val id: String,
    val name: String,
    val fullImageUrl: String,
    val progressFraction: Float
)

data class DropRewardUiModel(
    val id: String,
    val name: String,
    val fullImageUrl: String,
    val progressFraction: Float,
    val isUnlocked: Boolean,
    val requiredUnits: Int = 0,
    val remainingDurationText: String? = null
)

internal data class DropRewardProgress(val fraction: Float, val remainingMinutes: Long)

internal fun DropRewardUiModel.progressFrom(summary: DropCampaignProgressDto?): DropRewardProgress {
    val watched = (summary?.progressUnits ?: 0L).coerceAtLeast(0)
    val reward = summary?.rewards?.firstOrNull { it.id == id }
    val fraction = when {
        requiredUnits <= 0 || reward?.claimed == true -> 1f
        reward != null -> reward.progress.coerceIn(0f, 1f)
        else -> (watched.toFloat() / requiredUnits).coerceIn(0f, 1f)
    }
    val remaining = if (fraction >= 1f) 0L else (requiredUnits - watched).coerceAtLeast(0L)
    return DropRewardProgress(fraction, remaining)
}

sealed interface DropsUiState {
    data object Loading : DropsUiState
    data class Success(
        val campaigns: List<DropCampaignUiModel>,
        val channelId: Long? = null,
        val livestreamId: Long? = null,
        val accountGeneration: Long? = null
    ) : DropsUiState
    data object Empty : DropsUiState
    data object Error : DropsUiState
}

private val Context.dropsPreferences by preferencesDataStore(name = "drops_banner")
private val dismissedCampaigns = stringSetPreferencesKey("dismissed_campaigns")

class DropsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KickRepository(KickNetwork.api)
    private val _uiState = MutableStateFlow<DropsUiState>(DropsUiState.Empty)
    val uiState = _uiState.asStateFlow()
    private val preferences = application.dropsPreferences
    private val _dismissals = MutableStateFlow<Set<Long>?>(null)
    val dismissals = _dismissals.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.data
                .map { preferences ->
                    preferences[dismissedCampaigns].orEmpty().mapNotNull { it.substringBefore(':').toLongOrNull() }.toSet()
                }
                .catch { if (it is IOException) emit(emptySet()) else throw it }
                .collect { saved -> _dismissals.update { it.orEmpty() + saved } }
        }
    }

    fun dismissBanner(channelId: Long) {
        _dismissals.update { it.orEmpty() + channelId }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Finish saving the user's dismissal even if they leave the channel immediately.
            withContext(NonCancellable) {
                try {
                    preferences.edit { it[dismissedCampaigns] = it[dismissedCampaigns].orEmpty() + channelId.toString() }
                } catch (_: IOException) {
                    // The banner stays dismissed for this ViewModel.
                }
            }
        }
    }

    internal suspend fun loadDialogProgress(state: DropsUiState.Success): DropsUiState.Success {
        val summaries = AccountGraph.auth.getDropsProgressSummary(state.campaigns.map { it.id })
        return state.copy(campaigns = state.campaigns.map { campaign ->
            val summary = summaries[campaign.id]
            val progress = campaign.rewards.map { it.progressFrom(summary) }
            val remaining = progress.map { it.remainingMinutes }.filter { it > 0 }.minOrNull() ?: 0L
            campaign.copy(
                rewards = campaign.rewards.zip(progress).map { (reward, value) ->
                    reward.copy(
                        progressFraction = value.fraction,
                        isUnlocked = value.remainingMinutes == 0L,
                        remainingDurationText = formatRemainingDuration(value.remainingMinutes)
                    )
                },
                remainingDurationText = formatRemainingDuration(remaining)
            )
        })
    }

    private data class Target(
        val channelId: Long,
        val livestreamId: Long,
        val channelName: String,
        val categoryId: Long,
        val categoryName: String
    )

    private data class Campaign(val dto: DropCampaignDto, val starts: Instant, val ends: Instant)

    private var target: Target? = null
    private var boundChannelId: Long? = null
    private var accountGeneration: Long? = null
    private var signedIn = false
    private var locale = Locale.getDefault()
    private var resources: Resources = application.resources
    private var job: Job? = null
    private val watchedMillis = mutableMapOf<String, Long>()

    fun bind(channel: ChannelDetail?, generation: Long, signedIn: Boolean, locale: Locale) {
        val live = channel?.livestream
        val category = live?.categories?.firstOrNull()
        val next = if (channel != null && live != null && category != null) {
            Target(channel.id, live.id, channel.user?.username ?: channel.slug, category.id, category.name)
        } else null
        if (target == next && boundChannelId == channel?.id && accountGeneration == generation &&
            this.signedIn == signedIn && this.locale == locale
        ) {
            return
        }

        job?.cancel()
        if (boundChannelId != channel?.id || accountGeneration != generation) watchedMillis.clear()
        boundChannelId = channel?.id
        target = next
        accountGeneration = generation
        this.signedIn = signedIn
        this.locale = locale
        val application = getApplication<Application>()
        resources = application.createConfigurationContext(
            Configuration(application.resources.configuration).apply { setLocale(locale) }
        ).resources
        retry()
    }

    fun retry() {
        job?.cancel()
        val current = target
        if (current == null) {
            _uiState.value = DropsUiState.Empty
            return
        }

        val track = signedIn
        val generation = accountGeneration
        _uiState.value = DropsUiState.Loading
        job = viewModelScope.launch {
            val campaigns = try {
                repository.getLivestreamDrops(current.channelId, current.categoryId).mapNotNull { dto ->
                    if (dto.categoryId.toLong() != current.categoryId || dto.rewards.isEmpty()) return@mapNotNull null
                    val starts = runCatching { Instant.parse(dto.startsAt) }.getOrNull() ?: return@mapNotNull null
                    val ends = runCatching { Instant.parse(dto.endsAt) }.getOrNull() ?: return@mapNotNull null
                    if (ends <= starts || ends <= Instant.now()) return@mapNotNull null
                    Campaign(dto, starts, ends)
                }.distinctBy { it.dto.id }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = DropsUiState.Error
                return@launch
            }
            if (campaigns.isEmpty()) {
                _uiState.value = DropsUiState.Empty
                return@launch
            }
            // ViewerTrackingClient independently sends Kick's existing watch heartbeats.
            // The campaign endpoint supplies no per-user progress; count actual local playback.
            ViewerTrackingDiagnostics.playing.map { playing ->
                track && playing?.channelId == current.channelId && playing.livestreamId == current.livestreamId
            }.distinctUntilChanged().collectLatest { playing ->
                val startedAt = Instant.now()
                val startedMillis = SystemClock.elapsedRealtime()
                val previous = campaigns.associate { it.dto.id to (watchedMillis[it.dto.id] ?: 0L) }
                try {
                    do {
                        val now = Instant.now()
                        val elapsed = SystemClock.elapsedRealtime() - startedMillis
                        val active = campaigns.filter { now >= it.starts && now < it.ends }
                        var untilNextMinute = Long.MAX_VALUE
                        val models = active.map { campaign ->
                            val watched = previous.getValue(campaign.dto.id) +
                                if (playing) eligibleMillis(campaign, startedAt, elapsed) else 0L
                            val model = campaign.toUiModel(current, watched / 60_000L)
                            if (playing && model.rewards.any { !it.isUnlocked }) {
                                untilNextMinute = minOf(untilNextMinute, 60_000L - watched % 60_000L)
                            }
                            model
                        }
                        _uiState.value = if (models.isEmpty()) DropsUiState.Empty
                            else DropsUiState.Success(models, current.channelId, current.livestreamId, generation)
                        val nextBoundary = campaigns.flatMap { listOf(it.starts, it.ends) }
                            .filter { it > now }.minOrNull() ?: break
                        val untilBoundary = (nextBoundary.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(1L)
                        delay(minOf(untilNextMinute, untilBoundary))
                    } while (true)
                } finally {
                    if (playing && boundChannelId == current.channelId && accountGeneration == generation && signedIn == track) {
                        val elapsed = SystemClock.elapsedRealtime() - startedMillis
                        campaigns.forEach {
                            val watched = previous.getValue(it.dto.id) + eligibleMillis(it, startedAt, elapsed)
                            if (watched > 0L) {
                                watchedMillis.remove(it.dto.id)
                                watchedMillis[it.dto.id] = watched
                            }
                        }
                        while (watchedMillis.size > 128) watchedMillis.remove(watchedMillis.keys.first())
                    }
                }
            }
        }
    }

    private fun eligibleMillis(campaign: Campaign, startedAt: Instant, elapsed: Long): Long {
        val startOffset = (campaign.starts.toEpochMilli() - startedAt.toEpochMilli()).coerceAtLeast(0)
        val endOffset = campaign.ends.toEpochMilli() - startedAt.toEpochMilli()
        return (minOf(elapsed, endOffset) - startOffset).coerceAtLeast(0)
    }

    private fun Campaign.toUiModel(target: Target, watchedUnits: Long): DropCampaignUiModel {
        val rewards = dto.rewards.distinctBy { it.id }.map { reward ->
            val required = reward.requiredUnits.coerceAtLeast(0)
            DropRewardUiModel(reward.id, reward.name, resolveDropsImageUrl(reward.imageUrl),
                if (required == 0) 1f else (watchedUnits.toFloat() / required).coerceIn(0f, 1f),
                watchedUnits >= required,
                requiredUnits = required)
        }
        val remaining = dto.rewards.map { (it.requiredUnits - watchedUnits).coerceAtLeast(0L) }
            .filter { it > 0 }.minOrNull() ?: 0L
        val date = DateTimeFormatter.ofPattern("MMM d, yyyy", locale).withZone(ZoneId.systemDefault()).format(ends)
        val endsAt = text(R.string.drops_ends_on, date)
        val logo = resolveDropsImageUrl(dto.organizationLogoUrl)
        val maxRequiredUnits = dto.rewards.maxOf { it.requiredUnits }
        val banner = DropBannerUiModel(
            id = dto.id,
            title = dto.name,
            rewardCountSubtitle = resources.getQuantityString(R.plurals.drops_rewards_count, rewards.size, rewards.size),
            organizationLogoUrl = logo,
            eligibilityDescription = text(R.string.drops_eligibility, target.channelName, target.categoryName),
            rewards = rewards.map {
                DropRewardThumbnailUiModel(it.id, it.name, it.fullImageUrl, it.progressFraction)
            },
            endsAtFormatted = endsAt,
            overallProgressFraction = if (maxRequiredUnits <= 0) 1f
                else (watchedUnits.toFloat() / maxRequiredUnits).coerceIn(0f, 1f)
        )
        return DropCampaignUiModel(dto.id, dto.name, logo,
            target.channelName, target.categoryName, rewards, formatRemainingDuration(remaining), endsAt, banner)
    }

    private fun formatRemainingDuration(remaining: Long): String = when {
        remaining == 0L -> text(R.string.drops_unlocked)
        remaining < 60L -> text(R.string.drops_unlock_minutes, remaining)
        else -> text(R.string.drops_unlock_hours, remaining / 60, remaining % 60)
    }

    private fun text(@StringRes resId: Int, vararg arguments: Any): String = resources.getString(resId, *arguments)
}
