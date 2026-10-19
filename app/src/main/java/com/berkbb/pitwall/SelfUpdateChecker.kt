package com.berkbb.pitwall

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

const val SELF_RELEASE_API_URL = "https://api.github.com/repos/berkbb/pitwall-updater/releases/latest"
const val SELF_RELEASES_PAGE = "github.com/berkbb/pitwall-updater/releases"

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
                    val apk = selectApk(tag, parseAssets(json.optJSONArray("assets")), android.os.Build.SUPPORTED_ABIS)
                    statusFor(tag, currentVersion, apk)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> SelfUpdateStatus.Unavailable
                else -> throw IOException("HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Pure comparison of a release tag ("v1.0.8") with the installed version name ("1.0.7"). */
    private fun parseAssets(array: JSONArray?): List<ReleaseAsset> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i ->
            val asset = array.getJSONObject(i)
            ReleaseAsset(
                name = asset.optString("name", ""),
                downloadUrl = asset.optString("browser_download_url", ""),
                size = asset.optLong("size", 0L),
                digest = if (asset.isNull("digest")) null else asset.optString("digest", "")
            )
        }
    }

    /** Release APK names (see package.sh) by Android ABI; the universal APK is the fallback. */
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
        return ReleaseInfo(tag.trim(), asset.downloadUrl, asset.size, ReleaseResolver.parseSha256(asset.digest))
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
