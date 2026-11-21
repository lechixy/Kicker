package com.lechixy.kick.screens.channel

import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.DropCampaignDto
import com.lechixy.kick.data.channel.model.resolveDropsImageUrl
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.player.remote.ViewerTrackingDiagnostics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class DropCampaignUiModel(
    val id: String,
    val title: String,
    val organizationLogoUrl: String,
    val channelName: String,
    val categoryName: String,
    val rewards: List<DropRewardUiModel>,
    val remainingDurationText: String,
    val endsAtFormatted: String
)

data class DropRewardUiModel(
    val id: String,
    val name: String,
    val fullImageUrl: String,
    val progressFraction: Float,
    val isUnlocked: Boolean
)

sealed interface DropsUiState {
    data object Loading : DropsUiState
    data class Success(val campaigns: List<DropCampaignUiModel>) : DropsUiState
    data object Empty : DropsUiState
    data object Error : DropsUiState
}

class DropsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KickRepository(KickNetwork.api)
    private val _uiState = MutableStateFlow<DropsUiState>(DropsUiState.Empty)
    val uiState = _uiState.asStateFlow()

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
                        _uiState.value = if (models.isEmpty()) DropsUiState.Empty else DropsUiState.Success(models)
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
                watchedUnits >= required)
        }
        val remaining = dto.rewards.map { (it.requiredUnits - watchedUnits).coerceAtLeast(0L) }
            .filter { it > 0 }.minOrNull() ?: 0L
        val duration = when {
            remaining == 0L -> text(R.string.drops_unlocked)
            remaining < 60L -> text(R.string.drops_unlock_minutes, remaining)
            else -> text(R.string.drops_unlock_hours, remaining / 60, remaining % 60)
        }
        val date = DateTimeFormatter.ofPattern("MMM d, yyyy", locale).withZone(ZoneId.systemDefault()).format(ends)
        return DropCampaignUiModel(dto.id, dto.name, resolveDropsImageUrl(dto.organizationLogoUrl),
            target.channelName, target.categoryName, rewards, duration, text(R.string.drops_ends_on, date))
    }

    private fun text(@StringRes resId: Int, vararg arguments: Any): String = resources.getString(resId, *arguments)
}
