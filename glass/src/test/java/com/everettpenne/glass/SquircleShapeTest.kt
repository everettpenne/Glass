package com.everettpenne.glass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SquircleShapeTest {
    @Test fun anIconsCornerTakesInHalfItsSide() {
        assertEquals(0.5f, GlassCorner.ICON_FRACTION * SquircleShape.REACH, .01f)
    }

    @Test fun aZeroRadiusIsARectangle() {
        val outline = SquircleShape().createOutline(Size(80f, 40f), LayoutDirection.Ltr, Density(1f))
        assertTrue(outline is Outline.Generic)
    }

    @Test fun theScaleIsIosSteps() {
        assertEquals(8f, GlassCorner.small.value); assertEquals(16f, GlassCorner.medium.value)
        assertEquals(24f, GlassCorner.large.value); assertEquals(26f, GlassCorner.card.value)
    }
}
