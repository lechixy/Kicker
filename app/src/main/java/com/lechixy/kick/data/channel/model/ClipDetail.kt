package com.lechixy.kick.data.channel.model

import com.lechixy.kick.data.common.model.Category
import com.lechixy.kick.data.player.model.RecordedPlayback
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class ClipDetailResponse(val data: ClipDetail)

@Serializable
data class ClipDetail(
    val id: String,
    val title: String,
    val duration: Double,
    @SerialName("playback_url") val playbackUrl: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("views_count") val views: Long = 0,
    @SerialName("is_mature") val isMature: Boolean = false,
    val category: Category? = null,
    val channel: ClipChannel,
    val creator: ClipCreator,
    val video: ClipVideo? = null,
) {
    val durationMs get() = (duration * 1000).toLong().coerceAtLeast(0)
    val shareUrl get() = "https://kick.com/${channel.slug}/clips/$id"

    fun toRecording() = RecordedPlayback(
        id = id,
        title = title,
        durationMs = durationMs,
        thumbnail = thumbnailUrl,
        views = views,
        directUrl = playbackUrl,
    )
}

@Serializable
data class ClipChannel(
    val id: Long,
    val username: String,
    val slug: String,
    @SerialName("profile_picture") val profilePicture: String? = null,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("is_verified") val isVerified: Boolean = false,
)

@Serializable
data class ClipCreator(val id: Long, val username: String, val slug: String)

@Serializable
data class ClipVideo(val id: String, val title: String, val offset: Int = 0)

private val clipIdPattern = Regex("clip_[A-Za-z0-9]{1,64}")

internal fun kickClipId(url: String): String? {
    val link = url.toHttpUrlOrNull() ?: return null
    if (link.host !in setOf("kick.com", "www.kick.com", "web.kick.com")) return null
    val path = link.pathSegments.dropLastWhile(String::isEmpty)
    val id = when {
        path.size == 3 && path[1] == "clips" -> path[2]
        path.size == 2 && path[0] == "clips" -> path[1]
        path.size <= 1 -> link.queryParameter("clip")
        else -> null
    }
    return id?.takeIf(clipIdPattern::matches)
}
