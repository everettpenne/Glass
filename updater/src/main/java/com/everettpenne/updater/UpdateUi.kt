package com.everettpenne.updater

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import com.everettpenne.glass.GlassAlert

/** The app's [Updater], provided once at the root so the launch check, the dialog and the settings row all share it. Null when not provided. */
val LocalUpdater = compositionLocalOf<Updater?> { null }

private const val NOTES_LIMIT = 500

/** Release notes are Markdown; the alert shows plain text, so strip the emphasis, headings and links and turn list dashes into bullets. */
internal fun plainNotes(markdown: String): String = markdown.lines().joinToString("\n") { line ->
    line.trim().replace(Regex("^#+\\s*"), "").replace(Regex("^[-*]\\s+"), "• ")
        .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1").replace("**", "").replace("__", "").replace("`", "")
}.replace(Regex("\n{3,}"), "\n\n").trim()

/** The update prompt as a glass alert: release notes with Update / Later, then download progress, then Install. Draw it once, at the root. */
@Composable
fun UpdateDialog(updater: Updater, appName: String) {
    val visible by updater.dialogVisible.collectAsState()
    val state by updater.state.collectAsState()
    if (!visible) return
    val current = updater.config.currentVersion
    when (val s = state) {
        is UpdateState.Available -> GlassAlert(
            title = "$appName ${s.release.version} is available",
            message = buildString {
                append("You have $current.")
                val notes = plainNotes(s.release.notes)
                if (notes.isNotBlank()) append("\n\n").append(if (notes.length > NOTES_LIMIT) notes.take(NOTES_LIMIT).trimEnd() + "…" else notes)
            },
            confirmLabel = "Update", onConfirm = updater::startDownload, onDismiss = updater::dismiss, dismissLabel = "Later")
        is UpdateState.Downloading -> GlassAlert(
            title = "Downloading ${s.release.version}",
            message = s.progress?.let { "${(it * 100).toInt()}%" } ?: "Starting…",
            confirmLabel = "Cancel", onConfirm = updater::cancelDownload, onDismiss = updater::dismiss, dismissLabel = "Hide")
        is UpdateState.Downloaded -> GlassAlert(
            title = "Ready to install", message = "$appName ${s.release.version} has been downloaded. Android will ask you to confirm the install.",
            confirmLabel = "Install", onConfirm = updater::install, onDismiss = updater::dismiss, dismissLabel = "Close")
        is UpdateState.NeedsPermission -> GlassAlert(
            title = "Allow installs", message = "Android needs your permission for $appName to install updates. Turn it on in Settings, come back and tap Install.",
            confirmLabel = "Install", onConfirm = updater::install, onDismiss = updater::dismiss, dismissLabel = "Close")
        UpdateState.UpToDate -> GlassAlert(
            title = "You're up to date", message = "$appName $current is the latest version.",
            confirmLabel = "OK", onConfirm = updater::dismiss, onDismiss = updater::dismiss, dismissLabel = "Close")
        is UpdateState.Failed -> GlassAlert(
            title = "Couldn't update", message = s.message,
            confirmLabel = "Try Again", onConfirm = updater::startDownload, onDismiss = updater::dismiss, dismissLabel = "Close")
        else -> Unit
    }
}

/** The subtitle for a "Check for Updates" settings row; it follows the updater's state. */
@Composable
fun Updater.collectStatus(): String = Updater.statusText(state.collectAsState().value, config.currentVersion)
