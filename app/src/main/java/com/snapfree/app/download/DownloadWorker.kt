package com.snapfree.app.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.snapfree.app.R
import com.snapfree.app.SnapFreeApp
import com.snapfree.app.data.DownloadEntity
import com.snapfree.app.data.DownloadStatus
import com.snapfree.app.engine.DownloadSpec
import com.snapfree.app.engine.YtDlpEngine
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs one yt-dlp download as a foreground job, reports progress to the database and the
 * notification, then moves the result into shared storage.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val container = (context.applicationContext as SnapFreeApp).container
    private val dao = container.database.downloadDao()
    private val downloadId: String = params.id.toString()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(applicationContext.getString(R.string.status_queued), 0f, indeterminate = true)

    override suspend fun doWork(): Result {
        val entity = dao.get(downloadId) ?: return Result.failure()
        val title = entity.title
        val tempDir = File(applicationContext.cacheDir, "downloads/$downloadId")

        var slotAcquired = false
        try {
            setForegroundSafely(title, applicationContext.getString(R.string.status_queued), 0f, true)
            dao.updateProgress(downloadId, DownloadStatus.QUEUED, 0f, -1, null)

            container.engine.awaitReady()
            DownloadGate.acquire { container.settings.current().concurrentDownloads }
            slotAcquired = true

            val spec = DownloadSpec.fromJson(entity.spec)
            tempDir.deleteRecursively()
            tempDir.mkdirs()

            dao.updateProgress(downloadId, DownloadStatus.RUNNING, 0f, -1, null)
            setForegroundSafely(title, applicationContext.getString(R.string.status_starting), 0f, true)

            // Retries with an updated yt-dlp and other YouTube clients when YouTube answers 403 or similar.
            container.engine.withRetries(
                url = entity.url,
                onRetry = { _, _ ->
                    tempDir.deleteRecursively()
                    tempDir.mkdirs()
                    dao.updateProgress(downloadId, DownloadStatus.RUNNING, 0f, -1, null)
                    setForegroundSafely(title, applicationContext.getString(R.string.status_retrying), 0f, true)
                },
                isCancelled = { isStopped },
            ) { attempt ->
                val request = container.engine.buildDownloadRequest(entity.url, spec, container.settings.current(), tempDir, attempt)
                runYtDlp(entity, request)
            }
            val settings = container.settings.current()

            // Pick the final file (ignore partial and side files such as thumbnails).
            val output = tempDir.listFiles()
                ?.filter { it.isFile && it.extension !in setOf("part", "ytdl", "jpg", "webp", "png", "vtt", "srt") }
                ?.maxByOrNull { it.length() }
                ?: error(applicationContext.getString(R.string.error_no_output))

            dao.updateProgress(downloadId, DownloadStatus.PROCESSING, 1f, -1, null)
            setForegroundSafely(title, applicationContext.getString(R.string.status_saving), 1f, true)
            val saved = withContext(Dispatchers.IO) {
                MediaStoreSaver.save(applicationContext, output, settings.saveLocation, settings.subfolder)
            }
            dao.markCompleted(downloadId, saved.uri.toString(), saved.name, saved.mimeType, saved.size, System.currentTimeMillis())
            Notifications.completed(applicationContext, downloadId, title, saved.uri, saved.mimeType)
            return Result.success()
        } catch (e: CancellationException) {
            YoutubeDL.destroyProcessById(downloadId)
            withContext(NonCancellable) { dao.updateStatus(downloadId, DownloadStatus.CANCELLED) }
            throw e
        } catch (e: YoutubeDL.CanceledException) {
            dao.updateStatus(downloadId, DownloadStatus.CANCELLED)
            return Result.failure()
        } catch (t: Throwable) {
            Log.e(TAG, "Download $downloadId failed", t)
            val message = YtDlpEngine.cleanError(t)
            // A job cancelled by the user may surface as an interrupted/killed process instead of a cancellation.
            if (isStopped) {
                dao.updateStatus(downloadId, DownloadStatus.CANCELLED)
            } else {
                dao.updateStatus(downloadId, DownloadStatus.FAILED, message)
                Notifications.failed(applicationContext, downloadId, title, message)
            }
            return Result.failure()
        } finally {
            if (slotAcquired) withContext(NonCancellable) { DownloadGate.release() }
            tempDir.deleteRecursively()
        }
    }

    /** Executes yt-dlp, forwarding throttled progress updates to Room and the notification. */
    private suspend fun runYtDlp(entity: DownloadEntity, request: com.yausername.youtubedl_android.YoutubeDLRequest) =
        coroutineScope {
            val updates = Channel<ProgressUpdate>(Channel.CONFLATED)
            val reporter = launch {
                for (u in updates) {
                    dao.updateProgress(downloadId, u.status, u.progress, u.eta, u.speed)
                    val text = when (u.status) {
                        DownloadStatus.PROCESSING -> applicationContext.getString(R.string.status_processing)
                        else -> listOfNotNull("${(u.progress * 100).toInt()}%", u.speed).joinToString(" · ")
                    }
                    setForegroundSafely(entity.title, text, u.progress, u.status == DownloadStatus.PROCESSING)
                    kotlinx.coroutines.delay(PROGRESS_INTERVAL_MS)
                }
            }
            var lastProgress = 0f
            try {
                runInterruptible(Dispatchers.IO) {
                    YoutubeDL.execute(request, downloadId) { progress, eta, line ->
                        val status = if (POST_PROCESS_MARKERS.any { line.contains(it) }) {
                            DownloadStatus.PROCESSING
                        } else {
                            DownloadStatus.RUNNING
                        }
                        val parsed = PERCENT.find(line)?.groupValues?.get(1)?.toFloatOrNull()
                        val value = ((parsed ?: progress.takeIf { it >= 0 } ?: (lastProgress * 100)) / 100f).coerceIn(0f, 1f)
                        lastProgress = value
                        val speed = SPEED.find(line)?.groupValues?.get(1)
                        updates.trySend(ProgressUpdate(status, value, eta, speed))
                    }
                }
            } finally {
                updates.close()
                reporter.join()
            }
        }

    private suspend fun setForegroundSafely(title: String, text: String, progress: Float, indeterminate: Boolean) {
        try {
            setForeground(foregroundInfo(text, progress, indeterminate, title))
        } catch (t: Throwable) {
            // Starting a foreground service can be refused when the app is in the background on Android 12+.
            Log.w(TAG, "Could not promote worker to foreground", t)
        }
    }

    private fun foregroundInfo(text: String, progress: Float, indeterminate: Boolean, title: String? = null): ForegroundInfo {
        val notification = Notifications.progress(
            applicationContext, downloadId,
            title ?: applicationContext.getString(R.string.app_name),
            progress, text, indeterminate,
        )
        return ForegroundInfo(Notifications.idFor(downloadId), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private data class ProgressUpdate(val status: DownloadStatus, val progress: Float, val eta: Long, val speed: String?)

    companion object {
        private const val TAG = "DownloadWorker"
        private const val PROGRESS_INTERVAL_MS = 500L
        private val PERCENT = Regex("""\[download]\s+(\d+(?:\.\d+)?)%""")
        private val SPEED = Regex("""at\s+(\S+/s)""")
        private val POST_PROCESS_MARKERS = listOf(
            "[Merger]", "[ExtractAudio]", "[VideoRemuxer]", "[VideoConvertor]",
            "[EmbedThumbnail]", "[Metadata]", "[FixupM3u8]", "[EmbedSubtitle]", "[SponsorBlock]", "[ModifyChapters]",
        )
    }
}
