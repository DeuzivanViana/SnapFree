package com.snapfree.app.util

/** Result of checking the text typed or pasted in the link field. */
sealed interface LinkCheck {
    data object Empty : LinkCheck

    /**
     * Text that cannot be analyzed yet: not a link at all ([site] is null), or a link to a known
     * site that is still missing the part that identifies the media (e.g. `youtube.com/watch?v=ab`).
     */
    data class Incomplete(val site: Site?, val looksLikeLink: Boolean) : LinkCheck

    /**
     * A link complete enough to analyze.
     *
     * @param url the link with an explicit scheme, extracted from any surrounding text.
     * @param key identifies the media itself, so two links to the same video share a key
     *   (youtu.be/x and youtube.com/watch?v=x&si=...) and are analyzed only once.
     * @param confident true when the link matches a known media pattern of a supported site.
     *   Links to other sites are only "plausible" and get a longer debounce while typing.
     * @param label site name, or the host for other sites, shown in the UI.
     */
    data class Complete(
        val url: String,
        val site: Site,
        val key: String,
        val confident: Boolean,
        val label: String,
    ) : LinkCheck
}

/**
 * Decides, without any network request, whether the text in the link field is a complete media link.
 * Pure Kotlin (no android.net.Uri) so it can be unit tested on the JVM.
 */
object LinkDetector {
    private val HTTP_URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val BARE_URL = Regex("""^(?:[a-z0-9-]+\.)+[a-z]{2,}(?::\d+)?(?:[/?#]\S*)?$""", RegexOption.IGNORE_CASE)
    private val HOST = Regex("""^(?:[a-z0-9-]+\.)+[a-z]{2,}(?::\d+)?$""", RegexOption.IGNORE_CASE)

    private val YT_ID = Regex("""^[A-Za-z0-9_-]{11}$""")
    private val YT_PLAYLIST = Regex("""^[A-Za-z0-9_-]{13,}$""")
    private val IG_CODE = Regex("""^[A-Za-z0-9_-]{5,}$""")
    private val DIGITS = Regex("""^\d{5,}$""")
    private val TT_VIDEO_ID = Regex("""^\d{10,}$""")
    private val TT_SHORT = Regex("""^[A-Za-z0-9_-]{5,}$""")

    private class Parts(val url: String, val host: String, val segments: List<String>, val query: Map<String, String>)

    /**
     * @param lenient when true (explicit submit, shared link), any URL-like text with a host counts as
     *   complete, even a supported site link that does not match a known media pattern. yt-dlp then decides.
     */
    fun check(text: String, lenient: Boolean = false): LinkCheck {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return LinkCheck.Empty
        val parts = parse(trimmed) ?: return LinkCheck.Incomplete(site = null, looksLikeLink = trimmed.contains('.') || trimmed.contains("://"))
        val site = siteOf(parts.host)
        val key = when (site) {
            Site.YOUTUBE -> youtubeKey(parts)
            Site.INSTAGRAM -> instagramKey(parts)
            Site.TIKTOK -> tiktokKey(parts)
            Site.OTHER -> null
        }
        val label = when (site) {
            Site.YOUTUBE -> "YouTube"
            Site.INSTAGRAM -> "Instagram"
            Site.TIKTOK -> "TikTok"
            Site.OTHER -> parts.host
        }
        if (key != null) return LinkCheck.Complete(parts.url, site, key, confident = true, label = label)

        val hasPath = parts.segments.isNotEmpty() || parts.query.isNotEmpty()
        return when {
            lenient || (site == Site.OTHER && hasPath) ->
                LinkCheck.Complete(parts.url, site, genericKey(parts), confident = false, label = label)
            else -> LinkCheck.Incomplete(site = site, looksLikeLink = true)
        }
    }

