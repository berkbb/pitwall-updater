package com.berkbb.pitwall

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
    data class Available(val latest: String, val current: String) : SelfUpdateStatus
    /** No published release, or the repository is not public (GitHub answers 404). */
    data object Unavailable : SelfUpdateStatus
    data object Failed : SelfUpdateStatus
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
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    statusFor(JSONObject(json).optString("tag_name", ""), currentVersion)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> SelfUpdateStatus.Unavailable
                else -> throw IOException("HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Pure comparison of a release tag ("v1.0.8") with the installed version name ("1.0.7"). */
    fun statusFor(tag: String, currentVersion: String): SelfUpdateStatus {
        val latest = ReleaseResolver.parseVersion(tag)
        if (latest.isEmpty()) return SelfUpdateStatus.Unavailable
        val latestName = latest.joinToString(".")
        return if (ReleaseResolver.compareVersions(latest, ReleaseResolver.parseVersion(currentVersion)) > 0) {
            SelfUpdateStatus.Available(latestName, currentVersion)
        } else {
            SelfUpdateStatus.UpToDate(currentVersion)
        }
    }
}
