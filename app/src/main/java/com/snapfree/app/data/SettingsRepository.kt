package com.snapfree.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Format preselected on the format sheet after a link is analyzed. */
enum class DefaultMode { VIDEO, AUDIO }

enum class SaveLocation {
    /** Download/<subfolder> */
    DOWNLOADS,

    /** Movies/<subfolder> for video and Music/<subfolder> for audio, so files show up in gallery and music apps. */
    MEDIA,
}

enum class UpdateChannel { STABLE, NIGHTLY }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val defaultMode: DefaultMode = DefaultMode.VIDEO,
    val videoContainer: String = "mp4",
    /** Maximum video height; 0 means best available. */
    val maxHeight: Int = 1080,
    val audioFormat: String = "mp3",
    /** Audio bitrate in kbps for lossy conversions. */
    val audioBitrate: Int = 192,
    val saveLocation: SaveLocation = SaveLocation.DOWNLOADS,
    val subfolder: String = "SnapFree",
    val concurrentDownloads: Int = 2,
    val concurrentFragments: Int = 4,
    val wifiOnly: Boolean = false,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,
    val embedSubtitles: Boolean = false,
    val subtitleLanguages: String = "en,pt",
    val sponsorBlock: Boolean = false,
    val proxy: String = "",
    val rateLimit: String = "",
    val userAgent: String = "",
    val useCookies: Boolean = false,
    val extraArgs: String = "",
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    /** On by default: YouTube and Instagram change often and an outdated yt-dlp is the most common cause of errors. */
    val autoUpdateYtDlp: Boolean = true,
    val lastYtDlpUpdateCheck: Long = 0,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val pureBlack = booleanPreferencesKey("pure_black")
        val defaultMode = stringPreferencesKey("default_mode")
        val videoContainer = stringPreferencesKey("video_container")
        val maxHeight = intPreferencesKey("max_height")
        val audioFormat = stringPreferencesKey("audio_format")
        val audioBitrate = intPreferencesKey("audio_bitrate")
        val saveLocation = stringPreferencesKey("save_location")
        val subfolder = stringPreferencesKey("subfolder")
        val concurrentDownloads = intPreferencesKey("concurrent_downloads")
        val concurrentFragments = intPreferencesKey("concurrent_fragments")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val embedMetadata = booleanPreferencesKey("embed_metadata")
        val embedThumbnail = booleanPreferencesKey("embed_thumbnail")
        val embedSubtitles = booleanPreferencesKey("embed_subtitles")
        val subtitleLanguages = stringPreferencesKey("subtitle_languages")
        val sponsorBlock = booleanPreferencesKey("sponsorblock")
        val proxy = stringPreferencesKey("proxy")
        val rateLimit = stringPreferencesKey("rate_limit")
        val userAgent = stringPreferencesKey("user_agent")
        val useCookies = booleanPreferencesKey("use_cookies")
        val extraArgs = stringPreferencesKey("extra_args")
        val updateChannel = stringPreferencesKey("update_channel")
        // New key in 0.2.0 so auto update is switched on for existing installs.
        val autoUpdateYtDlp = booleanPreferencesKey("auto_update_ytdlp_v2")
        val seededYtDlp = stringPreferencesKey("seeded_ytdlp")
        val lastYtDlpUpdateCheck = stringPreferencesKey("last_ytdlp_update_check")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            themeMode = p[Keys.themeMode].toEnum(d.themeMode),
            pureBlack = p[Keys.pureBlack] ?: d.pureBlack,
            defaultMode = p[Keys.defaultMode].toEnum(d.defaultMode),
            videoContainer = p[Keys.videoContainer] ?: d.videoContainer,
            maxHeight = p[Keys.maxHeight] ?: d.maxHeight,
            audioFormat = p[Keys.audioFormat] ?: d.audioFormat,
            audioBitrate = p[Keys.audioBitrate] ?: d.audioBitrate,
            saveLocation = p[Keys.saveLocation].toEnum(d.saveLocation),
            subfolder = p[Keys.subfolder] ?: d.subfolder,
            concurrentDownloads = p[Keys.concurrentDownloads] ?: d.concurrentDownloads,
            concurrentFragments = p[Keys.concurrentFragments] ?: d.concurrentFragments,
            wifiOnly = p[Keys.wifiOnly] ?: d.wifiOnly,
            embedMetadata = p[Keys.embedMetadata] ?: d.embedMetadata,
            embedThumbnail = p[Keys.embedThumbnail] ?: d.embedThumbnail,
            embedSubtitles = p[Keys.embedSubtitles] ?: d.embedSubtitles,
            subtitleLanguages = p[Keys.subtitleLanguages] ?: d.subtitleLanguages,
            sponsorBlock = p[Keys.sponsorBlock] ?: d.sponsorBlock,
            proxy = p[Keys.proxy] ?: d.proxy,
            rateLimit = p[Keys.rateLimit] ?: d.rateLimit,
            userAgent = p[Keys.userAgent] ?: d.userAgent,
            useCookies = p[Keys.useCookies] ?: d.useCookies,
            extraArgs = p[Keys.extraArgs] ?: d.extraArgs,
            updateChannel = p[Keys.updateChannel].toEnum(d.updateChannel),
            autoUpdateYtDlp = p[Keys.autoUpdateYtDlp] ?: d.autoUpdateYtDlp,
            lastYtDlpUpdateCheck = p[Keys.lastYtDlpUpdateCheck]?.toLongOrNull() ?: d.lastYtDlpUpdateCheck,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(value: ThemeMode) = put(Keys.themeMode, value.name)
    suspend fun setPureBlack(value: Boolean) = put(Keys.pureBlack, value)
    suspend fun setDefaultMode(value: DefaultMode) = put(Keys.defaultMode, value.name)
    suspend fun setVideoContainer(value: String) = put(Keys.videoContainer, value)
    suspend fun setMaxHeight(value: Int) = put(Keys.maxHeight, value)
    suspend fun setAudioFormat(value: String) = put(Keys.audioFormat, value)
    suspend fun setAudioBitrate(value: Int) = put(Keys.audioBitrate, value)
    suspend fun setSaveLocation(value: SaveLocation) = put(Keys.saveLocation, value.name)
    suspend fun setSubfolder(value: String) = put(Keys.subfolder, value.trim().trim('/'))
    suspend fun setConcurrentDownloads(value: Int) = put(Keys.concurrentDownloads, value.coerceIn(1, 5))
    suspend fun setConcurrentFragments(value: Int) = put(Keys.concurrentFragments, value.coerceIn(1, 16))
    suspend fun setWifiOnly(value: Boolean) = put(Keys.wifiOnly, value)
    suspend fun setEmbedMetadata(value: Boolean) = put(Keys.embedMetadata, value)
    suspend fun setEmbedThumbnail(value: Boolean) = put(Keys.embedThumbnail, value)
    suspend fun setEmbedSubtitles(value: Boolean) = put(Keys.embedSubtitles, value)
    suspend fun setSubtitleLanguages(value: String) = put(Keys.subtitleLanguages, value.trim())
    suspend fun setSponsorBlock(value: Boolean) = put(Keys.sponsorBlock, value)
    suspend fun setProxy(value: String) = put(Keys.proxy, value.trim())
    suspend fun setRateLimit(value: String) = put(Keys.rateLimit, value.trim())
    suspend fun setUserAgent(value: String) = put(Keys.userAgent, value.trim())
    suspend fun setUseCookies(value: Boolean) = put(Keys.useCookies, value)
    suspend fun setExtraArgs(value: String) = put(Keys.extraArgs, value.trim())
    suspend fun setUpdateChannel(value: UpdateChannel) = put(Keys.updateChannel, value.name)
    suspend fun setAutoUpdateYtDlp(value: Boolean) = put(Keys.autoUpdateYtDlp, value)
    suspend fun setLastYtDlpUpdateCheck(value: Long) = put(Keys.lastYtDlpUpdateCheck, value.toString())

    suspend fun seededYtDlp(): String? = context.dataStore.data.first()[Keys.seededYtDlp]
    suspend fun setSeededYtDlp(value: String) = put(Keys.seededYtDlp, value)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
