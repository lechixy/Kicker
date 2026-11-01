package com.lechixy.kick.data.account.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ChallengeClaim(
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("reveal_type") val revealType: String = "roulette",
    val roulette: List<ShieldReward> = emptyList(),
    val winner: RewardWinner
)

@Serializable
data class ShieldReward(val id: String, @SerialName("item_url") val itemUrl: String)

@Serializable
data class RewardWinner(
    val id: String,
    val rarity: String,
    @SerialName("card_url") val cardUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
)

internal fun decodeChallengeClaim(response: JsonObject, challengeId: String, json: Json): ChallengeClaim {
    val payload = response["data"] as? JsonObject ?: response
    return json.decodeFromJsonElement<ChallengeClaim>(payload).also {
        require(it.challengeId == challengeId && it.winner.id.isNotBlank()) { "Invalid challenge reward response" }
    }
}

/** Unknown/hash-only asset names use the neutral common token. */
internal fun shieldRarity(url: String): String =
    Regex("_(common|uncommon|rare|epic|legendary|mythic|mythical)_", RegexOption.IGNORE_CASE)
        .find(url.substringBefore('?').substringAfterLast('/'))?.groupValues?.get(1)?.lowercase()
        ?.replace("mythical", "mythic") ?: "common"

internal data class ShieldReelPlan(val items: List<ShieldReward>, val winnerIndex: Int, val landingOffsetFraction: Float)

/** Repeats the server's candidates for visual travel only; the result is always the server winner. */
internal fun shieldReelPlan(claim: ChallengeClaim): ShieldReelPlan {
    val candidates = claim.roulette.filter { it.id.isNotBlank() && it.itemUrl.isNotBlank() }.take(200).toMutableList()
    if (candidates.none { it.id == claim.winner.id }) candidates += ShieldReward(claim.winner.id,
        claim.winner.thumbnailUrl ?: claim.winner.cardUrl.orEmpty())
    val cycles = maxOf(3, (48 + candidates.size - 1) / candidates.size)
    val winnerIndex = cycles * candidates.size + candidates.indexOfFirst { it.id == claim.winner.id }
    val items = List(winnerIndex + 6) { candidates[it % candidates.size] }
    val offset = ((claim.challengeId + claim.winner.id).hashCode().toLong() and 0xffff) / 65535f * 0.5f - 0.25f
    return ShieldReelPlan(items, winnerIndex, offset)
}
