package com.snapfree.app

import android.app.Application
import com.snapfree.app.download.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SnapFreeApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Lives as long as the process; used for work that must outlive screens. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.engine.initialize(appScope)
        appScope.launch(Dispatchers.IO) { runCatching { container.downloads.reconcile() } }
    }
}
