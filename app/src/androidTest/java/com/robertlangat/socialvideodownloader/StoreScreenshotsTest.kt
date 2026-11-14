package com.robertlangat.socialvideodownloader

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.robertlangat.socialvideodownloader.ads.Ads
import com.robertlangat.socialvideodownloader.data.PageUrls
import com.robertlangat.socialvideodownloader.data.Settings
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Captures clean screens of the real app for the store listing (saved to Pictures/SVD-test-screenshots). */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)

    private lateinit var server: MockWebServer
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("sample.mp4").use { it.readBytes() }
    }
    private var urls: Map<String, String> = emptyMap()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                val r = MockResponse().setHeader("Content-Type", "video/mp4").setBody(Buffer().write(sample))
                return when {
                    path.startsWith("/media/slow.mp4") -> r.throttleBody(1024, 400, TimeUnit.MILLISECONDS)
                    path.startsWith("/media/ok.mp4") -> r
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        urls = listOf("ok", "slow").associateWith { server.url("/media/$it.mp4").toString().replace("/", "\\/") }
        Ads.disabled = true
        Settings(context).clear()
        PageUrls.fake = { u ->
            val media = if (u.contains("7301")) urls.getValue("slow") else urls.getValue("ok")
            PageUrls.FakePage(
                u,
                """<html><body><script type="application/json">{"video_versions":[{"url":"$media"}],"playAddr":"$media","browser_native_hd_url":"$media"}</script></body></html>""",
            )
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    @After
    fun tearDown() {
        scenario.close()
        PageUrls.fake = null
        server.shutdown()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SVD-test-screenshots")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) } }
        Log.i("SVD-TEST", "store screenshot $name ${bmp.width}x${bmp.height}")
    }

    private fun enter(url: String) {
        compose.onNodeWithTag("url_field").performTextClearance()
        compose.onNodeWithTag("url_field").performTextInput(url)
        compose.waitForIdle()
    }

    private fun waitTag(tag: String) =
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun captureStoreScreens() {
        // 1. Link pasted, platform detected
        enter("https://www.tiktok.com/@daily.moments/video/7298765432109876543")
        shot("store_1_link_detected")

        // 2. Download in progress
        enter("https://www.instagram.com/reel/C7301abcDEF/")
        compose.onNodeWithTag("download_button").performClick()
        waitTag("cancel_button")
        Thread.sleep(6_000)
        shot("store_2_downloading")
        compose.onNodeWithTag("cancel_button").performClick()

        // 3. Saved, with Open video / Share
        enter("https://www.facebook.com/watch/?v=1029384756102938")
        compose.onNodeWithTag("download_button").performClick()
        waitTag("open_video_button")
        shot("store_3_saved")

        // 4. Settings
        compose.onNodeWithTag("settings_button").performClick()
        compose.waitForIdle()
        shot("store_4_settings")
    }
}
