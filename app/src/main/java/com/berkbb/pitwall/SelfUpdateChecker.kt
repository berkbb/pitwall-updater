package com.berkbb.pitwall

import android.os.Environment
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

const val SELF_REPO = "berkbb/pitwall-updater"
const val SELF_RELEASE_API_URL = "https://api.github.com/repos/$SELF_REPO/releases/latest"
const val SELF_RELEASES_PAGE = "github.com/$SELF_REPO/releases"

/** How often Pitwall Updater quietly checks GitHub for a newer release at startup. */
enum class UpdateCheckInterval(val labelRes: Int, val millis: Long) {
    OFF(R.string.auto_check_off, 0L),
    DAILY(R.string.auto_check_daily, 86_400_000L),
    WEEKLY(R.string.auto_check_weekly, 7 * 86_400_000L);

    fun next(): UpdateCheckInterval = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(raw: String?): UpdateCheckInterval = entries.firstOrNull { it.name == raw } ?: DAILY
    }
}

/** Pure schedule check for optional startup self-update checks. */
object UpdateSchedule {
    fun shouldCheck(interval: UpdateCheckInterval, lastCheckedMs: Long, nowMs: Long): Boolean {
        if (interval == UpdateCheckInterval.OFF) return false
        if (lastCheckedMs <= 0L || nowMs < lastCheckedMs) return true
        return nowMs - lastCheckedMs >= interval.millis
    }
}

/** Available and total internal data storage on the TV (no permissions required). */
data class StorageSummary(val freeBytes: Long, val totalBytes: Long)

object StorageFormat {
    fun formatBytes(bytes: Long, locale: Locale = Locale.ROOT): String {
        val safe = bytes.coerceAtLeast(0L)
        return if (safe < 1_000_000_000L) "${safe / 1_000_000L} MB"
        else String.format(locale, "%.1f GB", safe / 1_000_000_000.0)
    }

    fun usedPercent(freeBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        val used = (totalBytes - freeBytes).coerceIn(0L, totalBytes)
        return ((used * 100.0) / totalBytes).roundToInt().coerceIn(0, 100)
    }
}

fun readDeviceStorage(): StorageSummary? = runCatching {
    val stat = StatFs(Environment.getDataDirectory().path)
    StorageSummary(freeBytes = stat.availableBytes, totalBytes = stat.totalBytes)
}.getOrNull()

/** Result of checking Pitwall Updater's own GitHub releases (not F1 TV's). */
sealed interface SelfUpdateStatus {
    data object Idle : SelfUpdateStatus
    data object Checking : SelfUpdateStatus
    data class UpToDate(val current: String) : SelfUpdateStatus
    /** [apk] is the release APK for this device, or null when the release has none to install. */
    data class Available(val latest: String, val current: String, val apk: ReleaseInfo?) : SelfUpdateStatus
    /** No published release, or the repository is not public (GitHub answers 404). */
    data object Unavailable : SelfUpdateStatus
    data object Failed : SelfUpdateStatus
}

/** Downloading and installing a newer Pitwall Updater over itself. */
sealed interface SelfInstallState {
    data object Idle : SelfInstallState
    data class Downloading(val progress: Int) : SelfInstallState
    /** The system installer was started; the TV shows its confirmation prompt. */
    data object WaitingForConfirmation : SelfInstallState
    data class Failed(val messageRes: Int, val arg: String? = null) : SelfInstallState
}

object SelfUpdateChecker {

    /** Removes downloaded Pitwall Updater APKs (never touching F1 TV .apkm bundles). */
    fun cleanCachedApks(cacheDir: File) {
        cacheDir.listFiles()?.forEach { file ->
            val name = file.name
            if (file.isFile && name.startsWith("pitwall-updater-") && (name.endsWith(".apk") || name.endsWith(".apk.part"))) {
                file.delete()
            }
        }
    }

    /** Blocking; call off the main thread. */
    fun check(currentVersion: String): SelfUpdateStatus {
        val connection = (URL(SELF_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PitwallUpdater/$currentVersion")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10000
            readTimeout = 10000
        }
        try {
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val tag = json.optString("tag_name", "")
                    val apk = selectApk(tag, ReleaseResolver.parseAssets(json.optJSONArray("assets"), SELF_REPO), android.os.Build.SUPPORTED_ABIS)
                    statusFor(tag, currentVersion, apk)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> SelfUpdateStatus.Unavailable
                else -> throw IOException("HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Per-ABI APK names from older releases; current releases (see package.sh) ship only the universal APK. */
    private val ABI_SUFFIXES = mapOf("arm64-v8a" to "arm64", "armeabi-v7a" to "arm32", "x86_64" to "x64")

    /** The APK for the first supported ABI the release ships, else the universal APK, else null. */
    fun selectApk(tag: String, assets: List<ReleaseAsset>, supportedAbis: Array<String>): ReleaseInfo? {
        val apks = assets.filter { it.name.endsWith("-release.apk") && it.downloadUrl.isNotBlank() }
        val asset = supportedAbis.asSequence()
            .mapNotNull { ABI_SUFFIXES[it] }
            .mapNotNull { suffix -> apks.firstOrNull { it.name.endsWith("-$suffix-release.apk") } }
            .firstOrNull()
            ?: apks.firstOrNull { it.name.endsWith("-universal-release.apk") }
            ?: return null
        return ReleaseInfo(tag.trim(), asset.downloadUrl, asset.size, ReleaseResolver.parseSha256(asset.digest), asset.apiUrl)
    }

    fun statusFor(tag: String, currentVersion: String, apk: ReleaseInfo? = null): SelfUpdateStatus {
        val latest = ReleaseResolver.parseVersion(tag)
        if (latest.isEmpty()) return SelfUpdateStatus.Unavailable
        val latestName = latest.joinToString(".")
        return if (ReleaseResolver.compareVersions(latest, ReleaseResolver.parseVersion(currentVersion)) > 0) {
            SelfUpdateStatus.Available(latestName, currentVersion, apk)
        } else {
            SelfUpdateStatus.UpToDate(currentVersion)
        }
    }
}
