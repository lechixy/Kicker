package com.lechixy.kick.data.account.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

enum class SessionStatus { Guest, Connecting, Authenticated, ReauthenticationRequired }

data class AccountSession(
    val status: SessionStatus = SessionStatus.Guest,
    val profile: AccountProfile? = null,
    val error: String? = null,
    val generation: Long = 0
) {
    val signedIn get() = status == SessionStatus.Authenticated
}

@Serializable
data class AccountProfile(
    @SerialName("user_id") val userId: Long,
    val name: String,
    @SerialName("profile_picture") val avatar: String? = null,
    val createdAt: String? = null
)
