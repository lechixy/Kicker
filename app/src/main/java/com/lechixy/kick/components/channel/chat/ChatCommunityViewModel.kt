package com.lechixy.kick.components.channel.chat

import androidx.compose.ui.graphics.Color
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

import com.lechixy.kick.R
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.chat.model.ActiveChatter
import com.lechixy.kick.data.chat.model.ActiveChatters
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.ChatSender
import com.lechixy.kick.data.chat.model.ChatSenderBadge
import com.lechixy.kick.data.chat.model.parseActiveChatters
import com.lechixy.kick.data.common.remote.KickNetwork

enum class KickLevelTier(val minLevel: Int, val accentColor: Color) {
    TIER_20(20, Color(0xFFFF65B8)), TIER_30(30, Color(0xFF01D7E5)),
    TIER_40(40, Color(0xFF3FECB1)), TIER_50(50, Color(0xFF8599FF)),
    TIER_60(60, Color(0xFF9CD322)), TIER_70(70, Color(0xFFE88AFF)),
    TIER_80(80, Color(0xFFFFC760)), TIER_90(90, Color(0xFFFF6C47));
    val backgroundDottedUrl get() = "https://static.kick.com/tier/background/level-$minLevel.webp"
    val lightningAnimUrl get() = if (minLevel >= 40) "https://static.kick.com/tier/animation/level-$minLevel.webm" else null
    companion object {
        fun fromLevel(level: Int) = entries.lastOrNull { level >= it.minLevel } ?: TIER_20
    }
}

data class UserProfileCardUiState(
    val userId: Long? = null, val username: String = "", val slug: String = "",
    val profilePicUrl: String? = null, val bannerPicUrl: String? = null,
    val level: Int? = null, val streakDays: Int? = null,
    val createdAt: String? = null, val followingSince: String? = null,
    val subscribedMonths: Int? = null, val isFollowing: Boolean? = null,
    val badges: List<ResolvedChatBadge> = emptyList(), val isLoading: Boolean = true,
    val actionLoading: Boolean = false, @get:StringRes val error: Int? = null,
    val isMuted: Boolean = false
)

internal fun profileDate(raw: String?, locale: Locale = Locale.ENGLISH): String? = raw?.let {
    runCatching { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        .withZone(ZoneOffset.UTC).format(Instant.parse(it)) }.getOrNull()
}

