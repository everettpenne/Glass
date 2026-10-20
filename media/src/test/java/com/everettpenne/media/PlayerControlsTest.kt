package com.everettpenne.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerControlsTest {
    @Test fun clocksReadLikeAPlayers() {
        assertEquals("0:00", playerClock(0)); assertEquals("1:05", playerClock(65_000)); assertEquals("1:00:00", playerClock(3_600_000))
        assertEquals("0:00", playerClock(-5))
    }

    @Test fun theThumbFollowsTheFingerWhileScrubbingAndThePositionOtherwise() {
        assertEquals(.25f, scrubberFraction(15_000, 60_000, null), 0f)
        assertEquals(.9f, scrubberFraction(15_000, 60_000, .9f), 0f)
        assertEquals(0f, scrubberFraction(15_000, 0, null), 0f)
        assertEquals(1f, scrubberFraction(90_000, 60_000, null), 0f)
    }
}
