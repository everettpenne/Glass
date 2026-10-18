package com.everettpenne.glass

import org.junit.Assert.assertEquals
import org.junit.Test

/** The capsule's available width on a phone: screen − 32dp outer padding − 64dp search button. */
class TabWidthsTest {
    private fun available(screen: Float) = screen - 32f - 64f

    @Test fun twoTabsKeepTheOriginalWidthOnEveryPhone() {
        // 2 tabs (260dp capsule) must stay full width on a 360dp and a 411dp phone.
        val a360 = available(360f) // 264dp
        val a411 = available(411f) // 315dp
        assertEquals(TabWidths.canonicalSlot, TabWidths.slots(2, a360), 1e-3f)
        assertEquals(TabWidths.canonicalSlot, TabWidths.slots(2, a411), 1e-3f)
        assertEquals(260f, TabWidths.capsule(TabWidths.slots(2, a360), 2), 1e-3f)
        assertEquals(260f, TabWidths.capsule(TabWidths.slots(2, a411), 2), 1e-3f)
    }

    @Test fun threeTabsScaleDownUniformlyToFit() {
        // 3 tabs want 386dp; they must shrink to exactly the available width on both phones.
        val a360 = available(360f)
        val a411 = available(411f)
        assertEquals((a360 - 8f) / 3f, TabWidths.slots(3, a360), 1e-3f) // 85.333dp
        assertEquals((a411 - 8f) / 3f, TabWidths.slots(3, a411), 1e-3f) // 102.333dp
        assertEquals(a360, TabWidths.capsule(TabWidths.slots(3, a360), 3), 1e-3f)
        assertEquals(a411, TabWidths.capsule(TabWidths.slots(3, a411), 3), 1e-3f)
    }

    @Test fun fourTabsScaleDownUniformlyToFit() {
        val a360 = available(360f)
        val a411 = available(411f)
        assertEquals((a360 - 8f) / 4f, TabWidths.slots(4, a360), 1e-3f) // 64dp
        assertEquals((a411 - 8f) / 4f, TabWidths.slots(4, a411), 1e-3f) // 76.75dp
        assertEquals(a360, TabWidths.capsule(TabWidths.slots(4, a360), 4), 1e-3f)
        assertEquals(a411, TabWidths.capsule(TabWidths.slots(4, a411), 4), 1e-3f)
    }

    @Test fun slotNeverFallsBelowTheFloor() {
        // Even when the capsule cannot fit at all, slots keep the 44dp+ target and never collapse.
        assertEquals(TabWidths.minSlot, TabWidths.slots(3, 50f), 1e-3f)
        assertEquals(TabWidths.minSlot, TabWidths.slots(5, 0f), 1e-3f)
        assertEquals(TabWidths.minSlot, TabWidths.slots(9, TabWidths.minSlot * 9 + TabWidths.capsuleInset * 2 - 1f), 1e-3f)
    }

    @Test fun slotNeverExceedsCanonical() {
        assertEquals(TabWidths.canonicalSlot, TabWidths.slots(2, 10_000f), 1e-3f)
        assertEquals(TabWidths.canonicalSlot, TabWidths.slots(4, 10_000f), 1e-3f)
    }

    @Test fun capsuleGrowsByExactlyOneSlotPerTab() {
        val c2 = TabWidths.capsule(TabWidths.canonicalSlot, 2) // 260
        val c3 = TabWidths.capsule(TabWidths.canonicalSlot, 3) // 386
        val c4 = TabWidths.capsule(TabWidths.canonicalSlot, 4) // 512
        assertEquals(c2 + TabWidths.canonicalSlot, c3, 1e-3f) // 260 -> 386
        assertEquals(c3 + TabWidths.canonicalSlot, c4, 1e-3f) // 386 -> 512
        // The constant insets aside, a 3-tab capsule is 50% wider than a 2-tab one (3 slots vs 2).
        assertEquals(1.5f, (c3 - 8f) / (c2 - 8f), 1e-3f)
    }

    @Test fun rejectsNonPositiveCount() {
        try {
            TabWidths.slots(0, 300f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
