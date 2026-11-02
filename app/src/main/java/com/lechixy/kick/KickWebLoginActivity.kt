package com.lechixy.kick

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

import com.lechixy.kick.components.account.KICK_LOGIN_URL
import com.lechixy.kick.components.account.createKickLoginWebViewContainer
import com.lechixy.kick.components.account.isAllowedLoginUrl
import com.lechixy.kick.components.account.isKickLoginUrl
import com.lechixy.kick.components.account.kickLoginPageScript
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.model.SessionStatus
import com.lechixy.kick.data.account.remote.userMessage
import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.SettingsRepository
import com.lechixy.kick.theme.KickTheme
import com.lechixy.kick.theme.Theme

/** Login directly on Kick; only the verified Kick session is retained in encrypted storage. */
@AndroidEntryPoint
class KickWebLoginActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    private var webView: WebView? = null
    private var verifying by mutableStateOf(false)
    private var ready by mutableStateOf(false)
    private var pageProgress by mutableIntStateOf(0)
    private var currentUrl by mutableStateOf(KICK_LOGIN_URL)
    private var error by mutableStateOf<String?>(null)
    private var attemptedCookies: String? = null
    private var generation = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.login_enter, R.anim.login_hold)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.login_hold, R.anim.login_exit)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.login_enter, R.anim.login_hold)
        }
        generation = AccountGraph.auth.startLogin()
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            removeAllCookies { flush(); if (!isDestroyed) ready = true }
        }
        WebStorage.getInstance().deleteAllData()
        lifecycleScope.launch {
            AccountGraph.auth.session.collect {
                if (it.generation != generation) finish()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    if (ready) captureSession()
                    delay(1000.milliseconds)
                }
            }
        }
        setContent {
            val theme by settingsRepository.get(SettingsKeys.THEME)
                .collectAsStateWithLifecycle(initialValue = SettingsKeys.THEME.defaultValue)
            val dynamicColor by settingsRepository.get(SettingsKeys.DYNAMIC_COLOR)
                .collectAsStateWithLifecycle(initialValue = SettingsKeys.DYNAMIC_COLOR.defaultValue)
            val selectedTheme = when (theme) {
                "light" -> Theme.LIGHT
                "dark" -> Theme.DARK
                "amoled" -> Theme.AMOLED
                else -> Theme.SYSTEM
            }
            KickTheme(theme = selectedTheme, dynamicColor = dynamicColor) {
                val surface = MaterialTheme.colorScheme.surface
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = surface.luminance() > 0.5f
                        isAppearanceLightNavigationBars = surface.luminance() > 0.5f
                    }
                    window.isNavigationBarContrastEnforced = false
                }
                BackHandler {
                    val view = webView
                    if (view?.canGoBack() == true) view.goBack() else finish()
                }
                Surface(Modifier.fillMaxSize(), color = surface) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.web_login_title), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { finish() }) { Text(stringResource(R.string.web_login_close)) }
                        }
                        Text(
                            stringResource(if (verifying) R.string.web_login_verifying else R.string.web_login_info),
                            Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isKickLoginUrl(currentUrl)) {
                            TextButton(onClick = { startGoogleLogin() }, enabled = ready && !verifying && pageProgress == 100,
                                modifier = Modifier.padding(horizontal = 4.dp)) {
                                Text(stringResource(R.string.web_login_google))
                            }
                        } else {
                            Text(stringResource(R.string.web_login_google_info), Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (!ready || verifying) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else if (pageProgress < 100) {
                            LinearProgressIndicator(progress = { pageProgress / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                        error?.let {
                            Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = {
                                    error = null
                                    attemptedCookies = null
                                    captureSession()
                                    if (!verifying) webView?.reload()
                                }, enabled = !verifying) { Text(stringResource(R.string.web_login_retry)) }
                                TextButton(onClick = { restartLogin() }, enabled = !verifying) {
                                    Text(stringResource(R.string.web_login_restart))
                                }
                            }
                        }
                        if (ready) AndroidView(factory = { context ->
                            createKickLoginWebViewContainer(context,
                                onCreated = { view ->
                                    webView = view
                                    view.setBackgroundColor(surface.toArgb())
                                    view.webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                                            pageProgress = newProgress
                                        }
                                    }
                                    view.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            if (!request.isForMainFrame) return false
                                            val allowed = isAllowedLoginUrl(request.url.toString())
                                            if (!allowed) {
                                                // A Kick session may already exist when a final redirect arrives.
                                                // Verify it immediately instead of reporting a navigation failure.
                                                captureSession()
                                                if (!verifying) error = getString(R.string.web_login_blocked)
                                            }
                                            return !allowed
                                        }
                                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                            currentUrl = url.orEmpty()
                                            pageProgress = 0
                                            error = null
                                        }
                                        override fun onPageFinished(view: WebView, url: String?) {
                                            currentUrl = url.orEmpty()
                                            if (isKickLoginUrl(currentUrl)) {
                                                view.evaluateJavascript(kickLoginPageScript, null)
                                                captureSession()
                                            }
                                        }
                                        override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                                            if (request.isForMainFrame) {
                                                pageProgress = 100
                                                error = getString(R.string.web_login_load_error)
                                            }
                                        }
                                    }
                                },
                                onSized = { it.loadUrl(KICK_LOGIN_URL) }
                            )
                        }, modifier = Modifier.weight(1f).fillMaxWidth(),
                            update = { container -> container.getChildAt(0).setBackgroundColor(surface.toArgb()) })
                    }
                }
            }
        }
    }

    private fun startGoogleLogin() {
        val view = webView ?: return
        if (!isKickLoginUrl(view.url.orEmpty())) return
        // Use Kick's own button: Kick generates and validates its OAuth state and callback.
        view.evaluateJavascript("""
            (() => {
              const button = document.querySelector('[data-testid="oauth-google"]');
              if (!button || button.disabled) return false;
              button.click();
              return true;
            })()
        """.trimIndent()) { clicked ->
            if (clicked != "true" && !isFinishing) error = getString(R.string.web_login_google_unavailable)
        }
    }

    private fun captureSession() {
        if (verifying || isFinishing) return
        val view = webView ?: return
        val manager = CookieManager.getInstance()
        val kickCookies = manager.getCookie("https://kick.com").orEmpty()
        if (kickCookies == attemptedCookies || AuthRepository.cookie(kickCookies, "session_token") == null) return
        val webCookies = manager.getCookie("https://web.kick.com").orEmpty()
        attemptedCookies = kickCookies
        verifying = true; error = null
        lifecycleScope.launch {
            try {
                if (generation != AccountGraph.auth.session.value.generation) { finish(); return@launch }
                AccountGraph.auth.connect(kickCookies, webCookies, view.settings.userAgentString, generation)
                finish()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = if (failure is com.lechixy.kick.data.account.remote.AccountApiException)
                    getString(R.string.web_login_verify_error, failure.status)
                    else failure.userMessage()
            } finally { verifying = false }
        }
    }

    private fun restartLogin() {
        attemptedCookies = null; error = null; pageProgress = 0
        webView?.stopLoading()
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            if (!isFinishing) webView?.apply { clearHistory(); loadUrl(KICK_LOGIN_URL) }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.login_hold, R.anim.login_exit)
        }
    }

    override fun onDestroy() {
        webView?.apply { stopLoading(); clearHistory(); clearCache(true); destroy() }
        webView = null
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        WebStorage.getInstance().deleteAllData()
        if (AccountGraph.auth.session.value.status == SessionStatus.Connecting &&
            AccountGraph.auth.session.value.generation == generation) AccountGraph.auth.cancelLogin()
        super.onDestroy()
    }
}
