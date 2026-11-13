package com.robertlangat.socialvideodownloader.data

/**
 * Finds the progressive (audio + video) MP4 address inside a post's web page.
 * Pure Kotlin so it can be unit-tested with page snippets.
 */
object VideoUrlParser {

    private val tiktokKeys = listOf(
        Regex(""""playAddr"\s*:\s*"([^"]+)""""),
        Regex(""""downloadAddr"\s*:\s*"([^"]+)""""),
        Regex(""""PlayAddr"\s*:\s*\{[^}]*?"UrlList"\s*:\s*\[\s*"([^"]+)""""),
    )

    private val instagramKeys = listOf(
        Regex(""""video_versions"\s*:\s*\[\s*\{[^\]]*?"url"\s*:\s*"([^"]+)""""),
        Regex(""""video_url"\s*:\s*"([^"]+)""""),
        Regex("""<meta[^>]+property="og:video(?::secure_url)?"[^>]+content="([^"]+)""""),
    )

    private val facebookKeys = listOf(
        Regex(""""browser_native_hd_url"\s*:\s*"([^"]+)""""),
        Regex(""""playable_url_quality_hd"\s*:\s*"([^"]+)""""),
        Regex(""""hd_src(?:_no_ratelimit)?"\s*:\s*"([^"]+)""""),
        Regex(""""browser_native_sd_url"\s*:\s*"([^"]+)""""),
        Regex(""""playable_url"\s*:\s*"([^"]+)""""),
        Regex(""""sd_src(?:_no_ratelimit)?"\s*:\s*"([^"]+)""""),
        Regex("""<meta[^>]+property="og:video(?::secure_url|:url)?"[^>]+content="([^"]+)""""),
    )

    /** Returns the best video URL in [html], or null. */
    fun find(platform: Platform, html: String): String? {
        val keys = when (platform) {
            Platform.TIKTOK -> tiktokKeys
            Platform.INSTAGRAM -> instagramKeys
            Platform.FACEBOOK -> facebookKeys
        }
        // Pages sometimes embed JSON inside escaped strings; try the raw page, then de-escaped copies.
        var page = html
        repeat(3) {
            for (key in keys) {
                for (m in key.findAll(page)) {
                    val url = unescape(m.groupValues[1])
                    if (isUsable(url)) return url
                }
            }
            val next = page.replace("\\\"", "\"")
            if (next == page) return null
            page = next
        }
        return null
    }

    private fun isUsable(url: String): Boolean =
        (url.startsWith("https://") || url.startsWith("http://")) && !url.startsWith("blob:")

    /** Undoes JSON / HTML escaping, including double-escaped strings. */
    fun unescape(value: String): String {
        var s = value
        repeat(4) {
            val before = s
            s = Regex("""\\u([0-9a-fA-F]{4})""").replace(s) { it.groupValues[1].toInt(16).toChar().toString() }
            s = s.replace("\\/", "/").replace("\\\\", "\\")
            if (s == before) return@repeat
        }
        return s.replace("&amp;", "&")
    }

    enum class Problem { LOGIN_REQUIRED, UNAVAILABLE }

    /** Recognises login walls and "post not available" pages. */
    fun problem(platform: Platform, html: String, pageUrl: String): Problem? {
        val url = pageUrl.lowercase()
        return when (platform) {
            Platform.TIKTOK -> when {
                Regex(""""statusCode"\s*:\s*(10204|10216|10222|10215)""").containsMatchIn(html) -> Problem.UNAVAILABLE
                html.contains("Video currently unavailable") || html.contains("Couldn't find this account") -> Problem.UNAVAILABLE
                else -> null
            }

            Platform.INSTAGRAM -> when {
                url.contains("/accounts/login") || url.contains("/challenge") -> Problem.LOGIN_REQUIRED
                html.contains("Sorry, this page isn't available") || html.contains("Post isn't available") -> Problem.UNAVAILABLE
                else -> null
            }

            Platform.FACEBOOK -> when {
                url.contains("/login") || url.contains("login.php") || url.contains("/checkpoint") -> Problem.LOGIN_REQUIRED
                html.contains("This content isn't available") || html.contains("This video isn't available") -> Problem.UNAVAILABLE
                else -> null
            }
        }
    }
}
