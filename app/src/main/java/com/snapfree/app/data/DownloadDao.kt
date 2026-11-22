package com.snapfree.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, error: String? = null)

    @Query("UPDATE downloads SET status = :status, progress = :progress, etaSeconds = :eta, speed = :speed WHERE id = :id")
    suspend fun updateProgress(id: String, status: DownloadStatus, progress: Float, eta: Long, speed: String?)

    @Query(
        "UPDATE downloads SET status = 'COMPLETED', progress = 1, fileUri = :uri, fileName = :name, " +
            "mimeType = :mime, fileSize = :size, completedAt = :completedAt, error = NULL WHERE id = :id"
    )
    suspend fun markCompleted(id: String, uri: String, name: String, mime: String, size: Long, completedAt: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearFinished()

    /** Jobs left in an active state after the process died are marked as failed on startup. */
    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'RUNNING', 'PROCESSING')")
    suspend fun getActive(): List<DownloadEntity>
}
