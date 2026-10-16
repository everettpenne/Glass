package com.everettpenne.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    /** [progress] is 0..1, or null when the size is not known. */
    data class Downloading(val release: Release, val progress: Float?) : UpdateState
    data class Downloaded(val release: Release, val file: File) : UpdateState
    /** Android wants the user to allow installs from this app first; [file] is kept so one tap installs once they have. */
    data class NeedsPermission(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Checks the app's GitHub releases for a newer APK, downloads it, and hands it to the system installer. One per process (see [get]), so a
 * download carries on through a rotation. The UI is [UpdateDialog] plus whatever settings row the app draws from [state].
 */
class Updater private constructor(context: Context, val config: UpdateConfig) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences("updater", Context.MODE_PRIVATE)
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _dialog = MutableStateFlow(false)
    /** Whether [UpdateDialog] should be showing. */
    val dialogVisible: StateFlow<Boolean> = _dialog.asStateFlow()

    /** The check at launch: quiet unless there is something new that has not been waved away already. Does nothing while another run is going. */
    fun checkOnLaunch() = check(userInitiated = false)

    /** The settings row's tap: when an update is already known or in progress show it, otherwise ask GitHub. */
    fun onSettingsRowClick() = when (_state.value) {
        is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Downloaded, is UpdateState.NeedsPermission -> _dialog.value = true
        UpdateState.Checking -> Unit
        else -> check(userInitiated = true)
    }

    fun check(userInitiated: Boolean) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            try {
                val release = withContext(Dispatchers.IO) { fetchNewest() }
                if (release == null) {
                    _state.value = UpdateState.UpToDate
                    if (userInitiated) _dialog.value = true
                } else {
                    _state.value = UpdateState.Available(release)
                    if (userInitiated || prefs.getString(KEY_DISMISSED, null) != release.tag) _dialog.value = true
                }
            } catch (e: Exception) {
                if (!isActive) return@launch
                _state.value = if (userInitiated) UpdateState.Failed(e.message ?: "Couldn't reach GitHub.") else UpdateState.Idle
                if (userInitiated) _dialog.value = true
            }
        }
    }

    /** "Later": hides the dialog and stops the launch check from raising this version again (the settings row still offers it). */
    fun dismiss() {
        (_state.value as? UpdateState.Available)?.let { prefs.edit().putString(KEY_DISMISSED, it.release.tag).apply() }
        if (_state.value is UpdateState.UpToDate || _state.value is UpdateState.Failed) _state.value = UpdateState.Idle
        _dialog.value = false
    }

    fun startDownload() {
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.Failed -> return check(userInitiated = true)
            is UpdateState.Downloaded -> return install()
            is UpdateState.NeedsPermission -> return install()
            else -> return
        }
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, null)
            try {
                val file = withContext(Dispatchers.IO) { download(release) }
                _state.value = UpdateState.Downloaded(release, file)
                install()
            } catch (e: Exception) {
                if (!isActive) return@launch
                _state.value = UpdateState.Failed(e.message ?: "The download failed.")
            }
        }
    }

    fun cancelDownload() {
        val s = _state.value as? UpdateState.Downloading ?: return
        job?.cancel()
        _state.value = UpdateState.Available(s.release)
    }

    /** Opens the system installer on the downloaded APK, or Android's "install unknown apps" screen for this app if that is not allowed yet. */
    fun install() {
        val (release, file) = when (val s = _state.value) {
            is UpdateState.Downloaded -> s.release to s.file
            is UpdateState.NeedsPermission -> s.release to s.file
            else -> return
        }
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(release, file)
            app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        app.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        _state.value = UpdateState.Downloaded(release, file)
        _dialog.value = false
    }

    private fun fetchNewest(): Release? {
        val conn = URL("${config.apiBase}/repos/${config.repo}/releases?per_page=15").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000; conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "${app.packageName}-updater")
            if (conn.responseCode != 200) throw java.io.IOException("GitHub answered ${conn.responseCode}.")
            return ReleaseParser.newest(conn.inputStream.bufferedReader().use { it.readText() }, config.currentVersion)
        } finally { conn.disconnect() }
    }

    private suspend fun download(release: Release): File {
        val dir = File(app.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, release.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000; conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "${app.packageName}-updater")
            if (conn.responseCode != 200) throw java.io.IOException("The download answered ${conn.responseCode}.")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes.takeIf { it > 0 }
            var read = 0L; var lastPercent = -1
            conn.inputStream.use { input -> file.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    out.write(buffer, 0, n); read += n
                    val percent = if (total != null) (read * 100 / total).toInt() else -1
                    if (percent != lastPercent) { lastPercent = percent; _state.value = UpdateState.Downloading(release, if (total != null) read.toFloat() / total else null) }
                }
            } }
            if (release.sizeBytes > 0 && file.length() != release.sizeBytes) throw java.io.IOException("The download was cut short. Try again.")
            val info = app.packageManager.getPackageArchiveInfo(file.path, 0)
            if (info == null || info.packageName != app.packageName) throw java.io.IOException("That file is not an update for this app.")
            return file
        } catch (e: Exception) { file.delete(); throw e } finally { conn.disconnect() }
    }

    companion object {
        private const val KEY_DISMISSED = "dismissed_tag"
        @Volatile private var instance: Updater? = null
        fun get(context: Context, config: UpdateConfig): Updater =
            instance?.takeIf { it.config == config } ?: synchronized(this) { instance?.takeIf { it.config == config } ?: Updater(context, config).also { instance = it } }

        /** The line under the settings row's title. */
        fun statusText(state: UpdateState, currentVersion: String): String = when (state) {
            UpdateState.Idle -> "Version $currentVersion"
            UpdateState.Checking -> "Checking…"
            UpdateState.UpToDate -> "You have the latest version ($currentVersion)"
            is UpdateState.Available -> "Version ${state.release.version} is available"
            is UpdateState.Downloading -> "Downloading ${state.release.version}" + (state.progress?.let { " · ${(it * 100).toInt()}%" } ?: "…")
            is UpdateState.Downloaded -> "Version ${state.release.version} is ready to install"
            is UpdateState.NeedsPermission -> "Allow installs from this app, then tap to install ${state.release.version}"
            is UpdateState.Failed -> state.message
        }
    }
}
