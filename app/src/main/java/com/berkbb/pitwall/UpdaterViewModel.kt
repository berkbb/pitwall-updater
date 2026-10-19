package com.berkbb.pitwall

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdaterUiState(
    val installedVersion: String = "",
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val isInstalled: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val isSignatureConflict: Boolean = false,
    val downloadProgress: Int = 0,
    val statusResId: Int = R.string.status_detecting,
    // Format argument for statusResId: Int for %1$d strings, String for %1$s strings, null if none
    val statusArg: Any? = null,
    // Pitwall Updater's own release check (About window and the header line)
    val selfUpdate: SelfUpdateStatus = SelfUpdateStatus.Idle,
    // Set by a manual check from About: shown in a result window until dismissed
    val selfUpdateResult: SelfUpdateStatus? = null,
    val selfInstall: SelfInstallState = SelfInstallState.Idle
)

class UpdaterViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UpdaterUiState())
    val uiState: StateFlow<UpdaterUiState> = _uiState.asStateFlow()

    private var latestRelease: ReleaseInfo? = null
    private var installJob: Job? = null
    private var selfInstallJob: Job? = null
    @Volatile private var activeConnection: HttpURLConnection? = null

    private val app: Application get() = getApplication()

    init {
        refreshInstalledState()
        checkForUpdates()
        observeInstallEvents()
        checkSelfUpdate(manual = false)
    }

    /**
     * Checks Pitwall Updater's own GitHub releases: quietly at start-up (only the header line
     * shows a newer version), or from About, where every result opens a result window.
     */
    fun checkSelfUpdate(manual: Boolean = true) {
        if (_uiState.value.selfUpdate is SelfUpdateStatus.Checking) return
        viewModelScope.launch {
            _uiState.update { it.copy(selfUpdate = SelfUpdateStatus.Checking) }
            val status = withContext(Dispatchers.IO) {
                try {
                    SelfUpdateChecker.check(BuildConfig.VERSION_NAME)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    SelfUpdateStatus.Failed
                }
            }
            _uiState.update {
                it.copy(selfUpdate = status, selfUpdateResult = if (manual) status else it.selfUpdateResult)
            }
        }
    }

    fun dismissSelfUpdateResult() {
        _uiState.update {
            // A finished or failed attempt is forgotten with its window; a running one keeps going.
            val install = if (it.selfInstall is SelfInstallState.Failed) SelfInstallState.Idle else it.selfInstall
            it.copy(selfUpdateResult = null, selfInstall = install)
        }
    }

    /**
     * Downloads the newer Pitwall Updater APK for this device, checks it against the release's
     * SHA-256 and installs it over this app; the TV asks for confirmation and the app restarts.
     */
    fun installSelfUpdate() {
        val update = (_uiState.value.selfUpdateResult ?: _uiState.value.selfUpdate) as? SelfUpdateStatus.Available ?: return
        val apk = update.apk ?: run {
            _uiState.update { it.copy(selfInstall = SelfInstallState.Failed(R.string.self_update_no_apk, SELF_RELEASES_PAGE)) }
            return
        }
        if (!app.packageManager.canRequestPackageInstalls()) {
            _uiState.update { it.copy(selfInstall = SelfInstallState.Failed(R.string.status_permission_required)) }
            openInstallPermissionSettings()
            return
        }
        if (selfInstallJob?.isActive == true) return

        selfInstallJob = viewModelScope.launch {
            _uiState.update { it.copy(selfInstall = SelfInstallState.Downloading(0)) }
            val target = File(app.cacheDir, "pitwall-updater-${ReleaseResolver.cacheFileName(apk.tag).removeSuffix(".apkm")}.apk")
            val downloaded = withContext(Dispatchers.IO) { downloadSelfApk(apk, target) }
            if (!downloaded) {
                _uiState.update { it.copy(selfInstall = SelfInstallState.Failed(R.string.status_download_failed)) }
                return@launch
            }
            // Reopen the app once the system has replaced it (see SelfUpdateRestartReceiver)
            app.getSharedPreferences(SelfUpdateRestartReceiver.PREFS, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(SelfUpdateRestartReceiver.KEY_PENDING, true).apply()
            val result = withContext(Dispatchers.IO) { PackageInstallerHelper.installSelfApk(app, target) }
            _uiState.update {
                it.copy(
                    selfInstall = if (result == InstallStartResult.STARTED) SelfInstallState.WaitingForConfirmation
                    else SelfInstallState.Failed(R.string.status_install_failed)
                )
            }
        }
    }

    /** Downloads into a .part file and keeps it only when it matches the release digest/size. */
    private suspend fun downloadSelfApk(apk: ReleaseInfo, target: File): Boolean {
        if (ApkmCache.isValid(target, apk)) return true
        val part = ApkmCache.partialFileFor(target)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(apk.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PitwallUpdater/${BuildConfig.VERSION_NAME}")
                connectTimeout = 15000
                readTimeout = 30000
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return false
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: apk.size
            var done = 0L
            var lastProgress = -1
            conn.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        val progress = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                        if (progress != lastProgress) {
                            lastProgress = progress
                            _uiState.update { it.copy(selfInstall = SelfInstallState.Downloading(progress)) }
                        }
                    }
                }
            }
            if (!ApkmCache.isValid(part, apk)) return false
            target.delete()
            return part.renameTo(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            conn?.disconnect()
            if (part.exists()) part.delete()
        }
    }

    /** Opens the system "Install unknown apps" setting for this app (security settings as a fallback). */
    private fun openInstallPermissionSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${app.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    private fun observeInstallEvents() {
        viewModelScope.launch {
            InstallEvents.events.collect { event ->
                if (event.selfUpdate) {
                    // Success normally never arrives: the system replaces (and stops) this app first.
                    _uiState.update {
                        it.copy(
                            selfInstall = when (event) {
                                is InstallEvent.Success -> SelfInstallState.Idle
                                is InstallEvent.Failure -> SelfInstallState.Failed(R.string.toast_install_failed, event.message)
                            }
                        )
                    }
                    if (event is InstallEvent.Failure) {
                        app.getSharedPreferences(SelfUpdateRestartReceiver.PREFS, android.content.Context.MODE_PRIVATE)
                            .edit().remove(SelfUpdateRestartReceiver.KEY_PENDING).apply()
                    }
                    return@collect
                }
                when (event) {
                    is InstallEvent.Success -> {
                        _uiState.update { it.copy(isSignatureConflict = false) }
                        refreshInstalledState(preferredStatus = R.string.status_installed_success)
                    }
                    is InstallEvent.Failure -> {
                        _uiState.update {
                            if (event.isSignatureConflict) {
                                it.copy(
                                    isDownloading = false,
                                    isSignatureConflict = true,
                                    statusResId = R.string.status_signature_conflict,
                                    statusArg = null
                                )
                            } else {
                                it.copy(
                                    isDownloading = false,
                                    statusResId = R.string.toast_install_failed,
                                    statusArg = event.message
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun onAppResumed() {
        val canInstall = app.packageManager.canRequestPackageInstalls()
        val preferred = if (canInstall && _uiState.value.statusResId == R.string.status_permission_required) {
            R.string.status_permission_granted
        } else null

        refreshInstalledState(preferredStatus = preferred)
    }

    private fun installedF1TvVersion(): String? {
        return try {
            app.packageManager.getPackageInfo(F1TV_PACKAGE_NAME, 0).versionName ?: "N/A"
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun statusResFor(status: UpdateStatus): Int = when (status) {
        UpdateStatus.NOT_INSTALLED -> R.string.status_not_installed
        UpdateStatus.UPDATE_AVAILABLE -> R.string.status_update_available
        UpdateStatus.UP_TO_DATE -> R.string.status_up_to_date
    }

    fun refreshInstalledState(preferredStatus: Int? = null) {
        val version = installedF1TvVersion()
        val installed = version != null
        val status = ReleaseResolver.evaluate(version, installed, latestRelease?.tag)
        // Kurulum/indirme sürerken (ör. onay ekranından dönüşte) ilerleme mesajını ezme
        val keepStatus = preferredStatus == null && installJob?.isActive == true

        _uiState.update {
            val conflict = it.isSignatureConflict && installed
            it.copy(
                installedVersion = version.orEmpty(),
                isInstalled = installed,
                isUpdateAvailable = status != UpdateStatus.UP_TO_DATE,
                isSignatureConflict = conflict,
                statusResId = when {
                    keepStatus -> it.statusResId
                    preferredStatus != null -> preferredStatus
                    conflict -> R.string.status_signature_conflict
                    else -> statusResFor(status)
                },
                statusArg = if (keepStatus) it.statusArg else null
            )
        }
    }

    fun checkForUpdates() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, statusResId = R.string.status_checking, statusArg = null)
            }
            try {
                val release = fetchLatestRelease()
                latestRelease = release
                _uiState.update {
                    it.copy(
                        latestVersion = release?.tag.orEmpty(),
                        downloadUrl = release?.downloadUrl.orEmpty(),
                        isLoading = false
                    )
                }
                if (release == null) {
                    _uiState.update { it.copy(statusResId = R.string.status_no_release, statusArg = null) }
                } else {
                    refreshInstalledState()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusResId = R.string.status_check_failed,
                        statusArg = e.localizedMessage ?: e.javaClass.simpleName
                    )
                }
            }
        }
    }

    private suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = (URL(UPSTREAM_RELEASES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PitwallUpdater/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10000
            readTimeout = 10000
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
            ReleaseResolver.selectLatestStable(parseReleases(JSONArray(jsonStr)))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseReleases(array: JSONArray): List<RawRelease> {
        return (0 until array.length()).map { i ->
            val release = array.getJSONObject(i)
            val assetsJson = release.optJSONArray("assets") ?: JSONArray()
            val assets = (0 until assetsJson.length()).map { j ->
                val asset = assetsJson.getJSONObject(j)
                ReleaseAsset(
                    name = asset.optString("name", ""),
                    downloadUrl = asset.optString("browser_download_url", ""),
                    size = asset.optLong("size", 0L),
                    digest = if (asset.isNull("digest")) null else asset.optString("digest", "")
                )
            }
            RawRelease(
                tagName = release.optString("tag_name", ""),
                isDraft = release.optBoolean("draft", false),
                isPrerelease = release.optBoolean("prerelease", false),
                assets = assets
            )
        }
    }

    fun downloadAndInstall() {
        if (!app.packageManager.canRequestPackageInstalls()) {
            _uiState.update { it.copy(statusResId = R.string.status_permission_required, statusArg = null) }
            openInstallPermissionSettings()
            return
        }

        val release = latestRelease ?: return
        if (installJob?.isActive == true) return

        installJob = viewModelScope.launch {
            val target = ApkmCache.fileFor(app.cacheDir, release)
            val cached = withContext(Dispatchers.IO) {
                ApkmCache.pruneExcept(app.cacheDir, target)
                ApkmCache.isValid(target, release).also { valid -> if (!valid) target.delete() }
            }

            val required = ApkmCache.requiredFreeBytes(release.size, cached)
            val available = withContext(Dispatchers.IO) { availableBytes(app.cacheDir) }
            if (available < required) {
                _uiState.update {
                    it.copy(
                        statusResId = R.string.status_insufficient_storage,
                        statusArg = ApkmCache.toMegabytesCeil(required)
                    )
                }
                return@launch
            }

            if (!cached) {
                _uiState.update {
                    it.copy(
                        isDownloading = true,
                        downloadProgress = 0,
                        statusResId = R.string.status_starting_download,
                        statusArg = null
                    )
                }
                val downloaded = withContext(Dispatchers.IO) { download(release, target) }
                if (!downloaded) {
                    _uiState.update {
                        it.copy(isDownloading = false, statusResId = R.string.status_download_failed, statusArg = null)
                    }
                    return@launch
                }
            }

            _uiState.update {
                it.copy(isDownloading = false, statusResId = R.string.status_preparing, statusArg = null)
            }
            val result = withContext(Dispatchers.IO) {
                PackageInstallerHelper.installApkm(app, target)
            }
            val statusRes = when (result) {
                InstallStartResult.STARTED -> R.string.status_confirm_prompt
                InstallStartResult.NO_COMPATIBLE_ABI -> R.string.status_no_compatible_abi
                InstallStartResult.FAILED -> R.string.status_install_failed
            }
            _uiState.update { it.copy(statusResId = statusRes, statusArg = null) }
        }.also { job ->
            job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 0,
                            statusResId = R.string.status_download_cancelled,
                            statusArg = null
                        )
                    }
                }
            }
        }
    }

    /** Cancels an in-progress download; the partial file is removed by [download]'s cleanup. */
    fun cancelDownload() {
        if (!_uiState.value.isDownloading) return
        installJob?.cancel()
        // Engelleyen read() çağrısını hemen sonlandırmak için bağlantıyı kapat
        activeConnection?.let { connection ->
            viewModelScope.launch(Dispatchers.IO) { connection.disconnect() }
        }
    }

    /** Space the system can provide (including clearable cache), falling back to raw usable space. */
    private fun availableBytes(dir: File): Long {
        return try {
            val storageManager = app.getSystemService(StorageManager::class.java)
            storageManager.getAllocatableBytes(storageManager.getUuidForPath(dir))
        } catch (e: Exception) {
            dir.usableSpace
        }
    }

    /** Downloads into a .part file, verifies it against the release digest/size and only then publishes it. */
    private suspend fun download(release: ReleaseInfo, target: File): Boolean {
        val part = ApkmCache.partialFileFor(target)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(release.downloadUrl).openConnection() as HttpURLConnection).also { activeConnection = it }.apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PitwallUpdater/${BuildConfig.VERSION_NAME}")
                connectTimeout = 15000
                readTimeout = 30000
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return false

            val totalLength = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
            var downloaded = 0L
            var lastProgress = -1

            conn.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalLength > 0) {
                            val progress = ((downloaded * 100) / totalLength).toInt().coerceIn(0, 100)
                            // Yalnızca yüzde değiştiğinde UI'ı güncelle
                            if (progress != lastProgress) {
                                lastProgress = progress
                                _uiState.update {
                                    it.copy(
                                        downloadProgress = progress,
                                        statusResId = R.string.status_downloading,
                                        statusArg = progress
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!ApkmCache.isValid(part, release)) return false
            target.delete()
            return part.renameTo(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            activeConnection = null
            conn?.disconnect()
            if (part.exists()) part.delete()
        }
    }

    fun launchF1TV() {
        val pm = app.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(F1TV_PACKAGE_NAME)
            ?: pm.getLaunchIntentForPackage(F1TV_PACKAGE_NAME)

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        } else {
            _uiState.update { it.copy(statusResId = R.string.btn_not_installed, statusArg = null) }
        }
    }

    /** Opens the system uninstall prompt; required when the installed F1 TV uses a different signing key. */
    fun uninstallF1TV() {
        try {
            val intent = Intent(Intent.ACTION_DELETE, "package:$F1TV_PACKAGE_NAME".toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
