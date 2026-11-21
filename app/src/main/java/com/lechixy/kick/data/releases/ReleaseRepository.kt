package com.lechixy.kick.data.releases

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lechixy.kick.data.common.remote.KickNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

object ReleaseConfig {
    const val OWNER = "lechixy"
    const val REPOSITORY = "Kicker"
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
) {
    val publicationTime: Instant?
        get() = try {
            publishedAt?.let(Instant::parse)
        } catch (_: DateTimeParseException) {
            null
        }
}

internal interface ReleaseApi {
    @GET("repos/{owner}/{repo}/releases?per_page=100")
    suspend fun releases(@Path("owner") owner: String, @Path("repo") repo: String): List<GitHubRelease>
}

@Singleton
class ReleaseRepository internal constructor(
    private val cache: DataStore<Preferences>,
    private val fetchReleases: suspend () -> List<GitHubRelease>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        cache = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) {
            File(context.cacheDir, "github-releases.preferences_pb")
        },
        fetchReleases = { api.releases(ReleaseConfig.OWNER, ReleaseConfig.REPOSITORY) },
    )

    private val refreshLock = Mutex()
    private var cachedHistory: CachedReleaseHistory? = null

    internal suspend fun releases(
        retry: Boolean = false,
        versionForNotes: String? = null,
    ): ReleaseHistoryResponse = refreshLock.withLock {
        val stored = cachedHistory ?: readCache().also { cachedHistory = it }
        val requestTime = now()
        val hasRequestedNotes = versionForNotes == null ||
            stored.releases.any { compareVersions(it.tagName, versionForNotes) == 0 }
        if (hasRequestedNotes && isRecent(stored.fetchedAt, requestTime, CACHE_DURATION)) {
            return@withLock ReleaseHistoryResponse(stored.releases)
        }
        if (!retry && isRecent(stored.refreshFailedAt, requestTime, RETRY_DELAY)) {
            return@withLock ReleaseHistoryResponse(stored.releases, refreshFailed = true)
        }
        val releases = try {
            fetchReleases().filterNot { it.draft }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            writeCache(stored.copy(refreshFailedAt = now()))
            return@withLock ReleaseHistoryResponse(stored.releases, refreshFailed = true)
        }
        writeCache(CachedReleaseHistory(releases, fetchedAt = now()))
        ReleaseHistoryResponse(releases)
    }

    private suspend fun readCache(): CachedReleaseHistory = try {
        cache.data.first()[HISTORY]?.let { Json.decodeFromString<CachedReleaseHistory>(it) }
            ?: CachedReleaseHistory()
    } catch (_: IOException) {
        CachedReleaseHistory()
    } catch (_: SerializationException) {
        CachedReleaseHistory()
    }

    private suspend fun writeCache(history: CachedReleaseHistory) {
        cachedHistory = history
        try {
            cache.edit { it[HISTORY] = Json.encodeToString(history) }
        } catch (_: IOException) {
            // The in-memory copy remains available.
        }
    }

    private fun isRecent(timestamp: Long?, currentTime: Long, duration: Long): Boolean =
        timestamp != null && currentTime - timestamp in 0 until duration

    private companion object {
        val api by lazy { KickNetwork.publicService(ReleaseConfig.API_URL, ReleaseApi::class.java) }
        val HISTORY = stringPreferencesKey("history")
        val CACHE_DURATION = TimeUnit.HOURS.toMillis(1)
        val RETRY_DELAY = TimeUnit.MINUTES.toMillis(5)
    }
}

@Serializable
private data class CachedReleaseHistory(
    val releases: List<GitHubRelease> = emptyList(),
    val fetchedAt: Long? = null,
    val refreshFailedAt: Long? = null,
)

internal data class ReleaseHistoryResponse(val releases: List<GitHubRelease>, val refreshFailed: Boolean = false)

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
