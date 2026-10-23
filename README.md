# Glass

The iOS 26 "liquid glass" UI kit shared by Seekur, Spice, Thumper and Sietch: glass surfaces, controls, bars, tabs, slider, menus, alerts, sheets, the Apple HIG type scale set in Inter, and the iOS-style Material theme. Built on [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 1.0.0 (kept on purpose: 2.x needs compileSdk 37, AGP 9 and Kotlin 2.4) and Jetpack Compose.

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

## Updater

`:updater` (`implementation("com.everettpenne:updater")`, package `com.everettpenne.updater`) adds in-app updates from GitHub releases:

```kotlin
val updater = Updater.get(context, UpdateConfig("owner/Repo", BuildConfig.VERSION_NAME))
LaunchedEffect(Unit) { updater.checkOnLaunch() }          // quiet unless something newer exists
UpdateDialog(updater, "AppName")                          // glass alert: notes, Update / Later, progress, Install
CompositionLocalProvider(LocalUpdater provides updater) { /* so Settings can reach it */ }
// Settings row: NavRow("Check for Updates", updater.collectStatus()) { updater.onSettingsRowClick() }
```

It lists the repo's releases (not `/releases/latest`, which hides pre-releases), picks the newest one that has an https `.apk` asset and a higher version than the running app, downloads it into the cache (size and package name checked), then hands it to the system installer through a FileProvider. The library's manifest brings the `INTERNET` and `REQUEST_INSTALL_PACKAGES` permissions and the provider, so apps need no manifest changes. Android asks once for "install unknown apps". The APK must be signed with the same key as the installed app, so updates only apply to release builds. The launch check does not nag again about a version dismissed with "Later"; the settings row always offers it.

## Media

`:media` (`implementation("com.everettpenne:media")`, package `com.everettpenne.media`) is the full-screen image viewer, full-screen video, and saving pictures:

```kotlin
ImageViewerHost(saveAlbum = "Seekur") { /* content; anything inside can call LocalImageViewer.current(listOf(url), 0) */ }   // or provide LocalImageViewer yourself and draw ImageViewer(urls, start, saveAlbum) {}
VideoViewerHost { /* anything inside can call LocalVideoViewer.current(uri, title): a full-screen player that owns its own ExoPlayer */ }
MediaSaver.saveImage(context, url, "Seekur")            // copies the original bytes into Pictures/Seekur through MediaStore (no permission)
FullscreenVideo(player, title) { /* onDismiss */ }     // any Media3 Player: an ExoPlayer, or a MediaController of a playback service
VideoSurface(player, enabled = !fullscreen)             // the picture, for the inline view; disable it while FullscreenVideo has the player
```

`GlassMiniPlayer(title, subtitle, artwork, progress, playing, onOpen, onToggle, onNext)` is the now-playing capsule for above the tab bar: cover, two lines, play/pause, optional next, and a hairline of progress along its bottom edge. It is hidden by the caller when nothing is playing.

`ImageViewer`: pinch to zoom up to 6x, pan, double-tap to zoom, swipe between images, swipe down to dismiss, tap to hide the controls. `FullscreenVideo`: black, system bars hidden, turns the phone to landscape for wide video and back afterwards, glass controls (play/pause, seek slider, remaining time, mute) that hide while playing, tap to show or hide, double-tap either half to skip 10 s, Back to leave. The player is the caller's, so playback carries on across the switch with no re-buffering.

## Rows and lists

`glass/GlassRows.kt` has the list pieces every app was copying: `GlassSectionHeader` (a heading with optional trailing content), `GlassGroup` (an inset-grouped card for rows), `GlassListRow` (leading content, title, subtitle, trailing content, whole row tappable), `GlassRowDivider`, `GlassSearchField` (the clear-glass search capsule, with optional auto-focus) and `GlassEmptyState` (the glass disc, title, message and one optional action used for "nothing here yet"). They are deliberately flat rather than glass-on-glass so a long page stays smooth under the refracting bars.

## Browser

`implementation("com.everettpenne:browser")`. A built-in web browser for the apps, so a link opens over the app instead of sending you elsewhere.
Draw `BrowserHost(inApp = setting) { … }` once at the root: every `LocalUriHandler.openUri` (and `LocalBrowser`) then opens web pages in
`GlassBrowser`, and mail, phone, map and store links in the app for them. With `inApp` off, pages open in Vanadium when it is installed, else the
phone's browser.

`GlassBrowser` is a WebView between glass bars: Done, the site (tap to type an address or a search), a menu (reload, find on page, desktop site,
copy link, open in Vanadium, clear site data), and back, forward, share and open-outside along the bottom. On GrapheneOS Android's WebView *is*
Vanadium's, so pages render in Vanadium's hardened Chromium. Third-party cookies are refused, pages get no location, camera or microphone, no file
or content access and no mixed content; downloads go to Downloads; videos can go full screen; upload fields open the file picker. Address
handling (`BrowserUrls`) is unit-tested.

## Gallery

`:gallery` is a sample app showing every component on one page over striped content, with an instrumented test that renders and exercises it. Open it after any change to the glass recipe:

```bash
./gradlew :gallery:installDebug
./gradlew :gallery:connectedDebugAndroidTest
```

Release builds need no extra keep rules: the AGSL shaders are plain strings and Backdrop ships its own consumer rules.

## Licence

MIT, see `LICENSE`. Inter is SIL OFL 1.1 (`docs/licenses/Inter-OFL.txt`); Backdrop is Apache-2.0.
