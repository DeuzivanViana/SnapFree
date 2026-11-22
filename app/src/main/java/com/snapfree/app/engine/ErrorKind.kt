package com.snapfree.app.engine

import com.snapfree.app.util.Site
import com.snapfree.app.util.UrlUtils

/** What went wrong, in terms the UI can explain and act on. */
enum class ErrorKind(
    /** An older yt-dlp often causes this; updating may fix it. */
    val mayBeFixedByUpdate: Boolean,
    /** Worth retrying YouTube with other player clients. */
    val retryOnYouTube: Boolean,
) {
    INSTAGRAM_LOGIN(mayBeFixedByUpdate = true, retryOnYouTube = false),
    YOUTUBE_BOT_CHECK(mayBeFixedByUpdate = true, retryOnYouTube = true),
    YOUTUBE_FORBIDDEN(mayBeFixedByUpdate = true, retryOnYouTube = true),
    AGE_RESTRICTED(mayBeFixedByUpdate = false, retryOnYouTube = true),
    LOGIN_REQUIRED(mayBeFixedByUpdate = false, retryOnYouTube = false),
    PRIVATE(mayBeFixedByUpdate = false, retryOnYouTube = false),
    UNAVAILABLE(mayBeFixedByUpdate = false, retryOnYouTube = false),
    GEO_BLOCKED(mayBeFixedByUpdate = false, retryOnYouTube = false),
    UNSUPPORTED_URL(mayBeFixedByUpdate = true, retryOnYouTube = false),
    NETWORK(mayBeFixedByUpdate = false, retryOnYouTube = false),
    RATE_LIMITED(mayBeFixedByUpdate = false, retryOnYouTube = true),
    FORMAT_UNAVAILABLE(mayBeFixedByUpdate = true, retryOnYouTube = true),
    OTHER(mayBeFixedByUpdate = true, retryOnYouTube = true),
    ;

    companion object {
        fun classify(message: String?, url: String?): ErrorKind {
            val m = message.orEmpty().lowercase()
            val site = url?.let(UrlUtils::siteOf) ?: Site.OTHER
            fun has(vararg needles: String) = needles.any { m.contains(it) }

            return when {
                has("unable to resolve host", "failed to resolve", "getaddrinfo", "network is unreachable",
                    "no address associated", "timed out", "connection reset", "connection refused",
                    "temporary failure in name resolution", "nodename nor servname", "unable to connect",
                    "errno 7", "errno -2", "errno 101") -> NETWORK
                has("unsupported url") -> UNSUPPORTED_URL
                site == Site.INSTAGRAM && has("login required", "rate-limit reached", "--cookies",
                    "requested content is not available", "login_required", "checkpoint") -> INSTAGRAM_LOGIN
                has("sign in to confirm you", "not a bot") -> YOUTUBE_BOT_CHECK
                has("confirm your age", "age-restricted", "age restricted", "inappropriate for some users") -> AGE_RESTRICTED
                has("http error 429", "too many requests") -> RATE_LIMITED
                has("not available in your country", "geo restriction", "geo-restricted", "blocked it in your country") -> GEO_BLOCKED
                has("private video", "this account is private", "video is private", "is private") -> PRIVATE
                has("members-only", "join this channel", "login required", "--cookies", "log in", "sign in") -> LOGIN_REQUIRED
                has("requested format is not available", "only images are available") -> FORMAT_UNAVAILABLE
                has("http error 403", "forbidden") -> if (site == Site.YOUTUBE) YOUTUBE_FORBIDDEN else OTHER
                has("video unavailable", "has been removed", "no longer available", "does not exist", "http error 404",
                    "this video is not available", "post isn't available") -> UNAVAILABLE
                else -> OTHER
            }
        }
    }
}
