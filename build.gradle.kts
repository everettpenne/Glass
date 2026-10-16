plugins {
    id("com.android.application") version "8.11.0" apply false
    id("com.android.library") version "8.11.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
}

// The group is what lets an app's `includeBuild("../Glass")` substitute `implementation("com.everettpenne:glass")` with this module.
allprojects { group = "com.everettpenne" }
