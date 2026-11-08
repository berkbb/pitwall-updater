package com.berkbb.pitwall

import org.json.JSONArray

const val F1TV_PACKAGE_NAME = "com.formulaone.production"
const val UPSTREAM_REPO = "Alexvbp/f1tv-4k-patch"
const val UPSTREAM_RELEASES_URL = "https://api.github.com/repos/$UPSTREAM_REPO/releases?per_page=20"

/**
 * One GitHub release asset. [apiUrl] is the same asset on api.github.com (the asset's `url` field),
 * kept only for the expected repository; it is the fallback when the github.com download fails
 * (on 2026-11-08 one GitHub region answered 504 for every release download while the API kept working).
 */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val digest: String?,
    val apiUrl: String? = null
)

data class RawRelease(
    val tagName: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val assets: List<ReleaseAsset>
)

data class ReleaseInfo(
    val tag: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String?,
    val apiUrl: String? = null
)

enum class UpdateStatus { NOT_INSTALLED, UPDATE_AVAILABLE, UP_TO_DATE }

/**
 * Pure (Android-free) release selection and version comparison logic.
 */
object ReleaseResolver {

    private val VERSION_PREFIX = Regex("^[vV]?(\\d+(?:\\.\\d+)*)")
    private val STABLE_TAG = Regex("^[vV]?\\d+(?:\\.\\d+)*$")

    /** Extracts the leading numeric version: "v3.0.49.4" and "3.0.49.4-SP166.4.1-release-R54.2-tv" -> [3, 0, 49, 4]. */
    fun parseVersion(raw: String?): List<Int> {
        val match = VERSION_PREFIX.find(raw?.trim().orEmpty()) ?: return emptyList()
        return match.groupValues[1].split('.').map { it.toIntOrNull() ?: 0 }
    }

    /** Segment-wise numeric comparison; missing segments count as 0. */
    fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    /** True only for plain version tags such as "v3.0.49.4" (rejects "-dev", "-shield-…" variants). */
    fun isStableTag(tag: String): Boolean = STABLE_TAG.matches(tag.trim())

    /** Normalizes a GitHub asset digest ("sha256:abc…") into a lowercase hex string, or null. */
    fun parseSha256(digest: String?): String? {
        val value = digest?.trim()?.lowercase() ?: return null
        val hex = value.removePrefix("sha256:")
        return if (value.startsWith("sha256:") && hex.length == 64 && hex.all { it in "0123456789abcdef" }) hex else null
    }

    /** The asset's api.github.com address, kept only when it belongs to [repo] ("owner/name"); anything else is null. */
    fun apiAssetUrl(url: String?, repo: String): String? {
        val prefix = "https://api.github.com/repos/$repo/releases/assets/"
        return url?.trim()?.takeIf { it.startsWith(prefix) && it.length > prefix.length }
    }

    /** The `assets` array of a release reply; [repo] decides which API asset addresses are trusted. */
    fun parseAssets(array: JSONArray?, repo: String): List<ReleaseAsset> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i ->
            val asset = array.getJSONObject(i)
            ReleaseAsset(
                name = asset.optString("name", ""),
                downloadUrl = asset.optString("browser_download_url", ""),
                size = asset.optLong("size", 0L),
                digest = if (asset.isNull("digest")) null else asset.optString("digest", ""),
                apiUrl = apiAssetUrl(asset.optString("url", ""), repo)
            )
        }
    }

    /** The upstream releases list reply (an array of releases) as [RawRelease]s. */
    fun parseReleases(array: JSONArray): List<RawRelease> {
        return (0 until array.length()).map { i ->
            val release = array.getJSONObject(i)
            RawRelease(
                tagName = release.optString("tag_name", ""),
                isDraft = release.optBoolean("draft", false),
                isPrerelease = release.optBoolean("prerelease", false),
                assets = parseAssets(release.optJSONArray("assets"), UPSTREAM_REPO)
            )
        }
    }

    /** Picks the newest stable release that ships an .apkm asset. */
    fun selectLatestStable(releases: List<RawRelease>): ReleaseInfo? {
        return releases
            .asSequence()
            .filter { !it.isDraft && !it.isPrerelease && isStableTag(it.tagName) }
            .mapNotNull { release ->
                val asset = release.assets.firstOrNull { it.name.endsWith(".apkm") && it.downloadUrl.isNotBlank() }
                    ?: return@mapNotNull null
                ReleaseInfo(release.tagName.trim(), asset.downloadUrl, asset.size, parseSha256(asset.digest), asset.apiUrl)
            }
            .sortedWith { a, b -> compareVersions(parseVersion(b.tag), parseVersion(a.tag)) }
            .firstOrNull()
    }

    fun evaluate(installedVersion: String?, isInstalled: Boolean, latestTag: String?): UpdateStatus {
        if (!isInstalled) return UpdateStatus.NOT_INSTALLED
        val latest = parseVersion(latestTag)
        if (latest.isEmpty()) return UpdateStatus.UP_TO_DATE
        return if (compareVersions(latest, parseVersion(installedVersion)) > 0) {
            UpdateStatus.UPDATE_AVAILABLE
        } else {
            UpdateStatus.UP_TO_DATE
        }
    }

    /** Cache file name bound to the release tag so a new release never reuses an older package. */
    fun cacheFileName(tag: String): String {
        val safe = tag.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "f1tv_$safe.apkm"
    }
}
