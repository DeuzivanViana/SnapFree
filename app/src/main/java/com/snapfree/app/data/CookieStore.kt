package com.snapfree.app.data

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Sites the user can log in to inside the app. Cookies captured from the WebView are stored per site. */
enum class LoginSite(
    val key: String,
    val loginUrl: String,
    /** URLs whose cookies are exported for yt-dlp. */
    val cookieUrls: List<String>,
    /** Netscape domain written for each URL above (same order). */
    val cookieDomains: List<String>,
    /** A cookie that only exists after a successful login. */
    val sessionCookie: String,
) {
    INSTAGRAM(
        key = "instagram",
        loginUrl = "https://www.instagram.com/accounts/login/",
        cookieUrls = listOf("https://www.instagram.com"),
        cookieDomains = listOf(".instagram.com"),
        sessionCookie = "sessionid",
    ),
    YOUTUBE(
        key = "youtube",
        loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F",
        cookieUrls = listOf("https://www.youtube.com", "https://m.youtube.com"),
        cookieDomains = listOf(".youtube.com", ".youtube.com"),
        sessionCookie = "LOGIN_INFO",
    ),
    TIKTOK(
        key = "tiktok",
        loginUrl = "https://www.tiktok.com/login",
        cookieUrls = listOf("https://www.tiktok.com"),
        cookieDomains = listOf(".tiktok.com"),
        sessionCookie = "sessionid",
    ),
}

/**
 * Keeps the cookies handed to yt-dlp.
 *
 * - Manual cookies: a Netscape cookies.txt pasted or imported by the user (opt-in through the "Use cookies" switch).
 * - Login cookies: captured from the in-app WebView login, one file per [LoginSite]. Always used while present.
 *
 * Before each yt-dlp run everything is merged into a single file because yt-dlp accepts only one `--cookies` path.
 */
class CookieStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "cookies").apply { mkdirs() }

    /** Legacy location kept for manual cookies so files imported in 0.1.0 keep working. */
    val manualFile: File get() = File(context.filesDir, "cookies.txt")

    /** Each yt-dlp run gets its own copy because yt-dlp rewrites the cookie file when it exits. */
    private val runDir: File get() = File(context.cacheDir, "ytdlp-cookies").apply { mkdirs() }

    private fun siteFile(site: LoginSite) = File(dir, "${site.key}.txt")

    private val _loggedIn = MutableStateFlow(LoginSite.entries.filter { siteFile(it).length() > 0 }.toSet())
    val loggedIn: StateFlow<Set<LoginSite>> = _loggedIn.asStateFlow()

    fun hasManual(): Boolean = manualFile.exists() && manualFile.length() > 0

    fun isLoggedIn(site: LoginSite): Boolean = site in _loggedIn.value

    fun saveManual(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) {
            manualFile.delete()
            return
        }
        // yt-dlp requires the Netscape header line to accept the file.
        val content = if (normalized.startsWith(HEADER) || normalized.startsWith("# HTTP Cookie File")) {
            normalized
        } else {
            "$HEADER\n$normalized"
        }
        manualFile.writeText(content + "\n")
    }

    fun clearManual() {
        manualFile.delete()
    }

    /** True when the WebView currently holds a logged-in session for [site]. */
    fun webViewHasSession(site: LoginSite): Boolean {
        val manager = CookieManager.getInstance()
        return site.cookieUrls.any { url ->
            parse(manager.getCookie(url)).any { it.first == site.sessionCookie && it.second.isNotBlank() }
        }
    }

    /**
     * Exports the WebView cookies of [site] to Netscape format. CookieManager does not expose expiry dates or
     * flags, so cookies are written as secure, valid for one year, which is what yt-dlp needs.
     * Returns false when no login cookie was found.
     */
    fun captureFromWebView(site: LoginSite): Boolean {
        val manager = CookieManager.getInstance()
        manager.flush()
        val expiry = (System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365)) / 1000
        val lines = linkedSetOf<String>()
        var hasSession = false
        site.cookieUrls.forEachIndexed { index, url ->
            val domain = site.cookieDomains[index]
            for ((name, value) in parse(manager.getCookie(url))) {
                if (name == site.sessionCookie && value.isNotBlank()) hasSession = true
                lines += listOf(domain, "TRUE", "/", "TRUE", expiry.toString(), name, value).joinToString("\t")
            }
        }
        if (!hasSession) return false
        siteFile(site).writeText(HEADER + "\n" + lines.joinToString("\n") + "\n")
        _loggedIn.value = _loggedIn.value + site
        return true
    }

    /** Forgets the stored cookies of [site] and signs the WebView out of it. */
    fun logout(site: LoginSite) {
        siteFile(site).delete()
        _loggedIn.value = _loggedIn.value - site
        val manager = CookieManager.getInstance()
        site.cookieUrls.forEachIndexed { index, url ->
            val domain = site.cookieDomains[index]
            for ((name, _) in parse(manager.getCookie(url))) {
                manager.setCookie(url, "$name=; Max-Age=0; Domain=$domain; Path=/")
                manager.setCookie(url, "$name=; Max-Age=0; Path=/")
            }
        }
        manager.flush()
    }

    /**
     * Writes the cookies yt-dlp should use and returns the file, or null when there are none.
     * [includeManual] reflects the "Use cookies" setting.
     */
    fun prepareForYtDlp(includeManual: Boolean): File? {
        val parts = buildList {
            LoginSite.entries.map(::siteFile).filter { it.length() > 0 }.forEach { add(it.readText()) }
            if (includeManual && hasManual()) add(manualFile.readText())
        }
        if (parts.isEmpty()) return null
        val body = parts.flatMap { it.lineSequence() }
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && !it.startsWith("# ") && it != "#" }
            .distinct()
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(6)
        runDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val file = File(runDir, "${UUID.randomUUID()}.txt")
        file.writeText(HEADER + "\n" + body.joinToString("\n") + "\n")
        return file
    }

    private fun parse(header: String?): List<Pair<String, String>> =
        header.orEmpty().split(';').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.substring(0, index).trim() to part.substring(index + 1).trim()
        }

    companion object {
        private const val HEADER = "# Netscape HTTP Cookie File"
    }
}
