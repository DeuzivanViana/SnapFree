package com.snapfree.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfree.app.data.AppSettings
import com.snapfree.app.ui.SnapFreeRoot
import com.snapfree.app.ui.theme.SnapFreeTheme
import com.snapfree.app.ui.theme.isAppInDarkTheme
import com.snapfree.app.util.UrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : AppCompatActivity() {

    private val sharedLink = MutableStateFlow<SharedLink?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Optional: downloads work without it. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as SnapFreeApp).container
        if (savedInstanceState == null) handleIntent(intent)
        requestNotificationPermission()

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            SnapFreeTheme(darkTheme = isAppInDarkTheme(settings.themeMode), pureBlack = settings.pureBlack) {
                SnapFreeRoot(
                    container = container,
                    sharedLink = sharedLink.asStateFlow(),
                    onSharedLinkConsumed = { sharedLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
        UrlUtils.extractUrl(text)?.let { sharedLink.value = SharedLink(it) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** A link received from the Android share sheet. The timestamp makes repeated shares of the same URL distinct. */
data class SharedLink(val url: String, val receivedAt: Long = System.currentTimeMillis())

