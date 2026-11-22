package com.snapfree.app.engine

import org.json.JSONObject

/** Metadata about a link, extracted by yt-dlp, reduced to what the UI needs. */
data class MediaInfo(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploader: String?,
    val source: String?,
    val durationSeconds: Int,
    /** Distinct video heights available, highest first. Empty when the source has audio only. */
    val videoQualities: List<VideoQuality>,
    /** Best known size of the best audio-only stream, used for size estimates. */
    val bestAudioSize: Long,
    /** Every format reported by the source, for the advanced picker. */
    val formats: List<FormatItem>,
) {
    val hasVideo: Boolean get() = videoQualities.isNotEmpty() || formats.any { it.hasVideo }
}

data class VideoQuality(
    val height: Int,
    val fps: Int,
    /** Estimated size of the best video stream at this height plus audio, or 0 if unknown. */
    val estimatedSize: Long,
)

data class FormatItem(
    val formatId: String,
    val ext: String,
    val height: Int,
    val fps: Int,
    val vcodec: String?,
    val acodec: String?,
    val note: String?,
    val size: Long,
    val bitrate: Int,
) {
    val hasVideo: Boolean get() = vcodec != null && vcodec != "none"
    val hasAudio: Boolean get() = acodec != null && acodec != "none"
    val isAudioOnly: Boolean get() = hasAudio && !hasVideo
    val isVideoOnly: Boolean get() = hasVideo && !hasAudio
}

enum class DownloadType { VIDEO, AUDIO, CUSTOM }

/**
 * Everything needed to rebuild the yt-dlp command for a download.
 * Stored as JSON in the database so queued jobs survive app restarts.
 */
data class DownloadSpec(
    val type: DownloadType,
    /** mp4, webm or mkv for video downloads. */
    val container: String = "mp4",
    /** Maximum height for video downloads; 0 means best. */
    val maxHeight: Int = 0,
    /** mp3, m4a, opus, flac, wav or best for audio downloads. */
    val audioFormat: String = "mp3",
    val audioBitrate: Int = 192,
    /** Exact yt-dlp format id for custom downloads. */
    val formatId: String? = null,
    /** True when the custom format has no audio track, so the best audio is merged in. */
    val mergeAudio: Boolean = false,
) {
    fun toJson(): String = JSONObject()
        .put("type", type.name)
        .put("container", container)
        .put("maxHeight", maxHeight)
        .put("audioFormat", audioFormat)
        .put("audioBitrate", audioBitrate)
        .put("formatId", formatId)
        .put("mergeAudio", mergeAudio)
        .toString()

    companion object {
        fun fromJson(json: String): DownloadSpec {
            val o = JSONObject(json)
            return DownloadSpec(
                type = DownloadType.valueOf(o.getString("type")),
                container = o.optString("container", "mp4"),
                maxHeight = o.optInt("maxHeight", 0),
                audioFormat = o.optString("audioFormat", "mp3"),
                audioBitrate = o.optInt("audioBitrate", 192),
                formatId = if (o.isNull("formatId")) null else o.optString("formatId"),
                mergeAudio = o.optBoolean("mergeAudio", false),
            )
        }
    }
}

object FormatCatalog {
    val videoContainers = listOf("mp4", "webm", "mkv")
    val audioFormats = listOf("mp3", "m4a", "opus", "flac", "wav", "best")
    val audioBitrates = listOf(320, 256, 192, 160, 128, 96)
    val heights = listOf(0, 2160, 1440, 1080, 720, 480, 360, 240)

    /** Lossless or untouched outputs ignore the bitrate setting. */
    fun usesBitrate(audioFormat: String) = audioFormat !in setOf("flac", "wav", "best")
}