private fun JsonObject.obj(key: String) = this[key] as? JsonObject
private fun JsonObject.value(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.data() = obj("data") ?: this

class ChatCommunityViewModel : ViewModel() {
    private val api = KickNetwork.api
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val _chatters = MutableStateFlow<ActiveChatters?>(null)
    val chatters = _chatters.asStateFlow()
    private val _listError = MutableStateFlow<Int?>(null)
    val listError = _listError.asStateFlow()
    private val _profile = MutableStateFlow<UserProfileCardUiState?>(null)
    val profile = _profile.asStateFlow()
    private val _mutedUsers = MutableStateFlow<Set<String>>(emptySet())
    val mutedUsers = _mutedUsers.asStateFlow()
    private var listJob: Job? = null
    private var profileJob: Job? = null
    private var channelId: Long? = null

    init {
        viewModelScope.launch {
            var generation = AccountGraph.auth.session.value.generation
            AccountGraph.auth.session.collect { session ->
                if (generation != session.generation) {
                    closeProfile()
                    generation = session.generation
                }
            }
        }
    }

    fun loadChatters(id: Long) {
        listJob?.cancel()
        if (channelId != id) _chatters.value = null
        channelId = id
        _listError.value = null
        listJob = viewModelScope.launch {
            try { _chatters.value = parseActiveChatters(api.getActiveChatters(id)) }
            catch (e: Exception) { if (e is CancellationException) throw e; _listError.value = R.string.active_chatters_load_error }
        }
    }

    fun closeProfile() { profileJob?.cancel(); _profile.value = null }

    fun openProfile(channelSlug: String, chatter: ActiveChatter, sender: ChatSender? = null) {
        profileJob?.cancel()
        val slug = chatter.slug.ifBlank { chatter.username.lowercase(Locale.ROOT) }
        val initial = UserProfileCardUiState(userId = sender?.id, username = chatter.username,
            slug = slug, profilePicUrl = chatter.profilePicture, isMuted = chatter.username.lowercase(Locale.ROOT) in _mutedUsers.value,
            badges = ChatTextParser.resolveBadges(sender?.identity, emptyList()))
        _profile.value = initial
        profileJob = viewModelScope.launch {
            supervisorScope {
                val failures = mutableListOf<String>()
                suspend fun optional(label: String, block: suspend () -> JsonObject): JsonObject? = try { block().data() }
                    catch (e: Exception) { if (e is CancellationException) throw e; failures.add(label); null }
                val userRequest = async { optional("profile") {
                    if (AccountGraph.auth.session.value.signedIn) {
                        val encodedChannel = android.net.Uri.encode(channelSlug)
                        val encodedUser = android.net.Uri.encode(chatter.username)
                        AccountGraph.auth.request("https://kick.com/api/v2/channels/$encodedChannel/users/$encodedUser")
                    } else api.getChatter(channelSlug, chatter.username)
                } }
                val channelRequest = async { optional("channel") { api.getChatterChannel(slug) } }
                val meRequest = async {
                    if (AccountGraph.auth.session.value.signedIn)
                        optional("follow status") { AccountGraph.auth.request("https://kick.com/api/v2/channels/${android.net.Uri.encode(slug)}/me") }
                    else null
                }
                val user = userRequest.await()
                val channel = channelRequest.await()
                val channelUser = channel?.obj("user")
                val id = user?.value("id")?.toLongOrNull() ?: channelUser?.value("id")?.toLongOrNull() ?: sender?.id
                val streak = id?.let { userId -> optional("streak") {
                    if (AccountGraph.auth.session.value.signedIn)
                        AccountGraph.auth.request("https://web.kick.com/api/v1/gamification/users/$userId/streak")
                    else api.getChatterStreak(userId)
                } }
                val me = meRequest.await()
                val identity = user?.obj("identity")?.let {
                    runCatching { json.decodeFromJsonElement<ChatIdentity>(it) }.getOrNull()
                } ?: user?.let {
                    runCatching { json.decodeFromJsonElement<ChatIdentity>(it) }.getOrNull()
                }?.takeIf { it.badges.isNotEmpty() || it.badgesV2.isNotEmpty() } ?: sender?.identity
                val roles = listOf("moderator", "founder", "verified").filter { role ->
                    user?.value("is_$role") == "true" && identity?.badges?.none { it.type == role && it.active != false } != false
                }.map { ChatSenderBadge(type = it, active = true) }
                val badges = ChatTextParser.resolveBadges((identity ?: ChatIdentity()).let {
                    it.copy(badges = it.badges + roles)
                }, emptyList()).toMutableList()
                if (channel?.value("verified") == "true" && badges.none { it.drawableRes == com.lechixy.kick.R.drawable.verified_badge }) {
                    badges.add(ResolvedChatBadge("verified", "Verified", drawableRes = com.lechixy.kick.R.drawable.verified_badge, sortOrder = 99))
                }
                val level = user?.value("level")?.toIntOrNull() ?: user?.obj("gamification")?.value("level")?.toIntOrNull()
                    ?: identity?.badgesV2?.firstNotNullOfOrNull { it.levelNumber() }
                _profile.value = initial.copy(userId = id,
                    profilePicUrl = user?.value("profile_pic") ?: user?.value("profile_picture") ?: channelUser?.value("profile_pic") ?: initial.profilePicUrl,
                    bannerPicUrl = channel?.obj("banner_image")?.let { it.value("src") ?: it.value("url") },
                    level = level, streakDays = streak?.value("length_days")?.toIntOrNull(),
                    createdAt = user?.value("created_at") ?: channelUser?.value("created_at"),
                    followingSince = user?.value("following_since"),
                    subscribedMonths = user?.value("subscribed_for")?.toIntOrNull() ?: user?.obj("subscription")?.value("months")?.toIntOrNull(),
                    isFollowing = me?.value("is_following")?.toBooleanStrictOrNull(),
                    badges = badges.sortedBy { it.sortOrder }, isLoading = false,
                    isMuted = chatter.username.lowercase(Locale.ROOT) in _mutedUsers.value,
                    error = if (failures.isEmpty()) null else R.string.chatter_profile_partial_error)
            }
        }
    }

    fun toggleFollow() {
        val current = _profile.value ?: return
        val following = current.isFollowing ?: return
        if (current.actionLoading) return
        _profile.value = current.copy(actionLoading = true)
        viewModelScope.launch {
            try {
                AccountGraph.following.setFollowing(current.slug, !following)
                if (_profile.value?.slug == current.slug) _profile.update { it?.copy(isFollowing = !following, actionLoading = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (_profile.value?.slug == current.slug) _profile.update { it?.copy(actionLoading = false, error = R.string.channel_follow_failed) }
            }
        }
    }

    fun toggleMute() {
        val current = _profile.value ?: return
        val name = current.username.lowercase(Locale.ROOT)
        _mutedUsers.update { if (current.isMuted) it - name else it + name }
        _profile.update { it?.copy(isMuted = !current.isMuted) }
    }
}
