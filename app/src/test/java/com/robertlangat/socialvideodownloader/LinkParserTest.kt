package com.robertlangat.socialvideodownloader

import com.robertlangat.socialvideodownloader.data.LinkParser
import com.robertlangat.socialvideodownloader.data.Platform
import com.robertlangat.socialvideodownloader.data.VideoDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {

    private fun platformOf(s: String) = LinkParser.parse(s)?.platform

    @Test
    fun tiktokLinks() {
        listOf(
            "https://www.tiktok.com/@scout2015/video/6718335390845095173",
            "https://www.tiktok.com/@scout2015/video/6718335390845095173?is_from_webapp=1&sender_device=pc",
            "https://m.tiktok.com/v/6718335390845095173.html",
            "https://vm.tiktok.com/ZMeAbCdEf/",
            "https://vt.tiktok.com/ZSabc123/",
            "https://www.tiktok.com/t/ZTRabc123/",
            "http://tiktok.com/@a.b_c/video/7234567890123456789",
        ).forEach { assertEquals(it, Platform.TIKTOK, platformOf(it)) }
    }

    @Test
    fun instagramLinks() {
        listOf(
            "https://www.instagram.com/reel/C1a2B3c4D5e/",
            "https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=MWQ1ZGUxMzBkMA==",
            "https://instagram.com/p/C1a2B3c4D5e",
            "https://www.instagram.com/reels/C1a2B3c4D5e/",
            "https://www.instagram.com/tv/C1a2B3c4D5e/",
            "https://www.instagram.com/someuser/reel/C1a2B3c4D5e/",
            "https://www.instagram.com/share/reel/BAabc123xyz",
            "https://instagr.am/p/C1a2B3c4D5e/",
        ).forEach { assertEquals(it, Platform.INSTAGRAM, platformOf(it)) }
    }

    @Test
    fun facebookLinks() {
        listOf(
            "https://www.facebook.com/watch?v=1234567890123",
            "https://www.facebook.com/watch/?v=1234567890123",
            "https://m.facebook.com/watch/?v=1234567890123",
            "https://www.facebook.com/SomePage/videos/1234567890123/",
            "https://www.facebook.com/SomePage/videos/my-video-title/1234567890123/",
            "https://web.facebook.com/reel/1234567890123",
            "https://www.facebook.com/share/v/1AbCdEfGh/",
            "https://www.facebook.com/share/r/1AbCdEfGh/",
            "https://fb.watch/aBcDeF123/",
            "https://www.facebook.com/video.php?v=1234567890123",
        ).forEach { assertEquals(it, Platform.FACEBOOK, platformOf(it)) }
    }

    @Test
    fun invalidLinks() {
        listOf(
            "",
            "hello world",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.tiktok.com/@scout2015",
            "https://www.tiktok.com/",
            "https://www.instagram.com/someuser/",
            "https://www.instagram.com/",
            "https://www.facebook.com/SomePage",
            "https://www.facebook.com/watch?v=abc",
            "https://evil-tiktok.com/@a/video/6718335390845095173",
            "https://tiktok.com.evil.example/@a/video/6718335390845095173",
            "ftp://www.tiktok.com/@a/video/6718335390845095173",
            "not a url https:/broken",
        ).forEach { assertNull(it, LinkParser.parse(it)) }
    }

    @Test
    fun extractsUrlFromShareText() {
        val text = "Check out this video! https://vm.tiktok.com/ZMeAbCdEf/ #fyp"
        val link = LinkParser.parse(text)
        assertEquals(Platform.TIKTOK, link?.platform)
        assertEquals("https://vm.tiktok.com/ZMeAbCdEf/", link?.url)
    }

    @Test
    fun acceptsLinkWithoutScheme() {
        assertEquals(Platform.INSTAGRAM, platformOf("www.instagram.com/reel/C1a2B3c4D5e/"))
    }

    @Test
    fun httpIsUpgraded() {
        assertTrue(LinkParser.parse("http://www.instagram.com/reel/C1a2B3c4D5e/")!!.url.startsWith("https://"))
    }

    @Test
    fun fileNames() {
        assertEquals("my clip.mp4", VideoDownloader.buildFileName("my clip.mp4", Platform.TIKTOK))
        assertEquals("a_b_c.mp4", VideoDownloader.buildFileName("a/b\\c", Platform.TIKTOK))
        assertEquals("facebook_42.mp4", VideoDownloader.buildFileName(null, Platform.FACEBOOK, now = 42))
    }
}
