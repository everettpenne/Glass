plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.everettpenne.media"
    compileSdk = 36
    defaultConfig { minSdk = 31 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":glass"))
    // Media3's Player is in the public API (FullscreenVideo takes one); the apps add the exoplayer/session pieces they use.
    api("androidx.media3:media3-common:1.7.1")
    implementation("androidx.media3:media3-ui:1.7.1")
    // VideoViewer owns a player of its own for apps that only show files (Sietch); the others pass theirs to FullscreenVideo.
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("androidx.core:core-ktx:1.16.0")
    testImplementation("junit:junit:4.13.2")
}
