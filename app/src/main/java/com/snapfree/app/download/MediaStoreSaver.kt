package com.snapfree.app.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.snapfree.app.data.SaveLocation
import java.io.File

data class SavedFile(val uri: Uri, val name: String, val mimeType: String, val size: Long)

/** Copies a finished download from private storage into shared storage (Download, Movies or Music). */
object MediaStoreSaver {

    fun save(context: Context, file: File, location: SaveLocation, subfolder: String): SavedFile {
        val mime = mimeTypeOf(file)
        val isAudio = mime.startsWith("audio/")
        val isVideo = mime.startsWith("video/")

        val (collection, baseDir) = when {
            location == SaveLocation.MEDIA && isVideo ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MOVIES
            location == SaveLocation.MEDIA && isAudio ->
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MUSIC
            else ->
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_DOWNLOADS
        }
        val relativePath = listOf(baseDir, subfolder.trim('/')).filter { it.isNotBlank() }.joinToString("/")

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("Could not create file in shared storage")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out, bufferSize = 1 shl 16) }
            } ?: error("Could not open shared storage for writing")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        return SavedFile(uri = uri, name = file.name, mimeType = mime, size = file.length())
    }

    fun mimeTypeOf(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "opus" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
}
