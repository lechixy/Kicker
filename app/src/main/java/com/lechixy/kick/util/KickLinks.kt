package com.lechixy.kick.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.core.net.toUri
import com.lechixy.kick.data.channel.model.kickClipId
import com.lechixy.kick.screens.channel.ChannelTab
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal sealed interface KickLink {
    data class Channel(val slug: String, val tab: ChannelTab? = null) : KickLink
    data class Clip(val id: String) : KickLink
    data class Recording(val slug: String, val id: String) : KickLink
    data class Category(val slug: String) : KickLink
    data class Screen(val route: String) : KickLink
}

private val supportedHosts = setOf("kick.com", "www.kick.com")
private val slugPattern = Regex("[A-Za-z0-9_-]{1,100}")
private val recordingIdPattern = Regex("[A-Fa-f0-9]{8}(?:-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}")
private val channelTabs = mapOf(
    "about" to ChannelTab.ABOUT,
    "chat" to ChannelTab.CHAT,
    "clips" to ChannelTab.CLIPS,
    "home" to ChannelTab.HOME,
    "videos" to ChannelTab.VIDEOS,
)
private val websitePages = setOf(
    "account", "api", "categories", "category", "checkout", "clip", "clips", "community-guidelines",
    "dashboard", "dmca-policy", "downloads", "events", "following",
    "drops", "help", "livestreams", "login", "logout", "password", "privacy", "qr",
    "partner-terms-and-conditions", "privacy-policy", "register", "rewards", "search", "settings",
    "signup", "stream", "subscriptions", "terms", "terms-of-service", "tv", "tv_help", "video", "videos",
)

internal fun webLink(value: String): HttpUrl? =
    value.toHttpUrlOrNull()?.takeIf { it.username.isEmpty() && it.password.isEmpty() }

internal fun incomingKickLink(value: String): HttpUrl? =
    webLink(value)?.takeIf { it.host in supportedHosts && it.port == HttpUrl.defaultPort(it.scheme) }

internal fun kickLinkDestination(url: HttpUrl): KickLink? {
    if (url.port != HttpUrl.defaultPort(url.scheme)) return null
    kickClipId(url.toString())?.let { return KickLink.Clip(it) }
    if (url.host !in supportedHosts) return null

    val segments = url.pathSegments.dropLastWhile(String::isEmpty)
    val first = segments.firstOrNull()?.lowercase() ?: return KickLink.Screen("home")
    return when {
        segments.size == 1 && first == "following" -> KickLink.Screen("following")
        segments.size == 1 && first == "categories" -> KickLink.Screen("browse")
        segments.size == 2 && first in setOf("category", "categories") && slugPattern.matches(segments[1]) ->
            KickLink.Category(segments[1])
        first !in websitePages && slugPattern.matches(first) -> when (segments.size) {
            1 -> KickLink.Channel(first)
            2 -> channelTabs[segments[1]]?.let { KickLink.Channel(first, it) }
            3 -> segments[2].takeIf { segments[1] == "videos" && recordingIdPattern.matches(it) }
                ?.let { KickLink.Recording(first, it) }
            else -> null
        }
        else -> null
    }
}

internal fun Context.openBrowserTab(url: HttpUrl) {
    // Pin the browser so Kick links without a native destination cannot resolve back to MainActivity.
    val candidates = packageManager.queryIntentServices(Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION), 0)
        .map { it.serviceInfo.packageName }
    val browser = CustomTabsClient.getPackageName(this, candidates)
        ?: throw ActivityNotFoundException("No browser supports Custom Tabs")
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .apply { intent.setPackage(browser) }
        .launchUrl(this, url.toString().toUri())
}
