package com.everettpenne.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PipRatioTest {
    @Test fun anOrdinaryVideoKeepsItsShape() {
        assertEquals(16f / 9f, pipRatio(16f / 9f), 0f)
        assertEquals(9f / 16f, pipRatio(9f / 16f), 0f)
    }

    @Test fun shapesAndroidWouldRefuseAreClampedIntoRange() {
        assertEquals(2.39f, pipRatio(4f), 0f)
        assertEquals(1f / 2.39f, pipRatio(.1f), 0f)
    }

    @Test fun anUnknownShapeFallsBackToSixteenByNine() {
        assertEquals(16f / 9f, pipRatio(0f), 0f)
        assertEquals(16f / 9f, pipRatio(Float.NaN), 0f)
        assertEquals(16f / 9f, pipRatio(-1f), 0f)
    }
}
