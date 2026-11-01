package com.lechixy.kick.components.account

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class KickLoginViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun composeHostPreservesViewportHeightAndDoesNotClipLoginForm() {
        val loaded = CountDownLatch(1)
        val measured = AtomicReference<String>()
        var web: WebView? = null
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { context ->
                    createKickLoginWebViewContainer(context,
                        onCreated = { view ->
                            web = view
                            view.webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    view.evaluateJavascript("""
                                        (() => {
                                          const probe = document.getElementById('viewport').getBoundingClientRect();
                                          const dialog = document.querySelector('dialog').getBoundingClientRect();
                                          const form = document.querySelector('form').getBoundingClientRect();
                                          return {viewport: innerHeight, vh:probe.height, dialog:dialog.height,
                                            form:form.height, unclipped:form.bottom <= dialog.bottom &&
                                              dialog.height > 250 && probe.height > innerHeight * 0.95};
                                        })()
                                    """.trimIndent()) { measured.set(it); loaded.countDown() }
                                }
                            }
                        },
                        onSized = { view ->
                            view.loadDataWithBaseURL("https://kick.com/", """
                                <!doctype html><html><meta name="viewport" content="width=device-width, initial-scale=1">
                                <style>html,body{margin:0;height:100dvh;overflow:hidden}
                                #viewport{position:fixed;height:100vh;width:1px;visibility:hidden}
                                dialog{max-height:90vh;overflow:hidden;padding:16px}form{height:300px}</style>
                                <body><div id="viewport"></div><dialog><form><input name="username"></form></dialog>
                                <script>document.querySelector('dialog').showModal()</script></body></html>
                            """.trimIndent(), "text/html", "UTF-8", null)
                        })
                }, modifier = Modifier.fillMaxSize())
            }
        }
        try {
            assertTrue("WebView did not load", loaded.await(20, TimeUnit.SECONDS))
            assertTrue("Viewport regression: ${measured.get()}", measured.get().contains("\"unclipped\":true"))
        } finally { compose.runOnIdle { web?.apply { stopLoading(); destroy() } } }
    }
}
