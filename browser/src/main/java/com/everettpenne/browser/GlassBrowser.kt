package com.everettpenne.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.everettpenne.glass.GlassDropdownMenu
import com.everettpenne.glass.GlassIconButton
import com.everettpenne.glass.GlassMenuItem
import com.everettpenne.glass.LocalGlassColors
import com.everettpenne.glass.glassPalette

/** Opens a web page in the app's own browser (or, with it off, in the phone's). [BrowserHost] provides it; the default does nothing. */
val LocalBrowser = compositionLocalOf<(String) -> Unit> { {} }

/**
 * Shows an HTML document the app has read itself (a saved page in a file manager): (html, title). It is drawn with JavaScript off and no access
 * to files or other content, so a page from storage can only be looked at; its web links open as web pages. [BrowserHost] provides it.
 */
val LocalHtmlViewer = compositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

/**
 * Draw once at the root of an app. Links the app opens through [LocalUriHandler] (every `uriHandler.openUri`) or [LocalBrowser] then go to
 * [GlassBrowser] when [inApp] is on, or to the phone's browser when it is off; mail, phone, map and store links go to the app that handles them.
 * An app with its own [UriHandler] (one that opens some links itself) provides it inside this and sends the rest to [LocalBrowser].
 */
@Composable
fun BrowserHost(inApp: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf<String?>(null) }
    var document by remember { mutableStateOf<Pair<String, String>?>(null) }
    val inAppNow by rememberUpdatedState(inApp)
    val open: (String) -> Unit = remember {
        { url ->
            when (BrowserUrls.kind(url)) {
                LinkKind.WEB -> if (inAppNow) page = url else Browsers.openOutside(context, url)
                LinkKind.APP -> Browsers.openApp(context, url)?.let { fallback -> if (inAppNow) page = fallback else Browsers.openOutside(context, fallback) }
                LinkKind.NONE -> Unit
            }
        }
    }
    val handler = remember { object : UriHandler { override fun openUri(uri: String) = open(uri) } }
    CompositionLocalProvider(LocalBrowser provides open, LocalUriHandler provides handler, LocalHtmlViewer provides { html, title -> document = html to title }) {
        content()
        page?.let { GlassBrowser(it, onClose = { page = null }) }
        document?.let { (html, title) -> GlassBrowser("about:blank", onClose = { document = null }, document = html, documentTitle = title, onLink = { url -> document = null; open(url) }) }
    }
}

/** Opening pages and links outside the app. */
object Browsers {
    /** Whether Vanadium is installed, so the button can say where the page will open. */
    fun hasVanadium(context: Context) = runCatching { context.packageManager.getPackageInfo(BrowserUrls.VANADIUM, 0) }.isSuccess

