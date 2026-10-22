package com.everettpenne.browser

import java.net.URI

/** What to do with an address the browser is asked to load. */
enum class LinkKind {
    /** A web page: http or https, opened in the browser. */
    WEB,
    /** Something only another app can open: mail, phone, maps, an intent: link, a store page. Handed to Android. */
    APP,
    /** Nothing worth doing (javascript:, data:, about:blank, a malformed address). Ignored. */
    NONE,
}

/** The browser's address arithmetic, with no Android classes so it is unit-tested. */
object BrowserUrls {
    /** Vanadium, GrapheneOS's hardened Chromium browser; its WebView is what Android's WebView is on GrapheneOS. */
    const val VANADIUM = "app.vanadium.browser"

    /** The search page a typed phrase goes to: DuckDuckGo's, which needs no account and keeps no history of searches. */
    const val SEARCH = "https://duckduckgo.com/?q="

    fun kind(url: String): LinkKind {
        val scheme = url.substringBefore(':', "").lowercase()
        return when (scheme) {
            "http", "https" -> LinkKind.WEB
            "mailto", "tel", "sms", "smsto", "geo", "intent", "market", "magnet", "matrix", "signal", "sgnl", "xmpp" -> LinkKind.APP
            else -> LinkKind.NONE
        }
    }

    /** The site's name as the address bar shows it: the host without "www.", or the address itself when it has no host. */
    fun host(url: String): String {
        val h = runCatching { URI(url).host }.getOrNull() ?: return url
        return h.removePrefix("www.")
    }

    fun isSecure(url: String) = url.startsWith("https://", ignoreCase = true)

    /**
     * What a typed address bar entry means: an address as typed when it has a scheme, https:// added when it looks like a host
     * ("example.org/page", "localhost:8080"), and otherwise a search for the words.
     */
    fun fromTyped(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return t
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t)) return t
        val looksLikeHost = !t.contains(' ') && (Regex("^[^/\\s]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$").matches(t) || Regex("^localhost(:\\d+)?(/.*)?$").matches(t))
        return if (looksLikeHost) "https://$t" else SEARCH + java.net.URLEncoder.encode(t, "UTF-8")
    }

    /**
     * Where a web shortcut file points: a Windows `.url` ("URL=https://…" under [InternetShortcut]), a macOS `.webloc` (a plist with the
     * address in its <string>) or a Linux `.desktop` link (URL=). Null when it holds no web address.
     */
    fun shortcutTarget(text: String): String? {
        val raw = Regex("(?m)^\\s*URL\\s*=\\s*(\\S+)\\s*$").find(text)?.groupValues?.get(1)
            ?: Regex("<string>\\s*([^<\\s]+)\\s*</string>").find(text)?.groupValues?.get(1)
        return raw?.replace("&amp;", "&")?.takeIf { kind(it) == LinkKind.WEB }
    }

    /** A file name for a download: the one the server gave, else the last part of the address, made safe for a file system. */
    fun fileName(url: String, contentDisposition: String?, mime: String?): String {
        val fromHeader = contentDisposition?.let { cd ->
            Regex("filename\\*=(?:UTF-8'')?([^;]+)", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)?.let { runCatching { java.net.URLDecoder.decode(it.trim('"'), "UTF-8") }.getOrNull() }
                ?: Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)
        }
        val fromUrl = url.substringBefore('?').substringBefore('#').substringAfterLast('/').let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        var name = (fromHeader ?: fromUrl).trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(120)
        if (name.isBlank() || name == "_") name = "download"
        if ('.' !in name) extensionFor(mime)?.let { name += ".$it" }
        return name
    }

    private fun extensionFor(mime: String?): String? = when (mime?.substringBefore(';')?.trim()?.lowercase()) {
        "application/pdf" -> "pdf"; "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"
        "audio/mpeg" -> "mp3"; "video/mp4" -> "mp4"; "application/zip" -> "zip"; "text/plain" -> "txt"; "application/epub+zip" -> "epub"
        else -> null
    }
}
