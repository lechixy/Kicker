package com.lechixy.kick.components.account

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * AndroidView's default WRAP_CONTENT measurement can make Chromium resolve all
 * viewport-height units to zero. A MATCH_PARENT child receives an exact height
 * from FrameLayout; do not navigate until that measured viewport exists.
 */
internal fun createKickLoginWebViewContainer(
    context: Context,
    onCreated: (WebView) -> Unit,
    onSized: (WebView) -> Unit
): FrameLayout = FrameLayout(context).apply {
    val view = WebView(context)
    view.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    configureKickLoginWebView(view)
    onCreated(view)
    view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
        override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int,
                                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
            if (right > left && bottom > top) {
                v.removeOnLayoutChangeListener(this)
                onSized(view)
            }
        }
    })
    addView(view)
}

/** Website login compatibility, applied before the first navigation. */
@SuppressLint("SetJavaScriptEnabled")
internal fun configureKickLoginWebView(view: WebView) {
    view.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        userAgentString = kickLoginUserAgent(WebSettings.getDefaultUserAgent(view.context))
        useWideViewPort = true
        loadWithOverviewMode = true
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = true
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(view, true)
    }
    view.webChromeClient = WebChromeClient()
}

/** Keep the installed Chromium version; use the mobile site on tablets as well. */
internal fun kickLoginUserAgent(default: String): String {
    val browser = default.replace("; wv", "").replace("Version/4.0 ", "")
    return if (Regex("\\bMobile\\b").containsMatchIn(browser)) browser
    else browser.replace(" Safari/", " Mobile Safari/")
}
