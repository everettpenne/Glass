# Glass

The iOS 26 "liquid glass" UI kit shared by Seekur, Spice and Thumper: glass surfaces, controls, bars, tabs, slider, menus, alerts, sheets, the Apple HIG type scale set in Inter, and the iOS-style Material theme. Built on [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 1.0.0 (kept on purpose: 2.x needs compileSdk 37, AGP 9 and Kotlin 2.4) and Jetpack Compose.

Package `com.everettpenne.glass`, module `:glass`. Apps consume it with a composite build, so edits show up immediately and nothing is published:

```kotlin
// settings.gradle.kts
includeBuild("../Glass")
// app/build.gradle.kts
implementation("com.everettpenne:glass")
```

## Use

```kotlin
GlassTheme(GlassColors(accent = brandColor, red = ..., green = ...)) {   // Material theme + IosType + LocalGlassColors
    // a root page: record the page colour so controls inside scrolling content have something to bend
    GlassScreen(bars = { GlassTopBar("Title"); GlassTabBar(tabs, selected, onSelect, search = searchTab) }) { /* scrolling content */ }
}
```

Tabs are anything implementing `GlassTabSpec(route, label, icon)`; the label is the content description. `GlassTabBar(tabs, selected, onSelect, search = ...)` draws the capsule from `tabs` and an optional round search button.

## Gallery

`:gallery` is a sample app showing every component on one page over striped content, with an instrumented test that renders and exercises it. Open it after any change to the glass recipe:

```bash
./gradlew :gallery:installDebug
./gradlew :gallery:connectedDebugAndroidTest
```

Release builds need no extra keep rules: the AGSL shaders are plain strings and Backdrop ships its own consumer rules.

## Licence

MIT, see `LICENSE`. Inter is SIL OFL 1.1 (`docs/licenses/Inter-OFL.txt`); Backdrop is Apache-2.0.
