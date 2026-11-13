package com.robertlangat.socialvideodownloader.data

import java.net.URI

enum class Platform(val label: String, val apiName: String) {
    TIKTOK("TikTok", "tiktok"),
    INSTAGRAM("Instagram", "instagram"),
    FACEBOOK("Facebook", "facebook");

    companion object {
        fun fromApiName(name: String?): Platform? = entries.firstOrNull { it.apiName == name }
    }
}

/** A share link that was recognised as a video/reel link of a supported platform. */
data class ParsedLink(val platform: Platform, val url: String)

/**
 * Recognises video share links for TikTok, Instagram and Facebook.
 *
 * Only the *shape* of the link is checked here; whether the post exists, is public
 * and may be downloaded is decided by the backend resolver.
 */
object LinkParser {

    private val urlInText = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val bareDomainInText = Regex(
        """(?:^|\s)((?:www\.|m\.|web\.|vm\.|vt\.)?(?:tiktok\.com|instagram\.com|instagr\.am|facebook\.com|fb\.watch|fb\.com)/[^\s<>"']*)""",
        RegexOption.IGNORE_CASE,
    )

    private val digits = Regex("""\d{5,}""")
    private val igCode = Regex("""[A-Za-z0-9_-]{5,}""")
    private val shortCode = Regex("""[A-Za-z0-9_-]{3,}""")

    /** Pulls the first URL out of arbitrary share text ("Check this out! https://..."). */
    fun extractUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val match = urlInText.find(trimmed)?.value
            ?: bareDomainInText.find(trimmed)?.groupValues?.get(1)?.let { "https://$it" }
            ?: return null
        return match.trimEnd('.', ',', ')', ']', '!', '?', ';', ':')
    }

    fun parse(text: String): ParsedLink? {
        val raw = extractUrl(text) ?: return null
        val uri = try {
            URI(raw)
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val fullHost = uri.host?.lowercase() ?: return null
        val host = stripSubdomain(fullHost)
        val segments = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }
        val query = parseQuery(uri.rawQuery)

        val platform = when {
            host == "tiktok.com" || fullHost.endsWith(".tiktok.com") ->
                if (isTikTokVideo(fullHost, segments)) Platform.TIKTOK else null

            host == "instagram.com" || host == "instagr.am" ->
                if (isInstagramVideo(segments)) Platform.INSTAGRAM else null

            host == "facebook.com" || host == "fb.com" || host == "fb.watch" ->
                if (isFacebookVideo(host, segments, query)) Platform.FACEBOOK else null

            else -> null
        } ?: return null

        val normalised = if (scheme == "http") "https" + raw.substring(4) else raw
        return ParsedLink(platform, normalised)
    }

    private fun stripSubdomain(host: String): String {
        val prefixes = listOf("www.", "m.", "web.", "mobile.", "mbasic.", "touch.")
        var h = host
        for (p in prefixes) if (h.startsWith(p)) h = h.removePrefix(p)
        return h
    }

    private fun isTikTokVideo(fullHost: String, s: List<String>): Boolean {
        // Short links: https://vm.tiktok.com/ZMabc123/ , https://vt.tiktok.com/ZSabc/
        if (fullHost.startsWith("vm.") || fullHost.startsWith("vt.")) {
            return s.size == 1 && shortCode.matches(s[0])
        }
        if (s.isEmpty()) return false
        // https://www.tiktok.com/@user/video/7234567890123456789
        val videoIdx = s.indexOf("video")
        if (videoIdx >= 0 && videoIdx + 1 < s.size && digits.matches(s[videoIdx + 1])) return true
        // https://www.tiktok.com/t/ZTabc123/
        if (s[0] == "t" && s.size >= 2 && shortCode.matches(s[1])) return true
        // https://m.tiktok.com/v/7234567890123456789.html
        if (s[0] == "v" && s.size >= 2 && digits.matches(s[1].removeSuffix(".html"))) return true
        // https://www.tiktok.com/embed/v2/7234567890123456789
        if (s[0] == "embed") return s.drop(1).any { digits.matches(it) }
        return false
    }

    private fun isInstagramVideo(s: List<String>): Boolean {
        if (s.isEmpty()) return false
        val kinds = setOf("p", "reel", "reels", "tv")
        // /reel/{code}, /p/{code}, /tv/{code}
        if (s[0] in kinds) return s.size >= 2 && igCode.matches(s[1])
        // /share/reel/{code}, /share/p/{code}
        if (s[0] == "share") {
            return if (s.size >= 3 && s[1] in kinds) igCode.matches(s[2])
            else s.size >= 2 && igCode.matches(s[1])
        }
        // /{username}/reel/{code}, /{username}/p/{code}
        if (s.size >= 3 && s[1] in kinds) return igCode.matches(s[2])
        return false
    }

    private fun isFacebookVideo(host: String, s: List<String>, q: Map<String, String>): Boolean {
        // https://fb.watch/abcDEF123/
        if (host == "fb.watch") return s.size == 1 && shortCode.matches(s[0])
        if (s.isEmpty()) return false
        // /watch?v=123, /watch/?v=123, /video.php?v=123
        if ((s[0] == "watch" || s[0] == "video.php") && q["v"]?.let { digits.matches(it) } == true) return true
        // /reel/123
        if (s[0] == "reel") return s.size >= 2 && digits.matches(s[1])
        // /share/v/{code}, /share/r/{code}
        if (s[0] == "share" && s.size >= 3 && (s[1] == "v" || s[1] == "r")) return shortCode.matches(s[2])
        // /{page}/videos/123, /{page}/videos/{slug}/123, /videos/123
        val idx = s.indexOf("videos")
        if (idx >= 0) return s.drop(idx + 1).any { digits.matches(it) }
        return false
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
        }.toMap()
    }
}
