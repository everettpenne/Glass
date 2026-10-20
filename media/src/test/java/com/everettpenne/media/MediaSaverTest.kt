package com.everettpenne.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSaverTest {
    @Test fun typeComesFromTheExtensionIgnoringTheQuery() {
        assertEquals("image/png", MediaSaver.mimeFromName("https://i.redd.it/abc.PNG?width=640"))
        assertEquals("image/gif", MediaSaver.mimeFromName("https://x/y/z.gif#frag"))
        assertNull(MediaSaver.mimeFromName("https://example.com/picture"))
    }

    @Test fun theNameKeepsTheAddressStemAndTakesTheReceivedType() {
        assertEquals("abc123.jpg", MediaSaver.fileName("https://i.redd.it/abc123.jpeg?x=1", "image/jpeg"))
        assertEquals("abc123.png", MediaSaver.fileName("https://i.redd.it/abc123.jpeg", "image/png"))
        assertEquals("weird-name.webp", MediaSaver.fileName("https://h/we!ird-name.webp", "image/webp"))
    }

    @Test fun anAddressWithNoNameStillGetsOne() {
        val name = MediaSaver.fileName("https://example.com/", "image/gif")
        assert(name.startsWith("image-") && name.endsWith(".gif")) { name }
    }
}
