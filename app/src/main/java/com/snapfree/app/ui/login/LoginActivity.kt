package com.snapfree.app.ui.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfree.app.R
import com.snapfree.app.SnapFreeApp
import com.snapfree.app.data.AppSettings
import com.snapfree.app.data.LoginSite
import com.snapfree.app.ui.theme.SnapFreeTheme
import com.snapfree.app.ui.theme.isAppInDarkTheme

/**
 * Lets the user sign in to a site inside a WebView and hands the resulting cookies to yt-dlp.
 * The password goes only to the site itself; SnapFree only reads the session cookies afterwards.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var site: LoginSite
    private var webView: WebView? = null
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        site = LoginSite.entries.firstOrNull { it.key == intent.getStringExtra(EXTRA_SITE) } ?: run { finish(); return }
        val container = (application as SnapFreeApp).container

        setContent {
            val appSettings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            var progress by remember { mutableStateOf(0) }
            var pageTitle by remember { mutableStateOf("") }
            SnapFreeTheme(darkTheme = isAppInDarkTheme(appSettings.themeMode), pureBlack = appSettings.pureBlack) {
                BackHandler {
                    val web = webView
                    if (web != null && web.canGoBack()) web.goBack() else finish()
                }
                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = {
                                Text(
                                    stringResource(R.string.login_title, siteName(site)),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                                }
                            },
                            actions = {
                                TextButton(onClick = { tryFinish(manual = true) }) { Text(stringResource(R.string.login_done)) }
                            },
                        )
                    },
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    webView = this
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    // Google refuses sign-in from user agents that identify as an embedded WebView.
                                    settings.userAgentString = settings.userAgentString
                                        .replace("; wv", "")
                                        .replace(Regex("""Version/\d+(\.\d+)*\s"""), "")
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webChromeClient = object : android.webkit.WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress
                                        }

                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                            pageTitle = title.orEmpty()
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            progress = 5
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            progress = 100
                                            tryFinish(manual = false)
                                        }
                                    }
                                    loadUrl(site.loginUrl)
                                }
                            },
                        )
                        if (progress in 1..99) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Saves the cookies once the login cookie exists. With [manual], tells the user when it is missing. */
    private fun tryFinish(manual: Boolean) {
        if (finished) return
        val cookies = (application as SnapFreeApp).container.cookies
        if (!cookies.webViewHasSession(site)) {
            if (manual) Toast.makeText(this, R.string.login_not_yet, Toast.LENGTH_LONG).show()
            return
        }
        // YouTube logins land on Google first; wait until the browser is back on YouTube so its cookies exist.
        if (site == LoginSite.YOUTUBE && webView?.url?.contains("youtube.com") != true && !manual) return
        if (cookies.captureFromWebView(site)) {
            finished = true
            Toast.makeText(this, getString(R.string.login_success, siteName(site)), Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    /** Starts the login screen for a site; the result is true when the login succeeded. */
    class Contract : ActivityResultContract<LoginSite, Boolean>() {
        override fun createIntent(context: Context, input: LoginSite): Intent = intent(context, input)
        override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK
    }

    companion object {
        private const val EXTRA_SITE = "site"

        fun intent(context: Context, site: LoginSite): Intent =
            Intent(context, LoginActivity::class.java).putExtra(EXTRA_SITE, site.key)

        fun siteName(site: LoginSite): String = when (site) {
            LoginSite.INSTAGRAM -> "Instagram"
            LoginSite.YOUTUBE -> "YouTube"
            LoginSite.TIKTOK -> "TikTok"
        }
    }
}
