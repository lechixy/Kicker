package com.lechixy.kick.data.account.model

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

import com.lechixy.kick.data.account.remote.accountDiagnostic

@Serializable
data class Challenge(
    val id: String,
    val name: String,
    val description: String = "",
    val condition: Condition,
    @SerialName("drop_table") val dropTable: List<DropTableEntry> = emptyList(),
    val featured: Boolean = false,
    val recurrence: String? = null,
    val status: String,
    val window: Window? = null,
    @SerialName("active_period") val activePeriod: Window? = null,
    val animations: ChallengeAnimations? = null
) {
    val remaining: Long get() = (condition.threshold.coerceAtLeast(0) - condition.progress.coerceAtLeast(0)).coerceAtLeast(0)
    val claimed: Boolean get() = status in setOf("claimed", "redeemed")
    fun canClaim(now: Instant): Boolean = !claimed && condition.threshold > 0 && remaining == 0L &&
        window?.contains(now) != false && activePeriod?.contains(now) != false
    fun expired(now: Instant): Boolean = listOfNotNull(window?.end, activePeriod?.end).any { !now.isBefore(it) }
}
@Serializable
data class Condition(val progress: Long, val threshold: Long, val type: String)
@Serializable
data class DropTableEntry(val rarity: String, val weighting: Long)
@Serializable
data class Window(@SerialName("starts_at") val startsAt: String, @SerialName("ends_at") val endsAt: String) {
    val start: Instant? get() = runCatching { Instant.parse(startsAt) }.getOrNull()
    val end: Instant? get() = runCatching { Instant.parse(endsAt) }.getOrNull()
    fun contains(now: Instant): Boolean = start?.let { !now.isBefore(it) } == true && end?.let { now.isBefore(it) } == true
}
@Serializable
data class ChallengeAnimations(@SerialName("idle_url") val idleUrl: String? = null, @SerialName("unbox_url") val unboxUrl: String? = null)

/** Kick returns an animation base URL; Coil needs the GIF rendition, not the 404 base path. */
internal fun rewardAnimationImageUrl(raw: String?): String? {
    val url = raw?.toHttpUrlOrNull() ?: return null
    return if (url.host == "static.kick.com" && url.encodedPath.startsWith("/rewards/") &&
        url.pathSegments.lastOrNull() in setOf("idle", "unbox")) {
        url.newBuilder().encodedPath(url.encodedPath + ".gif").build().toString()
    } else url.toString()
}

internal fun orderChallenges(challenges: List<Challenge>): List<Challenge> = challenges.sortedWith(
    compareByDescending<Challenge> { it.activePeriod != null }.thenByDescending { it.featured }
)
@Serializable
data class WatchTimeGoalReached(
    @SerialName("user_id") val userId: Long,
    @SerialName("challenge_id") val challengeId: String,
    val recurrence: String? = null,
    @SerialName("threshold_minutes") val thresholdMinutes: Long
)

fun DropTableEntry.chancePercent(table: List<DropTableEntry>): BigDecimal {
    val total = table.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.weighting.coerceAtLeast(0).toBigDecimal() }
    return if (total.signum() == 0) BigDecimal.ZERO else weighting.coerceAtLeast(0).toBigDecimal()
        .multiply(BigDecimal(100)).divide(total, 12, RoundingMode.HALF_UP)
}
fun formatDropChance(entry: DropTableEntry, table: List<DropTableEntry>, locale: Locale): String {
    val value = entry.chancePercent(table)
    val formatter = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 8; isGroupingUsed = false }
    return if (value.signum() > 0 && value < BigDecimal("0.00000001")) "<${formatter.format(BigDecimal("0.00000001"))}%"
        else "${formatter.format(value)}%"
}
fun challengeCountdown(end: Instant?, now: Instant): String? {
    if (end == null) return null
    val seconds = Duration.between(now, end).seconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}

enum class ChallengeLoadFailure { Http, Network, Format, Other }
data class ChallengesState(val challenges: List<Challenge> = emptyList(), val loading: Boolean = false,
    val loaded: Boolean = false, val failed: Boolean = false,
    val failure: ChallengeLoadFailure? = null, val httpStatus: Int? = null,
    val claimingId: String? = null, val reward: ChallengeClaim? = null,
    val claimFailed: Boolean = false, val claimHttpStatus: Int? = null)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal fun decodeChallenges(response: JsonElement, json: Json): List<Challenge> {
    val array = when (response) {
        is JsonArray -> response
        is JsonObject -> response["data"] as? JsonArray
        else -> null
    } ?: throw SerializationException("Expected challenge array or data array")
    return array.mapIndexed { index, element ->
        try { json.decodeFromJsonElement<Challenge>(element) }
        catch (error: SerializationException) {
            val fields = (error as? MissingFieldException)?.missingFields?.joinToString().orEmpty()
            accountDiagnostic("KickChallenges", "Decode failed: entry=$index type=${error.javaClass.simpleName} missingFields=[$fields]")
            throw error
        }
    }.distinctBy { it.id }
}