    /** Opens [url] in Vanadium when it is installed, otherwise in the phone's default browser. */
    fun openOutside(context: Context, url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (hasVanadium(context) && runCatching { context.startActivity(Intent(view).setPackage(BrowserUrls.VANADIUM)) }.isSuccess) return
        runCatching { context.startActivity(Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Hands a mail, phone, map, store or intent: link to the app for it. An intent: link is stripped of any component or selector (a page must
     * not be able to start a particular part of some app), and, when no app takes it, its web fallback address is returned to load instead.
     */
    fun openApp(context: Context, url: String): String? {
        val intent = if (url.startsWith("intent:", ignoreCase = true)) {
            val parsed = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return null
            val fallback = parsed.getStringExtra("browser_fallback_url")?.takeIf { BrowserUrls.kind(it) == LinkKind.WEB }
            parsed.component = null; parsed.selector = null
            parsed.addCategory(Intent.CATEGORY_BROWSABLE)
            if (runCatching { context.startActivity(parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isFailure) return fallback
            return null
        } else Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
        return null
    }

    fun share(context: Context, url: String, title: String?) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).apply { title?.let { putExtra(Intent.EXTRA_SUBJECT, it) } }
        runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** What the browser shows about its page, mirrored from the WebView's callbacks. */
@Stable
private class PageState(start: String) {
    var url by mutableStateOf(start)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(true)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    var crashed by mutableStateOf(false)
    var fullscreen by mutableStateOf<View?>(null)
    var fullscreenDone: WebChromeClient.CustomViewCallback? = null
    var finding by mutableStateOf(false)
    var matches by mutableStateOf(0 to 0)
}

/**
 * The suite's browser: a page in a WebView between glass bars. On GrapheneOS Android's WebView is Vanadium's, so pages are rendered by
 * Vanadium's hardened Chromium with its protections; elsewhere by the phone's WebView. The top bar has Done, the site (tap to type an address
 * or a search) and a menu (reload, find in page, desktop site, copy, open in Vanadium or the phone's browser, clear site data); the bottom bar
 * has back, forward, share and open outside.
 *
 * It keeps to the suite's privacy line: no third-party cookies, no location, camera or microphone for pages, no file or content access, mixed
 * content refused. Downloads go to Downloads through Android's
 * download manager; videos can go full screen; upload fields open the system file picker.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GlassBrowser(url: String, onClose: () -> Unit, modifier: Modifier = Modifier, document: String? = null, documentTitle: String? = null,
                 onLink: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val page = remember(url) { PageState(url) }
    var generation by remember { mutableIntStateOf(0) }
    var upload by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        upload?.onReceiveValue(uris.toTypedArray()); upload = null
    }
    val pickerNow by rememberUpdatedState(picker)

    val web = remember(url, generation) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.apply {
                // A document from storage gets no script at all: it is something to read, and it came from no web origin.
                javaScriptEnabled = document == null; domStorageEnabled = document == null
                allowFileAccess = false; allowContentAccess = false
                setGeolocationEnabled(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = true
                setSupportMultipleWindows(false)
                builtInZoomControls = true; displayZoomControls = false
                useWideViewPort = true; loadWithOverviewMode = true
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
            // (The X-Requested-With header, which named the app to every site, is no longer sent by current WebViews, Vanadium's included.)
            CookieManager.getInstance().let { it.setAcceptCookie(true); it.setAcceptThirdPartyCookies(this, false) }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url.toString()
                    return when (BrowserUrls.kind(target)) {
                        LinkKind.WEB -> if (document != null && onLink != null) { onLink(target); true } else false
                        LinkKind.APP -> { Browsers.openApp(context, target)?.let(view::loadUrl); true }
                        LinkKind.NONE -> true
                    }
                }
                override fun onPageStarted(view: WebView, address: String, favicon: Bitmap?) { page.url = address; page.loading = true; update(view) }
                override fun onPageFinished(view: WebView, address: String) { page.url = address; page.loading = false; update(view) }
                override fun doUpdateVisitedHistory(view: WebView, address: String, isReload: Boolean) { page.url = address; update(view) }
                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean { page.crashed = true; return true }
                private fun update(view: WebView) { page.canBack = view.canGoBack(); page.canForward = view.canGoForward(); page.title = view.title.orEmpty() }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) { page.progress = newProgress }
                override fun onReceivedTitle(view: WebView, title: String?) { page.title = title.orEmpty() }
                // Pages never get the camera, the microphone or protected media: the app asks for none of them.
                override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) { page.fullscreen = view; page.fullscreenDone = callback }
                override fun onHideCustomView() { page.fullscreen = null; page.fullscreenDone?.onCustomViewHidden(); page.fullscreenDone = null }
                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?) = false
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                    upload?.onReceiveValue(null); upload = callback
                    val type = params.acceptTypes.firstOrNull { it.isNotBlank() }?.takeIf { '/' in it } ?: "*/*"
                    return runCatching { pickerNow.launch(type) }.isSuccess.also { if (!it) { upload = null; callback.onReceiveValue(null) } }
                }
            }
            setDownloadListener { address, agent, disposition, mime, _ -> page.notice = download(context, address, agent, disposition, mime) }
            setFindListener { active, count, done -> if (done) page.matches = (if (count == 0) 0 else active + 1) to count }
            if (document != null) loadDataWithBaseURL(null, document, "text/html", "UTF-8", null) else loadUrl(url)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(web, lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) web.onPause() else if (e == Lifecycle.Event.ON_RESUME) web.onResume() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); web.stopLoading(); (web.parent as? ViewGroup)?.removeView(web); web.destroy() }
    }
    LaunchedEffect(page.notice) { if (page.notice != null) { kotlinx.coroutines.delay(3500); page.notice = null } }

    BackHandler {
        when {
            page.fullscreen != null -> web.webChromeClient?.onHideCustomView()
            page.finding -> { page.finding = false; web.clearMatches() }
            web.canGoBack() -> web.goBack()
            else -> onClose()
        }
    }

    val bars = MaterialTheme.colorScheme.background.copy(alpha = .96f)
    val ink = glassPalette().ink
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("glass-browser")) {
        Column(Modifier.fillMaxSize()) {
            TopBar(page, web, bars, onClose, documentTitle = documentTitle?.takeIf { document != null }, onDesktop = { on ->
                page.desktop = on
                web.settings.userAgentString = if (on) desktopAgent(WebSettings.getDefaultUserAgent(context)) else null
                web.reload()
            }, onClearData = {
                CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData(); web.clearCache(true); web.clearHistory()
                page.notice = context.getString(R.string.browser_cleared); web.reload()
            })
            if (page.loading && page.progress in 1..99) Box(Modifier.fillMaxWidth().height(2.dp)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(page.progress / 100f).background(LocalGlassColors.current.accent))
            } else Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = .08f)))
            if (page.finding) FindBar(page, web, bars)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (page.crashed) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.SentimentDissatisfied, null, tint = ink.copy(alpha = .6f), modifier = Modifier.size(48.dp))
                    Text(context.getString(R.string.browser_crashed), color = ink, style = MaterialTheme.typography.bodyLarge)
                    com.everettpenne.glass.GlassButton(context.getString(R.string.browser_reload), { page.crashed = false; generation++ })
                } else AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
            }
            if (document == null) BottomBar(page, web, bars) else Spacer(Modifier.navigationBarsPadding())
        }
        AnimatedVisibility(page.notice != null, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp), enter = fadeIn(), exit = fadeOut()) {
            Text(page.notice.orEmpty(), color = Color.White, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xE6202024)).padding(horizontal = 16.dp, vertical = 10.dp))
        }
        page.fullscreen?.let { v -> AndroidView(factory = { v }, modifier = Modifier.fillMaxSize().background(Color.Black)) }
    }
}

