package com.lechixy.kick.components.account

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class KickLoginWebViewTest {
    @Test fun loginConfigurationRendersJavascriptModalAndKeepsDomStorageAvailable() {
        val loaded = CountDownLatch(1)
        val rendered = AtomicReference<String>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            var web: WebView? = null
            try {
                scenario.onActivity { activity ->
                    val view = WebView(activity)
                    web = view
                    configureKickLoginWebView(view)
                    assertTrue(CookieManager.getInstance().acceptThirdPartyCookies(view))
                    assertTrue(view.settings.userAgentString.contains("Mobile"))
                    assertFalse(view.settings.userAgentString.contains("; wv"))
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript("""
                                (() => {
                                  const dialog = document.querySelector('dialog');
                                  return dialog.open && dialog.getBoundingClientRect().width > 0 &&
                                    dialog.getBoundingClientRect().height > 0 &&
                                    localStorage.getItem('login-ready') === 'yes';
                                })()
                            """.trimIndent()) { rendered.set(it); loaded.countDown() }
                        }
                    }
                    activity.setContentView(view)
                    view.loadDataWithBaseURL("https://kick.com/", """
                        <!doctype html><html><meta name="viewport" content="width=device-width, initial-scale=1">
                        <body><dialog><form><label>Login <input name="username"></label></form></dialog>
                        <script>localStorage.setItem('login-ready','yes');document.querySelector('dialog').showModal();</script>
                        </body></html>
                    """.trimIndent(), "text/html", "UTF-8", null)
                }
                assertTrue("WebView should finish loading test HTML", loaded.await(20, TimeUnit.SECONDS))
                assertEquals("true", rendered.get())
            } finally {
                scenario.onActivity {
                    web?.apply { stopLoading(); destroy() }
                    WebStorage.getInstance().deleteAllData()
                }
            }
        }
    }
}
