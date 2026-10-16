package com.everettpenne.glass.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.everettpenne.glass.GlassColors
import com.everettpenne.glass.GlassTheme

/** A sample app that shows every piece of the kit on one scrolling page, to restyle against. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GlassTheme(GlassColors()) { GalleryScreen() } }
    }
}
