package com.robertlangat.socialvideodownloader

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.robertlangat.socialvideodownloader.data.Extraction
import com.robertlangat.socialvideodownloader.data.LinkParser
import com.robertlangat.socialvideodownloader.data.PageUrls
import com.robertlangat.socialvideodownloader.data.WebVideoExtractor
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Informational checks against the REAL sites (public posts, no sign-in) from the CI emulator.
 * Never fails the build: results are logged under the SVD-LIVE tag and shown in the CI report.
 * Note: CI runs from a data-centre IP, which these sites often treat differently from a phone.
 */
@RunWith(AndroidJUnit4::class)
class LiveExtractionTest {

    private val samples = listOf(
        "https://www.tiktok.com/@scout2015/video/6718335390845095173",
        "https://www.facebook.com/facebook/videos/10153231379946729/",
        "https://www.instagram.com/p/aye83DjauH/",
    )

    @Test
    fun liveExtraction_report() {
        PageUrls.fake = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val extractor = WebVideoExtractor(context)
        val http = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).build()
        for (url in samples) {
            val link = LinkParser.parse(url) ?: continue
            val line = try {
                when (val r = runBlocking { extractor.extract(link) }) {
                    is Extraction.Found -> {
                        val req = Request.Builder().url(r.videoUrl).header("Range", "bytes=0-65535")
                        r.headers.forEach { (k, v) -> req.header(k, v) }
                        val probe = try {
                            http.newCall(req.build()).execute().use { resp ->
                                "file HTTP ${resp.code} ${resp.header("Content-Type")} ${resp.body?.bytes()?.size ?: 0} bytes"
                            }
                        } catch (e: Exception) {
                            "file request failed: ${e.message}"
                        }
                        "FOUND video -> $probe"
                    }

                    is Extraction.LoginRequired -> "LOGIN REQUIRED"
                    is Extraction.Unavailable -> "NOT FOUND: ${r.message}"
                }
            } catch (e: Exception) {
                "ERROR: ${e.message}"
            }
            Log.i("SVD-LIVE", "${link.platform.label}: $line")
        }
    }
}
