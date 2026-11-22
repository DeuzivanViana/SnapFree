package com.snapfree.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Lifecycle of a single download job. */
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean get() = this == QUEUED || this == RUNNING || this == PROCESSING
}

/**
 * A download job and, once finished, a history entry.
 * The [id] matches the WorkManager request id so the job can be cancelled or retried.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploader: String?,
    val source: String?,
    /** Human readable description of the chosen format, e.g. "MP4 · 1080p". */
    val formatLabel: String,
    /** Serialized [com.snapfree.app.engine.DownloadSpec] used to rebuild the yt-dlp command. */
    val spec: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val etaSeconds: Long = -1,
    val speed: String? = null,
    val fileUri: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Long = 0,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)