@Composable
private fun TopBar(page: PageState, web: WebView, bars: Color, onClose: () -> Unit, documentTitle: String? = null, onDesktop: (Boolean) -> Unit, onClearData: () -> Unit) {
    val context = LocalContext.current
    val ink = glassPalette().ink
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().background(bars).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(context.getString(R.string.browser_done), color = LocalGlassColors.current.accent, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 10.dp).testTag("browser-done"))
        Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50)).background(ink.copy(alpha = .07f))) {
            if (documentTitle != null) Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.Description, null, tint = ink.copy(alpha = .55f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(documentTitle, color = ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            else if (editing) AddressField(page.url, onGo = { typed -> editing = false; BrowserUrls.fromTyped(typed).takeIf { it.isNotEmpty() }?.let(web::loadUrl) }, onCancel = { editing = false })
            else Row(Modifier.fillMaxSize().clickable { editing = true }.padding(horizontal = 12.dp).testTag("browser-address"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Icon(if (BrowserUrls.isSecure(page.url)) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (BrowserUrls.isSecure(page.url)) "Secure" else "Not secure",
                    tint = ink.copy(alpha = if (BrowserUrls.isSecure(page.url)) .55f else .9f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(BrowserUrls.host(page.url), color = ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box {
            GlassIconButton(Icons.Rounded.MoreHoriz, "More", { menu = true }, size = 40)
            GlassDropdownMenu(menu, { menu = false }) {
                if (documentTitle != null) { GlassMenuItem(context.getString(R.string.browser_find), { page.finding = true; menu = false }, icon = Icons.Rounded.Search); return@GlassDropdownMenu }
                if (page.loading) GlassMenuItem(context.getString(R.string.browser_stop), { web.stopLoading(); menu = false }, icon = Icons.Rounded.Close)
                else GlassMenuItem(context.getString(R.string.browser_reload), { web.reload(); menu = false }, icon = Icons.Rounded.Refresh)
                GlassMenuItem(context.getString(R.string.browser_find), { page.finding = true; menu = false }, icon = Icons.Rounded.Search)
                GlassMenuItem(context.getString(R.string.browser_desktop), { onDesktop(!page.desktop); menu = false }, icon = Icons.Rounded.DesktopWindows, selected = page.desktop)
                GlassMenuItem(context.getString(R.string.browser_copy), {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(page.title, page.url)); menu = false
                }, icon = Icons.Rounded.Link)
                GlassMenuItem(context.getString(if (Browsers.hasVanadium(context)) R.string.browser_open_vanadium else R.string.browser_open_outside),
                    { Browsers.openOutside(context, page.url); menu = false }, icon = Icons.Rounded.OpenInBrowser)
                GlassMenuItem(context.getString(R.string.browser_clear), { onClearData(); menu = false }, icon = Icons.Rounded.DeleteSweep, destructive = true)
            }
        }
    }
}

@Composable
private fun AddressField(current: String, onGo: (String) -> Unit, onCancel: () -> Unit) {
    val ink = glassPalette().ink
    val focus = remember { FocusRequester() }
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onCancel)
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(value, { value = it }, Modifier.weight(1f).focusRequester(focus).testTag("browser-address-field"), singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink, fontSize = 15.sp), cursorBrush = SolidColor(LocalGlassColors.current.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { onGo(value.text) }))
        Icon(Icons.Rounded.Cancel, "Clear", tint = ink.copy(alpha = .4f), modifier = Modifier.size(18.dp).clickable { value = TextFieldValue("") })
    }
}

@Composable
private fun FindBar(page: PageState, web: WebView, bars: Color) {
    val context = LocalContext.current
    val ink = glassPalette().ink
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().background(bars).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(50)).background(ink.copy(alpha = .07f)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(context.getString(R.string.browser_find_hint), color = ink.copy(alpha = .4f), fontSize = 15.sp)
            BasicTextField(query, { query = it; web.findAllAsync(it) }, Modifier.fillMaxWidth().focusRequester(focus).testTag("browser-find-field"), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink, fontSize = 15.sp), cursorBrush = SolidColor(LocalGlassColors.current.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { web.findNext(true) }))
        }
        if (query.isNotEmpty()) Text("${page.matches.first}/${page.matches.second}", color = ink.copy(alpha = .6f), fontSize = 13.sp)
        GlassIconButton(Icons.Rounded.KeyboardArrowUp, "Previous match", { web.findNext(false) }, size = 36)
        GlassIconButton(Icons.Rounded.KeyboardArrowDown, "Next match", { web.findNext(true) }, size = 36)
        GlassIconButton(Icons.Rounded.Close, "Close find", { page.finding = false; web.clearMatches() }, size = 36)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomBar(page: PageState, web: WebView, bars: Color) {
    val context = LocalContext.current
    val ink = glassPalette().ink
    // The bar steps aside while the keyboard is up, so a form keeps the room it needs.
    if (WindowInsets.isImeVisible) { Spacer(Modifier.imePadding()); return }
    Row(Modifier.fillMaxWidth().background(bars).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { if (web.canGoBack()) web.goBack() }, tint = ink.copy(alpha = if (page.canBack) 1f else .3f))
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowForward, "Forward", { if (web.canGoForward()) web.goForward() }, tint = ink.copy(alpha = if (page.canForward) 1f else .3f))
        GlassIconButton(Icons.Rounded.IosShare, "Share", { Browsers.share(context, page.url, page.title.takeIf { it.isNotBlank() }) })
        GlassIconButton(Icons.Rounded.Explore, if (Browsers.hasVanadium(context)) "Open in Vanadium" else "Open in browser", { Browsers.openOutside(context, page.url) })
        if (page.loading) GlassIconButton(Icons.Rounded.Close, "Stop", { web.stopLoading() }) else GlassIconButton(Icons.Rounded.Refresh, "Reload", { web.reload() })
    }
}

/** The phone's own user agent dressed as a desktop browser, for a site's full layout. */
private fun desktopAgent(mobile: String): String =
    mobile.replace(Regex("\\(Linux; Android [^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "").replace("; wv", "")

/** Hands a download to Android's download manager, into Downloads, with the page's cookies; returns what to tell the person. */
private fun download(context: Context, url: String, agent: String?, disposition: String?, mime: String?): String {
    val name = BrowserUrls.fileName(url, disposition, mime)
    if (BrowserUrls.kind(url) != LinkKind.WEB) return context.getString(R.string.browser_cant_download)
    return runCatching {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(name).setMimeType(mime)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
        agent?.let { request.addRequestHeader("User-Agent", it) }
        context.getSystemService(DownloadManager::class.java).enqueue(request)
        context.getString(R.string.browser_downloading, name)
    }.getOrElse { context.getString(R.string.browser_cant_download) }
}
