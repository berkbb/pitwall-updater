package com.berkbb.pitwall

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Tag-bound download cache for .apkm bundles. A cached file is only reused when it matches
 * the release's published SHA-256 digest (or, if GitHub provides none, its exact size).
 */
object ApkmCache {

    fun fileFor(cacheDir: File, release: ReleaseInfo): File = File(cacheDir, ReleaseResolver.cacheFileName(release.tag))

    fun partialFileFor(target: File): File = File(target.parentFile, target.name + ".part")

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }

    fun isValid(file: File, release: ReleaseInfo): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val expectedSha = release.sha256
        if (expectedSha != null) return sha256Of(file) == expectedSha
        return release.size > 0 && file.length() == release.size
    }

    /** Headroom for native library extraction and filesystem overhead during installation. */
    const val INSTALL_SAFETY_MARGIN_BYTES = 50L * 1024 * 1024

    /**
     * Free space needed to finish: the download itself (unless already cached) plus the copy
     * PackageInstaller stages from the bundle, plus a safety margin.
     */
    fun requiredFreeBytes(bundleSize: Long, alreadyCached: Boolean): Long {
        val size = bundleSize.coerceAtLeast(0L)
        val download = if (alreadyCached) 0L else size
        return download + size + INSTALL_SAFETY_MARGIN_BYTES
    }

    fun toMegabytesCeil(bytes: Long): Int {
        val mb = 1024L * 1024L
        return ((bytes.coerceAtLeast(0L) + mb - 1) / mb).toInt()
    }

    /** Removes cached bundles and partial downloads that do not belong to [keep]. */
    fun pruneExcept(cacheDir: File, keep: File) {
        cacheDir.listFiles()?.forEach { file ->
            val isBundle = file.name.endsWith(".apkm") || file.name.endsWith(".apkm.part")
            if (isBundle && file.name != keep.name) file.delete()
        }
    }
}
