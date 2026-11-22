package com.snapfree.app.engine

import android.content.Context
import android.util.Log
import com.snapfree.app.BuildConfig
import com.snapfree.app.data.AppSettings
import com.snapfree.app.data.CookieStore
import com.snapfree.app.data.SettingsRepository
import com.snapfree.app.data.UpdateChannel
import com.snapfree.app.util.Site
import com.snapfree.app.util.UrlUtils
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import com.yausername.youtubedl_common.SharedPrefsHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

sealed interface EngineState {
    data object Initializing : EngineState
    data class Ready(val version: String?) : EngineState
    data class Failed(val message: String) : EngineState
}

sealed interface UpdateResult {
    data class Updated(val version: String?) : UpdateResult
    data class AlreadyUpToDate(val version: String?) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/**
 * One way of asking YouTube for a video. yt-dlp's defaults come first; when YouTube rejects them
 * (HTTP 403, bot check, missing formats) other player clients are tried.
 *
 * @param youtubeClients value for `--extractor-args youtube:player_client=...`, null for yt-dlp defaults.
 */
data class Attempt(val index: Int, val youtubeClients: String?)

/**
 * Thin wrapper around youtubedl-android (yt-dlp + Python + FFmpeg).
 * Owns initialization, metadata extraction, command building and yt-dlp updates.
 */
class YtDlpEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val cookies: CookieStore,
) {
    private val _state = MutableStateFlow<EngineState>(EngineState.Initializing)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    /** Unpacks the bundled Python/yt-dlp/FFmpeg on first launch. Safe to call more than once. */
    fun initialize(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                seedBundledYtDlp()
                YoutubeDL.init(context)
                FFmpeg.init(context)
                _state.value = EngineState.Ready(version())
                maybeAutoUpdate()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize yt-dlp", t)
                _state.value = EngineState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    /** Suspends until the engine is ready, or throws if initialization failed. */
    suspend fun awaitReady() {
        when (val s = state.first { it !is EngineState.Initializing }) {
            is EngineState.Failed -> throw IllegalStateException(s.message)
            else -> Unit
        }
    }

    /** Installed yt-dlp version (release tag such as 2026.10.26). */
    fun version(): String = YoutubeDL.version(context) ?: BuildConfig.BUNDLED_YTDLP_VERSION

    suspend fun fetchInfo(rawUrl: String): MediaInfo {
        awaitReady()
        val url = UrlUtils.clean(rawUrl)
        return withRetries(url) { attempt ->
            val settings = settingsRepository.current()
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                applyNetworkOptions(settings, attempt)
            }
            val info = runInterruptible(Dispatchers.IO) { YoutubeDL.getInfo(request) }
            info.toMediaInfo(url)
        }
    }

    /**
     * Runs [block] and retries it when the failure is one that another attempt can fix:
     * 1. If yt-dlp has not been updated recently, update it once and repeat.
     * 2. On YouTube, clear yt-dlp's player cache and walk through [YOUTUBE_PLAN].
     * [onRetry] runs before every new attempt (e.g. to delete partial files).
     */
    suspend fun <T> withRetries(
        url: String,
        onRetry: suspend (Attempt, ErrorKind) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
        block: suspend (Attempt) -> T,
    ): T {
        awaitUpdateIdle()
        val settings = settingsRepository.current()
        val site = UrlUtils.siteOf(url)
        // Respect a player_client chosen by the user in the extra arguments.
        val plan = if (site == Site.YOUTUBE && !settings.extraArgs.contains("youtube:")) YOUTUBE_PLAN else listOf(null)
        var updated = false
        var index = 0
        var lastError: Throwable? = null
        while (index < plan.size) {
            val attempt = Attempt(index, plan[index])
            try {
                return block(attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: YoutubeDL.CanceledException) {
                throw e
            } catch (t: Throwable) {
                if (isCancelled()) throw t
                lastError = t
                val kind = ErrorKind.classify(cleanError(t), url)
                Log.w(TAG, "Attempt ${attempt.index} (${attempt.youtubeClients ?: "default"}) failed: $kind", t)
                if (!updated && kind.mayBeFixedByUpdate && updateIsStale()) {
                    updated = true
                    if (update(settingsRepository.current().updateChannel) is UpdateResult.Updated) {
                        onRetry(attempt, kind)
                        continue // same attempt with the new yt-dlp
                    }
                }
                if (site != Site.YOUTUBE || !kind.retryOnYouTube || index == plan.lastIndex) throw t
                if (index == 0) clearYouTubeCache()
                index++
                onRetry(Attempt(index, plan[index]), kind)
            }
        }
        throw lastError ?: IllegalStateException("No attempt was made")
    }

    /**
     * Builds the yt-dlp request for a download into [outputDir].
     * Files are written to a private temporary folder and copied to public storage afterwards.
     */
    fun buildDownloadRequest(url: String, spec: DownloadSpec, settings: AppSettings, outputDir: File, attempt: Attempt): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        request.addOption("--newline")
        request.addOption("--no-mtime")
        request.addOption("--restrict-filenames")
        request.addOption("-o", File(outputDir, "%(title).120B [%(id)s].%(ext)s").absolutePath)
        request.addOption("-N", settings.concurrentFragments)
        request.applyNetworkOptions(settings, attempt)
        if (settings.rateLimit.isNotBlank()) request.addOption("-r", settings.rateLimit)

        when (spec.type) {
            DownloadType.VIDEO -> {
                val sort = buildList {
                    if (spec.maxHeight > 0) add("res:${spec.maxHeight}")
                    when (spec.container) {
                        "mp4" -> { add("vcodec:h264"); add("acodec:m4a") }
                        "webm" -> { add("vcodec:vp9"); add("acodec:opus") }
                    }
                }
                request.addOption("-f", "bv*+ba/b")
                if (sort.isNotEmpty()) request.addOption("-S", sort.joinToString(","))
                when (spec.container) {
                    "mp4" -> {
                        request.addOption("--merge-output-format", "mp4")
                        request.addOption("--remux-video", "mp4")
                    }
                    "webm" -> request.addOption("--merge-output-format", "webm/mkv")
                    else -> request.addOption("--merge-output-format", "mkv")
                }
                if (settings.embedSubtitles && spec.container != "webm") {
                    request.addOption("--embed-subs")
                    request.addOption("--sub-langs", settings.subtitleLanguages.ifBlank { "en" })
                }
                if (settings.embedThumbnail && spec.container != "webm") request.addOption("--embed-thumbnail")
            }

            DownloadType.AUDIO -> {
                request.addOption("-f", "ba/b")
                request.addOption("-x")
                request.addOption("--audio-format", spec.audioFormat)
                if (FormatCatalog.usesBitrate(spec.audioFormat)) {
                    request.addOption("--audio-quality", "${spec.audioBitrate}K")
                } else {
                    request.addOption("--audio-quality", "0")
                }
                // WAV cannot hold cover art.
                if (settings.embedThumbnail && spec.audioFormat != "wav") request.addOption("--embed-thumbnail")
            }

            DownloadType.CUSTOM -> {
                val id = requireNotNull(spec.formatId)
                request.addOption("-f", if (spec.mergeAudio) "$id+ba/$id" else id)
            }
        }

        if (settings.embedMetadata) request.addOption("--embed-metadata")
        if (settings.sponsorBlock) request.addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
        if (settings.extraArgs.isNotBlank()) request.addCommands(splitArgs(settings.extraArgs))
        return request
    }

    suspend fun update(channel: UpdateChannel): UpdateResult = withContext(Dispatchers.IO) {
        if (_updating.value) return@withContext UpdateResult.Failed("Update already running")
        _updating.value = true
        try {
            awaitReady()
            val before = version()
            val result = try {
                val ytChannel = when (channel) {
                    UpdateChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
                    UpdateChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                }
                val status = YoutubeDL.updateYoutubeDL(context, ytChannel)
                if (status == YoutubeDL.UpdateStatus.DONE) UpdateResult.Updated(version())
                else UpdateResult.AlreadyUpToDate(version())
            } catch (t: Throwable) {
                // The library asks the GitHub API, which is rate limited per IP (common on mobile networks).
                // Fall back to the plain release download, which has no such limit.
                Log.w(TAG, "Library update failed, trying direct download", t)
                directUpdate(channel, before)
            }
            settingsRepository.setLastYtDlpUpdateCheck(System.currentTimeMillis())
            _state.value = EngineState.Ready(version())
            result
        } catch (t: Throwable) {
            Log.e(TAG, "yt-dlp update failed", t)
            UpdateResult.Failed(cleanError(t))
        } finally {
            _updating.value = false
        }
    }

    /** Downloads the latest yt-dlp release straight from GitHub release assets. */
    private fun directUpdate(channel: UpdateChannel, current: String?): UpdateResult {
        val repo = if (channel == UpdateChannel.NIGHTLY) "yt-dlp/yt-dlp-nightly-builds" else "yt-dlp/yt-dlp"
        // releases/latest redirects to releases/tag/<version>, which tells us the latest version without the API.
        val latestTag = (URL("https://github.com/$repo/releases/latest").openConnection() as HttpURLConnection).run {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 15_000
            try {
                getHeaderField("Location")?.substringAfterLast("/tag/")?.takeIf { it.isNotBlank() }
            } finally {
                disconnect()
            }
        }
        if (latestTag != null && current != null && compareVersions(current, latestTag) >= 0) {
            return UpdateResult.AlreadyUpToDate(current)
        }
        val tmp = File(context.cacheDir, "yt-dlp.download")
        val conn = URL("https://github.com/$repo/releases/latest/download/yt-dlp").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode != 200) error("GitHub returned HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        // The release asset is a Python zip app starting with a shebang line.
        val head = tmp.inputStream().use { it.readNBytes(2) }
        if (tmp.length() < 1_000_000 || !head.contentEquals("#!".toByteArray())) {
            tmp.delete()
            error("Downloaded yt-dlp looks invalid")
        }
        val target = File(ytDlpDir(), "yt-dlp")
        target.parentFile?.mkdirs()
        tmp.copyTo(target, overwrite = true)
        tmp.delete()
        val newVersion = latestTag ?: runVersion() ?: "?"
        SharedPrefsHelper.update(context, PREF_DLP_VERSION, newVersion)
        SharedPrefsHelper.update(context, PREF_DLP_VERSION_NAME, newVersion)
        return UpdateResult.Updated(newVersion)
    }

    private fun runVersion(): String? = runCatching {
        YoutubeDL.execute(YoutubeDLRequest(emptyList<String>()).apply { addOption("--version") }).out.trim()
            .lineSequence().lastOrNull { it.isNotBlank() }
    }.getOrNull()

    private suspend fun updateIsStale(): Boolean =
        System.currentTimeMillis() - settingsRepository.current().lastYtDlpUpdateCheck > TimeUnit.HOURS.toMillis(1)

    /** Waits (up to 2 minutes) for an update that is already running, so requests use the new yt-dlp. */
    private suspend fun awaitUpdateIdle() {
        withTimeoutOrNull(TimeUnit.MINUTES.toMillis(2)) { updating.first { !it } }
    }

    private suspend fun maybeAutoUpdate() {
        val settings = settingsRepository.current()
        val interval = TimeUnit.HOURS.toMillis(12)
        if (settings.autoUpdateYtDlp && System.currentTimeMillis() - settings.lastYtDlpUpdateCheck > interval) {
            update(settings.updateChannel)
        }
    }

    private fun ytDlpDir() = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp")

    /**
     * youtubedl-android only unpacks its bundled yt-dlp when none is installed yet, so an app update would
     * keep running the old copy. When this APK ships a newer yt-dlp than the installed one, replace it.
     */
    private suspend fun seedBundledYtDlp() {
        val bundled = BuildConfig.BUNDLED_YTDLP_VERSION
        if (settingsRepository.seededYtDlp() == bundled) return
        val installed = SharedPrefsHelper.get(context, PREF_DLP_VERSION)
        if (installed == null || compareVersions(installed, bundled) < 0) {
            ytDlpDir().deleteRecursively()
            SharedPrefsHelper.update(context, PREF_DLP_VERSION, bundled)
            SharedPrefsHelper.update(context, PREF_DLP_VERSION_NAME, bundled)
        }
        settingsRepository.setSeededYtDlp(bundled)
    }

    /**
     * yt-dlp cache (YouTube player code and solved JS challenges). Without it youtubedl-android passes
     * --no-cache-dir and every request solves the challenges again with QuickJS, which is slow on phones.
     */
    private val ytDlpCacheDir: File get() = File(context.cacheDir, "yt-dlp-cache")

    /** Removes yt-dlp's cached YouTube player data, which can go stale and cause 403s. */
    private fun clearYouTubeCache() {
        ytDlpCacheDir.deleteRecursively()
    }

    private fun YoutubeDLRequest.applyNetworkOptions(settings: AppSettings, attempt: Attempt) {
        if (settings.proxy.isNotBlank()) addOption("--proxy", settings.proxy)
        if (settings.userAgent.isNotBlank()) addOption("--user-agent", settings.userAgent)
        cookies.prepareForYtDlp(includeManual = settings.useCookies)?.let { addOption("--cookies", it.absolutePath) }
        attempt.youtubeClients?.let { addOption("--extractor-args", "youtube:player_client=$it") }
        addOption("--extractor-retries", 3)
        addOption("--cache-dir", ytDlpCacheDir.absolutePath)
    }

    private fun VideoInfo.toMediaInfo(originalUrl: String): MediaInfo {
        val items = formats.orEmpty().mapNotNull { f ->
            val id = f.formatId ?: return@mapNotNull null
            FormatItem(
                formatId = id,
                ext = f.ext ?: "?",
                height = f.height,
                fps = f.fps,
                vcodec = f.vcodec,
                acodec = f.acodec,
                note = f.formatNote,
                size = if (f.fileSize > 0) f.fileSize else f.fileSizeApproximate,
                bitrate = if (f.tbr > 0) f.tbr else f.abr,
            )
        }.filterNot { it.ext == "mhtml" } // storyboards

        val bestAudioSize = items.filter { it.isAudioOnly }.maxOfOrNull { it.size } ?: 0L
        val qualities = items
            .filter { it.hasVideo && it.height > 0 }
            .groupBy { it.height }
            .map { (height, list) ->
                val best = list.maxByOrNull { it.size } ?: list.first()
                val estimate = when {
                    best.size <= 0 -> 0L
                    best.isVideoOnly -> best.size + bestAudioSize
                    else -> best.size
                }
                VideoQuality(height = height, fps = list.maxOf { it.fps }, estimatedSize = estimate)
            }
            .sortedByDescending { it.height }
            .toMutableList()

        // Some extractors (e.g. TikTok, Instagram) return a single progressive format without a format list.
        if (qualities.isEmpty() && height > 0) {
            qualities += VideoQuality(height = height, fps = 0, estimatedSize = fileSize.takeIf { it > 0 } ?: fileSizeApproximate)
        }

        return MediaInfo(
            url = webpageUrl ?: originalUrl,
            title = title ?: fulltitle ?: id ?: originalUrl,
            thumbnailUrl = thumbnail ?: thumbnails?.lastOrNull()?.url,
            uploader = uploader,
            source = extractorKey ?: extractor,
            durationSeconds = duration,
            videoQualities = qualities,
            bestAudioSize = bestAudioSize,
            formats = items.sortedWith(compareByDescending<FormatItem> { it.hasVideo }.thenByDescending { it.height }.thenByDescending { it.bitrate }),
        )
    }

    companion object {
        private const val TAG = "YtDlpEngine"
        private const val PREF_DLP_VERSION = "dlpVersion"
        private const val PREF_DLP_VERSION_NAME = "dlpVersionName"

        /**
         * YouTube attempts, in order. The first two use yt-dlp's own defaults (the second after clearing the
         * player cache). The next ones use clients that need no PO token: TV clients, then visionos (which also
         * needs no JavaScript challenge), web_embedded and web_safari (its HLS formats need no PO token).
         * Client names that a future yt-dlp drops are skipped with a warning instead of failing.
         */
        val YOUTUBE_PLAN: List<String?> = listOf(
            null,
            null,
            "tv_samsung,tv,tv_downgraded",
            "visionos,web_embedded,web_safari",
        )

        /** Compares yt-dlp versions such as 2026.10.26 or 2026.11.20.232918. */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.').map { it.toLongOrNull() ?: 0L }
            val pb = b.split('.').map { it.toLongOrNull() ?: 0L }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val c = pa.getOrElse(i) { 0L }.compareTo(pb.getOrElse(i) { 0L })
                if (c != 0) return c
            }
            return 0
        }

        /** Extracts the most useful line from a yt-dlp error dump. */
        fun cleanError(t: Throwable): String {
            val raw = t.message ?: t.javaClass.simpleName
            val errorLine = raw.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("ERROR:") }
            return (errorLine?.removePrefix("ERROR:")?.trim() ?: raw.lineSequence().firstOrNull { it.isNotBlank() } ?: raw)
                .take(400)
        }

        /** Splits a command line into arguments, honoring single and double quotes. */
        fun splitArgs(input: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            var hasToken = false
            for (c in input) {
                when {
                    quote != null && c == quote -> quote = null
                    quote != null -> current.append(c)
                    c == '"' || c == '\'' -> { quote = c; hasToken = true }
                    c.isWhitespace() -> {
                        if (hasToken || current.isNotEmpty()) result += current.toString()
                        current.clear(); hasToken = false
                    }
                    else -> { current.append(c); hasToken = true }
                }
            }
            if (hasToken || current.isNotEmpty()) result += current.toString()
            return result
        }
    }
}
