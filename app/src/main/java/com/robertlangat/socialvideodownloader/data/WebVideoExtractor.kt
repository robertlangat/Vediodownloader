package com.robertlangat.socialvideodownloader.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume

/** Network or loading problem that is worth retrying. */
class RetryableException(message: String, cause: Throwable? = null) : IOException(message, cause)

sealed interface Extraction {
    data class Found(val videoUrl: String, val headers: Map<String, String>) : Extraction
    data class LoginRequired(val message: String) : Extraction
    data class Unavailable(val message: String, val canRetry: Boolean = false) : Extraction
}

/** Test hook: lets the emulator tests supply post pages (shown as if loaded from [baseUrl]). */
object PageUrls {
    data class FakePage(val baseUrl: String, val html: String)

    @Volatile
    var fake: ((String) -> FakePage?)? = null
}

object UserAgents {
    const val DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    fun forPlatform(context: Context, platform: Platform): String = when (platform) {
        Platform.INSTAGRAM -> WebSettings.getDefaultUserAgent(context)
        else -> DESKTOP
    }
}

/** Where each platform's sign-in page is, and the cookie that marks a signed-in browser. */
object SignIn {
    fun loginUrl(p: Platform) = when (p) {
        Platform.TIKTOK -> "https://www.tiktok.com/login"
        Platform.INSTAGRAM -> "https://www.instagram.com/accounts/login/"
        Platform.FACEBOOK -> "https://www.facebook.com/login/"
    }

    fun isSignedIn(p: Platform): Boolean {
        val (site, cookie) = when (p) {
            Platform.TIKTOK -> "https://www.tiktok.com" to "sessionid="
            Platform.INSTAGRAM -> "https://www.instagram.com" to "sessionid="
            Platform.FACEBOOK -> "https://www.facebook.com" to "c_user="
        }
        return try {
            CookieManager.getInstance().getCookie(site)?.contains(cookie) == true
        } catch (e: Exception) {
            false
        }
    }

    fun signOutAll() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}

/**
 * Opens the post in an invisible WebView (a real browser on the phone, with the user's
 * sign-ins), then reads the video file address out of the loaded page.
 */
class WebVideoExtractor(private val context: Context) {

    private class Snapshot(val url: String, val html: String, val videoSrc: String)

    suspend fun extract(link: ParsedLink, timeoutMs: Long = 25_000): Extraction = withContext(Dispatchers.Main) {
        val ua = UserAgents.forPlatform(context, link.platform)
        val fakePage = PageUrls.fake?.invoke(link.url)
        val target = fakePage?.baseUrl ?: link.url
        val media = CopyOnWriteArrayList<String>()
        var mainFrameError: String? = null
        var mainFrameHttpStatus = 0

        val webView = createWebView(ua, media, onError = { mainFrameError = it }, onHttpError = { mainFrameHttpStatus = it })
        try {
            if (fakePage != null) {
                webView.loadDataWithBaseURL(fakePage.baseUrl, fakePage.html, "text/html", "utf-8", null)
            } else {
                webView.loadUrl(target)
            }
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            var last: Snapshot? = null
            delay(1200)
            while (SystemClock.elapsedRealtime() < deadline) {
                mainFrameError?.let {
                    throw RetryableException("Couldn't open the post ($it). Check your connection and retry.")
                }
                if (mainFrameHttpStatus == 404 || mainFrameHttpStatus == 410) {
                    return@withContext Extraction.Unavailable("This post was removed or the link is broken.")
                }
                val snap = snapshot(webView)
                Log.d(TAG, "poll url=${snap?.url} html=${snap?.html?.length} video=${snap?.videoSrc} media=${media.size}")
                if (snap != null) {
                    last = snap
                    VideoUrlParser.find(link.platform, snap.html)?.let { return@withContext found(it, snap.url, ua, link.platform) }
                    if (snap.videoSrc.startsWith("http")) return@withContext found(snap.videoSrc, snap.url, ua, link.platform)
                    when (VideoUrlParser.problem(link.platform, snap.html, snap.url)) {
                        VideoUrlParser.Problem.LOGIN_REQUIRED -> return@withContext loginRequired(link.platform)
                        VideoUrlParser.Problem.UNAVAILABLE -> return@withContext Extraction.Unavailable(
                            "This ${link.platform.label} post is private, removed or not available in your region."
                        )

                        null -> Unit
                    }
                }
                if (link.platform == Platform.TIKTOK) {
                    media.firstOrNull()?.let { return@withContext found(it, last?.url ?: target, ua, link.platform) }
                }
                delay(1000)
            }
            if (link.platform != Platform.TIKTOK && !SignIn.isSignedIn(link.platform)) {
                return@withContext loginRequired(link.platform)
            }
            Extraction.Unavailable(
                "Couldn't find a video in this ${link.platform.label} post. It may be private, removed, or a photo post.",
                canRetry = true,
            )
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private fun loginRequired(p: Platform) = Extraction.LoginRequired(
        "${p.label} only shows this video to signed-in people. Sign in to ${p.label} once, then try again."
    )

    private fun found(videoUrl: String, pageUrl: String, ua: String, platform: Platform): Extraction.Found {
        val headers = mutableMapOf("User-Agent" to ua)
        headers["Referer"] = when (platform) {
            Platform.TIKTOK -> "https://www.tiktok.com/"
            else -> pageUrl
        }
        try {
            CookieManager.getInstance().getCookie(videoUrl)?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }
        } catch (_: Exception) {
        }
        return Extraction.Found(videoUrl, headers)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(
        ua: String,
        media: MutableList<String>,
        onError: (String) -> Unit,
        onHttpError: (Int) -> Unit,
    ): WebView {
        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            blockNetworkImage = true
            userAgentString = ua
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url.toString()
                if (isTikTokMedia(u)) media.add(u)
                return null
            }

            // Stay in the page: never hand off to the platform's app (intent://, snssdk://...).
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = Unit

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) onError(error.description?.toString() ?: "error ${error.errorCode}")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) onHttpError(errorResponse.statusCode)
            }
        }
        webView.layout(0, 0, 1080, 1920)
        return webView
    }

    private fun isTikTokMedia(u: String): Boolean =
        (u.contains("tiktok") || u.contains("byteoversea") || u.contains("ibyteimg")) &&
            (u.contains("/video/tos/") || u.contains("mime_type=video_mp4"))

    private suspend fun snapshot(webView: WebView): Snapshot? = suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(SNAPSHOT_JS) { result ->
            val snap = try {
                val text = JSONTokener(result).nextValue() as? String
                text?.let {
                    val o = JSONObject(it)
                    Snapshot(o.optString("u"), o.optString("h"), o.optString("v"))
                }
            } catch (e: Exception) {
                null
            }
            if (cont.isActive) cont.resume(snap)
        }
    }

    companion object {
        private const val TAG = "SVD-EXTRACT"
        private const val SNAPSHOT_JS = """(function(){
            var v=document.querySelector('video');
            return JSON.stringify({u:location.href, v:v?(v.currentSrc||v.src||''):'', h:document.documentElement.outerHTML});
        })()"""
    }
}
