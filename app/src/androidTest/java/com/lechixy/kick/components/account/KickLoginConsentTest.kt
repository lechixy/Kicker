package com.lechixy.kick.components.account

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class KickLoginConsentTest {
    @Test fun acceptsDelayedCookieBannerWithoutClickingOtherButtons() {
        val loaded = CountDownLatch(1)
        val checked = CountDownLatch(1)
        val result = AtomicReference<String>()
        var web: WebView? = null
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = WebView(activity)
                web = view
                configureKickLoginWebView(view)
                activity.setContentView(view)
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(kickLoginPageScript) { loaded.countDown() }
                    }
                }
                view.loadDataWithBaseURL("https://kick.com/", """
                    <!doctype html><html><body>
                    <button id="other" onclick="document.body.dataset.other='clicked'">Accept</button>
                    <script>
                      setTimeout(() => {
                        const button = document.createElement('button');
                        button.id = 'onetrust-accept-btn-handler';
                        button.textContent = 'Accept All Cookies';
                        button.onclick = () => { document.body.dataset.accepted = 'yes'; button.remove(); };
                        document.body.appendChild(button);
                      }, 250);
                    </script></body></html>
                """.trimIndent(), "text/html", "UTF-8", null)
            }
            try {
                assertTrue(loaded.await(20, TimeUnit.SECONDS))
                // Keep waiting off the UI thread while the asynchronous banner arrives.
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (System.nanoTime() < deadline) {
                    val probe = CountDownLatch(1)
                    scenario.onActivity {
                        web!!.evaluateJavascript("document.body.dataset.accepted === 'yes'") {
                            result.set(it); probe.countDown()
                        }
                    }
                    assertTrue(probe.await(5, TimeUnit.SECONDS))
                    if (result.get() == "true") break
                    Thread.sleep(100)
                }
                assertEquals("true", result.get())
                scenario.onActivity {
                    web!!.evaluateJavascript("document.body.dataset.other === undefined") {
                        result.set(it); checked.countDown()
                    }
                }
                assertTrue(checked.await(5, TimeUnit.SECONDS))
                assertEquals("true", result.get())
            } finally {
                scenario.onActivity { web?.apply { stopLoading(); destroy() } }
            }
        }
    }
}
