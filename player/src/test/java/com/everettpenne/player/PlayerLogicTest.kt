package com.everettpenne.player

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlayerLogicTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun cyclesWrapAround() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next()); assertEquals(RepeatMode.ONE, RepeatMode.ALL.next()); assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
        assertEquals(FitMode.ZOOM, FitMode.FIT.next()); assertEquals(FitMode.FIT, FitMode.STRETCH.next())
    }

    @Test fun speedLabelsAndSteps() {
        assertEquals("1×", speedLabel(1f)); assertEquals("0.25×", speedLabel(0.25f)); assertEquals("1.5×", speedLabel(1.5f)); assertEquals("0.5×", speedLabel(0.5f))
        assertEquals(1.25f, stepSpeed(1f, +1)); assertEquals(0.75f, stepSpeed(1f, -1))
        assertEquals("never past the ends", 4f, stepSpeed(4f, +1)); assertEquals(0.25f, stepSpeed(0.25f, -1))
        assertEquals("an odd speed steps from where it sits", 2f, stepSpeed(1.6f, +1)); assertEquals(1.5f, stepSpeed(1.6f, -1))
    }

    @Test fun dragMapsToALevel() {
        assertEquals(0.5f, levelAfterDrag(0.5f, 0f, 1000f), 0.001f)
        assertEquals("up raises it", 1f, levelAfterDrag(0.5f, -400f, 1000f), 0.001f)
        assertEquals("80% of the height is the whole range", 0f, levelAfterDrag(1f, 800f, 1000f), 0.001f)
        assertEquals("clamped", 1f, levelAfterDrag(0.9f, -5000f, 1000f), 0.001f)
        assertEquals("no height, no change", 0.3f, levelAfterDrag(0.3f, 50f, 0f), 0.001f)
    }

    @Test fun seekDragIsBoundedByTheItem() {
        assertEquals(60_000L, seekTarget(60_000, 0f, 1000f, 600_000))
        assertEquals("half the width is two minutes on a long film", 60_000L + 120_000L, seekTarget(60_000, 500f, 1000f, 7_200_000))
        assertEquals("a short clip's span is its own length", 15_000L, seekTarget(5_000, 500f, 1000f, 20_000))
        assertEquals(0L, seekTarget(10_000, -1000f, 1000f, 600_000)); assertEquals(20_000L, seekTarget(10_000, 1000f, 1000f, 20_000))
        assertEquals("unknown length stays put", 7_000L, seekTarget(7_000, 300f, 1000f, 0))
    }

    @Test fun seekLabelShowsTheJump() {
        assertEquals("1:23  (+0:23)", seekLabel(60_000, 83_000)); assertEquals("0:50  (−0:10)", seekLabel(60_000, 50_000))
    }

    @Test fun volumeSteps() { assertEquals(0, volumeStep(0f, 15)); assertEquals(15, volumeStep(1f, 15)); assertEquals(8, volumeStep(0.5f, 15)); assertEquals(15, volumeStep(9f, 15)) }

    @Test fun findsSubtitlesBesideAVideo() {
        val d = tmp.newFolder(); val v = File(d, "film.mkv").also { it.writeText("v") }
        listOf("film.srt", "film.en.srt", "film.fr.vtt", "film.ass", "other.srt", "film2.srt", "film.txt", "FILM.srt").forEach { File(d, it).writeText("s") }
        val found = sidecarSubtitles(v).map { it.name }
        assertEquals(listOf("film.ass", "film.en.srt", "film.fr.vtt", "film.srt"), found)
        assertEquals("en", sidecarLanguage(v, File(d, "film.en.srt"))); assertNull(sidecarLanguage(v, File(d, "film.srt")))
        assertEquals("fr", sidecarLanguage(v, File(d, "film.fr.vtt")))
        assertTrue(sidecarSubtitles(File("/nonexistent/x.mp4")).isEmpty())
    }

    @Test fun subtitleMimeFromExtension() {
        assertEquals("application/x-subrip", subtitleMime("SRT")); assertEquals("text/vtt", subtitleMime("vtt")); assertNotNull(subtitleMime("ass")); assertNull(subtitleMime("txt"))
    }

    @Test fun trackNames() {
        assertEquals("Commentary · Stereo", trackLabel("Commentary", "en", 2, 0, true))
        assertEquals("Track 3", trackLabel(null, null, 0, 2, false)); assertEquals("Track 1", trackLabel("  ", "und", 0, 0, false))
        assertEquals("English · 5.1", trackLabel(null, "en", 6, 0, true))
        assertEquals("subtitles have no channel layout", "English", trackLabel(null, "en", 2, 0, false))
    }

    @Test fun resumeOnlyWhenItIsWorthIt() {
        assertFalse(ResumeBook.worthResuming(10_000, 20_000)); assertFalse("short clip", ResumeBook.worthResuming(15_000, 25_000))
        assertFalse("barely started", ResumeBook.worthResuming(3_000, 600_000)); assertFalse("nearly done", ResumeBook.worthResuming(595_000, 600_000))
        assertTrue(ResumeBook.worthResuming(120_000, 600_000))
    }

    @Test fun resumeBookRemembersAndForgets() {
        var t = 0L; val b = ResumeBook(max = 3) { t }
        b.save("a", 100_000, 600_000); assertEquals(100_000L, b.resumeAt("a"))
        b.save("a", 2_000, 600_000); assertNull("going back to the start forgets it", b.resumeAt("a"))
        b.save("a", 100_000, 600_000); b.finish("a"); assertNull(b.resumeAt("a"))
        for (k in listOf("1", "2", "3", "4")) { t += 10; b.save(k, 100_000, 600_000) }
        assertEquals(3, b.size); assertNull("the oldest is dropped", b.resumeAt("1")); assertNotNull(b.resumeAt("4"))
    }

    @Test fun resumeBookSurvivesStorage() {
        val b = ResumeBook { 5L }; b.save("file:///sdcard/Movies/My Film.mkv", 200_000, 900_000); b.save("https://x/y?a=1&b=2", 50_000, 400_000)
        val back = ResumeBook.decode(b.encode()) { 9L }
        assertEquals(200_000L, back.resumeAt("file:///sdcard/Movies/My Film.mkv")); assertEquals(50_000L, back.resumeAt("https://x/y?a=1&b=2"))
        assertEquals(0, ResumeBook.decode("garbage\n\n\t\t").size)
    }
}
