package com.snapfree.app.ui.components

import androidx.annotation.StringRes
import com.snapfree.app.R
import com.snapfree.app.data.LoginSite
import com.snapfree.app.engine.ErrorKind
import com.snapfree.app.util.Site
import com.snapfree.app.util.UrlUtils

/** A yt-dlp error translated into a friendly title, a hint and, when it helps, a site to log in to. */
data class ErrorExplanation(
    val kind: ErrorKind,
    @StringRes val title: Int,
    @StringRes val hint: Int,
    val loginSite: LoginSite?,
)

fun explainError(message: String?, url: String?): ErrorExplanation {
    val kind = ErrorKind.classify(message, url)
    val siteLogin = when (url?.let(UrlUtils::siteOf)) {
        Site.YOUTUBE -> LoginSite.YOUTUBE
        Site.INSTAGRAM -> LoginSite.INSTAGRAM
        Site.TIKTOK -> LoginSite.TIKTOK
        else -> null
    }
    return when (kind) {
        ErrorKind.INSTAGRAM_LOGIN -> ErrorExplanation(kind, R.string.err_instagram_login_title, R.string.err_instagram_login_hint, LoginSite.INSTAGRAM)
        ErrorKind.YOUTUBE_BOT_CHECK -> ErrorExplanation(kind, R.string.err_youtube_bot_title, R.string.err_youtube_bot_hint, LoginSite.YOUTUBE)
        ErrorKind.YOUTUBE_FORBIDDEN -> ErrorExplanation(kind, R.string.err_youtube_403_title, R.string.err_youtube_403_hint, LoginSite.YOUTUBE)
        ErrorKind.AGE_RESTRICTED -> ErrorExplanation(kind, R.string.err_age_title, R.string.err_age_hint, siteLogin)
        ErrorKind.LOGIN_REQUIRED -> ErrorExplanation(kind, R.string.err_login_title, R.string.err_login_hint, siteLogin)
        ErrorKind.PRIVATE -> ErrorExplanation(kind, R.string.err_private_title, R.string.err_private_hint, siteLogin)
        ErrorKind.UNAVAILABLE -> ErrorExplanation(kind, R.string.err_unavailable_title, R.string.err_unavailable_hint, null)
        ErrorKind.GEO_BLOCKED -> ErrorExplanation(kind, R.string.err_geo_title, R.string.err_geo_hint, null)
        ErrorKind.UNSUPPORTED_URL -> ErrorExplanation(kind, R.string.err_unsupported_title, R.string.err_unsupported_hint, null)
        ErrorKind.NETWORK -> ErrorExplanation(kind, R.string.err_network_title, R.string.err_network_hint, null)
        ErrorKind.RATE_LIMITED -> ErrorExplanation(kind, R.string.err_rate_title, R.string.err_rate_hint, siteLogin)
        ErrorKind.FORMAT_UNAVAILABLE -> ErrorExplanation(kind, R.string.err_format_title, R.string.err_format_hint, null)
        ErrorKind.OTHER -> ErrorExplanation(kind, R.string.home_error_title, R.string.home_error_hint, null)
    }
}
