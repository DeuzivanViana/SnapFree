package com.snapfree.app

import android.content.Context
import com.snapfree.app.data.AppDatabase
import com.snapfree.app.data.CookieStore
import com.snapfree.app.data.DownloadRepository
import com.snapfree.app.data.SettingsRepository
import com.snapfree.app.engine.YtDlpEngine

/** Manual dependency container shared by the whole app. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val cookies = CookieStore(context)
    val engine = YtDlpEngine(context, settings, cookies)
    val downloads = DownloadRepository(context, database.downloadDao(), settings)
}
