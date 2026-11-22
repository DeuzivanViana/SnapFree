package com.snapfree.app.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.snapfree.app.download.DownloadWorker
import com.snapfree.app.engine.DownloadSpec
import com.snapfree.app.engine.MediaInfo
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Entry point for creating, observing and controlling downloads. */
class DownloadRepository(
    private val context: Context,
    private val dao: DownloadDao,
    private val settings: SettingsRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun enqueue(info: MediaInfo, spec: DownloadSpec, formatLabel: String): String {
        val id = UUID.randomUUID()
        dao.upsert(
            DownloadEntity(
                id = id.toString(),
                url = info.url,
                title = info.title,
                thumbnailUrl = info.thumbnailUrl,
                uploader = info.uploader,
                source = info.source,
                formatLabel = formatLabel,
                spec = spec.toJson(),
            )
        )
        schedule(id)
        return id.toString()
    }

    /** Re-runs a failed or cancelled download with the same options under a new id. */
    suspend fun retry(entity: DownloadEntity) {
        val id = UUID.randomUUID()
        dao.delete(entity.id)
        dao.upsert(
            entity.copy(
                id = id.toString(),
                status = DownloadStatus.QUEUED,
                progress = 0f,
                etaSeconds = -1,
                speed = null,
                error = null,
                createdAt = System.currentTimeMillis(),
                completedAt = null,
            )
        )
        schedule(id)
    }

    fun cancel(id: String) {
        YoutubeDL.destroyProcessById(id)
        workManager.cancelWorkById(UUID.fromString(id))
    }

    suspend fun delete(id: String) {
        cancel(id)
        dao.delete(id)
    }

    suspend fun clearFinished() = dao.clearFinished()

    /** Marks jobs whose worker no longer exists (e.g. app was force-stopped) as failed. */
    suspend fun reconcile() {
        for (entity in dao.getActive()) {
            val info = workManager.getWorkInfoById(UUID.fromString(entity.id)).get()
            if (info == null || info.state.isFinished) {
                dao.updateStatus(entity.id, DownloadStatus.FAILED, "Interrupted")
            }
        }
    }

    private suspend fun schedule(id: UUID) {
        val wifiOnly = settings.current().wifiOnly
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setId(id)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(id.toString(), ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val TAG = "download"
    }
}