    private fun parse(text: String): Parts? {
        val raw = HTTP_URL.find(text)?.value
            ?: text.takeIf { !it.contains(' ') && BARE_URL.matches(it) }?.let { "https://$it" }
            ?: return null
        val url = raw.trimEnd('.', ',', ')', ']', '!', '?', ';', ':')
        val afterScheme = url.substringAfter("://")
        val hostEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) afterScheme.length else it }
        val hostPort = afterScheme.substring(0, hostEnd).substringAfterLast('@')
        if (!HOST.matches(hostPort)) return null
        val host = hostPort.substringBefore(':').lowercase()
        val rest = afterScheme.substring(hostEnd).substringBefore('#')
        val path = rest.substringBefore('?')
        val queryString = if (rest.contains('?')) rest.substringAfter('?') else ""
        val segments = path.split('/').filter { it.isNotEmpty() }
        val query = queryString.split('&').filter { it.isNotEmpty() }.associate { pair ->
            pair.substringBefore('=').lowercase() to pair.substringAfter('=', "")
        }
        return Parts(url, host, segments, query)
    }

    private fun bareHost(host: String) = host.removePrefix("www.").removePrefix("m.")

    fun siteOf(host: String): Site {
        val h = bareHost(host.lowercase())
        return when {
            h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com") || h.endsWith("youtube-nocookie.com") -> Site.YOUTUBE
            h == "instagram.com" || h.endsWith(".instagram.com") || h == "instagr.am" -> Site.INSTAGRAM
            h == "tiktok.com" || h.endsWith(".tiktok.com") -> Site.TIKTOK
            else -> Site.OTHER
        }
    }

    private fun youtubeKey(p: Parts): String? {
        val s = p.segments
        if (bareHost(p.host) == "youtu.be") return s.firstOrNull()?.takeIf { YT_ID.matches(it) }?.let { "yt:$it" }
        val first = s.firstOrNull()?.lowercase()
        p.query["v"]?.takeIf { (first == "watch" || first == null) && YT_ID.matches(it) }?.let { return "yt:$it" }
        if (first in setOf("shorts", "live", "embed", "v", "e") && s.size >= 2 && YT_ID.matches(s[1])) return "yt:${s[1]}"
        if (first == "playlist") p.query["list"]?.takeIf { YT_PLAYLIST.matches(it) }?.let { return "ytlist:$it" }
        return null
    }

    private fun instagramKey(p: Parts): String? {
        val s = p.segments
        // /p/CODE, /reel/CODE, /reels/CODE, /tv/CODE, also /username/reel/CODE and /share/reel/CODE.
        val index = s.indexOfFirst { it.lowercase() in setOf("p", "reel", "reels", "tv") }
        if (index >= 0 && index + 1 < s.size && IG_CODE.matches(s[index + 1])) {
            val shared = s.firstOrNull()?.lowercase() == "share"
            return (if (shared) "igshare:" else "ig:") + s[index + 1]
        }
        // /share/CODE (no type segment).
        if (s.size == 2 && s[0].lowercase() == "share" && IG_CODE.matches(s[1])) return "igshare:${s[1]}"
        // /stories/username/123456789
        if (s.size >= 3 && s[0].lowercase() == "stories" && DIGITS.matches(s[2])) return "igstory:${s[2]}"
        return null
    }

    private fun tiktokKey(p: Parts): String? {
        val s = p.segments
        val h = bareHost(p.host)
        // Short share links: vm.tiktok.com/CODE, vt.tiktok.com/CODE, tiktok.com/t/CODE.
        if ((h == "vm.tiktok.com" || h == "vt.tiktok.com") && s.size == 1 && TT_SHORT.matches(s[0])) return "ttshort:${s[0]}"
        if (s.size == 2 && s[0].lowercase() == "t" && TT_SHORT.matches(s[1])) return "ttshort:${s[1]}"
        // /@user/video/ID, /@user/photo/ID
        if (s.size >= 3 && s[0].startsWith("@") && s[1].lowercase() in setOf("video", "photo") && TT_VIDEO_ID.matches(s[2])) return "tt:${s[2]}"
        // m.tiktok.com/v/ID.html, /embed/v2/ID, /embed/ID
        if (s.size >= 2 && s[0].lowercase() == "v" && TT_VIDEO_ID.matches(s[1].removeSuffix(".html"))) return "tt:${s[1].removeSuffix(".html")}"
        if (s.isNotEmpty() && s[0].lowercase() == "embed" && s.lastOrNull()?.let { TT_VIDEO_ID.matches(it) } == true) return "tt:${s.last()}"
        return null
    }

    private val GENERIC_TRACKING_PREFIXES = listOf("utm_")
    private val GENERIC_TRACKING = setOf("fbclid", "gclid", "mibextid", "igsh", "igshid", "si")

    private fun genericKey(p: Parts): String {
        val query = p.query
            .filterKeys { k -> k !in GENERIC_TRACKING && GENERIC_TRACKING_PREFIXES.none { k.startsWith(it) } }
            .toSortedMap()
            .entries.joinToString("&") { "${it.key}=${it.value}" }
        return "url:" + bareHost(p.host) + "/" + p.segments.joinToString("/") + if (query.isEmpty()) "" else "?$query"
    }
}
