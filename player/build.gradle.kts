plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.everettpenne.player"
    compileSdk = 36
    defaultConfig { minSdk = 31 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Media3 marks parts of its player UI and format API unstable; they are the parts every player uses, and the version is pinned here.
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
    }
    lint { disable += "UnsafeOptInUsageError" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":glass"))
    // The shared pieces: picture-in-picture (VideoPip), the scrubber and the clock.
    api(project(":media"))
    api("androidx.media3:media3-common:1.7.1")
    implementation("androidx.media3:media3-ui:1.7.1")
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    // The service that plays in the background, and the controller every screen talks to it with.
    implementation("androidx.media3:media3-session:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    testImplementation("junit:junit:4.13.2")
}
