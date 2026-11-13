package com.robertlangat.socialvideodownloader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.robertlangat.socialvideodownloader.ads.Ads
import com.robertlangat.socialvideodownloader.data.Settings
import com.robertlangat.socialvideodownloader.data.VideoDownloader
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end tests of the paste -> check -> download -> save-to-Gallery flow.
 *
 * A MockWebServer on the device plays both roles that exist in production:
 *  - the resolver backend (POST /api/resolve), returning the same JSON the real backend returns, and
 *  - the platform CDN that serves the authorised video file (GET /media/...).
 * The served file is a real H.264/AAC MP4 (androidTest/assets/sample.mp4).
 */
@RunWith(AndroidJUnit4::class)
class DownloadFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val sampleBytes: ByteArray by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("sample.mp4").use { it.readBytes() }
    }
    private val runId = System.currentTimeMillis()
    private val resolveCalls = AtomicInteger(0)
    private val breakNextDownload = AtomicBoolean(false)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = route(request)
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)

        Ads.disabled = true // full-screen ads must not cover the UI under test
        Settings(context).apply {
            clear()
            backendUrl = server.url("/").toString()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    @After
    fun tearDown() {
        scenario.close()
        server.shutdown()
    }

    private fun route(request: RecordedRequest): MockResponse {
        val path = request.path ?: ""
        return when {
            path.startsWith("/api/resolve") -> {
                resolveCalls.incrementAndGet()
                val body = JSONObject(request.body.readUtf8())
                val url = body.getString("url")
                val platform = body.getString("platform")
                val json = when {
                    url.contains("9999999999") || url.contains("REMOVED") || url.contains("removed") -> JSONObject()
                        .put("status", "unavailable")
                        .put("reason", "removed")
                        .put("message", "This post was removed or is private, so it can't be downloaded.")
                        .put("originalUrl", url)

                    else -> {
                        val media = when {
                            breakNextDownload.getAndSet(false) -> "broken"
                            url.contains("SLOW") || url.contains("5555555555") -> "slow"
                            else -> "ok"
                        }
                        JSONObject()
                            .put("status", "ready")
                            .put("downloadUrl", server.url("/media/$media.mp4").toString())
                            .put("fileName", "${platform}_test_${runId}_${resolveCalls.get()}.mp4")
                            .put("mimeType", "video/mp4")
                    }
                }
                MockResponse().setHeader("Content-Type", "application/json").setBody(json.toString())
            }

            path.startsWith("/media/ok.mp4") -> MockResponse()
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(sampleBytes))

            path.startsWith("/media/slow.mp4") -> MockResponse()
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(sampleBytes))
                .throttleBody(2048, 500, java.util.concurrent.TimeUnit.MILLISECONDS)

            path.startsWith("/media/broken.mp4") -> MockResponse()
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(sampleBytes))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)

            else -> MockResponse().setResponseCode(404)
        }
    }

    // ---------- helpers ----------

    private fun enterLink(url: String) {
        compose.onNodeWithTag("url_field").performTextClearance()
        compose.onNodeWithTag("url_field").performTextInput(url)
        compose.waitForIdle()
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 30_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun statusText(): String {
        val nodes = compose.onAllNodesWithTag("status_text", useUnmergedTree = true).fetchSemanticsNodes()
        return nodes.joinToString(" | ") { n ->
            n.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString { it.text }
        }
    }

    private fun assertSelected(tag: String) {
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    private fun findSaved(prefix: String): Uri? {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.IS_PENDING),
            "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("$prefix%", "%${VideoDownloader.ALBUM}%"),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                assertEquals("saved item must not be pending", 0, c.getInt(2))
                return Uri.withAppendedPath(collection, c.getLong(0).toString())
            }
        }
        return null
    }

    private fun countPending(): Int {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val args = android.os.Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?")
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%${VideoDownloader.ALBUM}%"))
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
        }
        return context.contentResolver.query(collection, arrayOf(MediaStore.Video.Media._ID), args, null)?.use { it.count } ?: 0
    }

    /** Verifies the Gallery entry is a playable video: it has a video track, a duration and a decodable frame. */
    private fun assertPlayable(uri: Uri) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            assertEquals("yes", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("duration was $duration ms", duration in 1500..2500)
            assertNotNull("first frame should decode", r.getFrameAtTime(500_000))
        } finally {
            r.release()
        }
        context.contentResolver.openInputStream(uri)!!.use { assertEquals(sampleBytes.size, it.readBytes().size) }

        // Play it through the platform media stack, the same path a Gallery app uses.
        val player = MediaPlayer()
        try {
            player.setDataSource(context, uri)
            player.setVolume(0f, 0f)
            player.prepare()
            assertTrue("player duration", player.duration in 1500..2500)
            player.start()
            Thread.sleep(1200)
            assertTrue("playback should advance, position=${player.currentPosition}", player.currentPosition > 300)
            player.stop()
        } finally {
            player.release()
        }

        // Which app the "Open video" button would hand the file to (informational).
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val handler = context.packageManager.resolveActivity(view, 0)?.activityInfo?.packageName
        Log.i("SVD-TEST", "ACTION_VIEW handler for saved video: $handler")
    }

    /** Saves a PNG of the current screen to Pictures/SVD-test-screenshots (kept after the test APK is uninstalled). */
    private fun screenshot(name: String) {
        try {
            compose.waitForIdle()
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SVD-test-screenshots")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            if (uri != null) context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Throwable) {
            Log.w("SVD-TEST", "screenshot $name failed", e)
        }
    }

    private fun downloadAndVerify(url: String, expectedTag: String, apiName: String) {
        enterLink(url)
        assertSelected(expectedTag)
        compose.onNodeWithTag("download_button").assertIsEnabled().performClick()
        waitForTag("open_video_button")
        compose.onNodeWithTag("share_button").assertExists()
        screenshot("saved_$apiName")
        val uri = findSaved("${apiName}_test_$runId")
        assertNotNull("video should be in MediaStore", uri)
        assertPlayable(uri!!)
        Log.i("SVD-TEST", "PASS $apiName saved=$uri status='${statusText()}'")
    }

    // ---------- tests ----------

    @Test
    fun tiktok_pasteToDownload_savesPlayableVideo() =
        downloadAndVerify("https://www.tiktok.com/@scout2015/video/6718335390845095173", "platform_TIKTOK", "tiktok")

    @Test
    fun instagram_pasteToDownload_savesPlayableVideo() =
        downloadAndVerify("https://www.instagram.com/reel/C1a2B3c4D5e/", "platform_INSTAGRAM", "instagram")

    @Test
    fun facebook_pasteToDownload_savesPlayableVideo() =
        downloadAndVerify("https://www.facebook.com/watch/?v=1234567890123", "platform_FACEBOOK", "facebook")

    @Test
    fun pasteShortcut_readsClipboard_andAutoSelectsPlatform() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("link", "Watch this https://fb.watch/aBcDeF123/ !"))
        }
        assertSelected("platform_TIKTOK")
        compose.onNodeWithTag("paste_button").performClick()
        compose.waitForIdle()
        assertSelected("platform_FACEBOOK")
        compose.onNodeWithTag("download_button").assertIsEnabled()
    }

    @Test
    fun home_showsSupportedAppsAndPermissionButton_tapSelectsPlatform() {
        compose.onNodeWithTag("permission_card").assertExists()
        compose.onNodeWithTag("home_allow_storage").assertExists()
        compose.onNodeWithTag("open_app_button").assertExists()
        assertSelected("platform_TIKTOK")
        compose.onNodeWithTag("platform_INSTAGRAM").performClick()
        compose.waitForIdle()
        assertSelected("platform_INSTAGRAM")
        screenshot("home_idle")
    }

    @Test
    fun settings_showsStorageFolderAndHidesServiceUrlUnderAdvanced() {
        compose.onNodeWithTag("settings_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings_screen").assertExists()
        compose.onNodeWithTag("allow_storage_button").assertExists()
        compose.onNodeWithTag("choose_folder_button").assertExists()
        compose.onNodeWithTag("save_folder_label").assertTextContains("Movies/SocialVideoDownloader")
        compose.onNodeWithTag("backend_url_field").assertDoesNotExist()
        screenshot("settings")
        compose.onNodeWithTag("advanced_toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("backend_url_field").assertExists()
        compose.onNodeWithTag("settings_back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("download_button").assertExists()
    }

    @Test
    fun invalidLink_keepsDownloadDisabled() {
        compose.onNodeWithTag("download_button").assertIsNotEnabled()
        enterLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        compose.onNodeWithTag("download_button").assertIsNotEnabled()
        compose.onNodeWithTag("invalid_hint", useUnmergedTree = true).assertExists()
        enterLink("https://www.instagram.com/someprofile/")
        compose.onNodeWithTag("download_button").assertIsNotEnabled()
        assertEquals("no backend call for invalid links", 0, resolveCalls.get())
        screenshot("invalid_link")
    }

    @Test
    fun unavailablePost_explainsAndOffersOriginal() {
        enterLink("https://www.instagram.com/reel/REMOVEDpost1/")
        compose.onNodeWithTag("download_button").performClick()
        waitForTag("open_original_button")
        assertTrue(statusText(), statusText().contains("removed or is private"))
        compose.onNodeWithTag("retry_button").assertDoesNotExist()
        screenshot("unavailable_post")
    }

    @Test
    fun interruptedDownload_showsRetry_thenSucceeds() {
        breakNextDownload.set(true)
        enterLink("https://www.facebook.com/SomePage/videos/1234567890123/")
        compose.onNodeWithTag("download_button").performClick()
        waitForTag("retry_button")
        assertTrue(statusText(), statusText().contains("interrupted", ignoreCase = true))
        assertEquals("partial file must be cleaned up", 0, countPending())
        screenshot("interrupted_retry")

        compose.onNodeWithTag("retry_button").performClick()
        waitForTag("open_video_button")
        val uri = findSaved("facebook_test_$runId")
        assertNotNull(uri)
        assertPlayable(uri!!)
    }

    @Test
    fun cancelDuringDownload_removesPartialFile() {
        enterLink("https://www.tiktok.com/@someone/video/5555555555123")
        compose.onNodeWithTag("download_button").performClick()
        waitForTag("cancel_button")
        screenshot("downloading_progress")
        compose.onNodeWithTag("cancel_button").performClick()
        compose.waitUntil(10_000) { statusText().contains("cancelled") }
        Thread.sleep(1500)
        assertEquals(0, countPending())
        compose.onNodeWithTag("download_button").assertIsEnabled()
    }
}
