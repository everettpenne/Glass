package com.everettpenne.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {
    private fun release(tag: String, apk: String? = "App-${tag.trimStart('v')}.apk", draft: Boolean = false, pre: Boolean = true, url: String = "https://github.com/x/y/releases/download/$tag/$apk", body: String = "\"notes for $tag\"") =
        """{"tag_name":"$tag","name":"App ${tag.trimStart('v')}","draft":$draft,"prerelease":$pre,"body":$body,"assets":[${if (apk == null) "" else """{"name":"$apk","size":123,"browser_download_url":"$url"}"""}]}"""
    private fun list(vararg r: String) = "[${r.joinToString(",")}]"

    @Test fun versionsCompareNumerically() {
        assertTrue(ReleaseParser.compare(ReleaseParser.parseVersion("v0.10.0")!!, ReleaseParser.parseVersion("0.9.9")!!) > 0)
        assertEquals(0, ReleaseParser.compare(listOf(1, 0), listOf(1, 0, 0)))
        assertEquals(listOf(0, 3, 1), ReleaseParser.parseVersion("Thumper 0.3.1-beta"))
        assertNull(ReleaseParser.parseVersion("nightly"))
    }

    @Test fun picksTheNewestPreReleaseAbovetheInstalledVersion() {
        val r = ReleaseParser.newest(list(release("v0.3.1"), release("v0.4.0"), release("v0.3.0")), "0.3.1")
        assertEquals("0.4.0", r?.version); assertTrue(r!!.prerelease); assertEquals("notes for v0.4.0", r.notes)
    }

    @Test fun nothingWhenCurrentIsLatest() {
        assertNull(ReleaseParser.newest(list(release("v0.3.1"), release("v0.3.0")), "0.3.1"))
        assertNull(ReleaseParser.newest("[]", "0.3.1"))
    }

    @Test fun skipsDraftsReleasesWithoutAnApkAndInsecureLinks() {
        assertNull(ReleaseParser.newest(list(release("v9.0.0", draft = true), release("v8.0.0", apk = null), release("v7.0.0", url = "http://insecure/x.apk")), "0.1.0"))
        assertEquals("0.2.0", ReleaseParser.newest(list(release("v9.0.0", draft = true), release("v0.2.0")), "0.1.0")?.version)
    }

    @Test fun nullNotesBecomeEmpty() {
        assertEquals("", ReleaseParser.newest(list(release("v0.2.0", body = "null")), "0.1.0")?.notes)
    }
}

class PlainNotesTest {
    @Test fun stripsMarkdown() {
        assertEquals("Frosted bars.\n\n• The controls stay\n• A link", plainNotes("**Frosted bars.**\n\n- The **controls** stay\n- A [link](http://x)"))
    }
}
