package com.ytdownloader.app

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ytdownloader.app.ui.AppTheme

/** Lets the user sign in to YouTube in a WebView so yt-dlp can reuse the session cookies. */
class LoginActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var webView by remember { mutableStateOf<WebView?>(null) }
                BackHandler {
                    val view = webView
                    if (view != null && view.canGoBack()) view.goBack() else finish()
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Sign in to YouTube") },
                            navigationIcon = {
                                IconButton(onClick = ::finish) { Icon(Icons.Default.Close, contentDescription = "Close") }
                            },
                            actions = { TextButton(onClick = ::finishLogin) { Text("Done") } },
                        )
                    },
                ) { padding ->
                    AndroidView(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                // Google refuses sign-in from user agents flagged as an embedded WebView.
                                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String) {
                                        // Back on YouTube after signing in: save the session automatically.
                                        val host = Uri.parse(url).host.orEmpty()
                                        if (host.endsWith("youtube.com") && Cookies.export(this@LoginActivity)) {
                                            done()
                                        }
                                    }
                                }
                                loadUrl(LOGIN_URL)
                                webView = this
                            }
                        },
                    )
                }
            }
        }
    }

    private fun finishLogin() {
        if (Cookies.export(this)) {
            done()
        } else {
            Toast.makeText(this, "Not signed in yet. Finish signing in first.", Toast.LENGTH_LONG).show()
        }
    }

    private fun done() {
        if (isFinishing) return
        Toast.makeText(this, "Signed in. Try your link again.", Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        private const val LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"
    }
}
