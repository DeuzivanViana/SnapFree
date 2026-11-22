package com.snapfree.app.util

import android.net.Uri

enum class Site { YOUTUBE, INSTAGRAM, TIKTOK, OTHER }

object UrlUtils {
    private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /**
     * Query parameters that only identify who shared a link or where it was clicked, per site.
     * They never change which video is returned, so removing them is safe. Parameters that do
     * matter (v, list, t, index, story ids...) are kept.
     */
    private val YOUTUBE_TRACKING = setOf(
        "si", "feature", "pp", "ab_channel", "embeds_referring_euri", "embeds_referring_origin", "source_ve_path",
    )
    private val INSTAGRAM_TRACKING = setOf("igsh", "igshid", "img_index_unused")
    private val TIKTOK_TRACKING = setOf(
        "is_from_webapp", "sender_device", "sender_web_id", "is_copy_url", "share_app_id", "share_link_id",
        "share_item_id", "social_share_type", "tt_from", "u_code", "user_id", "sec_user_id", "timestamp",
        "checksum", "language", "_r", "_t", "_d", "preview_pb", "source", "refer", "web_id", "enter_from",
    )
    private val GENERIC_TRACKING = setOf("fbclid", "gclid", "mibextid", "igsh", "igshid")

    /** Finds the first http(s) URL in shared text such as "Check this out https://youtu.be/xyz". */
    fun extractUrl(text: String?): String? =
        text?.let { URL_REGEX.find(it)?.value?.trimEnd('.', ',', ')', ']') }

    fun isUrl(text: String): Boolean = URL_REGEX.matches(text.trim())

    fun siteOf(url: String): Site {
        val host = runCatching { Uri.parse(url.trim()).host }.getOrNull()?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return Site.OTHER
        return when {
            host == "youtu.be" || host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> Site.YOUTUBE
            host.endsWith("instagram.com") || host == "instagr.am" -> Site.INSTAGRAM
            host.endsWith("tiktok.com") -> Site.TIKTOK
            else -> Site.OTHER
        }
    }

    /**
     * Removes share-tracking parameters (e.g. YouTube `si=`, Instagram `igsh=`) and fragments.
     * Unknown sites only lose well-known click ids and utm_* parameters.
     */
    fun clean(url: String): String {
        val trimmed = extractUrl(url) ?: return url.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed
        if (uri.isOpaque) return trimmed
        val tracking = when (siteOf(trimmed)) {
            Site.YOUTUBE -> YOUTUBE_TRACKING
            Site.INSTAGRAM -> INSTAGRAM_TRACKING
            Site.TIKTOK -> TIKTOK_TRACKING
            Site.OTHER -> emptySet()
        }
        val names = runCatching { uri.queryParameterNames }.getOrDefault(emptySet())
        val builder = uri.buildUpon().clearQuery().fragment(null)
        for (name in names) {
            val lower = name.lowercase()
            if (lower in tracking || lower in GENERIC_TRACKING || lower.startsWith("utm_")) continue
            for (value in uri.getQueryParameters(name)) builder.appendQueryParameter(name, value)
        }
        return builder.build().toString()
    }
}
