package com.everettpenne.glass.gallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.everettpenne.glass.GlassColors
import com.everettpenne.glass.GlassTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders every component, opens each overlay, and checks the semantics the apps' tests rely on. */
@RunWith(AndroidJUnit4::class)
class GalleryTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyComponentRenders() {
        rule.setContent { GlassTheme(GlassColors()) { GalleryScreen() } }
        listOf("Home", "Library", "Search", "Share", "More").forEach { rule.onNodeWithContentDescription(it).assertIsDisplayed() }
        rule.onNodeWithText("Prominent").assertIsDisplayed()
        rule.onNodeWithContentDescription("Library").performClick()
        rule.onNodeWithContentDescription("Search").performClick()
    }

    @Test fun overlaysOpen() {
        rule.setContent { GlassTheme(GlassColors()) { GalleryScreen() } }
        rule.onNodeWithText("Alert").performClick(); rule.onNodeWithText("Delete everything?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Sheet").performClick(); rule.onNodeWithText("A floating glass card for forms and long lists.").assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithContentDescription("More").performClick(); rule.onNodeWithText("Refresh").assertIsDisplayed()
    }
}
