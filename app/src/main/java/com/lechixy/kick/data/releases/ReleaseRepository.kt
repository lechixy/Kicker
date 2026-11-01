package com.lechixy.kick.data.releases

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import com.lechixy.kick.data.common.remote.KickNetwork
import javax.inject.Inject
import javax.inject.Singleton

object ReleaseConfig {
    const val OWNER = "lechixy"
    const val REPOSITORY = "Kick"
    const val API_URL = "https://api.github.com/"
    const val RELEASES_URL = "https://github.com/$OWNER/$REPOSITORY/releases"
    // Set this for a store flavor when its listing is published; GitHub works with F-Droid/Obtainium builds.
    const val STORE_URL = ""
}

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = ReleaseConfig.RELEASES_URL,
    val draft: Boolean = false,
    val prerelease: Boolean = false
)

internal interface ReleaseApi {
    @GET("repos/{owner}/{repo}/releases?per_page=100")
    suspend fun releases(@Path("owner") owner: String, @Path("repo") repo: String): List<GitHubRelease>
}

@Singleton
class ReleaseRepository @Inject constructor() {
    private val api by lazy { KickNetwork.publicService(ReleaseConfig.API_URL, ReleaseApi::class.java) }
    suspend fun releases() = api.releases(ReleaseConfig.OWNER, ReleaseConfig.REPOSITORY).filterNot { it.draft }
}

fun normalizeVersion(value: String): String = value.trim().trimStart('v', 'V')

/** Numeric components, prerelease precedence and build metadata follow SemVer, including legacy 1.0 tags. */
fun compareVersions(left: String, right: String): Int? {
    fun parse(value: String): Pair<List<java.math.BigInteger>, List<String>>? {
        val clean = normalizeVersion(value).substringBefore('+')
        if (!Regex("[0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z.-]+)?").matches(clean)) return null
        val core = clean.substringBefore('-').split('.').map { it.toBigInteger() }
        val pre = clean.substringAfter('-', "").takeIf { it.isNotEmpty() }?.split('.').orEmpty()
        if (pre.any { it.isBlank() }) return null
        return core to pre
    }
    val a = parse(left) ?: return null
    val b = parse(right) ?: return null
    repeat(maxOf(a.first.size, b.first.size)) { index ->
        val compared = (a.first.getOrNull(index) ?: java.math.BigInteger.ZERO)
            .compareTo(b.first.getOrNull(index) ?: java.math.BigInteger.ZERO)
        if (compared != 0) return compared
    }
    if (a.second.isEmpty() && b.second.isEmpty()) return 0
    if (a.second.isEmpty()) return 1
    if (b.second.isEmpty()) return -1
    repeat(minOf(a.second.size, b.second.size)) { index ->
        val x = a.second[index]; val y = b.second[index]
        val nx = x.toBigIntegerOrNull(); val ny = y.toBigIntegerOrNull()
        val compared = when {
            nx != null && ny != null -> nx.compareTo(ny)
            nx != null -> -1
            ny != null -> 1
            else -> x.compareTo(y)
        }
        if (compared != 0) return compared
    }
    return a.second.size.compareTo(b.second.size)
}

fun newestRelease(releases: List<GitHubRelease>, current: String): GitHubRelease? = releases
    .filter { !it.draft && (!it.prerelease || normalizeVersion(current).contains('-')) }
    .filter { (compareVersions(it.tagName, current) ?: 0) > 0 }
    .maxWithOrNull { a, b -> compareVersions(a.tagName, b.tagName) ?: 0 }

enum class ReleaseDialogMode { WhatsNew, UpdateAvailable }
data class ReleaseDialogRequest(val mode: ReleaseDialogMode, val release: GitHubRelease)
