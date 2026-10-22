package com.everettpenne.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserUrlsTest {
    @Test fun webPagesOpenInTheBrowserAndOtherLinksGoToTheirApps() {
        assertEquals(LinkKind.WEB, BrowserUrls.kind("https://grapheneos.org/"))
        assertEquals(LinkKind.WEB, BrowserUrls.kind("HTTP://example.org"))
        listOf("mailto:a@b.c", "tel:+15551234", "geo:0,0", "intent://x#Intent;scheme=y;end", "market://details?id=x").forEach { assertEquals(it, LinkKind.APP, BrowserUrls.kind(it)) }
        listOf("javascript:alert(1)", "data:text/html,hi", "about:blank", "file:///sdcard/x", "content://x", "nonsense").forEach { assertEquals(it, LinkKind.NONE, BrowserUrls.kind(it)) }
    }

    @Test fun theAddressBarShowsTheSite() {
        assertEquals("grapheneos.org", BrowserUrls.host("https://www.grapheneos.org/features#x"))
        assertEquals("sub.example.org", BrowserUrls.host("http://sub.example.org:8080/a"))
        assertTrue(BrowserUrls.isSecure("https://a.b")); assertFalse(BrowserUrls.isSecure("http://a.b"))
    }

    @Test fun aTypedAddressGoesThereAndWordsAreSearched() {
        assertEquals("https://example.org/page", BrowserUrls.fromTyped(" example.org/page "))
        assertEquals("http://x.y", BrowserUrls.fromTyped("http://x.y"))
        assertEquals("https://localhost:8080", BrowserUrls.fromTyped("localhost:8080"))
        assertEquals(BrowserUrls.SEARCH + "grapheneos+pixel+fold", BrowserUrls.fromTyped("grapheneos pixel fold"))
        assertTrue(BrowserUrls.fromTyped("what is 2.5").startsWith(BrowserUrls.SEARCH))
    }

    @Test fun webShortcutFilesPointWhereTheySay() {
        assertEquals("https://grapheneos.org/", BrowserUrls.shortcutTarget("[InternetShortcut]\r\nURL=https://grapheneos.org/\r\nIconIndex=0"))
        assertEquals("https://a.b/?x=1&y=2", BrowserUrls.shortcutTarget("<plist><dict><key>URL</key><string>https://a.b/?x=1&amp;y=2</string></dict></plist>"))
        assertNull("only web addresses", BrowserUrls.shortcutTarget("[InternetShortcut]\nURL=file:///etc/passwd"))
        assertNull(BrowserUrls.shortcutTarget("hello"))
    }

    @Test fun downloadsGetSafeNames() {
        assertEquals("report.pdf", BrowserUrls.fileName("https://x.y/dl?id=3", "attachment; filename=\"report.pdf\"", "application/pdf"))
        assertEquals("café menu.pdf", BrowserUrls.fileName("https://x.y/a", "attachment; filename*=UTF-8''caf%C3%A9%20menu.pdf", null))
        assertEquals("photo.jpg", BrowserUrls.fileName("https://x.y/photo?w=2", null, "image/jpeg"))
        assertEquals("a_b.txt", BrowserUrls.fileName("https://x.y/a", "attachment; filename=\"a/b.txt\"", null))
        assertEquals("download", BrowserUrls.fileName("https://x.y/", null, null))
    }
}
