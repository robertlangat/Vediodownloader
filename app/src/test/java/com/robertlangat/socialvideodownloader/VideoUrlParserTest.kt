package com.robertlangat.socialvideodownloader

import com.robertlangat.socialvideodownloader.data.Platform
import com.robertlangat.socialvideodownloader.data.VideoUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoUrlParserTest {

    @Test
    fun tiktokPlayAddrFromRehydrationJson() {
        val html = """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">{"__DEFAULT_SCOPE__":{"webapp.video-detail":{"itemInfo":{"itemStruct":{"video":{"playAddr":"https://v16-webapp-prime.tiktok.com/video/tos/abc/?a=1988&mime_type=video_mp4","downloadAddr":"https://other"}}}}}}</script>"""
        assertEquals(
            "https://v16-webapp-prime.tiktok.com/video/tos/abc/?a=1988&mime_type=video_mp4",
            VideoUrlParser.find(Platform.TIKTOK, html),
        )
    }

    @Test
    fun tiktokRemovedStatus() {
        val html = """{"webapp.video-detail":{"statusCode":10204,"statusMsg":"item doesn't exist"}}"""
        assertNull(VideoUrlParser.find(Platform.TIKTOK, html))
        assertEquals(VideoUrlParser.Problem.UNAVAILABLE, VideoUrlParser.problem(Platform.TIKTOK, html, "https://www.tiktok.com/@a/video/1"))
    }

    @Test
    fun instagramVideoVersions() {
        val html = """..."video_versions":[{"width":720,"height":1280,"url":"https:\/\/scontent.cdninstagram.com\/o1\/v\/t16\/f2\/m86\/a.mp4?efg=x&_nc_ht=y","type":101}]..."""
        assertEquals(
            "https://scontent.cdninstagram.com/o1/v/t16/f2/m86/a.mp4?efg=x&_nc_ht=y",
            VideoUrlParser.find(Platform.INSTAGRAM, html),
        )
    }

    @Test
    fun instagramDoubleEscapedEmbed() {
        val html = """window.__additionalDataLoaded('extra',{\"video_url\":\"https:\\\/\\\/scontent.cdninstagram.com\\\/v\\\/b.mp4?x=1\"});"""
        assertEquals("https://scontent.cdninstagram.com/v/b.mp4?x=1", VideoUrlParser.find(Platform.INSTAGRAM, html))
    }

    @Test
    fun instagramLoginWall() {
        assertEquals(
            VideoUrlParser.Problem.LOGIN_REQUIRED,
            VideoUrlParser.problem(Platform.INSTAGRAM, "<html></html>", "https://www.instagram.com/accounts/login/?next=%2Freel%2Fabc%2F"),
        )
    }

    @Test
    fun facebookPrefersHd() {
        val html = """{"browser_native_sd_url":"https:\/\/video.fxyz1-1.fna.fbcdn.net\/sd.mp4?a=1","browser_native_hd_url":"https:\/\/video.fxyz1-1.fna.fbcdn.net\/hd.mp4?a=1&2"}"""
        assertEquals("https://video.fxyz1-1.fna.fbcdn.net/hd.mp4?a=1&2", VideoUrlParser.find(Platform.FACEBOOK, html))
    }

    @Test
    fun facebookPlayableUrlFallback() {
        val html = """{"playable_url":"https:\/\/video.fbcdn.net\/p.mp4","playable_url_quality_hd":null}"""
        assertEquals("https://video.fbcdn.net/p.mp4", VideoUrlParser.find(Platform.FACEBOOK, html))
    }

    @Test
    fun ignoresBlobAndMissing() {
        assertNull(VideoUrlParser.find(Platform.FACEBOOK, """{"playable_url":"blob:https://x"}"""))
        assertNull(VideoUrlParser.find(Platform.INSTAGRAM, "<html>nothing</html>"))
    }
}
