package com.snapfree.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkDetectorTest {
    private fun key(text: String): String? = (LinkDetector.check(text) as? LinkCheck.Complete)?.key
    private fun incomplete(text: String) = assertTrue(text, LinkDetector.check(text) is LinkCheck.Incomplete)

    @Test fun youtube() {
        assertEquals("yt:dQw4w9WgXcQ", key("https://youtu.be/dQw4w9WgXcQ?si=abc123"))
        assertEquals("yt:dQw4w9WgXcQ", key("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s"))
        assertEquals("yt:dQw4w9WgXcQ", key("https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ"))
        assertEquals("yt:dQw4w9WgXcQ", key("https://youtube.com/shorts/dQw4w9WgXcQ?si=x"))
        assertEquals("yt:dQw4w9WgXcQ", key("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("yt:dQw4w9WgXcQ", key("youtu.be/dQw4w9WgXcQ"))
        assertEquals("yt:dQw4w9WgXcQ", key("Look at this https://youtu.be/dQw4w9WgXcQ!"))
        incomplete("https://youtu.be/dQw4w9W")
        incomplete("https://www.youtube.com/watch?v=")
        incomplete("https://www.youtube.com/")
        incomplete("https://youtube.com/shorts/abc")
    }

    @Test fun instagram() {
        assertEquals("ig:C1a2B3c4D5e", key("https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=MWx"))
        assertEquals("ig:C1a2B3c4D5e", key("https://instagram.com/p/C1a2B3c4D5e"))
        assertEquals("ig:C1a2B3c4D5e", key("https://www.instagram.com/someone/reel/C1a2B3c4D5e/"))
        assertEquals("ig:C1a2B3c4D5e", key("https://www.instagram.com/tv/C1a2B3c4D5e"))
        assertEquals("igshare:BAabc123", key("https://www.instagram.com/share/reel/BAabc123"))
        assertEquals("igstory:3141592653589", key("https://www.instagram.com/stories/someone/3141592653589/"))
        incomplete("https://www.instagram.com/reel/")
        incomplete("https://www.instagram.com/someone/")
    }

    @Test fun tiktok() {
        assertEquals("tt:7301234567890123456", key("https://www.tiktok.com/@user.name/video/7301234567890123456?is_from_webapp=1"))
        assertEquals("ttshort:ZMabc123", key("https://vm.tiktok.com/ZMabc123/"))
        assertEquals("ttshort:ZMabc123", key("https://vt.tiktok.com/ZMabc123"))
        assertEquals("ttshort:ZMabc123", key("https://www.tiktok.com/t/ZMabc123/"))
        assertEquals("tt:7301234567890123456", key("https://m.tiktok.com/v/7301234567890123456.html"))
        incomplete("https://www.tiktok.com/@user.name/video/")
        incomplete("https://www.tiktok.com/@user.name")
    }

    @Test fun otherSites() {
        val check = LinkDetector.check("https://vimeo.com/123456?utm_source=x") as LinkCheck.Complete
        assertEquals(false, check.confident)
        assertEquals("url:vimeo.com/123456", check.key)
        incomplete("https://vimeo.com")
        incomplete("hello world")
        incomplete("https://")
        assertTrue(LinkDetector.check("  ") is LinkCheck.Empty)
    }

    @Test fun lenientAcceptsSupportedSiteWithoutKnownPattern() {
        assertTrue(LinkDetector.check("https://www.youtube.com/@channel", lenient = true) is LinkCheck.Complete)
    }
}
